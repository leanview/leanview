package com.liskovsoft.youtubeapi.app.prewarm

/**
 * leanview: which player url has a VALIDATED extractor, per flavor (the regular player and the TCL player). Written by the code
 * that builds an extractor after it validated it (or found it invalid), read by anybody on any thread without a lock: the app
 * lock that is held while a player is being solved must not be needed to ask "is it solved".
 */
class SolvedPlayers {
    @Volatile
    private var regular: String? = null

    @Volatile
    private var tcl: String? = null

    /** The player url of the valid extractor of this flavor, or null when there is none. */
    fun solvedUrl(tclFlavor: Boolean): String? = if (tclFlavor) tcl else regular

    /**
     * Records the extractor that is now the flavor's one (the one the app persisted) and whether it validated: the flavor is solved
     * for its url if it did, and not solved at all if it did not, whatever url was solved before (that extractor is gone).
     */
    fun record(tclFlavor: Boolean, playerUrl: String?, valid: Boolean) {
        val solved = if (valid) playerUrl else null
        if (tclFlavor) tcl = solved else regular = solved
    }

    /** Forgets the flavor (its extractor was dropped). */
    fun clear(tclFlavor: Boolean) {
        if (tclFlavor) tcl = null else regular = null
    }
}
