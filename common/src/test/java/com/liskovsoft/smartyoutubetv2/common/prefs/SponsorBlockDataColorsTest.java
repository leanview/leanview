package com.liskovsoft.smartyoutubetv2.common.prefs;

import static org.junit.Assert.assertEquals;

import android.content.Context;

import androidx.core.content.ContextCompat;

import com.liskovsoft.mediaserviceinterfaces.data.SponsorSegment;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;

// leanview: the seek bar wears the web's SponsorBlock palette (yt.example.com, YouTube theme, as
// Chromium paints it on 2026-11-09), and what is only marked is drawn at half opacity.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class SponsorBlockDataColorsTest {
    private Context mContext;
    private SponsorBlockData mData;

    @Before
    public void setUp() throws Exception {
        mContext = RuntimeEnvironment.getApplication();
        forget(SponsorBlockData.class);
        forget(AppPrefs.class);
        mData = SponsorBlockData.instance(mContext);
    }

    private static void forget(Class<?> singleton) throws Exception {
        Field instance = singleton.getDeclaredField("sInstance");
        instance.setAccessible(true);
        instance.set(null, null);
    }

    private int color(String category) {
        return ContextCompat.getColor(mContext, mData.getColorRes(category));
    }

    @Test
    public void eachCategoryHasTheWebColor() {
        assertEquals(0xFFFFC921, color(SponsorSegment.CATEGORY_SPONSOR));
        assertEquals(0xFFE9A48A, color(SponsorSegment.CATEGORY_MUSIC_OFF_TOPIC));
        assertEquals(0xFFA8C283, color(SponsorSegment.CATEGORY_SELF_PROMO));
        assertEquals(0xFF7ECAA9, color(SponsorSegment.CATEGORY_INTRO));
        assertEquals(0xFF93BAF1, color(SponsorSegment.CATEGORY_PREVIEW_RECAP));
        assertEquals(0xFFB7AEED, color(SponsorSegment.CATEGORY_OUTRO));
        assertEquals(0xFFD2A5DB, color(SponsorSegment.CATEGORY_FILLER));
        assertEquals(0xFFE6A0BA, color(SponsorSegment.CATEGORY_INTERACTION));
        assertEquals(0xFFF1F1F1, color(SponsorSegment.CATEGORY_POI_HIGHLIGHT));
    }

    @Test
    public void onlyWhatIsMarkedIsDimmed() {
        int sponsor = color(SponsorSegment.CATEGORY_SPONSOR);
        assertEquals(0x80FFC921, SponsorBlockData.markerColor(sponsor, SponsorBlockData.ACTION_DO_NOTHING));
        assertEquals(sponsor, SponsorBlockData.markerColor(sponsor, SponsorBlockData.ACTION_SKIP_ONLY));
        assertEquals(sponsor, SponsorBlockData.markerColor(sponsor, SponsorBlockData.ACTION_SKIP_WITH_TOAST));
        assertEquals(sponsor, SponsorBlockData.markerColor(sponsor, SponsorBlockData.ACTION_SHOW_DIALOG));
        assertEquals(sponsor, SponsorBlockData.markerColor(sponsor, SponsorBlockData.ACTION_UNDEFINED));
    }
}
