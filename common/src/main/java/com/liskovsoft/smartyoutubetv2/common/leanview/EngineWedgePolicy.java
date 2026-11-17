package com.liskovsoft.smartyoutubetv2.common.leanview;

/**
 * leanview: when the app ends its own process because the JavaScript engine is wedged. A script that outlived its caller is
 * still running past the hard cap (it may never end); no player can be solved until it ends, and a second engine next to it
 * could get the app killed for memory. Ending the process frees it, but only at a safe point: the app is in the background
 * and nothing plays (no video in front or behind, no corner window) and no backup is being written. No Android code.
 */
public final class EngineWedgePolicy {
    /** What the guard does after a check. */
    public enum Action {
        /** Nothing to do now: no wedge, or a screen of the app is started (the next screen stop checks again). */
        NONE,
        /** Wedged and away, but something plays or a backup runs: no event says when that ends, so check again later. */
        CHECK_AGAIN,
        /** Wedged, away and idle: save the data and end the process. */
        END_PROCESS
    }

    private EngineWedgePolicy() {
    }

    /**
     * @param scriptPastCap a discarded script is still running past the hard cap
     * @param inBackground none of the app's screens is started
     * @param playbackActive a video or its audio plays, in front or behind
     * @param inPip the corner window (picture in picture) is open
     * @param backupRunning a backup worker (Drive or local) is writing its files
     */
    public static Action decide(boolean scriptPastCap, boolean inBackground, boolean playbackActive, boolean inPip, boolean backupRunning) {
        if (!scriptPastCap || !inBackground) {
            return Action.NONE;
        }
        if (playbackActive || inPip || backupRunning) {
            return Action.CHECK_AGAIN;
        }
        return Action.END_PROCESS;
    }
}
