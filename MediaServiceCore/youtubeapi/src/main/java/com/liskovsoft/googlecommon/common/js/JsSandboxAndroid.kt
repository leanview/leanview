package com.liskovsoft.googlecommon.common.js

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.javascriptengine.EvaluationResultSizeLimitExceededException
import androidx.javascriptengine.IsolateStartupParameters
import androidx.javascriptengine.IsolateTerminatedException
import androidx.javascriptengine.JavaScriptException
import androidx.javascriptengine.JavaScriptIsolate
import androidx.javascriptengine.JavaScriptSandbox
import androidx.javascriptengine.MemoryLimitExceededException
import androidx.javascriptengine.SandboxDeadException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * leanview: androidx.javascriptengine (Apache-2.0): Chrome's V8 in the sandboxed process of Android System WebView. The library
 * needs API 26 (the app runs from 23, see tools:overrideLibrary): nothing here is touched before [isAvailable] said yes.
 */
internal object AndroidSandbox {
    /** The result of the largest script, the TCL solve with its preprocessed player, is about 4 MB; the default allows 20 MB. */
    private const val MAX_RESULT_BYTES = 32 * 1024 * 1024

    fun isAvailable(): Boolean = Build.VERSION.SDK_INT >= 26 && supported()

    @RequiresApi(26)
    private fun supported(): Boolean = try {
        JavaScriptSandbox.isSupported()
    } catch (_: Throwable) {
        false
    }

    /** Starts the one connection of the process. Only after [isAvailable]. */
    fun connect(context: Context): Future<SandboxService> {
        check(Build.VERSION.SDK_INT >= 26) { "the system sandbox needs Android 8" }
        return Connection(JavaScriptSandbox.createConnectedInstanceAsync(context.applicationContext))
    }

    @RequiresApi(26)
    private class Connection(private val future: Future<JavaScriptSandbox>) : Future<SandboxService> {
        override fun cancel(mayInterruptIfRunning: Boolean): Boolean = future.cancel(mayInterruptIfRunning)
        override fun isCancelled(): Boolean = future.isCancelled
        override fun isDone(): Boolean = future.isDone
        override fun get(): SandboxService = Service(future.get())
        override fun get(timeout: Long, unit: TimeUnit): SandboxService = Service(future.get(timeout, unit))
    }

    @RequiresApi(26)
    private class Service(private val sandbox: JavaScriptSandbox) : SandboxService {
        override fun isFeatureSupported(feature: String): Boolean = sandbox.isFeatureSupported(feature)

        override fun newIsolate(maxHeapBytes: Long): SandboxIsolate {
            try {
                val params = IsolateStartupParameters()
                if (maxHeapBytes > 0 && sandbox.isFeatureSupported(JavaScriptSandbox.JS_FEATURE_ISOLATE_MAX_HEAP_SIZE)) {
                    params.maxHeapSizeBytes = maxHeapBytes
                }
                params.maxEvaluationReturnSizeBytes = MAX_RESULT_BYTES
                return Isolate(sandbox.createIsolate(params))
            } catch (e: Throwable) {
                throw JsBackendLostException("the system JavaScript engine cannot make a context: $e", e)
            }
        }

        override fun close() {
            sandbox.close()
        }

        override fun killForTest() {
            sandbox.killImmediatelyOnThread()
        }
    }

    @RequiresApi(26)
    private class Isolate(private val isolate: JavaScriptIsolate) : SandboxIsolate {
        override fun evaluate(script: String): String {
            val future = try {
                isolate.evaluateJavaScriptAsync(script)
            } catch (e: IllegalStateException) {
                throw SandboxIsolateTerminated("the context is closed: ${e.message}", e)
            } catch (e: Throwable) {
                throw JsBackendLostException("the system JavaScript engine failed: $e", e)
            }
            try {
                return future.get()
            } catch (e: ExecutionException) {
                throw map(e.cause ?: e)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw JsEngineException("interrupted", e)
            }
        }

        private fun map(cause: Throwable): JsEngineException = when (cause) {
            is SandboxDeadException -> JsBackendLostException("the process of the system JavaScript engine died: ${cause.message}", cause)
            is MemoryLimitExceededException -> SandboxOutOfMemory(JsEngines.plain(cause.message), cause)
            is IsolateTerminatedException -> SandboxIsolateTerminated(cause.message ?: "terminated", cause)
            is EvaluationResultSizeLimitExceededException -> JsEngineException("the result is too large: ${JsEngines.plain(cause.message)}", cause)
            is JavaScriptException -> JsEngineException(JsEngines.plain(cause.message), cause)
            else -> JsBackendLostException("the system JavaScript engine failed: $cause", cause)
        }

        override fun terminate() {
            isolate.close() // with JS_FEATURE_ISOLATE_TERMINATION the running evaluation fails at once and the isolate is terminated
        }

        override fun close() {
            isolate.close()
        }
    }
}
