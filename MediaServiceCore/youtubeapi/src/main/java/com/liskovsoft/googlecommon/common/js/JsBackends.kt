package com.liskovsoft.googlecommon.common.js

import com.whl.quickjs.android.QuickJSLoader
import com.whl.quickjs.wrapper.QuickJSContext
import com.whl.quickjs.wrapper.QuickJSException
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** leanview: what [GuardedContext] needs from an engine; implemented for QuickJS and the system sandbox, faked in the JVM tests. */
internal interface NativeJs {
    /** Runs the script and returns its value (a String is the only result callers accept). Throws [JsEngineException] on a script error. */
    fun eval(script: String): Any?

    /** Frees the engine's memory. Called on the engine's own thread. */
    fun destroy()

    /**
     * Stops the running [eval] from another thread, for real: the script ends and [eval] returns at once (with an error). False when
     * the engine cannot do that (QuickJS: the wrapper has no interrupt handler), then the script runs to its end.
     */
    fun terminate(): Boolean = false

    /** The engine, for the logs: QUICKJS or SANDBOX (a [JsBackend] name). */
    val engineName: String
        get() = JsBackend.QUICKJS.name
}

/**
 * leanview: the contexts of one engine, and the scripts that still run on contexts that were discarded after a timeout.
 *
 * A discarded context keeps its thread and its engine memory until its script ends (the engine cannot be stopped). [createForSolve]
 * refuses while such a script is running, so a solve (hundreds of MB of engine memory on a cold player) never starts next to
 * one that is still running; with the solve lock of the callers (JsEngines.solveLock) at most one context of a solve is alive in
 * the process, the discarded one included.
 */
internal class JsEngineSet(
    private val timeoutMs: Long,
    private val lateCapMs: Long,
    private val stackBytes: Long,
    private val threadName: String,
    private val native: () -> NativeJs
) {
    private val discardedRunning = AtomicInteger()
    private val pastCap = AtomicInteger()

    /** Called (on a timer thread) when a discarded script passes the hard cap and is still running: the engine is wedged. */
    @Volatile
    var onWedged: Runnable? = null

    /** Scripts (or engine creations) still running on contexts discarded after a timeout. */
    fun discardedRunning(): Int = discardedRunning.get()

    /** Of those, the ones still running past the hard cap: no solve can run until they end, which may be never. */
    fun pastCapRunning(): Int = pastCap.get()

    fun create(): JsContext = create(native, stackBytes, threadName)

    /** A context of another engine ([engine], made on its own thread with [stack] bytes of stack), under the same counts. */
    fun create(engine: () -> NativeJs, stack: Long, name: String): JsContext =
        GuardedContext(timeoutMs, stack, name, lateCapMs, discardedRunning, pastCap, { onWedged?.run() }, engine)

    /** @throws JsEngineBusyException while the script of a discarded context is still running */
    fun createForSolve(): JsContext = createForSolve(native, stackBytes, threadName)

    /** @throws JsEngineBusyException while the script of a discarded context is still running */
    fun createForSolve(engine: () -> NativeJs, stack: Long, name: String): JsContext {
        if (discardedRunning.get() > 0) {
            throw JsEngineBusyException("the script of a discarded context is still running: no second solve next to it")
        }
        return create(engine, stack, name)
    }
}

/**
 * leanview: one engine context on its own thread, with a deadline on every call.
 *
 * - QuickJS recurses on the native stack and its context is bound to the thread that made it, so the context lives on a thread of
 *   its own with a big stack ([stackBytes]) and every call is handed to that thread and waited for.
 * - QuickJS has no interrupt handler (wrapper 3.2.3), so a script that does not end cannot be stopped. A call that is still
 *   running after [timeoutMs] throws [JsTimeoutException] to its caller (who releases its locks), and the context is DISCARDED:
 *   never used again; its thread runs the script to its end (the memory limit bounds a runaway allocation), hands the outcome to
 *   the caller's [LateResult] if it gave one (unless [lateCapMs] since the call passed first: then the LateResult gets a
 *   [JsTimeoutException] and the script is abandoned for good), then frees the engine memory and ends. Until then the script
 *   counts in [discardedRunning]; [abandonedCount] counts the discards of the process. A discarded script (with or without a
 *   LateResult) that is still running at [lateCapMs] also counts in [pastCap] until it ends, and [onPastCap] is called.
 * - An engine that CAN stop a script ([NativeJs.terminate], the system sandbox) is stopped at the deadline instead: the script ends
 *   at once, its outcome (an error) goes to the LateResult, the engine is freed, and the caller waits up to [TERMINATE_WAIT_MS] for
 *   that release, so the next solve is not refused as busy and nothing is left to pass the hard cap (no wedge).
 * - The same holds for the creation of the engine: one that is not ready in time is destroyed when (if) it is made.
 * - A caller that is interrupted keeps waiting (the interrupt flag is restored when the call ends): the solve finishes and its
 *   result reaches the caller, which caches it, instead of being thrown away for a user who has moved on.
 * - [close] can be called any number of times.
 */
