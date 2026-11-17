package com.liskovsoft.smartyoutubetv2.common.leanview;

/**
 * leanview: when the account picker opens by itself (2026-10-30): each time the app
 * is opened, and when it comes back after being away at least the time set in Configuración >
 * Cuentas (the TV turned on again, say). Away means none of the app's screens is started, so moving
 * between its own screens (browse, player, dialogs) never counts: the next one starts before the
 * last one stops. Once shown during a return (the start screen shows it on opening, before Android
 * reports the app started), the return does not show it again.
 */
public final class AccountPicker {
    /** As the away time: never on coming back. */
    public static final long NEVER = -1;
    public static final long DEFAULT_AWAY_MS = 2 * 60 * 60_000;
    public static final long[] AWAY_OPTIONS_MS = {NEVER, 15 * 60_000, 60 * 60_000, DEFAULT_AWAY_MS, 4 * 60 * 60_000};

    private static final AccountPicker sInstance = new AccountPicker();

    private int mStarted;
    private long mAwaySinceMs = NEVER;
    private long mShownAtMs = NEVER;
    private boolean mBack;
    private boolean mReturned;

    public static AccountPicker instance() {
        return sInstance;
    }

    /**
     * Tells profiles apart by email and name: a brand channel (like "Carol") can have no email. Null: no account.
     */
    public static String profileKey(String email, String name) {
        return email == null && name == null ? null : (email != null ? email : "") + "|" + (name != null ? name : "");
    }

    /**
     * Whether choosing the profile nextKey (profileKey) moves away from currentKey. A video left
     * playing in the corner window belongs to the profile that started it, so another profile must
     * not inherit it (2026-11-01).
     */
    public static boolean isAnotherProfile(String currentKey, String nextKey) {
        return currentKey == null ? nextKey != null : !currentKey.equalsIgnoreCase(nextKey);
    }

    /** One of the app's screens started; awayMs is the setting (NEVER: off). */
    public void onScreenStarted(long nowMs, long awayMs) {
        if (mStarted == 0 && mAwaySinceMs != NEVER && awayMs != NEVER && nowMs - mAwaySinceMs >= awayMs
                && mShownAtMs < mAwaySinceMs) {
            mBack = true;
        }
        // Any return from the launcher, however short (ContinueWatching), not the first start
        if (mStarted == 0 && mAwaySinceMs != NEVER) {
            mReturned = true;
        }
        mStarted++;
    }

    public void onScreenStopped(long nowMs) {
        mStarted = Math.max(0, mStarted - 1);
        if (mStarted == 0) {
            mAwaySinceMs = nowMs;
        }
    }

    /** None of the app's screens is started: the app is in the background (or not opened yet). */
    public boolean isAway() {
        return mStarted == 0;
    }

    /** The picker opened (for any reason). */
    public void onShown(long nowMs) {
        mShownAtMs = nowMs;
        mBack = false;
    }

    /** True once after the app came back from the launcher, however short the time away. */
    public boolean takeReturned() {
        boolean returned = mReturned;
        mReturned = false;
        return returned;
    }

    /** True once after the app came back from being away long enough. */
    public boolean takeBack() {
        boolean back = mBack;
        mBack = false;
        return back;
    }
}
