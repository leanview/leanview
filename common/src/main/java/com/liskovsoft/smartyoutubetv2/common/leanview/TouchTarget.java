package com.liskovsoft.smartyoutubetv2.common.leanview;

import android.content.Context;
import android.content.res.Resources;

import com.liskovsoft.smartyoutubetv2.common.R;

/**
 * leanview: touch target sizes on a phone: at least 48 dp of the REAL screen (the Android guideline; 40 dp
 * is the hard floor). SmartTube rewrites the display density to scale its TV layout (MotherActivity.initDpi:
 * 2.4375 on a 1080x2400 phone whose real density is 2.625), so a "48dp" in a layout file is only 44.6 real
 * dp there. The size is therefore computed from the system density.
 */
public final class TouchTarget {
    private TouchTarget() {
    }

    /** The pixels that are {@code dp} on the real screen. */
    public static int px(float realDensity, int dp) {
        return Math.round(dp * realDensity);
    }

    /** The size to give a target: what it has, or the minimum when it is smaller. */
    public static int atLeast(int currentPx, int minPx) {
        return Math.max(currentPx, minPx);
    }

    /** The minimum touch target in pixels on this device. */
    public static int minPx(Context context) {
        return px(realDensity(), context.getResources().getInteger(R.integer.leanview_touch_target_min_dp));
    }

    /** The density of the real screen, not the one the app rewrote for its own layout. */
    public static float realDensity() {
        return Resources.getSystem().getDisplayMetrics().density;
    }
}
