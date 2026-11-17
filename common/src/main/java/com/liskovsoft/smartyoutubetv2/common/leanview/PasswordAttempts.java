package com.liskovsoft.smartyoutubetv2.common.leanview;

/**
 * leanview: wrong PIN guesses on a profile. After five, every try waits: 30 s, then twice as long
 * after each further wrong guess, up to 15 minutes. One per profile, kept with the profile's data,
 * so neither closing the app nor entering another profile resets it.
 */
public final class PasswordAttempts {
    public static final int ACCEPTED = 0;
    public static final int WRONG = 1;
    public static final int WAIT = 2;

    private static final int FREE_TRIES = 5;
    private static final long FIRST_WAIT_MS = 30_000;
    private static final long LONGEST_WAIT_MS = 15 * 60_000;

    private int mFailures;
    private long mWaitUntilMs;

    public int check(String stored, String typed, long nowMs) {
        clampWait(nowMs);
        if (nowMs < mWaitUntilMs) {
            return WAIT;
        }
        if (PasswordHash.matches(stored, typed)) {
            mFailures = 0;
            mWaitUntilMs = 0;
            return ACCEPTED;
        }
        mFailures++;
        if (mFailures >= FREE_TRIES) {
            long waitMs = FIRST_WAIT_MS << Math.min(mFailures - FREE_TRIES, 10);
            mWaitUntilMs = nowMs + Math.min(waitMs, LONGEST_WAIT_MS);
        }
        return WRONG;
    }

    public long waitSeconds(long nowMs) {
        clampWait(nowMs);
        return Math.max(0, (mWaitUntilMs - nowMs + 999) / 1000);
    }

    // A wait saved while the clock was wrong (TV boxes may boot in 1970 until they sync) or a
    // damaged value never locks the profile for longer than the longest wait.
    private void clampWait(long nowMs) {
        if (mWaitUntilMs > nowMs + LONGEST_WAIT_MS) {
            mWaitUntilMs = nowMs + LONGEST_WAIT_MS;
        }
    }

    @Override
    public String toString() {
        return mFailures + ":" + mWaitUntilMs;
    }

    public static PasswordAttempts fromString(String spec) {
        PasswordAttempts attempts = new PasswordAttempts();
        if (spec == null) {
            return attempts;
        }
        String[] parts = spec.split(":");
        try {
            attempts.mFailures = Integer.parseInt(parts[0]);
            attempts.mWaitUntilMs = Long.parseLong(parts[1]);
        } catch (RuntimeException e) {
            return new PasswordAttempts();
        }
        return attempts;
    }
}
