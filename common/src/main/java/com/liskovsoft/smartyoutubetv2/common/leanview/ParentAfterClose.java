package com.liskovsoft.smartyoutubetv2.common.leanview;

/**
 * leanview: what the app does when a window closes (ViewManager.startParentView). A link launches the
 * player and "Who's watching?" in the same instant; the picker comes up first, so when it closes the
 * player is not on the window stack yet. Upstream then leaves for the launcher and, with the same
 * flag, sends the player behind it as soon as it appears. A player launched moments ago is let in
 * instead. No Android code.
 */
public final class ParentAfterClose {
    public enum Next { OPEN_PARENT, LEAVE_APP, LET_PLAYER_COME }

    /**
     * How long a launched window that has not appeared yet still counts as on its way: the player is
     * launched when the link arrives, and the person may take a while to pick a profile. A launch that
     * never arrives costs only this: the closing window does not send the app behind the launcher.
     */
    public static final long ON_ITS_WAY_MS = 5 * 60_000;

    private ParentAfterClose() {
    }

    /**
     * @param hasParent      there is a window to go back to
     * @param playerOnlyMode the app was opened from a link and shows only the player
     * @param playerOnItsWay the player was launched and has not appeared yet
     */
    public static Next next(boolean hasParent, boolean playerOnlyMode, boolean playerOnItsWay) {
        if (playerOnItsWay) {
            return Next.LET_PLAYER_COME; // also over a parent: with the app open the link brought Browse back instead
        }

        return hasParent && !playerOnlyMode ? Next.OPEN_PARENT : Next.LEAVE_APP;
    }

    /** A window launched at {@code launchedAtMs} that has not appeared yet. */
    public static boolean onItsWay(Long launchedAtMs, long nowMs) {
        if (launchedAtMs == null) {
            return false;
        }

        long elapsed = nowMs - launchedAtMs;

        return elapsed >= 0 && elapsed < ON_ITS_WAY_MS;
    }
}
