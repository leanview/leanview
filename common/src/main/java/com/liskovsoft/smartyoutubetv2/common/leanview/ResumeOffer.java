package com.liskovsoft.smartyoutubetv2.common.leanview;

/**
 * leanview: what a profile is offered of the video it left unfinished (2026-11-09). Back to
 * the profile within an hour, it opens again paused where it was (once: closing it without watching
 * does not reopen it again); up to a day it is the first card of Home; finished or older, only the
 * history has it. Each profile keeps its own (ContinueWatching).
 */
public final class ResumeOffer {
    public enum Action { NONE, OPEN_PAUSED, HOME_CARD }

    public static final long OPEN_WITHIN_MS = 60 * 60_000L;
    public static final long OFFER_WITHIN_MS = 24 * 60 * 60_000L;
    static final long MIN_POSITION_MS = 10_000;
    static final long MIN_DURATION_MS = 60_000;
    static final float FINISHED_PERCENT = 95;
    // Moving less than this since the last record is not watching more (a paused reopen, a re-save)
    static final long SAME_POSITION_MS = 5_000;

    private ResumeOffer() {
    }

    public static float percent(long positionMs, long durationMs) {
        return durationMs > 0 ? positionMs * 100f / durationMs : 0;
    }

    /** Worth offering again: started, before the credits, not live and not a short. */
    public static boolean isUnfinished(long positionMs, long durationMs, boolean live) {
        if (live || durationMs < MIN_DURATION_MS || positionMs < MIN_POSITION_MS) {
            return false;
        }
        return percent(positionMs, durationMs) < FINISHED_PERCENT;
    }

    public static boolean isFinished(long positionMs, long durationMs) {
        return durationMs > 0 && percent(positionMs, durationMs) >= FINISHED_PERCENT;
    }

    /** On arriving at the profile; autoOpened: it already opened by itself once since it was left. */
    public static Action onArrival(long leftAtMs, long nowMs, boolean autoOpened) {
        long age = nowMs - leftAtMs;
        if (age < 0 || age >= OFFER_WITHIN_MS) {
            return Action.NONE;
        }
        return age < OPEN_WITHIN_MS && !autoOpened ? Action.OPEN_PAUSED : Action.HOME_CARD;
    }

    /** Whether Home shows it first. */
    public static boolean showsOnHome(long leftAtMs, long nowMs) {
        long age = nowMs - leftAtMs;
        return age >= 0 && age < OFFER_WITHIN_MS;
    }

    /** A new record of the same video that barely moved keeps when it was left. */
    public static boolean isSameStop(String oldVideoId, long oldPositionMs, String videoId, long positionMs) {
        return oldVideoId != null && oldVideoId.equals(videoId) && Math.abs(positionMs - oldPositionMs) < SAME_POSITION_MS;
    }
}
