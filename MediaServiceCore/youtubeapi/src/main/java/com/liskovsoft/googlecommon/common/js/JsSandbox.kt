package com.liskovsoft.googlecommon.common.js

import com.google.gson.GsonBuilder
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** leanview: the two engines a context can run on. */
enum class JsBackend(val label: String) {
    /** The system's V8, in the sandboxed process of Android System WebView (androidx.javascriptengine). */
    SANDBOX("system JavaScriptSandbox (V8)"),

    /** QuickJS inside the app (wang.harlon.quickjs). */
    QUICKJS("QuickJS")
}

/** leanview: what the app needs from the system sandbox (androidx.javascriptengine.JavaScriptSandbox); faked in the JVM tests. */
internal interface SandboxService {
    /** Is [feature] (a `JavaScriptSandbox.JS_FEATURE_*` name) offered by this sandbox? */
    fun isFeatureSupported(feature: String): Boolean

    /**
     * A new isolate (a context of its own), with a heap limit of [maxHeapBytes] when the sandbox offers one.
     *
     * @throws JsBackendLostException when the sandbox cannot make one (its process died, it was closed)
     */
    fun newIsolate(maxHeapBytes: Long): SandboxIsolate

    /** Unbinds from the sandbox process. */
    fun close()

    /** Instrumented tests only: ends the sandbox process at once, as a crash would. */
    fun killForTest() {}
}

/** leanview: one isolate of the system sandbox. */
internal interface SandboxIsolate {
    /**
     * Runs [script], blocking, and returns its result (the sandbox gives a non-string result as "").
     *
     * @throws JsEngineException on a script error
     * @throws SandboxOutOfMemory when the script hit the heap limit (the sandbox process ends with it)
     * @throws SandboxIsolateTerminated when the isolate was stopped ([terminate] or the sandbox itself)
     * @throws JsBackendLostException when the sandbox process died or failed
     */
    fun evaluate(script: String): String

    /** Stops a running [evaluate] from another thread: it fails at once and the isolate is gone. */
    fun terminate()

    fun close()
}

/** leanview: the isolate was stopped while a script ran. */
internal class SandboxIsolateTerminated(message: String, cause: Throwable? = null) : JsEngineException(message, cause)

/**
 * leanview: the script hit the heap limit. Measured on the API 36 emulator (WebView 133): the sandbox process ends with it, and the
 * next isolate fails with "sandbox was dead before call to createIsolate".
 */
internal class SandboxOutOfMemory(message: String, cause: Throwable? = null) : JsEngineException(message, cause)

/**
 * leanview: a context of the system sandbox behind [GuardedContext].
 *
 * - [terminate] stops the script for real (JS_FEATURE_ISOLATE_TERMINATION is required to choose this backend), so a timeout never
 *   leaves a script running.
 * - The sandbox gives every non-string result as "". When [typed], each script is run through an indirect eval that tags its
 *   result, so a non-string comes back as null (the [JsContext] contract: JsResultUndefinedException). This copies the script once
 *   more; the solve contexts are not typed (their scripts always end in a string: `"";` and `JSON.stringify(...)`).
 * - The isolate stopped by anything but [terminate], or a sandbox that died, is reported to [onLost] (the sandbox is not used again
 *   in this process) and thrown as [JsBackendLostException]: the caller runs the call again on QuickJS.
 * - A script that hit the heap limit also ends the sandbox ([SandboxOutOfMemory]): reported to [onLost] too, but thrown as the
 *   script's own "out of memory" error, not run again (QuickJS has the same limit).
 */
