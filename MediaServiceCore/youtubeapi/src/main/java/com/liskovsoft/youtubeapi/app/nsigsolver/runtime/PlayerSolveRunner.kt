package com.liskovsoft.youtubeapi.app.nsigsolver.runtime

import com.liskovsoft.googlecommon.common.js.JsBackendLostException
import com.liskovsoft.googlecommon.common.js.JsContext
import com.liskovsoft.googlecommon.common.js.JsEngineBusyException
import com.liskovsoft.googlecommon.common.js.JsEngineException
import com.liskovsoft.googlecommon.common.js.JsEngines
import com.liskovsoft.googlecommon.common.js.JsTimeoutException
import com.liskovsoft.googlecommon.common.js.LateResult
import com.liskovsoft.youtubeapi.app.prewarm.CurrentPlayerUrl
import com.liskovsoft.youtubeapi.app.prewarm.PlayerFailureBreaker
import java.util.concurrent.atomic.AtomicReference

/** leanview: the player is not tried now because it failed too often or timed out (see PlayerFailureBreaker). */
internal class PlayerCircuitOpenException(message: String) : JsEngineException(message)

/**
 * leanview: runs the solve of one player on the JavaScript engine with the rules the regular and the TCL solver share.
 *
 * - One solve at a time in the process ([lock], shared by both solvers), and none while the script of a discarded context is
 *   still running ([newContext] throws JsEngineBusyException; that is not held against the player, nothing failed).
 * - The [breaker] is asked again once the lock is held, so callers that queued behind a solve that failed or timed out do not
 *   each start their own.
 * - Each outcome counts once in the breaker: an engine error is a failure, an output the solver finds invalid is one too
 *   ([invalid], called by the solver that parsed it), a valid output clears it ([solved]). Network failures never reach it.
 * - A solve that outlives the caller's deadline is not thrown away: the caller gets the JsTimeoutException at once, and when the
 *   script ends with an output that [LateOutput.parse] finds valid, the breaker is cleared. The output is kept only if its player
 *   is the app's CURRENT player ([currentPlayerUrl], what the app info names now; [solverUrlOf] maps it to this solver's url),
 *   never because of what a solver was asked last (the app's own fallback asks for another url right after a timeout). It is
 *   held in memory, not written to the one-entry cache section yet: the extractor the app still uses (a fallback player) keeps
 *   its cached player. Once the discarded context is released (not before: its solve would be refused as busy), [onLateSolved]
 *   asks for the extractor of the current player to be rebuilt; that solve takes the held
 *   output ([takeLate]), which writes it to the cache ([store]) at that moment. A late output of a player that is no longer
 *   current is dropped. The breaker opens only if the late run fails or passes the engine's hard cap.
 */
