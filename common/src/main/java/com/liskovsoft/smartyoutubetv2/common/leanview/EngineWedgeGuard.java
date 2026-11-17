package com.liskovsoft.smartyoutubetv2.common.leanview;

import android.content.Context;

import com.liskovsoft.googlecommon.common.js.JsEngines;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.leanview.EngineWedgePolicy.Action;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

/**
 * leanview: applies {@link EngineWedgePolicy}. The engine reports a wedge (listener installed when the app starts) and the last
 * screen of the app stopping ({@link StartedScreens}, every activity of the process) both schedule ONE check a second later on
 * the main thread; that check decides, saves the app's data and ends the process in the same main-thread step, so a user who
 * came back in that second (a screen started) is never closed on. While the app is away but something plays or a backup runs,
 * nothing reports the end of that, so the check runs again every minute until it can act or the wedge is gone (final review,
 * M1 and M3).
 */
public final class EngineWedgeGuard {
    private static final String TAG = EngineWedgeGuard.class.getSimpleName();
    private static final long CHECK_DELAY_MS = 1_000;
    private static final long CHECK_AGAIN_MS = 60_000;
    private static Context sContext;
    private static final Runnable sCheck = EngineWedgeGuard::check;

    private EngineWedgeGuard() {
    }

    /** At app start: a wedge that happens before any screen stopped (or with no screen at all) is acted on too. */
    public static void install(Context context) {
        sContext = context.getApplicationContext();
        JsEngines.setOnWedged(() -> Utils.postDelayed(sCheck, CHECK_DELAY_MS));
    }

    /** A screen of the app stopped (called after AccountPicker counted it). A later check replaces a pending one. */
    public static void onScreenStopped(Context context) {
        if (sContext == null) {
            install(context);
        }
        if (JsEngines.isWedged()) {
            Utils.postDelayed(sCheck, CHECK_DELAY_MS);
        }
    }

    private static void check() {
        Context context = sContext;
        if (context == null) {
            return;
        }
        Action action = decide(context);
        if (action == Action.CHECK_AGAIN) {
            Utils.postDelayed(sCheck, CHECK_AGAIN_MS);
            return;
        }
        if (action != Action.END_PROCESS) {
            return;
        }
        Log.e(TAG, "A player script has run past its cap and blocks every solve: the app ends its process while in the background");
        Utils.persistAppData(context);
        Runtime.getRuntime().exit(0);
    }

    private static Action decide(Context context) {
        PlaybackPresenter playback = PlaybackPresenter.instance(context);
        boolean playing = playback.isPlaying() || playback.isRunningInBackground() || playback.isEngineBlocked();
        return EngineWedgePolicy.decide(JsEngines.isWedged(), AccountPicker.instance().isAway(), playing, playback.isInPipMode(),
                RunningBackups.any());
    }
}
