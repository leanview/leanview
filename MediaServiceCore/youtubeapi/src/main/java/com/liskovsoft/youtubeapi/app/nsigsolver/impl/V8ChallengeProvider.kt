package com.liskovsoft.youtubeapi.app.nsigsolver.impl

import com.liskovsoft.googlecommon.common.js.JsEngineException
import com.liskovsoft.googlecommon.common.js.JsEngines
import com.liskovsoft.youtubeapi.app.nsigsolver.common.loadScript
import com.liskovsoft.youtubeapi.app.nsigsolver.provider.JsChallengeProviderError
import com.liskovsoft.youtubeapi.app.nsigsolver.runtime.JsRuntimeChalBaseJCP
import com.liskovsoft.youtubeapi.app.nsigsolver.runtime.PlayerSolveRunner
import com.liskovsoft.youtubeapi.app.nsigsolver.runtime.Script
import com.liskovsoft.youtubeapi.app.nsigsolver.runtime.ScriptSource
import com.liskovsoft.youtubeapi.app.nsigsolver.runtime.ScriptType
import com.liskovsoft.youtubeapi.app.nsigsolver.runtime.ScriptVariant

internal object V8ChallengeProvider: JsRuntimeChalBaseJCP() {
    private val tag = V8ChallengeProvider::class.simpleName
    private val v8NpmLibFilename = listOf("${libPrefix}polyfill.js", "${libPrefix}meriyah-6.1.4.min.js", "${libPrefix}astring-1.9.0.min.js")

    override fun iterScriptSources(): Sequence<Pair<ScriptSource, (ScriptType) -> Script?>> = sequence {
        for ((source, func) in super.iterScriptSources()) {
            if (source == ScriptSource.WEB || source == ScriptSource.BUILTIN)
                yield(Pair(ScriptSource.BUILTIN, ::v8NpmSource))
            yield(Pair(source, func))
        }
    }

    private fun v8NpmSource(scriptType: ScriptType): Script? {
        if (scriptType != ScriptType.LIB)
            return null
        // V8-specific lib scripts that uses Deno NPM imports
        val code = loadScript(v8NpmLibFilename, "Failed to read v8 challenge solver lib script")
        return Script(scriptType, ScriptVariant.V8_NPM, ScriptSource.BUILTIN, scriptVersion, code)
    }

    // leanview: the context lives only inside solveRunner.run (made, used and closed there, under the solve lock of the process)
    override fun runJsRuntime(playerUrl: String, stdin: String, late: PlayerSolveRunner.LateOutput): String {
        try {
            return solveRunner.run(playerUrl, constructCommonStdin(), stdin, late)
        } catch (e: JsEngineException) {
            if (JsEngines.isBrokenScriptError(e.message))
                ie.cache.clear(cacheSection) // cached data broken? (QuickJS says SyntaxError, V8 said Invalid or unexpected token)
            throw JsChallengeProviderError("V8 runtime error: ${e.message}", e)
        }
    }
}
