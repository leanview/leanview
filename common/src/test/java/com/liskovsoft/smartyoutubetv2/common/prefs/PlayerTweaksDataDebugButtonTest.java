package com.liskovsoft.smartyoutubetv2.common.prefs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import com.liskovsoft.sharedutils.helpers.Helpers;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.lang.reflect.Field;

// leanview: the debug info button (the bug icon) is off by default in the leanview flavor. A profile
// saved by an older build loses it only if its buttons are still exactly upstream's defaults.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class PlayerTweaksDataDebugButtonTest {
    private static final int STATS = PlayerTweaksData.PLAYER_BUTTON_VIDEO_STATS;
    private static final int DEFAULT = PlayerTweaksData.PLAYER_BUTTON_DEFAULT;
    private Context mContext;

    @Before
    public void setUp() throws Exception {
        mContext = RuntimeEnvironment.getApplication();
        forget(PlayerTweaksData.class);
        forget(AppPrefs.class);
    }

    private static void forget(Class<?> singleton) throws Exception {
        Field instance = singleton.getDeclaredField("sInstance");
        instance.setAccessible(true);
        instance.set(null, null);
    }

    @Test
    public void anUntouchedOldProfileLosesTheButton() {
        assertEquals(DEFAULT & ~STATS, PlayerTweaksData.withoutDebugButtonIfUntouched(DEFAULT, 0, true));
    }

    @Test
    public void aProfileWhereSomethingWasChangedKeepsItsButtons() {
        int edited = (DEFAULT | PlayerTweaksData.PLAYER_BUTTON_SHARE);
        assertEquals(edited, PlayerTweaksData.withoutDebugButtonIfUntouched(edited, 0, true));

        int withoutPip = DEFAULT & ~PlayerTweaksData.PLAYER_BUTTON_PIP;
        assertEquals(withoutPip, PlayerTweaksData.withoutDebugButtonIfUntouched(withoutPip, 0, true));
    }

    @Test
    public void aProfileAlreadySwitchedKeepsTheButtonIfItTurnedItBackOn() {
        assertEquals(DEFAULT, PlayerTweaksData.withoutDebugButtonIfUntouched(DEFAULT, 1, true));
    }

    @Test
    public void otherFlavorsKeepTheButton() {
        assertEquals(DEFAULT, PlayerTweaksData.withoutDebugButtonIfUntouched(DEFAULT, 0, false));
    }

    @Test
    public void theVersionIsSavedWithTheNextSaveAndSurvivesARestart() throws Exception {
        PlayerTweaksData data = PlayerTweaksData.instance(mContext);
        assertTrue(data.isPlayerButtonEnabled(PlayerTweaksData.PLAYER_BUTTON_PIP));
        data.persistNow();
        ShadowLooper.idleMainLooper();

        String[] saved = Helpers.splitData(AppPrefs.instance(mContext).getProfileData("video_player_tweaks_data"));
        assertEquals("1", saved[62]);
        assertEquals(String.valueOf(data.getDefaultPlayerButtons()), saved[13]);

        forget(PlayerTweaksData.class);
        assertTrue(PlayerTweaksData.instance(mContext).isPlayerButtonEnabled(data.getDefaultPlayerButtons()));
    }
}
