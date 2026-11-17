package com.liskovsoft.smartyoutubetv2.common.leanview;

import static com.liskovsoft.smartyoutubetv2.common.leanview.BackgroundPlayback.Action.AUDIO;
import static com.liskovsoft.smartyoutubetv2.common.leanview.BackgroundPlayback.Action.BEHIND;
import static com.liskovsoft.smartyoutubetv2.common.leanview.BackgroundPlayback.Action.CLOSE;
import static com.liskovsoft.smartyoutubetv2.common.leanview.BackgroundPlayback.Action.PIP;
import static org.junit.Assert.assertEquals;

import android.content.Context;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

// leanview: BACK and HOME choices live in the player data of each profile. Data saved before them
// gets the leanview defaults (BACK closes, HOME corner window), whatever the old mode field said.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class BackgroundPlaybackPrefsTest {
    private static final String KEY = "video_player_data";
    private Context mContext;
    private PlayerData mData;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.getApplication();
        mData = PlayerData.instance(mContext);
    }

    /** Player data as the previous builds wrote it: this many slots, the old mode in slot 7. */
    private void seed(int slots, int oldMode, Object marker63, Object back64, Object home65) {
        Object[] items = new Object[slots];
        items[7] = oldMode;
        if (slots > 63) {
            items[63] = marker63;
        }
        if (slots > 64) {
            items[64] = back64;
        }
        if (slots > 65) {
            items[65] = home65;
        }
        AppPrefs.instance(mContext).setProfileData(KEY, Helpers.mergeData(items));
        mData.onProfileChanged(); // reads the data again
    }

    private String[] saved() {
        ShadowLooper.idleMainLooper();
        mData.persistNow();
        ShadowLooper.idleMainLooper();
        return Helpers.splitData(AppPrefs.instance(mContext).getProfileData(KEY));
    }

    @Test
    public void dataFromBeforeTheForcedPipGetsTheDefaults() {
        seed(63, 1 /* only audio */, null, null, null);

        assertEquals(CLOSE, mData.getBackAction());
        assertEquals(PIP, mData.getHomeAction());
    }

    @Test
    public void dataFromTheForcedPipBuildGetsTheDefaultsToo() {
        seed(64, 2 /* pip */, true, null, null);

        assertEquals(CLOSE, mData.getBackAction());
        assertEquals(PIP, mData.getHomeAction());
    }

    @Test
    public void emptyDataGetsTheDefaults() {
        AppPrefs.instance(mContext).setProfileData(KEY, null);
        mData.onProfileChanged();

        assertEquals(CLOSE, mData.getBackAction());
        assertEquals(PIP, mData.getHomeAction());
    }

    @Test
    public void theOldForcedPipMarkerCannotOverrideTheChoice() {
        seed(66, 2, true, AUDIO.code, CLOSE.code);

        assertEquals(AUDIO, mData.getBackAction());
        assertEquals(CLOSE, mData.getHomeAction());
        assertEquals(1 /* sound, as the old field says */, mData.getBackgroundMode());
    }

    @Test
    public void aChoiceWithoutTheMarkerIsKeptToo() {
        seed(66, 0, false, PIP.code, BEHIND.code);

        assertEquals(PIP, mData.getBackAction());
        assertEquals(BEHIND, mData.getHomeAction());
    }

    @Test
    public void unknownCodesFallBackToTheDefaults() {
        seed(66, 0, true, 42, -3);

        assertEquals(CLOSE, mData.getBackAction());
        assertEquals(PIP, mData.getHomeAction());
    }

    @Test
    public void choicesAreSavedAtTheEndAndSurviveRestore() {
        seed(63, 0, null, null, null);

        mData.setBackAction(PIP);
        mData.setHomeAction(AUDIO);
        String[] slots = saved();

        assertEquals(String.valueOf(PIP.code), slots[64]);
        assertEquals(String.valueOf(AUDIO.code), slots[65]);
        assertEquals("true", slots[63]); // the retired marker is still written for older readers

        mData.onProfileChanged();
        assertEquals(PIP, mData.getBackAction());
        assertEquals(AUDIO, mData.getHomeAction());
    }

    @Test
    public void theOldModeFieldIsKeptInStep() {
        seed(63, 0, null, null, null);

        mData.setBackAction(CLOSE);
        mData.setHomeAction(CLOSE);
        assertEquals("0", saved()[7]);

        mData.setHomeAction(AUDIO);
        assertEquals("1", saved()[7]);

        mData.setBackAction(PIP);
        assertEquals("2", saved()[7]);

        mData.setBackAction(CLOSE);
        mData.setHomeAction(BEHIND);
        assertEquals("3", saved()[7]);
    }
}
