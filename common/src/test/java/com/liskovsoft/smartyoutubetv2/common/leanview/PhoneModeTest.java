package com.liskovsoft.smartyoutubetv2.common.leanview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.pm.PackageManager;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

// leanview: "phone" = a touchscreen and NOT a leanback / TV UI. On the TV nothing may change.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class PhoneModeTest {
    @Test
    public void onlyATouchscreenWithoutLeanbackOrTvUiIsAPhone() {
        assertTrue(PhoneMode.decide(true, false, false));
        assertFalse(PhoneMode.decide(false, false, false));  // a box with no touchscreen
        assertFalse(PhoneMode.decide(true, true, false));    // leanback device that also reports touch
        assertFalse(PhoneMode.decide(true, false, true));    // television UI mode
        assertFalse(PhoneMode.decide(false, true, true));    // a normal TV
    }

    private static Context withFeatures(boolean touch, boolean leanback, boolean television) {
        Context context = RuntimeEnvironment.getApplication();
        shadowOf(context.getPackageManager()).setSystemFeature(PackageManager.FEATURE_TOUCHSCREEN, touch);
        shadowOf(context.getPackageManager()).setSystemFeature(PackageManager.FEATURE_LEANBACK, leanback);
        shadowOf(context.getPackageManager()).setSystemFeature(PackageManager.FEATURE_TELEVISION, television);
        return context;
    }

    @Test
    public void aTouchscreenDeviceWithoutLeanbackIsAPhone() {
        assertTrue(PhoneMode.isPhone(withFeatures(true, false, false)));
    }

    @Test
    public void aTvIsNeverAPhoneWhateverItsTouchscreenSays() {
        assertFalse(PhoneMode.isPhone(withFeatures(false, true, true)));
        assertFalse(PhoneMode.isPhone(withFeatures(true, true, true)));
        assertFalse(PhoneMode.isPhone(withFeatures(true, true, false)));
        assertFalse(PhoneMode.isPhone(withFeatures(true, false, true)));
    }

    @Test
    @Config(sdk = 28, qualifiers = "television")
    public void aTelevisionUiModeIsNeverAPhone() {
        assertFalse(PhoneMode.isPhone(withFeatures(true, false, false)));
    }

    @Test
    public void noTouchscreenIsNotAPhone() {
        assertFalse(PhoneMode.isPhone(withFeatures(false, false, false)));
    }

    // Alice's ruling 2026-11-14 (review I-1): Settings > UI > Screen type forces TV or Phone; Automatic detects.
    @Test
    public void theScreenTypeSettingComesFirst() {
        Context context = withFeatures(true, false, false); // a phone

        ScreenType.set(context, ScreenType.TV);
        assertFalse(PhoneMode.isPhone(context)); // forced to behave as a TV
        ScreenType.set(context, ScreenType.AUTOMATIC);
        assertTrue(PhoneMode.isPhone(context)); // back to detection

        withFeatures(false, true, true); // a TV (the same application, other features)
        ScreenType.set(context, ScreenType.PHONE);
        assertTrue(PhoneMode.isPhone(context)); // forced to behave as a phone
        ScreenType.set(context, ScreenType.AUTOMATIC);
        assertFalse(PhoneMode.isPhone(context));
    }

    @Test
    public void aTouchBoxWithNoLeanbackCanBeForcedToTv() {
        // the AVD / generic box case: a touchscreen and no leanback is detected as a phone ...
        Context box = withFeatures(true, false, false);
        assertTrue(PhoneMode.isPhone(box));
        // ... and the person turns that off
        ScreenType.set(box, ScreenType.TV);
        assertFalse(PhoneMode.isPhone(box));
    }

    @Test
    public void theScreenTypeIsStoredAndDefaultsToAutomatic() {
        Context context = RuntimeEnvironment.getApplication();
        assertEquals(ScreenType.AUTOMATIC, ScreenType.get(context));
        ScreenType.set(context, ScreenType.PHONE);
        assertEquals(ScreenType.PHONE, ScreenType.get(context));
        ScreenType.set(context, ScreenType.TV);
        assertEquals(ScreenType.TV, ScreenType.get(context));
    }

    @Test
    public void portraitIsTallerThanWide() {
        assertTrue(PhoneMode.isPortrait(1080, 2400));
        assertFalse(PhoneMode.isPortrait(2400, 1080));
        assertFalse(PhoneMode.isPortrait(1080, 1080));
    }

    @Test
    public void captionsUseTheWholeWidthOnlyOnAPhoneHeldUpright() {
        float density = 2.625f;
        int layoutPadding = 394; // 150sp of the layout, as measured on the phone
        assertEquals(Math.round(12 * density), PhoneMode.captionSidePaddingPx(true, true, density, layoutPadding, 12));
        assertEquals(layoutPadding, PhoneMode.captionSidePaddingPx(true, false, density, layoutPadding, 12)); // landscape: as before
        assertEquals(layoutPadding, PhoneMode.captionSidePaddingPx(false, true, density, layoutPadding, 12)); // TV: untouched
        assertEquals(layoutPadding, PhoneMode.captionSidePaddingPx(false, false, density, layoutPadding, 12));
    }
}
