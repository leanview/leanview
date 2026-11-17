package com.liskovsoft.smartyoutubetv2.common.leanview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// leanview: topic sections start hidden except Music, which sits right after Subscriptions; a menu saved
// by an older build is switched only if it is still the default of the version it was saved with.
public class SidebarDefaultsTest {
    private static List<Integer> upstream() {
        return new ArrayList<>(Arrays.asList(
                MediaGroup.TYPE_HOME, MediaGroup.TYPE_SHORTS, MediaGroup.TYPE_KIDS_HOME, MediaGroup.TYPE_SPORTS,
                MediaGroup.TYPE_LIVE, MediaGroup.TYPE_GAMING, MediaGroup.TYPE_NEWS, MediaGroup.TYPE_MUSIC,
                MediaGroup.TYPE_CHANNEL_UPLOADS, MediaGroup.TYPE_SUBSCRIPTIONS, MediaGroup.TYPE_HISTORY,
                MediaGroup.TYPE_USER_PLAYLISTS, MediaGroup.TYPE_MY_VIDEOS, MediaGroup.TYPE_SETTINGS));
    }

    /** What version 1 of this app created: no topics, Music still hidden. */
    private static List<Integer> v1() {
        return new ArrayList<>(Arrays.asList(
                MediaGroup.TYPE_HOME, MediaGroup.TYPE_SHORTS, MediaGroup.TYPE_CHANNEL_UPLOADS,
                MediaGroup.TYPE_SUBSCRIPTIONS, MediaGroup.TYPE_HISTORY, MediaGroup.TYPE_USER_PLAYLISTS,
                MediaGroup.TYPE_MY_VIDEOS, MediaGroup.TYPE_SETTINGS));
    }

    private static List<Integer> v2() {
        return new ArrayList<>(Arrays.asList(
                MediaGroup.TYPE_HOME, MediaGroup.TYPE_SHORTS, MediaGroup.TYPE_CHANNEL_UPLOADS,
                MediaGroup.TYPE_SUBSCRIPTIONS, MediaGroup.TYPE_MUSIC, MediaGroup.TYPE_HISTORY,
                MediaGroup.TYPE_USER_PLAYLISTS, MediaGroup.TYPE_MY_VIDEOS, MediaGroup.TYPE_SETTINGS));
    }

    @Test
    public void topicsAreTheFiveThatStartHidden() {
        assertTrue(SidebarDefaults.isTopic(MediaGroup.TYPE_KIDS_HOME));
        assertTrue(SidebarDefaults.isTopic(MediaGroup.TYPE_SPORTS));
        assertTrue(SidebarDefaults.isTopic(MediaGroup.TYPE_LIVE));
        assertTrue(SidebarDefaults.isTopic(MediaGroup.TYPE_GAMING));
        assertTrue(SidebarDefaults.isTopic(MediaGroup.TYPE_NEWS));
        assertFalse(SidebarDefaults.isTopic(MediaGroup.TYPE_MUSIC));
        assertFalse(SidebarDefaults.isTopic(MediaGroup.TYPE_HOME));
        assertFalse(SidebarDefaults.isTopic(MediaGroup.TYPE_SHORTS));
        assertFalse(SidebarDefaults.isTopic(MediaGroup.TYPE_SUBSCRIPTIONS));
        assertFalse(SidebarDefaults.isTopic(MediaGroup.TYPE_MY_VIDEOS));
        assertFalse(SidebarDefaults.isTopic(MediaGroup.TYPE_SETTINGS));
        assertFalse(SidebarDefaults.isTopic(-1)); // a pinned channel
    }

    @Test
    public void aFreshMenuIsHomeShortsChannelsSubscriptionsMusicHistoryPlaylistsMyVideosSettings() {
        assertEquals(v2(), SidebarDefaults.freshMenu());
    }

    @Test
    public void versionIsTwo() {
        assertEquals(2, SidebarDefaults.VERSION);
    }