internal class GuardedContext(
    private val timeoutMs: Long,
    stackBytes: Long,
    threadName: String,
    private val lateCapMs: Long = timeoutMs,
    private val discardedRunning: AtomicInteger = AtomicInteger(),
    private val pastCap: AtomicInteger = AtomicInteger(),
    private val onPastCap: () -> Unit = {},
    create: () -> NativeJs
) : JsContext {
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(null, r, threadName, stackBytes).apply { isDaemon = true } }
    private val closed = AtomicBoolean(false)
    private val destroyed = AtomicBoolean(false)

    @Volatile
    private var discarded = false
    private val engine: NativeJs

    init {
        val started = System.nanoTime()
        val made = executor.submit(Callable { create() })
        engine = try {
            waitFor(made)
        } catch (e: JsTimeoutException) {
            // the engine may still be made after the deadline: whatever create() returns is destroyed on its own thread
            discard(made, started, null, null) {
                try {
                    made.get().destroy()
                } catch (_: ExecutionException) {
                }
            }
            throw e
        } catch (t: Throwable) {
            executor.shutdownNow() // no thread of 16 MB left behind when the engine cannot start
            throw t
        }
    }

    private fun <T> waitFor(future: Future<T>): T {
        var interrupted = false
        try {
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
            while (true) {
                val left = deadline - System.nanoTime()
                try {
                    if (left <= 0) {
                        throw TimeoutException()
                    }
                    return future.get(left, TimeUnit.NANOSECONDS)
                } catch (e: InterruptedException) {
                    interrupted = true
                } catch (e: TimeoutException) {
                    throw JsTimeoutException("the script did not end in ${timeoutMs / 1000} s; its context was discarded")
                } catch (e: ExecutionException) {
                    throw e.cause ?: JsEngineException("script call failed", e)
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt()
            }
        }
    }

    private fun <T> call(late: ((T?, Throwable?) -> Unit)?, block: () -> T): T = call(late, null, block)

    private fun <T> call(late: ((T?, Throwable?) -> Unit)?, afterRelease: Runnable?, block: () -> T): T {
        if (discarded) {
            throw JsEngineException("the script context was discarded after a timeout")
        }
        val started = System.nanoTime()
        val future = try {
            executor.submit(Callable { block() })
        } catch (e: RejectedExecutionException) {
            throw JsEngineException("the script context is closed", e)
        }
        try {
            return waitFor(future)
        } catch (e: JsTimeoutException) {
            val released = discard(future, started, late, afterRelease) { destroyOnce() }
            if (stopScript()) {
                awaitQuietly(released, TERMINATE_WAIT_MS) // a stopped script releases its engine at once
            }
            throw e
        }
    }

    private fun stopScript(): Boolean = try {
        engine.terminate()
    } catch (_: Throwable) {
        false
    }

    private fun awaitQuietly(latch: CountDownLatch?, ms: Long) {
        if (latch == null) {
            return
        }
        var interrupted = false
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(ms)
        while (true) {
            val left = deadline - System.nanoTime()
            if (left <= 0) {
                break
            }
            try {
                if (latch.await(left, TimeUnit.NANOSECONDS)) {
                    break
                }
            } catch (_: InterruptedException) {
                interrupted = true
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt()
        }
    }

    /**
     * Never used again. Queued behind the running [script] on the engine's thread: the outcome goes to [late] (unless the hard cap
     * passed first), then [free], then the script stops counting as running (the returned latch opens), then [afterRelease].
     */
    private fun <T> discard(script: Future<T>, startedNs: Long, late: ((T?, Throwable?) -> Unit)?, afterRelease: Runnable?, free: () -> Unit): CountDownLatch? {
        if (discarded) {
            return null
        }
        val released = CountDownLatch(1)
        discarded = true
        abandoned.incrementAndGet()
        discardedRunning.incrementAndGet()
        val state = AtomicInteger(RUNNING) // RUNNING -> PAST_CAP (the cap fired first) or ENDED (the script ended first)
        val capLeftNs = startedNs + TimeUnit.MILLISECONDS.toNanos(lateCapMs) - System.nanoTime()
        val cap = capTimer.schedule(Runnable {
            if (state.compareAndSet(RUNNING, PAST_CAP)) {
                pastCap.incrementAndGet()
                if (late != null) {
                    deliver(late, null, JsTimeoutException("the script did not end in ${lateCapMs / 1000} s; its result is not waited for any more"))
                }
                try {
                    onPastCap()
                } catch (_: Throwable) {
                }
            }
        }, maxOf(0L, capLeftNs), TimeUnit.NANOSECONDS)
        try {
            executor.execute {
                try {
                    cap.cancel(false)
                    val before = state.getAndSet(ENDED)
                    if (before == PAST_CAP) {
                        pastCap.decrementAndGet()
                    } else if (late != null) {
                        try {
                            deliver(late, script.get(), null)
                        } catch (e: ExecutionException) {
                            deliver(late, null, e.cause ?: e)
                        }
                    }
                    free()
                } catch (_: Throwable) {
                } finally {
                    discardedRunning.decrementAndGet()
                    released.countDown()
                }
                try {
                    afterRelease?.run() // only now: a solve it starts is no longer refused
                } catch (_: Throwable) {
                }
            }
        } catch (_: RejectedExecutionException) {
            discardedRunning.decrementAndGet() // not expected: the executor is shut down only below
            released.countDown()
        }
        executor.shutdown()
        return released
    }

    private fun <T> deliver(late: (T?, Throwable?) -> Unit, value: T?, error: Throwable?) {
        try {
            late(value, error)
        } catch (_: Throwable) {
        }
    }

    private fun destroyOnce() {
        if (destroyed.compareAndSet(false, true)) {
            engine.destroy()
        }
    }

    override fun evalString(script: String): String {
        val r = call(null) { engine.eval(script) }
        return r as? String ?: throw JsResultUndefinedException("result is not a string: ${r?.javaClass?.simpleName}")
    }

    override fun evalString(script: String, late: LateResult): String = evalString(script, late, Runnable {})

    override fun evalString(script: String, late: LateResult, afterRelease: Runnable): String {
        val r = call<Any?>({ value, error ->
            when {
                error != null -> late.onLate(null, error)
                value is String -> late.onLate(value, null)
                else -> late.onLate(null, JsResultUndefinedException("result is not a string: ${value?.javaClass?.simpleName}"))
            }
        }, afterRelease) { engine.eval(script) }
        return r as? String ?: throw JsResultUndefinedException("result is not a string: ${r?.javaClass?.simpleName}")
    }

    override fun evalVoid(script: String) {
        call(null) { engine.eval(script) }
    }

    override fun engineName(): String = engine.engineName

    override fun close() {
        if (!closed.compareAndSet(false, true) || discarded) {
            return
        }
        try {
            call(null) { destroyOnce() }
        } catch (_: JsEngineException) {
            // a destroy that fails or times out leaves nothing to do: the context is discarded or the thread is ending
        } finally {
            executor.shutdown()
        }
    }

    companion object {
        private const val RUNNING = 0
        private const val PAST_CAP = 1
        private const val ENDED = 2

        /** How long a caller waits, after its deadline, for a script that the engine stopped to release the engine. */
        const val TERMINATE_WAIT_MS = 5_000L
        private val abandoned = AtomicInteger()

        /** Fires the hard cap of the late results; one daemon thread for the process, made on first use. */
        private val capTimer: ScheduledThreadPoolExecutor by lazy {
            ScheduledThreadPoolExecutor(1) { r -> Thread(r, "js-late-cap").apply { isDaemon = true } }.apply { removeOnCancelPolicy = true }
        }

        /** How many contexts were discarded after a timeout in this process. */
        fun abandonedCount(): Int = abandoned.get()
    }
}

/** leanview: wang.harlon.quickjs:wrapper-android (QuickJS, Java API, synchronous). */
internal class QuickJsNative private constructor(private val cx: QuickJSContext) : NativeJs {
    override fun eval(script: String): Any? {
        try {
            return cx.evaluate(script)
        } catch (e: QuickJSException) {
            throw JsEngineException(JsEngines.describe(e.message), e)
        }
    }

    override fun destroy() {
        cx.destroy()
    }

    companion object {
        @Volatile
        private var loaded = false

        /** Runs on the engine's own thread. */
        fun create(maxStackBytes: Int, memoryLimitBytes: Int): QuickJsNative {
            if (!loaded) {
                QuickJSLoader.init()
                loaded = true
            }
            val cx = QuickJSContext.create()
            try {
                cx.setMaxStackSize(maxStackBytes)
                cx.setMemoryLimit(memoryLimitBytes)
            } catch (t: Throwable) {
                cx.destroy()
                throw t
            }
            return QuickJsNative(cx)
        }
    }
}
