package com.liskovsoft.youtubeapi.app.nsigsolver.runtime

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.liskovsoft.googlecommon.common.js.JsEngineException
import com.liskovsoft.googlecommon.common.js.JsEngines
import com.liskovsoft.youtubeapi.app.prewarm.PlayerPrewarm
import com.liskovsoft.youtubeapi.app.prewarm.PrewarmReason
import com.liskovsoft.sharedutils.mylogger.Log
import com.liskovsoft.youtubeapi.app.nsigsolver.common.CachedData
import com.liskovsoft.youtubeapi.app.nsigsolver.common.loadScript
import com.liskovsoft.youtubeapi.app.nsigsolver.provider.ChallengeOutput
import com.liskovsoft.youtubeapi.app.nsigsolver.provider.JsChallengeProvider
import com.liskovsoft.youtubeapi.app.nsigsolver.provider.JsChallengeProviderError
import com.liskovsoft.youtubeapi.app.nsigsolver.provider.JsChallengeProviderRejectedRequest
import com.liskovsoft.youtubeapi.app.nsigsolver.provider.JsChallengeProviderResponse
import com.liskovsoft.youtubeapi.app.nsigsolver.provider.JsChallengeRequest
import com.liskovsoft.youtubeapi.app.nsigsolver.provider.JsChallengeResponse
import com.liskovsoft.youtubeapi.app.nsigsolver.provider.JsChallengeType

internal abstract class JsRuntimeChalBaseJCP: JsChallengeProvider() {
    private val tag = JsRuntimeChalBaseJCP::class.simpleName
    protected val cacheSection = "challenge-solver"

    private val jcpGuideUrl = "https://github.com/yt-dlp/yt-dlp/wiki/YouTube-JS-Challenges"
    private val repository = "yt-dlp/ejs"
    override val supportedTypes = listOf(JsChallengeType.N, JsChallengeType.SIG)
    protected val scriptVersion = "0.0.1"
    protected val libPrefix = "nsigsolver/"

    private val scriptFilenames = mapOf(
        ScriptType.LIB to "${libPrefix}yt.solver.lib.js",
        ScriptType.CORE to "${libPrefix}yt.solver.core.js"
    )

    private val minScriptFilenames = mapOf(
        ScriptType.LIB to "yt.solver.lib.min.js",
        ScriptType.CORE to "yt.solver.core.min.js"
    )

    // leanview: the engine rules every solve follows (one at a time, the failure breaker, a late result of the current player is
    // kept); a late result asks the prewarm to rebuild the extractor of that player, whose solve then takes the held result
    protected val solveRunner = PlayerSolveRunner(
        store = { playerUrl, preprocessed -> ie.cache.store(cacheSection, "player:$playerUrl", CachedData(preprocessed)) },
        onLateSolved = { PlayerPrewarm.instance().request(PrewarmReason.LATE_SOLVE, it) }
    )

    protected abstract fun runJsRuntime(playerUrl: String, stdin: String, late: PlayerSolveRunner.LateOutput): String

    override fun realBulkSolve(requests: List<JsChallengeRequest>): Sequence<JsChallengeProviderResponse> = sequence {
        val grouped: Map<String, List<JsChallengeRequest>> = requests.groupBy { it.input.playerUrl }

        for ((playerUrl, groupedRequests) in grouped) {
            // leanview: a player that timed out or failed again and again is not tried for every video (each try can take minutes),
            // and nothing is downloaded while a solve that timed out is still running
            try {
                solveRunner.precheck(playerUrl)
            } catch (e: JsEngineException) {
                throw JsChallengeProviderError("Player $playerUrl not solved now: ${e.message}", e)
            }

            val data = ie.cache.load(cacheSection, "player:$playerUrl")
            var player = data?.code ?: solveRunner.takeLate(playerUrl) // leanview: a late solve of this player, held for it

            val cached = if (player != null) {
                true
            } else {
                player = getPlayer(playerUrl)
                false
            }

            val stdin = constructStdin(player, cached, groupedRequests)
            val stdout = runJsRuntime(playerUrl, stdin, lateOutput()) // leanview: engine failures are counted by solveRunner

            val gson = Gson()
            val output: SolverOutput = try {
                gson.fromJson(stdout, solverOutputType)
            } catch (e: JsonSyntaxException) {
                solveRunner.invalid(playerUrl) // leanview
                throw JsChallengeProviderError("Cannot parse solver output", e)
            } ?: run {
                solveRunner.invalid(playerUrl) // leanview
                throw JsChallengeProviderError("Empty solver output")
            }

            if (output.type == "error") {
                solveRunner.invalid(playerUrl) // leanview
                throw JsChallengeProviderError(output.error ?: "Unknown solver output error")
            }
            solveRunner.solved(playerUrl) // leanview

            val preprocessed = output.preprocessed_player
            if (preprocessed != null)
                ie.cache.store(cacheSection, "player:$playerUrl", CachedData(preprocessed))

            for ((request, responseData) in groupedRequests.zip(output.responses)) {
                if (responseData.type == "error") {
                    yield(JsChallengeProviderResponse(
                        request, null, JsChallengeProviderError(responseData.error ?: "Unknown solver output error")))
                } else {
                    yield(JsChallengeProviderResponse(
                        request, JsChallengeResponse(request.type, ChallengeOutput(responseData.data))
                    ))
                }
            }
        }
    }

