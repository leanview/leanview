package com.liskovsoft.smartyoutubetv2.common.leanview;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;

import com.liskovsoft.smartyoutubetv2.common.prefs.AccountsData;

/**
 * leanview: counts EVERY started screen of the process for {@link AccountPicker} (away or not) and tells
 * {@link EngineWedgeGuard} when one stops. It used to count only the MotherActivity screens, so an activity of a library (the
 * file picker, the search screen) or a trampoline counted as "away": with a wedged engine and nothing playing, opening the
 * file picker from Settings stopped the screen behind it and the guard ended the process under the open picker (final
 * review, M2). Registered once, at app start ({@link #register}).
 */
public final class StartedScreens implements Application.ActivityLifecycleCallbacks {
    /** The away time of the account picker (Settings > Accounts), read when a screen starts. */
    interface AwaySetting {
        long awayMs(Context context);
    }

    /** What happens after a screen stopped and was counted. */
    interface OnStopped {
        void onStopped(Activity activity);
    }

    private static boolean sRegistered;
    private final AccountPicker mPicker;
    private final AwaySetting mAway;
    private final OnStopped mOnStopped;

    StartedScreens(AccountPicker picker, AwaySetting away, OnStopped onStopped) {
        mPicker = picker;
        mAway = away;
        mOnStopped = onStopped;
    }

    public static synchronized void register(Application app) {
        if (sRegistered) {
            return;
        }
        sRegistered = true;
        app.registerActivityLifecycleCallbacks(new StartedScreens(AccountPicker.instance(),
                context -> AccountsData.instance(context).getPickerAwayMs(), EngineWedgeGuard::onScreenStopped));
    }

    @Override
    public void onActivityStarted(Activity activity) {
        mPicker.onScreenStarted(SystemClock.elapsedRealtime(), mAway.awayMs(activity));
    }

    @Override
    public void onActivityStopped(Activity activity) {
        mPicker.onScreenStopped(SystemClock.elapsedRealtime());
        mOnStopped.onStopped(activity);
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
    }

    @Override
    public void onActivityResumed(Activity activity) {
    }

    @Override
    public void onActivityPaused(Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
    }
}
