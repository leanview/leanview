package com.liskovsoft.googlecommon.common.js

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Looper
import com.liskovsoft.sharedutils.mylogger.Log
import com.liskovsoft.sharedutils.prefs.GlobalPreferences

/**
 * leanview: the one place that creates the JavaScript engine. Two engines, chosen once per process ([JsBackendSelector]):
 * - the system's V8 in the sandboxed process of Android System WebView (androidx.javascriptengine, Apache-2.0), when the device
 *   has it with every feature a solve needs (Android 8 or later and a recent WebView); it is a JIT, 3 to 4 times faster than
 *   QuickJS on the TV's ARM CPU, and a script that times out is really stopped;
 * - otherwise QuickJS (MIT) through wang.harlon.quickjs (Apache-2.0), unchanged.
 * The engine this replaced was licensed EPL-1.0, which is not GPL compatible.
 */
object JsEngines {
    private const val TAG = "JsEngines"

    private const val GENERATE_CALL = "return astring.generate(program);"
    private const val GENERATE_ARRAY = "var __parts = []; astring.generate(program, {output: {write: function (s) { __parts.push(s); }}}); return __parts.join('');"

    /**
     * Deadline of every script call: what the user waits at most. The slowest solve measured is the cold TCL player on an x86_64
     * emulator, 24 to 29 s; the target TV is a 32-bit ARM box running an interpreter, so the deadline is 6 times the emulator's
     * worst case. PROVISIONAL until measured on a real armeabi-v7a TV. A solve that is slower is not lost: its script runs on (see
     * [LATE_RESULT_CAP_MS]) and its result is stored for the next video.
     */
    const val SCRIPT_TIMEOUT_MS = 180_000L

    /**
     * How long the script of a solve whose caller gave up is still waited for (counted from the start of the call). A solve that
     * ends in this time is stored like any other; one that does not is abandoned for good and its player is not tried for a while.
     */
    const val LATE_RESULT_CAP_MS = 10 * 60 * 1000L

    /** QuickJS recurses on the native stack; the TCL player AST needs more than the wrapper's 256 KB, 12 MB is plenty (thread: 16 MB). */
    private const val MAX_JS_STACK_BYTES = 12 * 1024 * 1024
    private const val THREAD_STACK_BYTES = 16L * 1024 * 1024

    /**
     * Least memory limit of a context: the cold solve of the TCL player, measured on the emulator with the real player, ends with
     * "out of memory" at 256 MB and finishes at 320 MB (the regular player needs 192 MB). 320 MB is the measured floor.
     */
    const val MIN_MEMORY_LIMIT_BYTES = 320 * 1024 * 1024

    /** Largest memory limit of a context: twice the measured need, room for a player that grows. */
    const val MAX_MEMORY_LIMIT_BYTES = 640 * 1024 * 1024

    /**
     * One solve at a time in the process: the regular and the TCL solver both hold it while their context lives. With
     * [createForSolve] refusing next to a discarded script, at most one context of a solve exists at a time.
     */
    @JvmField
    val solveLock = Any()

    private val quickJs: () -> NativeJs = { QuickJsNative.create(MAX_JS_STACK_BYTES, memoryLimitBytes()) }

    private val engines = JsEngineSet(SCRIPT_TIMEOUT_MS, LATE_RESULT_CAP_MS, THREAD_STACK_BYTES, "quickjs", quickJs)

    private val hub = JsEngineHub(
        engines = engines,
        selector = JsBackendSelector(
            available = { JsBackendSelector.sandboxAllowed(Build.VERSION.SDK_INT, appContext() != null) { AndroidSandbox.isAvailable() } },
            connect = { AndroidSandbox.connect(appContext()!!) },
            log = { Log.i(TAG, it) },
            ready = { appContext() != null }
        ),
        quickJs = quickJs,
        heapBytes = { memoryLimitBytes().toLong() },
        stackBytes = THREAD_STACK_BYTES,
        isMainThread = { Looper.myLooper() == Looper.getMainLooper() }
    )

    @Volatile
    private var knownMemoryLimit = 0

    /** A context for a short script (no solve). */
    @JvmStatic
    fun create(): JsContext = hub.create()

    /**
     * A context for a solve; the caller holds [solveLock].
     *
     * @throws JsEngineBusyException while the script of a discarded context is still running (it holds its memory until it ends)
     */
    @JvmStatic
    fun createForSolve(): JsContext = hub.createForSolve()

    /** The engine chosen for this process, or null while it is not chosen yet (the first context chooses). */
    @JvmStatic
    fun backend(): JsBackend? = hub.backend()

    /** Why [backend] was chosen. */
    @JvmStatic
    fun backendReason(): String = hub.reason()

    /** Starts connecting to the system sandbox, without waiting (the prewarm does it early: connecting costs time). */
    @JvmStatic
    fun startConnecting() {
        try {
            hub.startConnecting()
        } catch (_: Throwable) {
        }
    }

    /** Instrumented tests only: run on QuickJS ([forceQuickJs]) or choose again as the app would. */
    internal fun resetBackendForTest(forceQuickJs: Boolean) = hub.resetForTest(forceQuickJs)