    /** leanview: reads a solve that ended after its caller gave up (solveRunner decides whether its player is kept). */
    private fun lateOutput() = PlayerSolveRunner.LateOutput { stdout ->
        val output: SolverOutput? = try {
            Gson().fromJson(stdout, solverOutputType)
        } catch (e: JsonSyntaxException) {
            null
        }
        if (output == null || output.type == "error") null else PlayerSolveRunner.LateSolve(output.preprocessed_player)
    }

    private fun constructStdin(player: String, preprocessed: Boolean, requests: List<JsChallengeRequest>): String {
        val jsonRequests = requests.map { request ->
            mapOf(
                // TODO: i despise nsig name
                //"type" to if (request.type.value == "n") "nsig" else request.type.value,
                "type" to request.type.value,
                "challenges" to request.input.challenges
            )
        }
        val data = if (preprocessed) {
            mapOf(
                "type" to "preprocessed",
                "preprocessed_player" to player,
                "requests" to jsonRequests
            )
        } else {
            mapOf(
                "type" to "player",
                "player" to player,
                "requests" to jsonRequests,
                "output_preprocessed" to true
            )
        }
        val gson = Gson()
        val jsonData = gson.toJson(data)
        return """
        JSON.stringify(jsc($jsonData));
        """
    }

    protected fun constructCommonStdin(): String {
        return """
        ${libScript.code}
        ${coreScript.code}
        "";
        """
    }

    // region: challenge solver script

    private val libScript: Script by lazy {
        getScript(ScriptType.LIB)
    }

    private val coreScript: Script by lazy {
        getScript(ScriptType.CORE)
    }

    private fun getScript(scriptType: ScriptType): Script {
        for ((_, fromSource) in iterScriptSources()) {
            var script = fromSource(scriptType)
            if (script == null)
                continue
            if (scriptType == ScriptType.CORE) {
                // leanview: the core runs on QuickJS only with the one change of JsEngines.adaptSolverCore; a source that does not
                // have the line it replaces (a newer or minified ejs) would run for minutes, so it is not used
                try {
                    script = script.copy(code = JsEngines.adaptSolverCore(script.code))
                } catch (e: IllegalStateException) {
                    Log.w(tag, "Challenge solver core from ${script.source.value} skipped: ${e.message}")
                    continue
                }
            }
            if (script.version != scriptVersion)
                Log.w(tag, "Challenge solver ${scriptType.value} script version ${script.version} " +
                        "is not supported (source: ${script.source.value}, supported version: $scriptVersion)")

            Log.d(tag, "Using challenge solver ${script.type.value} script v${script.version} " +
                    "(source: ${script.source.value}, variant: ${script.variant.value})")
            return script
        }
        throw JsChallengeProviderRejectedRequest("No usable challenge solver ${scriptType.value} script available")
    }

    protected open fun iterScriptSources(): Sequence<Pair<ScriptSource, (scriptType: ScriptType) -> Script?>> = sequence {
        yieldAll(listOf(
            Pair(ScriptSource.CACHE, ::cachedSource),
            Pair(ScriptSource.BUILTIN, ::builtinSource),
            Pair(ScriptSource.WEB, ::webReleaseSource)
        ))
    }

    private fun cachedSource(scriptType: ScriptType): Script? {
        val data = ie.cache.load(cacheSection, scriptType.value) ?: return null
        return Script(scriptType,
            ScriptVariant.valueOf(data.variant ?: "unknown"), ScriptSource.CACHE, data.version ?: "unknown", data.code)
    }

    private fun builtinSource(scriptType: ScriptType): Script? {
        val fileName = scriptFilenames[scriptType] ?: return null
        val code = loadScript(fileName, "Failed to read builtin challenge solver ${scriptType.value}")
        return Script(scriptType, ScriptVariant.UNMINIFIED, ScriptSource.BUILTIN, scriptVersion, code)
    }

    private fun webReleaseSource(scriptType: ScriptType): Script? {
        val fileName = minScriptFilenames[scriptType] ?: return null
        val url = "https://github.com/$repository/releases/download/$scriptVersion/$fileName"
        val code = ie.downloadWebpageWithRetries(url, "[${tag}] Failed to download challenge solver ${scriptType.value} script")
        Log.d(tag, "[${tag}] Downloading challenge solver ${scriptType.value} script from $url")
        ie.cache.store(cacheSection, scriptType.value, CachedData(code))
        return Script(scriptType, ScriptVariant.MINIFIED, ScriptSource.WEB, scriptVersion, code)
    }

    // endregion: challenge solver script
}