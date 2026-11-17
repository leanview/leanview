package com.liskovsoft.youtubeapi.app.prewarm

/** leanview: when the prewarm drops the extractor it finds kept, so that the app's current player is built again. */
object ExtractorRebuild {
    /**
     * @param keptUrl the player url of the extractor that is kept for the flavor
     * @param keptValid whether it validated
     * @param currentUrl the app's current player url
     * @param lateSolvedUrl the player a solve that outlived its caller just solved (LATE_SOLVE), or null
     * @return true when the kept extractor must go: it is invalid, or a late solve of the current player is waiting while the
     *   app still uses another (valid fallback) player
     */
    @JvmStatic
    fun mustDrop(keptUrl: String?, keptValid: Boolean, currentUrl: String?, lateSolvedUrl: String?): Boolean =
        !keptValid || (lateSolvedUrl != null && lateSolvedUrl == currentUrl && keptUrl != currentUrl)
}