internal class SandboxNative(
    private val isolate: SandboxIsolate,
    private val typed: Boolean,
    private val onLost: (String) -> Unit
) : NativeJs {
    @Volatile
    private var stopped = false

    override val engineName: String
        get() = JsBackend.SANDBOX.name

    override fun eval(script: String): Any? {
        try {
            val result = isolate.evaluate(if (typed) typedScript(script) else script)
            return if (typed) typedResult(result) else result
        } catch (e: SandboxOutOfMemory) {
            onLost("a script ran out of memory, which ends the sandbox: ${e.message}")
            throw JsEngineException("out of memory: ${e.message}", e)
        } catch (e: SandboxIsolateTerminated) {
            if (stopped) {
                throw JsTimeoutException("the script was stopped at the deadline: ${e.message}")
            }
            onLost("the isolate was stopped: ${e.message}")
            throw JsBackendLostException("the system JavaScript engine stopped the context: ${e.message}", e)
        } catch (e: JsBackendLostException) {
            if (stopped) {
                throw JsTimeoutException("the script was stopped at the deadline: ${e.message}")
            }
            onLost(e.message ?: "lost")
            throw e
        }
    }

    override fun terminate(): Boolean {
        stopped = true
        isolate.terminate()
        return true
    }

    override fun destroy() {
        try {
            isolate.close()
        } catch (_: Throwable) {
        }
    }

    companion object {
        private val gson = GsonBuilder().disableHtmlEscaping().create()

        /** The script, run by a global indirect eval (same scope as a script: `var` and functions are globals), its result tagged. */
        internal fun typedScript(script: String): String =
            "(function(){var r=(0,eval)(" + gson.toJson(script) + ");return typeof r==='string'?'s'+r:'u'+typeof r;})()"

        /** The tagged result of [typedScript]: the string, or null when the script ended in something else. */
        internal fun typedResult(result: String): String? = if (result.startsWith("s")) result.substring(1) else null
    }
}

/**
 * leanview: decides once per process which engine the contexts use, and keeps the one connection to the system sandbox.
 *
 * - [available]: the Android version and the WebView can run the sandbox at all (checked before any class of the library is used).
 * - [connect] is called at most once; the connection is reused by every context until the app ends (connecting costs time).
 * - The connection is resolved ONCE per process (J2 review, J2-1): one caller at a time waits for it, checks the features and
 *   decides; a caller that queued behind it finds the choice made. Comparing wrappers was not enough: the Android connection
 *   gives a new wrapper on every get(), so a second caller "lost the race" and closed the one sandbox of the process. Done here,
 *   not by caching the wrapper, so it holds for any [connect] (and the fake of the tests can give a new wrapper per get()).
 * - A connection that completes after [connectWaitMs] is closed when it arrives (J2-3); it is not cancelled, since nothing shows
 *   that cancelling the library's future unbinds a bind already in flight.
 * - The sandbox is chosen only when it offers every feature in [REQUIRED_FEATURES]; otherwise QuickJS, for the whole process.
 * - A caller that may not wait (the main thread: the connection completes on it) gets QuickJS for that one context while the
 *   choice is not made yet; the choice itself is not changed by it. The same while [ready] says no (the app context is not known
 *   yet): such an early context must not decide the engine of the whole process.
 * - [lost]: the sandbox died or failed: QuickJS from then on, until the app restarts.
 * - The choice is logged once ([log]); a loss is logged once too.
 */