    @Test
    public void anUntouchedUpstreamMenuGetsTheFreshMenu() {
        assertEquals(v2(), SidebarDefaults.migratedMenu(upstream(), 0, true));
    }

    @Test
    public void theVersionOneDefaultGetsMusicAfterSubscriptions() {
        assertEquals(v2(), SidebarDefaults.migratedMenu(v1(), 1, true));
    }

    @Test
    public void nothingChangesInAFlavorThatDoesNotAsk() {
        assertNull(SidebarDefaults.migratedMenu(upstream(), 0, false));
        assertNull(SidebarDefaults.migratedMenu(v1(), 1, false));
    }

    @Test
    public void aMenuAlreadyAtTheCurrentVersionIsNotSwitchedAgain() {
        // The user removed Music after the switch: the v1 list again, but saved at version 2.
        assertNull(SidebarDefaults.migratedMenu(v1(), SidebarDefaults.VERSION, true));
        assertNull(SidebarDefaults.migratedMenu(upstream(), SidebarDefaults.VERSION, true));
        assertNull(SidebarDefaults.migratedMenu(v2(), SidebarDefaults.VERSION, true));
    }

    @Test
    public void aMenuIsOnlyMatchedAgainstTheDefaultOfItsOwnVersion() {
        assertNull(SidebarDefaults.migratedMenu(v1(), 0, true));
        assertNull(SidebarDefaults.migratedMenu(upstream(), 1, true));
    }

    @Test
    public void aVersionOneMenuTheOwnerEditedKeepsItsChoices() {
        // A hand-arranged menu: already has Music after Subscriptions.
        assertNull(SidebarDefaults.migratedMenu(v2(), 1, true));

        List<Integer> musicElsewhere = v1();
        musicElsewhere.add(1, MediaGroup.TYPE_MUSIC);
        assertNull(SidebarDefaults.migratedMenu(musicElsewhere, 1, true));

        List<Integer> withoutHistory = v1();
        withoutHistory.remove(Integer.valueOf(MediaGroup.TYPE_HISTORY));
        assertNull(SidebarDefaults.migratedMenu(withoutHistory, 1, true));

        List<Integer> reordered = v1();
        reordered.add(0, reordered.remove(reordered.size() - 1));
        assertNull(SidebarDefaults.migratedMenu(reordered, 1, true));

        List<Integer> pinned = v1();
        pinned.add(pinned.size() - 1, -1); // a pinned channel
        assertNull(SidebarDefaults.migratedMenu(pinned, 1, true));

        List<Integer> withNews = v1();
        withNews.add(MediaGroup.TYPE_NEWS);
        assertNull(SidebarDefaults.migratedMenu(withNews, 1, true));
    }

    @Test
    public void aVersionZeroMenuTheOwnerEditedKeepsItsChoices() {
        List<Integer> withoutNews = upstream();
        withoutNews.remove(Integer.valueOf(MediaGroup.TYPE_NEWS));
        assertNull(SidebarDefaults.migratedMenu(withoutNews, 0, true));

        List<Integer> reordered = upstream();
        reordered.add(0, reordered.remove(reordered.size() - 1));
        assertNull(SidebarDefaults.migratedMenu(reordered, 0, true));

        List<Integer> pinned = upstream();
        pinned.add(pinned.size() - 1, -1); // a pinned channel
        assertNull(SidebarDefaults.migratedMenu(pinned, 0, true));

        List<Integer> extra = upstream();
        extra.add(MediaGroup.TYPE_TRENDING);
        assertNull(SidebarDefaults.migratedMenu(extra, 0, true));
    }

    @Test
    public void emptyOrMissingMenuIsLeftAlone() {
        assertNull(SidebarDefaults.migratedMenu(new ArrayList<>(), 0, true));
        assertNull(SidebarDefaults.migratedMenu(null, 0, true));
        assertNull(SidebarDefaults.migratedMenu(new ArrayList<>(), 1, true));
        assertNull(SidebarDefaults.migratedMenu(null, 1, true));
    }
}
