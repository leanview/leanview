package com.liskovsoft.smartyoutubetv2.common.leanview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

// leanview: every touch target on a phone is at least 48 dp of the REAL screen (40 dp is the hard floor).
// SmartTube rewrites the display density to scale its TV layout (MotherActivity.initDpi), so "48dp" in a
// layout file is only 44.6 real dp on a 1080x2400 phone: the size is computed from the system density.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class TouchTargetTest {
    @Test
    public void theMinimumIsTheAndroidGuideline() {
        Context context = RuntimeEnvironment.getApplication();
        int min = context.getResources().getInteger(R.integer.leanview_touch_target_min_dp);
        int floor = context.getResources().getInteger(R.integer.leanview_touch_target_floor_dp);
        assertEquals(48, min);
        assertEquals(40, floor);
        assertTrue(min >= floor);
    }

    @Test
    public void dpAreCountedOnTheRealScreen() {
        assertEquals(126, TouchTarget.px(2.625f, 48)); // a 420 dpi phone
        assertEquals(48, TouchTarget.px(1f, 48));
    }

    @Test
    public void onlyWhatIsTooSmallGrows() {
        int min = TouchTarget.px(2.625f, 48);
        assertEquals(126, TouchTarget.atLeast(117, min)); // the 48dp button as SmartTube's scaled layout draws it
        assertEquals(126, TouchTarget.atLeast(0, min));
        assertEquals(200, TouchTarget.atLeast(200, min));
    }

    // The next three read the system density the device really has (the Robolectric qualifier), so a change of
    // where the density comes from, or of the 48, makes them fail.
    @Test
    @Config(sdk = 28, qualifiers = "420dpi")
    public void aPhoneOf420DpiGetsAMinimumOf126Px() {
        assertEquals(2.625f, TouchTarget.realDensity(), 0.001f);
        assertEquals(126, TouchTarget.minPx(RuntimeEnvironment.getApplication()));
    }

    @Test
    @Config(sdk = 28, qualifiers = "xxhdpi")
    public void aDenserScreenGetsAProportionallyBiggerMinimum() {
        assertEquals(144, TouchTarget.minPx(RuntimeEnvironment.getApplication()));
    }

    @Test
    @Config(sdk = 28, qualifiers = "mdpi")
    public void theMinimumIsNeverBelowTheFloorInPixels() {
        int floorPx = TouchTarget.px(TouchTarget.realDensity(), 40);
        assertEquals(40, floorPx);
        assertTrue(TouchTarget.minPx(RuntimeEnvironment.getApplication()) > floorPx);
    }
}
