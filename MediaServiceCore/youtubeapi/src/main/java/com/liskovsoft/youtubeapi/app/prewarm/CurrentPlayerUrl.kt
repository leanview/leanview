package com.liskovsoft.youtubeapi.app.prewarm

/**
 * leanview: the player url the app info names now (the app's CURRENT player), readable on any thread without a lock (the app-info
 * lock can be held for a network call). Written by the app-info cache when it loads the app info.
 */
object CurrentPlayerUrl {
    @Volatile
    private var url: String? = null

    @JvmStatic
    fun get(): String? = url

    @JvmStatic
    fun set(playerUrl: String?) {
        if (playerUrl != null) {
            url = playerUrl
        }
    }
}
