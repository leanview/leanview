package com.liskovsoft.smartyoutubetv2.common.leanview;

/**
 * leanview: the clock of the "Up next" end screen. IDLE -> COUNTING -> FIRED or CANCELLED.
 * The engine reports one ending more than once (two triggers, plus a retry after a dialog), so
 * start() while it is already COUNTING, FIRED or CANCELLED does nothing: a cancelled countdown
 * stays cancelled until reset() (new video, replay). tick() fires exactly once.
 * Paused (a dialog is open, the focus is on a related video) means time does not pass.
 * No Android code; the caller supplies the elapsed time.
 */
public final class EndScreenCountdown {
    public enum State { IDLE, COUNTING, FIRED, CANCELLED }

    /** A screen that waits for a choice: counting, but never fires by itself. */
    public static final long NO_TIMER = -1;

    public static final int PAUSE_DIALOG = 1;
    public static final int PAUSE_FOCUS = 2;

    private State mState = State.IDLE;
    private long mRemainingMs;
    private boolean mHasTimer;
    private int mPauses;

    public State state() {
        return mState;
    }

    /** @return true only when it really started (it was IDLE) */
    public boolean start(long totalMs) {
        if (mState != State.IDLE) {
            return false;
        }

        mState = State.COUNTING;
        mHasTimer = totalMs != NO_TIMER;
        mRemainingMs = mHasTimer ? Math.max(0, totalMs) : 0;
        return true;
    }

    /** @return true exactly once: on the tick that makes the time run out */
    public boolean tick(long deltaMs) {
        if (mState != State.COUNTING || !mHasTimer || isPaused() || deltaMs <= 0) {
            return false;
        }

        mRemainingMs = Math.max(0, mRemainingMs - deltaMs);

        if (mRemainingMs == 0) {
            mState = State.FIRED;
            return true;
        }

        return false;
    }

    /** "Play now": fires at once while counting (paused or not) or after a cancel; once. */
    public boolean fireNow() {
        if (mState != State.COUNTING && mState != State.CANCELLED) {
            return false;
        }

        mState = State.FIRED;
        mRemainingMs = 0;
        return true;
    }

    public void cancel() {
        if (mState == State.COUNTING) {
            mState = State.CANCELLED;
        }
    }

    public void reset() {
        mState = State.IDLE;
        mRemainingMs = 0;
        mHasTimer = false;
        mPauses = 0;
    }

    /**
     * For events that only say "the screen went away" (view destroyed, engine released, activity
     * finished): drops a running or fired countdown but keeps a CANCELLED one, so the same ending
     * reported again afterwards cannot start a new countdown. Only reset() (new video, replay,
     * real playback again) clears a cancel.
     */
    public void softReset() {
        if (mState != State.CANCELLED) {
            reset();
        }
    }

    /** The playback position is at the end of a known duration (live streams: unknown, never "at the end"). */
    public static boolean isAtEnd(long positionMs, long durationMs) {
        return durationMs > 0 && positionMs >= durationMs - 500;
    }

    public void setPaused(int reason, boolean paused) {
        if (paused) {
            mPauses |= reason;
        } else {
            mPauses &= ~reason;
        }
    }

    public boolean isPaused() {
        return mPauses != 0;
    }

    public long remainingMs() {
        return mRemainingMs;
    }

    /** Whole seconds left, rounded up (8.0 -> 8, 7.2 -> 8, 0.1 -> 1). */
    public int remainingSeconds() {
        return (int) ((mRemainingMs + 999) / 1_000);
    }
}