internal class PlayerSolveRunner(
    private val breaker: PlayerFailureBreaker = PlayerFailureBreaker.shared,
    private val newContext: () -> JsContext = JsEngines::createForSolve,
    private val isRefused: () -> Boolean = JsEngines::isSolveRefused,
    private val lock: Any = JsEngines.solveLock,
    private val clock: () -> Long = System::currentTimeMillis,
    private val currentPlayerUrl: () -> String? = CurrentPlayerUrl::get,
    private val solverUrlOf: (appPlayerUrl: String) -> String = { it },
    private val store: (playerUrl: String, preprocessedPlayer: String) -> Unit = { _, _ -> },
    private val onLateSolved: (appPlayerUrl: String) -> Unit = {},
    private val log: (String) -> Unit = ::logSolve
) {
    /** A valid solver output that came after its caller gave up; [preprocessedPlayer] is what the solver caches (may be absent). */
    class LateSolve(val preprocessedPlayer: String?)

    /** How a solver reads an output that came after its caller gave up. Runs on the engine's thread. */
    fun interface LateOutput {
        /** The parsed output, or null when it is not a valid solver output. */
        fun parse(stdout: String): LateSolve?
    }

    /** The held late output: (solver player url, preprocessed player). */
    private val held = AtomicReference<Pair<String, String>?>(null)

    /**
     * The checks to make before anything is downloaded for a solve. It does not decide what the app's current player is.
     *
     * @throws PlayerCircuitOpenException when the player is not to be tried now
     * @throws JsEngineBusyException while the script of a discarded context is still running
     */
    fun precheck(playerUrl: String) {
        if (breaker.isOpen(playerUrl, clock())) {
            throw PlayerCircuitOpenException("player $playerUrl failed too often, not tried again for a while")
        }
        if (isRefused()) {
            throw JsEngineBusyException("a solve that timed out is still running, not started next to it")
        }
    }

    /**
     * Runs [init] (the solver's libraries; its result is ignored) and then [solve] in a new context.
     *
     * @return the solve's output, which the caller parses and then reports with [solved] or [invalid]
     * @throws JsEngineException: PlayerCircuitOpenException, JsEngineBusyException, JsTimeoutException (the solve's late run
     *   decides about the breaker), or the script error (counted as a failure)
     *
     * When the system JavaScript engine is lost during the solve (JsBackendLostException: its process died, or it stopped offering
     * what the solve needs), the solve runs once more, from the start, in a new context (on QuickJS: the lost engine is not used
     * again in this process). The loss is not counted against the player.
     *
     * Every solve logs one line: the engine its (last) context ran on, the time, and how it ended (J2 review: which engine each
     * video used must be visible in the timings).
     */
    fun run(playerUrl: String, init: String, solve: String, late: LateOutput): String {
        synchronized(lock) {
            if (breaker.isOpen(playerUrl, clock())) {
                throw PlayerCircuitOpenException("player $playerUrl failed too often, not tried again for a while")
            }
            val started = clock()
            var engine = "?"
            var retried = false
            var outcome = "error"
            try {
                val output = try {
                    runOnce(playerUrl, init, solve, late) { engine = it }
                } catch (e: JsBackendLostException) {
                    retried = true
                    runOnce(playerUrl, init, solve, late) { engine = it }
                }
                outcome = "ok"
                return output
            } catch (e: Throwable) {
                outcome = e.javaClass.simpleName
                throw e
            } finally {
                log("solve engine=$engine ms=${clock() - started}${if (retried) " retried" else ""} outcome=$outcome player=${shortUrl(playerUrl)}")
            }
        }
    }

    /** One solve in one context; the caller holds [lock]. [onEngine] gets the engine of that context. */
    private fun runOnce(playerUrl: String, init: String, solve: String, late: LateOutput, onEngine: (String) -> Unit): String {
        val context = newContext()
        onEngine(context.engineName().ifEmpty { "?" })
        try {
            try {
                context.evalString(init)
            } catch (e: JsTimeoutException) {
                breaker.recordTimeout(playerUrl, clock()) // the libraries did not load in time: no solve is coming from this run
                throw e
            }
            // the rebuild is asked for only after the discarded context is released (asked earlier, its solve is refused as busy)
            val rebuild = AtomicReference<String?>(null)
            return context.evalString(solve,
                LateResult { stdout, error -> settleLate(playerUrl, stdout, error, late, rebuild) },
                Runnable { rebuild.getAndSet(null)?.let(onLateSolved) })
        } catch (e: JsTimeoutException) {
            throw e
        } catch (e: JsEngineBusyException) {
            throw e
        } catch (e: JsBackendLostException) {
            throw e // not the player's fault: run() tries once more
        } catch (e: JsEngineException) {
            breaker.recordFailure(playerUrl, clock())
            throw e
        } finally {
            context.close()
        }
    }

    /**
     * The preprocessed player of a late solve of [playerUrl], if one is held: it is written to the cache now ([store]) and
     * returned, for the solve that is about to start. Null when none is held for this url.
     */
    fun takeLate(playerUrl: String): String? {
        val late = held.get() ?: return null
        if (late.first != playerUrl || !held.compareAndSet(late, null)) {
            return null
        }
        store(playerUrl, late.second)
        return late.second
    }

    /** The output of [run] was a valid solve. */
    fun solved(playerUrl: String) {
        breaker.recordSuccess(playerUrl)
    }

    /** The output of [run] could not be used (not JSON, or the solver's own error). */
    fun invalid(playerUrl: String) {
        breaker.recordFailure(playerUrl, clock())
    }

    private fun settleLate(playerUrl: String, stdout: String?, error: Throwable?, late: LateOutput, rebuild: AtomicReference<String?>) {
        val output = if (stdout != null && error == null) {
            try {
                late.parse(stdout)
            } catch (_: Throwable) {
                null
            }
        } else {
            null
        }
        if (output == null) {
            breaker.recordTimeout(playerUrl, clock())
            return
        }
        breaker.recordSuccess(playerUrl)
        val current = currentPlayerUrl()
        if (current == null || solverUrlOf(current) != playerUrl) {
            return // a player the app does not use now: dropped, no cache is touched
        }
        output.preprocessedPlayer?.let { held.set(Pair(playerUrl, it)) }
        rebuild.set(current) // asked for once the context is released
    }
}

/** The player part of a player url ("3d3ba064/player_ias.vflset"), short enough for one log line. */
private fun shortUrl(playerUrl: String): String = playerUrl.substringAfter("/player/").substringBeforeLast("/")

private fun logSolve(line: String) {
    try {
        android.util.Log.i("PlayerSolveRunner", line)
    } catch (_: Throwable) {
        // JVM tests: android.util.Log is not there
    }
}
