package com.liskovsoft.smartyoutubetv2.common.leanview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

// leanview: each profile keeps the video it left unfinished (Alice, 2026-11-09), kept on disk at
// once and never mixed with another profile's.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ContinueWatchingTest {
    private static final long MIN = 60_000;
    private static final long TEN_MIN = 10 * MIN;
    private static final long T = 1_000_000_000L;
    private Context mContext;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.getApplication();
        mContext.getSharedPreferences("leanview_continue_watching", Context.MODE_PRIVATE).edit().clear().commit();
    }

    /** As the player has it after opening a link: only the metadata title (fields 17 and 18). */
    private static Video fromLink(String id, String metadataTitle, String metadataSecondTitle) {
        Video empty = new Video();
        empty.videoId = id;
        String[] fields = empty.toString().split("&vi;", -1);
        fields[17] = metadataTitle;
        fields[18] = metadataSecondTitle;
        return Video.fromString(String.join("&vi;", fields));
    }

    private static Video video(String id) {
        Video video = new Video();
        video.videoId = id;
        video.title = "Title " + id;
        return video;
    }

    @Test
    public void leftAMomentAgoItOpensPausedWhereItWasOnce() {
        ContinueWatching.record(mContext, "carol", video("a"), 4 * MIN, TEN_MIN, T);

        Video open = ContinueWatching.takeJustLeft(mContext, "carol", T + 30 * MIN);

        assertNotNull(open);
        assertEquals("a", open.videoId);
        assertEquals("Title a", open.title);
        assertEquals(4 * MIN, open.pendingPosMs);
        assertTrue(open.leanviewOpenPaused);
        assertNull("only once", ContinueWatching.takeJustLeft(mContext, "carol", T + 31 * MIN));
        assertNotNull("Home still has it", ContinueWatching.homeCard(mContext, "carol", T + 31 * MIN));
    }

    @Test
    public void closingTheReopenedVideoWithoutWatchingKeepsWhenItWasLeft() {
        ContinueWatching.record(mContext, "carol", video("a"), 4 * MIN, TEN_MIN, T);
        ContinueWatching.takeJustLeft(mContext, "carol", T + 10 * MIN);
        // The player saves it again as it closes, a second further
        ContinueWatching.record(mContext, "carol", video("a"), 4 * MIN + 1_000, TEN_MIN, T + 11 * MIN);

        assertNull(ContinueWatching.takeJustLeft(mContext, "carol", T + 12 * MIN));
        // Still left at T: a day after T it is gone (left at T + 11 min it would still be offered)
        assertNull(ContinueWatching.homeCard(mContext, "carol", T + 24 * 60 * MIN));
    }

    @Test
    public void watchingMoreOpensItAgainNextTime() {
        ContinueWatching.record(mContext, "carol", video("a"), 4 * MIN, TEN_MIN, T);
        ContinueWatching.takeJustLeft(mContext, "carol", T + 10 * MIN);
        ContinueWatching.record(mContext, "carol", video("a"), 6 * MIN, TEN_MIN, T + 13 * MIN);

        Video open = ContinueWatching.takeJustLeft(mContext, "carol", T + 20 * MIN);

        assertNotNull(open);
        assertEquals(6 * MIN, open.pendingPosMs);
    }

    @Test
    public void laterItIsOnlyTheHomeCardWithItsProgress() {
        ContinueWatching.record(mContext, "carol", video("a"), 3 * MIN, TEN_MIN, T);

        assertNull(ContinueWatching.takeJustLeft(mContext, "carol", T + 2 * 60 * MIN));
        Video card = ContinueWatching.homeCard(mContext, "carol", T + 2 * 60 * MIN);
        assertNotNull(card);
        assertEquals(30f, card.percentWatched, 0.01f);
        assertFalse(card.leanviewOpenPaused);
    }

    @Test
    public void eachProfileHasItsOwn() {
        ContinueWatching.record(mContext, "carol", video("a"), 3 * MIN, TEN_MIN, T);
        ContinueWatching.record(mContext, "bob", video("b"), 5 * MIN, TEN_MIN, T);

        assertEquals("a", ContinueWatching.homeCard(mContext, "carol", T + MIN).videoId);
        assertEquals("b", ContinueWatching.homeCard(mContext, "bob", T + MIN).videoId);
        assertNull(ContinueWatching.homeCard(mContext, "none", T + MIN));
    }

    @Test
    public void finishingItForgetsIt() {
        ContinueWatching.record(mContext, "carol", video("a"), 3 * MIN, TEN_MIN, T);
        ContinueWatching.record(mContext, "carol", video("a"), TEN_MIN, TEN_MIN, T + 8 * MIN);

        assertNull(ContinueWatching.homeCard(mContext, "carol", T + 9 * MIN));
    }

    @Test
    public void anotherVideoBarelyStartedOrFinishedKeepsTheUnfinishedOne() {
        ContinueWatching.record(mContext, "carol", video("a"), 3 * MIN, TEN_MIN, T);
        ContinueWatching.record(mContext, "carol", video("b"), 5_000, TEN_MIN, T + MIN);
        ContinueWatching.record(mContext, "carol", video("c"), TEN_MIN, TEN_MIN, T + 2 * MIN);

        assertEquals("a", ContinueWatching.homeCard(mContext, "carol", T + 3 * MIN).videoId);
    }

    @Test
    public void anotherVideoLeftUnfinishedTakesItsPlace() {
        ContinueWatching.record(mContext, "carol", video("a"), 3 * MIN, TEN_MIN, T);
        ContinueWatching.record(mContext, "carol", video("b"), 2 * MIN, TEN_MIN, T + MIN);

        assertEquals("b", ContinueWatching.homeCard(mContext, "carol", T + 3 * MIN).videoId);
    }

    @Test
    public void aVideoOpenedFromALinkStillMakesAFullCard() {
        // Opened from a link, the player only has the title it read from the page (seen on the
        // emulator 2026-11-09: the card was blank)
        Video fromLink = fromLink("G-ESDCBVI1s", "La burbuja inmobiliaria explicada con plátanos", "Los Ecomonos");
        ContinueWatching.record(mContext, "none", fromLink, 2 * MIN, TEN_MIN, T);

        Video card = ContinueWatching.homeCard(mContext, "none", T + MIN);

        assertEquals("La burbuja inmobiliaria explicada con plátanos", card.title);
        assertEquals("Los Ecomonos", card.secondTitle);
        assertEquals("https://i.ytimg.com/vi/G-ESDCBVI1s/hqdefault.jpg", card.cardImageUrl);
        assertNull("the player's own video is left as it was", fromLink.title);
    }

    @Test
    public void theClosedAtOnceMarkIsNudgedSoHomeShowsIt() {
        ContinueWatching.record(mContext, "carol", video("a"), MIN, TEN_MIN, T); // exactly 10 %

        assertEquals(10.5f, ContinueWatching.homeCard(mContext, "carol", T + MIN).percentWatched, 0.01f);
    }
}
