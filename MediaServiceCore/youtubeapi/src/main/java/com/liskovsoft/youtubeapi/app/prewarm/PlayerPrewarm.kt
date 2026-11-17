package com.liskovsoft.youtubeapi.app.prewarm

import com.liskovsoft.googlecommon.common.js.JsEngines
import com.liskovsoft.sharedutils.mylogger.Log
import com.liskovsoft.youtubeapi.app.AppService
import com.liskovsoft.youtubeapi.common.helpers.AppClient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.function.Supplier

/**
 * leanview: what one prewarm run found: the player url it worked on (null when unknown), whether that player is now solved and
 * valid, and whether YouTube's player changed during the run (then the run says nothing about either player).
 */
class PrewarmResult(val playerUrl: String?, val solved: Boolean, val superseded: Boolean = false)

/**
 * leanview: solves the challenge of the current YouTube player in the background (downloads the player, preprocesses it and tests
 * its functions) so the first video does not wait for it. QuickJS needs seconds for that where V8 needed one.
 *
 * - [request] only puts a small task on [executor] and returns: no lock, no warm check, no network on the caller, whatever thread
 *   the caller is (the splash asks on the UI thread, the app-info refresh asks while it holds the app-info lock). The warm
 *   check and the work run on the executor's thread;
 * - [executor] MUST run its tasks one at a time (a single thread): that is what makes runs never overlap; a request that comes
 *   during a run is decided after the run ends (a change to the player that was just solved then does nothing);
 * - the work is `AppService.prewarmPlayer`, which uses the disk cache of the preprocessed player first;
 * - success is a VALID extractor for the player url ([PrewarmResult.solved]); a run whose player changed while it ran is
 *   superseded (the change's own request decides); anything else is a failure of that player url: back-off for that url only;
 * - the [PlayerFailureBreaker] is only read here (a player that keeps failing or timed out is not tried): the solvers count their
 *   own outcomes in it, once each, and a network failure of the prewarm is not a failure of the player.
 */
class PlayerPrewarm internal constructor(
    private val executor: Executor,
    private val clock: () -> Long,
    private val isWarm: () -> Boolean,
    /** The run; its argument is the player url of a LATE_SOLVE request (that player's extractor is built), else null. */
    private val work: (lateSolvedUrl: String?) -> PrewarmResult,
    private val policy: PrewarmPolicy = PrewarmPolicy(),
    private val breaker: PlayerFailureBreaker = PlayerFailureBreaker(),
    private val log: (String) -> Unit = {}
) {
    private val queued = ConcurrentHashMap.newKeySet<Pair<PrewarmReason, String?>>()

    // only touched by the executor's thread
    private var solvedPlayerUrl: String? = null
    private var lastAttemptUrl: String? = null
    private val lastFailureAtMs = object : LinkedHashMap<String?, Long>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String?, Long>?): Boolean = size > MAX_REMEMBERED_FAILURES
    }

    /** Never blocks, never throws. A request equal to one that is waiting in the queue is dropped. */
    fun request(reason: PrewarmReason, playerUrl: String? = null) {
        val key = Pair(reason, playerUrl)
        if (!queued.add(key)) {
            return
        }
        try {
            executor.execute {
                queued.remove(key)
                handle(reason, playerUrl)
            }
        } catch (e: RejectedExecutionException) {
            queued.remove(key)
        }
    }

    private fun handle(reason: PrewarmReason, playerUrl: String?) {
        val now = clock()
        val urlKey = playerUrl ?: lastAttemptUrl
        val decision = policy.decide(reason, playerUrl, solvedPlayerUrl, isWarmSafe(), now, lastFailureAtMs[urlKey], breaker.isOpen(urlKey, now))
        log("prewarm $reason -> $decision")
        if (decision != PrewarmDecision.RUN) {
            return
        }
        val result = try {
            work(if (reason == PrewarmReason.LATE_SOLVE) playerUrl else null)
        } catch (e: Throwable) {
            log("prewarm failed: ${e.message}")
            PrewarmResult(null, false)
        }
        val finished = clock()
        val url = result.playerUrl ?: urlKey
        lastAttemptUrl = url ?: lastAttemptUrl
        when {
            result.solved -> {
                solvedPlayerUrl = result.playerUrl
                lastFailureAtMs.remove(url)
            }
            result.superseded -> {} // no back-off: the request of the new player is queued and decides on its own
            else -> lastFailureAtMs[url] = finished
        }
        log("prewarm $reason done in ${finished - now} ms, solved=${result.solved}, superseded=${result.superseded}")
    }

    private fun isWarmSafe(): Boolean = try {
        isWarm()
    } catch (_: Throwable) {
        false
    }

    companion object {
        private const val TAG = "PlayerPrewarm"
        private const val MAX_REMEMBERED_FAILURES = 8

        @Volatile
        private var clientProvider: Supplier<*>? = null
        private val shared: PlayerPrewarm by lazy {
            PlayerPrewarm(
                // one thread: the runs cannot overlap
                executor = Executors.newSingleThreadExecutor { r ->
                    Thread(r, "player-prewarm").apply {
                        isDaemon = true
                        priority = Thread.NORM_PRIORITY - 1
                    }
                },
                clock = System::currentTimeMillis,
                isWarm = {
                    // leanview: the first step of every request, on this thread: connect to the system JavaScript engine early (it
                    // costs time and is reused by every solve); never waits
                    JsEngines.startConnecting()
                    AppService.instance().isPlayerSolved(clientProvider?.get() as AppClient?)
                },
                work = { lateSolvedUrl ->
                    val app = AppService.instance()
                    runWork({ app.prewarmPlayer(clientProvider?.get() as AppClient?, lateSolvedUrl) }, { playerUrlOrNull(app) })
                },
                breaker = PlayerFailureBreaker.shared,
                log = { Log.d(TAG, it) }
            )
        }

        /**
         * One run: [prewarm] solves and returns the url of the valid extractor of the current player (or null); [playerUrl] reads
         * the current player url, before and after, to tell a run superseded by a player change from a failure.
         */
        internal fun runWork(prewarm: () -> String?, playerUrl: () -> String?): PrewarmResult {
            val before = playerUrl()
            val solved = prewarm()
            val after = playerUrl()
            return PrewarmResult(solved ?: after, solved != null, superseded = solved == null && before != null && after != null && before != after)
        }

        private fun playerUrlOrNull(app: AppService): String? = try {
            app.playerUrl
        } catch (_: Throwable) {
            null
        }

        @JvmStatic
        fun instance(): PlayerPrewarm = shared

        /** The client the next video will use (an AppClient) decides which player flavor is solved (regular or TCL). */
        @JvmStatic
        fun setClientProvider(provider: Supplier<*>?) {
            clientProvider = provider
        }
    }
}
