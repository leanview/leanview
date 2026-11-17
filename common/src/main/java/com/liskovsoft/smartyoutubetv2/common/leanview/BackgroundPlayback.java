package com.liskovsoft.smartyoutubetv2.common.leanview;

import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerConstants;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;

/**
 * leanview: what the player does when BACK or HOME is pressed in it. Two separate choices per
 * profile (BACK and HOME), each one CLOSE the video, keep playing in a corner window (PIP) or keep
 * playing only the audio (AUDIO); HOME also has BEHIND (play behind, old TVs only).
 * No Android code: the activity only asks what to do (the imports are only int constants).
 */
public final class BackgroundPlayback {
    public enum Action {
        CLOSE(0), PIP(1), AUDIO(2), BEHIND(3);

        public final int code;

        Action(int code) {
            this.code = code;
        }

        public static Action fromCode(int code, Action fallback) {
            for (Action action : values()) {
                if (action.code == code) {
                    return action;
                }
            }

            return fallback;
        }
    }

    public static final Action DEFAULT_BACK = Action.CLOSE;
    public static final Action DEFAULT_HOME = Action.PIP;

    /** What finish() of the player activity must do. */
    public static final class FinishDecision {
        public final boolean enterPip;
        public final boolean keepEngine;
        public final boolean close;

        FinishDecision(boolean enterPip, boolean keepEngine, boolean close) {
            this.enterPip = enterPip;
            this.keepEngine = keepEngine;
            this.close = close;
        }
    }

    /** What HOME in the player must do. */
    public static final class HomeDecision {
        public final boolean enterPip;
        public final boolean keepEngine;
        public final boolean playBehind;

        HomeDecision(boolean enterPip, boolean keepEngine, boolean playBehind) {
            this.enterPip = enterPip;
            this.keepEngine = keepEngine;
            this.playBehind = playBehind;
        }
    }

    private BackgroundPlayback() {
    }

    public static Action effective(Action action, boolean pipSupported) {
        return action == Action.PIP && !pipSupported ? Action.CLOSE : action;
    }

    public static FinishDecision decideFinish(boolean isBackPressed, boolean engineBlockedByPipButton, Action backAction, boolean pipSupported) {
        if (engineBlockedByPipButton) {
            return new FinishDecision(true, true, false);
        }

        if (!isBackPressed) {
            // Stop key, remote stop, embed errors...: never shrink or keep playing
            return new FinishDecision(false, false, true);
        }

        switch (effective(backAction, pipSupported)) {
            case PIP:
                return new FinishDecision(true, true, false);
            case AUDIO:
                return new FinishDecision(false, true, false);
            default: // CLOSE, and BEHIND which is a HOME-only choice
                return new FinishDecision(false, false, true);
        }
    }

    public static HomeDecision decideHome(Action homeAction, boolean pipSupported) {
        switch (effective(homeAction, pipSupported)) {
            case PIP:
                return new HomeDecision(true, true, false);
            case AUDIO:
                return new HomeDecision(false, true, false);
            case BEHIND:
                return new HomeDecision(false, false, true);
            default:
                return new HomeDecision(false, false, false);
        }
    }

    /** Screen turned off while the player is shown: keep the sound going if the user chose any background playback. */
    public static boolean keepOnScreenOff(Action backAction, Action homeAction, boolean pipSupported) {
        return effective(backAction, pipSupported) != Action.CLOSE || effective(homeAction, pipSupported) != Action.CLOSE;
    }

    /** Value for the old single "background mode" field (PlayerConstants.BACKGROUND_MODE_*), so old readers still work. */
    public static int legacyMode(Action backAction, Action homeAction) {
        if (backAction == Action.PIP || homeAction == Action.PIP) {
            return PlayerConstants.BACKGROUND_MODE_PIP;
        }

        if (backAction == Action.AUDIO || homeAction == Action.AUDIO) {
            return PlayerConstants.BACKGROUND_MODE_SOUND;
        }

        if (homeAction == Action.BEHIND) {
            return PlayerConstants.BACKGROUND_MODE_PLAY_BEHIND;
        }

        return PlayerConstants.BACKGROUND_MODE_DEFAULT;
    }

    /** Value for the old trigger field (GeneralData.BACKGROUND_PLAYBACK_SHORTCUT_*), so old readers still work. */
    public static int legacyShortcut(Action backAction, Action homeAction) {
        boolean back = backAction != Action.CLOSE && backAction != Action.BEHIND;
        boolean home = homeAction != Action.CLOSE;

        if (back && home) {
            return GeneralData.BACKGROUND_PLAYBACK_SHORTCUT_HOME_BACK;
        }

        return back ? GeneralData.BACKGROUND_PLAYBACK_SHORTCUT_BACK : GeneralData.BACKGROUND_PLAYBACK_SHORTCUT_HOME;
    }
}
