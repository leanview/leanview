package com.liskovsoft.smartyoutubetv2.common.leanview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.leanview.EndScreenSettings.NextSource;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

// leanview: the end screen settings live in the player data of each profile, appended after the
// BACK and HOME slots. Data saved before them gets the leanview defaults.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class EndScreenPrefsTest {
    private static final String KEY = "video_player_data";
    private Context mContext;
    private PlayerData mData;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.getApplication();
        mData = PlayerData.instance(mContext);
    }

    private void seed(int slots, Object slot66) {
        Object[] items = new Object[slots];
        if (slots > 66) {
            items[66] = slot66;
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
    public void dataFromBeforeTheEndScreenGetsTheDefaults() {
        seed(66, null);

        assertEquals(EndScreenSettings.defaults().serialize(), mData.getEndScreen().serialize());
    }

    @Test
    public void emptyDataGetsTheDefaults() {
        AppPrefs.instance(mContext).setProfileData(KEY, null);
        mData.onProfileChanged();

        assertEquals(EndScreenSettings.defaults().serialize(), mData.getEndScreen().serialize());
    }

    @Test
    public void aChoiceIsSavedInTheNextFreeSlotAndSurvivesRestore() {
        seed(66, null);

        mData.setEndScreen(EndScreenSettings.defaults().withEnabled(false).withRelatedCountdownSec(20));
        String[] slots = saved();

        assertEquals(EndScreenSettings.defaults().withEnabled(false).withRelatedCountdownSec(20).serialize(), slots[66]);

        mData.onProfileChanged();
        assertFalse(mData.getEndScreen().enabled);
        assertEquals(20, mData.getEndScreen().relatedCountdownSec);
    }

    @Test
    public void theBackAndHomeSlotsAreUntouched() {
        seed(66, null);
        mData.setEndScreen(EndScreenSettings.defaults().withNextSource(NextSource.PLAYLIST_ONLY));

        String[] slots = saved();

        assertEquals("true", slots[63]);
        assertEquals("0", slots[64]); // BACK closes
        assertEquals("1", slots[65]); // HOME corner window
    }

    @Test
    public void aGarbledSlotGetsTheDefaults() {
        seed(67, "not;a;setting;at;all;?;?");

        assertTrue(mData.getEndScreen().enabled);
        assertEquals(8, mData.getEndScreen().relatedCountdownSec);
        assertEquals(3, mData.getEndScreen().playlistCountdownSec);
    }
}
