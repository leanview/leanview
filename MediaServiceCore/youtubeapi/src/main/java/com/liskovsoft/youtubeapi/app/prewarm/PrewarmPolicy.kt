package com.liskovsoft.youtubeapi.app.prewarm

/** leanview: why somebody asks for the player to be solved ahead of the first video. */
enum class PrewarmReason {
    /** The app was opened. */
    APP_OPEN,

    /** The app learned that YouTube serves another player than the one that is solved. */
    PLAYER_CHANGED,

    /**
     * A solve of the app's current player outlived its caller's deadline and ended: the extractor of that player (the url of the
     * request) is built now from that result (about a second), even when another (fallback) player is valid. Not held back by
     * the back-off of the failure it repairs.
     */
    LATE_SOLVE
}

/** leanview: what to do with one request. */
enum class PrewarmDecision {
    /** Start the work. */
    RUN,

    /** Already solved for this player (or for this process): nothing to do. */
    SKIP_WARM,

    /** The last attempt failed a short time ago. */
    SKIP_BACKOFF,

    /** This player failed so often (or timed out) that it is not tried again for a while. */
    SKIP_CIRCUIT_OPEN
}

/**
 * leanview: the scheduling rules of the player prewarm, with no clock, thread or Android in it, so they can be tested.
 *
 * - nothing extra on a warm cache: an app open finds a valid extractor in this process, a change names the player that is solved already;
 * - a failed attempt of a player is not repeated for [retryAfterFailureMs] (the first video retries on its own, the prewarm must not
 *   hammer the network); the caller passes the last failure of the player that is asked for, so a failure of one player does not
 *   hold back another;
 * - a player whose circuit is open (it failed too often, or timed out) is not tried at all.
 *
 * "One run at a time" is not a rule here: the runs are serialized by the single thread they run on.
 */
class PrewarmPolicy(private val retryAfterFailureMs: Long = 2 * 60 * 1000L) {
    fun decide(
        reason: PrewarmReason,
        requestedPlayerUrl: String?,
        solvedPlayerUrl: String?,
        cacheWarm: Boolean,
        nowMs: Long,
        lastFailureAtMs: Long?,
        circuitOpen: Boolean
    ): PrewarmDecision {
        if (circuitOpen) {
            return PrewarmDecision.SKIP_CIRCUIT_OPEN
        }
        if (reason != PrewarmReason.LATE_SOLVE && lastFailureAtMs != null && nowMs - lastFailureAtMs < retryAfterFailureMs) {
            return PrewarmDecision.SKIP_BACKOFF
        }
        return when (reason) {
            PrewarmReason.APP_OPEN -> if (cacheWarm) PrewarmDecision.SKIP_WARM else PrewarmDecision.RUN
            // the flavor-wide warm check does not apply: a valid fallback player is exactly what the late solve replaces
            PrewarmReason.LATE_SOLVE, PrewarmReason.PLAYER_CHANGED ->
                if (requestedPlayerUrl != null && requestedPlayerUrl == solvedPlayerUrl) PrewarmDecision.SKIP_WARM else PrewarmDecision.RUN
        }
    }
}
