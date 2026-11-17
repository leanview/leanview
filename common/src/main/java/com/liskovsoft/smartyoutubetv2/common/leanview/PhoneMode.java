package com.liskovsoft.smartyoutubetv2.common.leanview;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;

import com.liskovsoft.sharedutils.helpers.Helpers;

/**
 * leanview: the one way to tell "phone", used by every touch change of the app (cards that open on one
 * tap, finger seeking, 48 dp buttons, the upright on-screen controls and captions). The person's
 * "Screen type" setting comes first (TV / Phone force the answer); Automatic detects: a touchscreen and
 * NOT an Android TV (Helpers.isAndroidTV: the television or leanback feature, the repo's one definition of
 * a TV) and NOT a television UI mode (which also covers API 21 and older, where isAndroidTV is false).
 * On the TV it is false, so nothing there changes.
 *
 * Two upstream behaviours deliberately keep their own touch-only test (Helpers.isTouchSupported): the
 * double-tap overlay (PlaybackFragment) and the edge swipe back (MotherActivity). They exist today on any
 * device that reports a touchscreen, TV included; moving them here would change such a TV.
 */
public final class PhoneMode {
    private PhoneMode() {
    }

    public static boolean isPhone(Context context) {
        if (context == null) {
            return false;
        }
        switch (ScreenType.get(context)) {
            case TV:
                return false;
            case PHONE:
                return true;
            default:
                break;
        }
        boolean touchscreen = context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN);
        int uiType = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_TYPE_MASK;
        return decide(touchscreen, Helpers.isAndroidTV(context), uiType == Configuration.UI_MODE_TYPE_TELEVISION);
    }

    static boolean decide(boolean touchscreen, boolean androidTv, boolean televisionUiMode) {
        return touchscreen && !androidTv && !televisionUiMode;
    }

    /** Taller than wide: the phone is held upright. */
    public static boolean isPortrait(int widthPx, int heightPx) {
        return heightPx > widthPx;
    }

    /**
     * Side padding of the captions. The TV layout keeps 150sp on each side; a phone held upright uses
     * the whole width (a small margin); everything else keeps the layout's own padding.
     */
    public static int captionSidePaddingPx(boolean phone, boolean portrait, float realDensity, int layoutPaddingPx, int portraitDp) {
        return phone && portrait ? Math.round(portraitDp * realDensity) : layoutPaddingPx;
    }
}
