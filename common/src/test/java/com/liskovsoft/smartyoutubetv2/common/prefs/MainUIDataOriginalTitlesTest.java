package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.lang.reflect.Field;
import java.time.Duration;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

// leanview: video titles as their creators wrote them (SmartTube's "unlocalized titles", oEmbed) are on
// by default, also for a profile that had them off before this build, but only once: turning them
// off afterwards stays off.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class MainUIDataOriginalTitlesTest {
    private Context mContext;

    @Before
    public void setUp() throws Exception {
        mContext = RuntimeEnvironment.getApplication();
        forget(MainUIData.class);
        forget(AppPrefs.class);
    }

    private static void forget(Class<?> singleton) throws Exception {
        Field instance = singleton.getDeclaredField("sInstance");
        instance.setAccessible(true);
        instance.set(null, null);
    }

    // Settings are written 10 s after a change.
    private static void written() {
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofSeconds(11));
    }

    private MainUIData reopen() throws Exception {
        written();
        forget(MainUIData.class);
        return MainUIData.instance(mContext);
    }

    @Test
    public void onForANewInstall() throws Exception {
        assertTrue(reopen().isUnlocalizedTitlesEnabled());
    }

    @Test
    public void turnedOnOnceForAProfileThatHadThemOff() throws Exception {
        MainUIData data = reopen();
        data.setUnlocalizedTitlesEnabled(false);
        written();
        // What a build before this one stored: the same fields, without the leanview flag at 24.
        String stored = AppPrefs.instance(mContext).getProfileData("main_ui_data2");
        String[] fields = stored.split("%OB%", -1);
        assertFalse(Boolean.parseBoolean(fields[22]));
        StringBuilder before = new StringBuilder();
        for (int i = 0; i < 24; i++) {
            before.append(i == 0 ? "" : "%OB%").append(i < fields.length ? fields[i] : "");
        }
        AppPrefs.instance(mContext).setProfileData("main_ui_data2", before.toString());

        assertTrue(reopen().isUnlocalizedTitlesEnabled());
    }

    @Test
    public void turningThemOffAfterwardsStaysOff() throws Exception {
        reopen().setUnlocalizedTitlesEnabled(false);
        assertFalse(reopen().isUnlocalizedTitlesEnabled());
    }
}
