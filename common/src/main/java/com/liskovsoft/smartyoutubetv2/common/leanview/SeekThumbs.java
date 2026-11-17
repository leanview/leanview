package com.liskovsoft.smartyoutubetv2.common.leanview;

/**
 * leanview: when the seek preview thumbnails go away. After a seek is committed (arrow key up) and
 * the keys stay quiet for a short delay, the thumbnails hide while the rest of the controls stay.
 * If the key up never arrives (lost event), a watchdog commits the seek and then hides them.
 * In a "confirm" seek mode (confirm-pause / confirm-play) nothing is committed or hidden here:
 * the thumbnails stay until the user confirms. No Android imports: the view code only asks
 * {@link #due} what to do and when to ask again ({@link #nextDueInMs}).
 *
 * The generation counter lets the view ignore late thumbnail bitmaps from an earlier seek.
 */
public final class SeekThumbs {
    public enum Action { NONE, HIDE_THUMBS, COMMIT_SEEK }

    public static final long DEFAULT_HIDE_DELAY_MS = 400;
    public static final long DEFAULT_LOST_KEY_UP_MS = 1200;

    private final long mHideDelayMs;
    private final long mLostKeyUpMs;
    private boolean mConfirmMode;
    private boolean mKeyDown;
    private long mLastDownMs;
    private boolean mHidePending;
    private long mLastUpMs;
    private int mGeneration;

    public SeekThumbs() {
        this(DEFAULT_HIDE_DELAY_MS, DEFAULT_LOST_KEY_UP_MS);
    }

    public SeekThumbs(long hideDelayMs, long lostKeyUpMs) {
        mHideDelayMs = hideDelayMs;
        mLostKeyUpMs = lostKeyUpMs;
    }

    public void setConfirmMode(boolean confirm) {
        mConfirmMode = confirm;
    }

    /** Every seek key down, including the auto-repeats of a held key. */
    public void onSeekKeyDown(long nowMs) {
        mKeyDown = true;
        mLastDownMs = nowMs;
        mHidePending = false;
    }

    /** The seek key was released (or the seek was committed some other way). */
    public void onSeekKeyUp(long nowMs) {
        mKeyDown = false;
        mHidePending = true;
        mLastUpMs = nowMs;
    }

    public Action due(long nowMs) {
        if (mConfirmMode) {
            return Action.NONE;
        }
        if (mKeyDown) {
            if (nowMs < mLastDownMs) {
                mLastDownMs = nowMs; // clock went backwards: count from now
            }
            if (nowMs - mLastDownMs >= mLostKeyUpMs) {
                onSeekKeyUp(nowMs);
                return Action.COMMIT_SEEK;
            }
            return Action.NONE;
        }
        if (mHidePending) {
            if (nowMs < mLastUpMs) {
                mLastUpMs = nowMs;
            }
            if (nowMs - mLastUpMs >= mHideDelayMs) {
                mHidePending = false;
                return Action.HIDE_THUMBS;
            }
        }
        return Action.NONE;
    }

    /** Milliseconds until {@link #due} may return something; -1 when nothing is pending. */
    public long nextDueInMs(long nowMs) {
        if (mConfirmMode) {
            return -1;
        }
        if (mKeyDown) {
            return Math.max(0, mLastDownMs + mLostKeyUpMs - nowMs);
        }
        if (mHidePending) {
            return Math.max(0, mLastUpMs + mHideDelayMs - nowMs);
        }
        return -1;
    }

    public void reset() {
        mKeyDown = false;
        mHidePending = false;
    }

    public int nextGeneration() {
        return ++mGeneration;
    }

    public boolean isCurrentGeneration(int generation) {
        return generation == mGeneration;
    }
}