internal class JsBackendSelector(
    private val available: () -> Boolean,
    private val connect: () -> Future<SandboxService>,
    private val connectWaitMs: Long = CONNECT_WAIT_MS,
    private val log: (String) -> Unit = {},
    private val ready: () -> Boolean = { true }
) {
    private val lock = Any()

    /** Held while the one connection is waited for and checked: only one caller does that (J2-1). */
    private val resolveLock = Any()

    @Volatile
    private var decided: JsBackend? = null

    @Volatile
    private var service: SandboxService? = null
    private var pending: Future<SandboxService>? = null
    private var reason: String = ""

    /** The engine chosen for the process, or null while not chosen yet. */
    fun chosen(): JsBackend? = decided

    /** Why that engine was chosen (or why the sandbox was dropped). */
    fun reason(): String = reason

    /** Starts the connection if it is not started; never waits. */
    fun startConnecting() {
        if (!isReady()) {
            return
        }
        synchronized(lock) {
            if (decided == null) {
                start()
            }
        }
    }

    /**
     * The engine for a new context and, for [JsBackend.SANDBOX], the connection to use.
     *
     * @param mayWait false on the main thread: a choice that is not made yet gives QuickJS for this context without waiting
     */
    fun choose(mayWait: Boolean): Pair<JsBackend, SandboxService?> {
        decided?.let { return Pair(it, service) }
        if (!isReady()) {
            return Pair(JsBackend.QUICKJS, null) // too early to decide for the process
        }
        val future = synchronized(lock) {
            decided?.let { return Pair(it, service) }
            start() ?: return Pair(decided ?: JsBackend.QUICKJS, null)
        }
        if (!mayWait && !future.isDone) {
            return Pair(JsBackend.QUICKJS, null)
        }
        synchronized(resolveLock) {
            decided?.let { return Pair(it, service) } // resolved by the caller this one queued behind
            resolve(future)
        }
        return Pair(decided ?: JsBackend.QUICKJS, service)
    }

    /** Under [resolveLock]: waits for the connection, checks its features and decides. */
    private fun resolve(future: Future<SandboxService>) {
        val connected: SandboxService? = try {
            future.get(connectWaitMs, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            decide(JsBackend.QUICKJS, null, "the system JavaScript engine did not connect in ${connectWaitMs / 1000} s")
            closeWhenDone(future)
            null
        } catch (e: ExecutionException) {
            decide(JsBackend.QUICKJS, null, "the system JavaScript engine could not connect: ${e.cause ?: e}")
            null
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            return // this caller only (QuickJS for its context); the choice is made by the next one
        } catch (e: Throwable) {
            decide(JsBackend.QUICKJS, null, "the system JavaScript engine could not connect: $e")
            null
        }
        if (connected != null) {
            val missing = REQUIRED_FEATURES.filterNot { feature ->
                try {
                    connected.isFeatureSupported(feature)
                } catch (_: Throwable) {
                    false
                }
            }
            if (missing.isEmpty()) {
                val heap = try {
                    connected.isFeatureSupported(FEATURE_MAX_HEAP_SIZE)
                } catch (_: Throwable) {
                    false
                }
                decide(JsBackend.SANDBOX, connected, "the system sandbox offers ${REQUIRED_FEATURES.joinToString()}" +
                        if (heap) " and a heap limit" else " (no heap limit)")
            } else {
                decide(JsBackend.QUICKJS, null, "the system sandbox lacks ${missing.joinToString()}")
                closeQuietly(connected)
            }
        }
    }

    /** A connection that comes after the choice was made without it is closed when it arrives (it is never used). */
    private fun closeWhenDone(future: Future<SandboxService>) {
        Thread({
            try {
                closeQuietly(future.get())
            } catch (_: Throwable) {
                // it failed or was cancelled: nothing is bound
            }
        }, "jssandbox-late-close").apply { isDaemon = true }.start()
    }

    /** The sandbox died, failed or lost a feature: QuickJS for the rest of the process. */
    fun lost(why: String) {
        val old: SandboxService?
        synchronized(lock) {
            if (decided == JsBackend.QUICKJS) {
                return
            }
            old = service
            service = null
            decided = JsBackend.QUICKJS
            reason = "the system JavaScript engine was lost ($why): QuickJS until the app restarts"
        }
        log("JavaScript engine: ${JsBackend.QUICKJS.label}; $reason")
        closeQuietly(old)
    }

    /** Tests only: forget the choice (and the connection), or force QuickJS. */
    internal fun resetForTest(forceQuickJs: Boolean) {
        val old: SandboxService?
        synchronized(lock) {
            old = service
            service = null
            pending = null
            decided = if (forceQuickJs) JsBackend.QUICKJS else null
            reason = if (forceQuickJs) "forced by a test" else ""
        }
        closeQuietly(old)
    }

    private fun isReady(): Boolean = try {
        ready()
    } catch (_: Throwable) {
        false
    }

    /** Under [lock]. The pending connection, started now if needed; null when the sandbox is not available (QuickJS is chosen). */
    private fun start(): Future<SandboxService>? {
        pending?.let { return it }
        val ok = try {
            available()
        } catch (_: Throwable) {
            false
        }
        if (!ok) {
            decideLocked(JsBackend.QUICKJS, null, "the system sandbox is not available on this device (Android 8 and a recent WebView are needed)")
            return null
        }
        return try {
            connect().also { pending = it }
        } catch (e: Throwable) {
            decideLocked(JsBackend.QUICKJS, null, "the system JavaScript engine could not connect: $e")
            null
        }
    }

    private fun decide(backend: JsBackend, connected: SandboxService?, why: String) {
        val unused: SandboxService?
        synchronized(lock) {
            unused = if (decided != null && connected !== service) connected else null
            decideLocked(backend, connected, why)
        }
        closeQuietly(unused) // a connection that lost the race is not kept
    }

    private fun decideLocked(backend: JsBackend, connected: SandboxService?, why: String) {
        if (decided != null) {
            return
        }
        service = if (backend == JsBackend.SANDBOX) connected else null
        reason = why
        decided = backend
        log("JavaScript engine: ${backend.label}; $why")
    }

    private fun closeQuietly(s: SandboxService?) {
        try {
            s?.close()
        } catch (_: Throwable) {
        }
    }

    companion object {
        const val FEATURE_ISOLATE_TERMINATION = "JS_FEATURE_ISOLATE_TERMINATION"
        const val FEATURE_EVALUATE_WITHOUT_TRANSACTION_LIMIT = "JS_FEATURE_EVALUATE_WITHOUT_TRANSACTION_LIMIT"
        const val FEATURE_MAX_HEAP_SIZE = "JS_FEATURE_ISOLATE_MAX_HEAP_SIZE"

        /**
         * What a solve needs: a script that times out is really stopped, and the ~3.5 MB script and its large result pass without
         * the binder transaction limit (about 1 MB).
         */
        val REQUIRED_FEATURES = listOf(FEATURE_ISOLATE_TERMINATION, FEATURE_EVALUATE_WITHOUT_TRANSACTION_LIMIT)

        /** The longest a caller waits for the first connection; a sandbox that is slower is not used in this process. */
        const val CONNECT_WAIT_MS = 20_000L

        /**
         * Whether the system sandbox may be asked for at all (J2-5): the Android version is checked FIRST, so on Android 6 and 7
         * [probe] (the code that touches androidx.javascriptengine, which needs API 26) never runs.
         */
        fun sandboxAllowed(sdk: Int, hasContext: Boolean, probe: () -> Boolean): Boolean = sdk >= 26 && hasContext && probe()
    }
}

/**
 * leanview: makes the contexts: on the engine [selector] chose, under the counts of [engines] (busy refusal, hard cap, wedge).
 * A sandbox that cannot make an isolate is reported lost and the context is made on QuickJS.
 */
internal class JsEngineHub(
    private val engines: JsEngineSet,
    private val selector: JsBackendSelector,
    private val quickJs: () -> NativeJs,
    private val heapBytes: () -> Long,
    private val stackBytes: Long,
    private val isMainThread: () -> Boolean
) {
    fun create(): JsContext = make(solve = false)

    fun createForSolve(): JsContext = make(solve = true)

    fun backend(): JsBackend? = selector.chosen()

    fun reason(): String = selector.reason()

    fun startConnecting() = selector.startConnecting()

    internal fun resetForTest(forceQuickJs: Boolean) = selector.resetForTest(forceQuickJs)

    private fun make(solve: Boolean): JsContext {
        val (backend, service) = selector.choose(mayWait = !isMainThread())
        val engine: () -> NativeJs = if (backend == JsBackend.SANDBOX && service != null) {
            { sandboxOrQuickJs(service, typed = !solve) }
        } else {
            quickJs
        }
        val name = if (backend == JsBackend.SANDBOX) "jssandbox" else "quickjs"
        return if (solve) engines.createForSolve(engine, stackBytes, name) else engines.create(engine, stackBytes, name)
    }

    private fun sandboxOrQuickJs(service: SandboxService, typed: Boolean): NativeJs = try {
        SandboxNative(service.newIsolate(heapBytes()), typed, selector::lost)
    } catch (e: JsBackendLostException) {
        selector.lost(e.message ?: "no isolate")
        quickJs()
    }
}