    /** Is a solve refused now (see [createForSolve])? A cheap check before a caller downloads anything for a solve. */
    @JvmStatic
    fun isSolveRefused(): Boolean = engines.discardedRunning() > 0

    /**
     * Is the engine wedged: a discarded script still running past [LATE_RESULT_CAP_MS]? No solve runs until it ends, which may be
     * never; only the end of the process frees it (the app does that at a safe point).
     */
    @JvmStatic
    fun isWedged(): Boolean = engines.pastCapRunning() > 0

    /** [listener] runs on a timer thread each time a discarded script passes the cap. */
    @JvmStatic
    fun setOnWedged(listener: Runnable?) {
        engines.onWedged = listener
    }

    /**
     * The memory a context may use: a script that allocates more ends with an "out of memory" error at this size. It bounds the
     * engine heap of ONE context only; the app and the video decoder need the rest of the device's memory, so the limit is a third
     * of the device's memory, between [MIN_MEMORY_LIMIT_BYTES] and [MAX_MEMORY_LIMIT_BYTES] ([MAX_MEMORY_LIMIT_BYTES] when the
     * device's memory is unknown). On a 1 to 2 GB TV the floor can still be more than the system lets the app use: the limit makes
     * a runaway script fail, it does not make a solve safe from the low-memory killer.
     */
    internal fun memoryLimitFor(totalMemBytes: Long?): Int {
        if (totalMemBytes == null || totalMemBytes <= 0) {
            return MAX_MEMORY_LIMIT_BYTES
        }
        return (totalMemBytes / 3).coerceIn(MIN_MEMORY_LIMIT_BYTES.toLong(), MAX_MEMORY_LIMIT_BYTES.toLong()).toInt()
    }

    private fun memoryLimitBytes(): Int {
        val known = knownMemoryLimit
        if (known > 0) {
            return known
        }
        val total = deviceTotalMemory()
        val limit = memoryLimitFor(total)
        if (total != null) {
            knownMemoryLimit = limit
        }
        return limit
    }

    private fun appContext(): Context? = try {
        if (GlobalPreferences.isInitialized()) GlobalPreferences.sInstance.context else null
    } catch (_: Throwable) {
        null
    }

    private fun deviceTotalMemory(): Long? = try {
        val context = appContext()
        val manager = context?.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        manager?.let {
            val info = ActivityManager.MemoryInfo()
            it.getMemoryInfo(info)
            info.totalMem.takeIf { total -> total > 0 }
        }
    } catch (_: Throwable) {
        null
    }

    /**
     * leanview: QuickJS appends strings by copying (no ropes), so astring.generate() of a whole player (3.6 MB of output,
     * built with `this.output += code`) takes quadratic time and did not finish in 15 minutes. Same output, collected in
     * an array. The bundled ejs script stays as it is on disk; the text is changed when it is loaded.
     *
     * Fails loudly when the call is not there (a newer or minified ejs): a core that is not adapted would run for minutes, so
     * the caller must not use it.
     *
     * @throws IllegalStateException when the script does not contain exactly the call that is replaced
     */
    @JvmStatic
    fun adaptSolverCore(code: String): String {
        val count = code.split(GENERATE_CALL).size - 1
        check(count == 1) { "the solver core has $count calls of `$GENERATE_CALL` (one is expected): it cannot run on QuickJS unchanged" }
        return code.replace(GENERATE_CALL, GENERATE_ARRAY)
    }

    /** The adapted text turned back into the original, for the tests that must show that nothing else changed. */
    internal fun undoAdaptation(adapted: String): String = adapted.replace(GENERATE_ARRAY, GENERATE_CALL)

    /**
     * The plain error text: QuickJS says "'x' is not defined"; SigExtractor matches on `x is not defined` at the end, so the quotes around the name go.
     */
    internal fun plain(raw: String?): String {
        val first = raw?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim() ?: return "script error"
        return first.replace(Regex("""'([\w$]+)' is not defined$"""), "$1 is not defined")
    }

    /**
     * The error text for the caller. The wrapper's message has no error name ("function name expected", "'x' is not defined"),
     * only the message and the stack, so a script that does not parse is recognised by its stack, whose first frame is a bare
     * location ("    at unknown.js:1") where a runtime error has the frame of the code that was running ("    at <eval> (unknown.js)"):
     * those get the prefix "SyntaxError: " that V8 and every other engine would have put there.
     * Checked on the emulator against the real messages (see JsEnginesTest, JsEngineGuardTest).
     */
    internal fun describe(raw: String?): String {
        val first = plain(raw)
        return if (looksLikeParseError(raw)) "SyntaxError: $first" else first
    }

    internal fun looksLikeParseError(raw: String?): Boolean {
        val frame = raw?.lineSequence()?.drop(1)?.map { it.trim() }?.firstOrNull { it.startsWith("at ") } ?: return false
        return Regex("""at \S+:\d+""").matches(frame)
    }

    /**
     * Does this error say that the script text itself is broken (a corrupt cached solver or preprocessed player)? Judged on
     * [describe]'s text; the V8 text of the engine that was replaced is kept because a cached value can come from either.
     */
    @JvmStatic
    fun isBrokenScriptError(message: String?): Boolean =
        message != null && (message.contains("SyntaxError") || message.contains("Invalid or unexpected token"))
}
