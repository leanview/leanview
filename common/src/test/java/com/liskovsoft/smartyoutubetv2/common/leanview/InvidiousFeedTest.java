package com.liskovsoft.smartyoutubetv2.common.leanview;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

// leanview: Home for "Ninguna" (no account) is three rows from Alice's Invidious: popular, trending
// for the device country, gaming trending. These tests pin the URLs (type=music/movies are useless),
// the mapping of the Invidious video JSON to a MediaItem, and that one failing row does not hide the rest.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class InvidiousFeedTest {
    // The app's own words per magnitude, as the YouTube rows of the same app read them.
    private static final String[] ES = {"%s vistas", "%s k vistas", "%s M de vistas", "%s mil M de vistas"};
    private static final String[] EN = {"%s views", "%sK views", "%sM views", "%sB views"};

    private static final String INSTANCE = "https://invidious.example";

    private static String video(String id, String title) {
        return "{\"type\":\"video\",\"title\":\"" + title + "\",\"videoId\":\"" + id + "\","
                + "\"videoThumbnails\":[{\"quality\":\"maxres\",\"url\":\"https://i/" + id + "/maxres.jpg\"},"
                + "{\"quality\":\"medium\",\"url\":\"https://i/" + id + "/mqdefault.jpg\"}],"
                + "\"author\":\"Chan\",\"authorId\":\"UCchan\",\"lengthSeconds\":3725,\"liveNow\":false,"
                + "\"viewCount\":4018404,\"viewCountText\":\"4M views\",\"published\":1793463579,"
                + "\"publishedText\":\"8 hours ago\"}";
    }

    private static String list(String... videos) {
        return "[" + String.join(",", videos) + "]";
    }

    @Test
    public void mapsAnInvidiousVideoToAMediaItem() throws Exception {
        MediaItem item = InvidiousFeed.parseVideos(list(video("abc123", "Hello")), ES).get(0);

        assertEquals("abc123", item.getVideoId());
        assertEquals("Hello", item.getTitle());
        assertEquals("Chan", item.getAuthor());
        assertEquals("UCchan", item.getChannelId());
        assertEquals(MediaItem.TYPE_VIDEO, item.getType());
        assertEquals(3725_000L, item.getDurationMs());
        assertEquals("1:02:05", item.getBadgeText());
        assertEquals("Chan \u2022 4 M de vistas \u2022 8 hours ago", item.getSecondTitle().toString()); // the app's own word, not Invidious' "4M views"
        assertEquals("https://i/abc123/mqdefault.jpg", item.getCardImageUrl());
        assertFalse(item.isLive());
        assertFalse(item.isShorts());
    }

    @Test
    public void shortVideosAreNormalItemsThatPlayByVideoId() throws Exception {
        String json = "[{\"type\":\"shortVideo\",\"title\":\"S\",\"videoId\":\"sh1\",\"lengthSeconds\":59}]";

        MediaItem item = InvidiousFeed.parseVideos(json, EN).get(0);

        assertEquals("sh1", item.getVideoId());
        assertEquals("0:59", item.getBadgeText());
        assertNull(item.getChannelId());
        assertEquals("https://i.ytimg.com/vi/sh1/mqdefault.jpg", item.getCardImageUrl());
    }

    @Test
    public void liveStreamHasNoDurationBadge() throws Exception {
        String json = "[{\"title\":\"L\",\"videoId\":\"live1\",\"lengthSeconds\":0,\"liveNow\":true,\"author\":\"A\",\"viewCount\":0,\"publishedText\":\"0 seconds ago\"}]";

        MediaItem item = InvidiousFeed.parseVideos(json, EN).get(0);

        assertTrue(item.isLive());
        assertNull(item.getBadgeText());
        assertEquals(0L, item.getDurationMs());
        assertEquals("A", item.getSecondTitle().toString());
    }

    // /trending sends its live streams with liveNow false; only the zero length and zero views
    // give them away (seen on the TV 2026-11-01 as "0 vistas - hace 0 segundos").
    @Test
    public void aTrendingLiveStreamWithoutTheLiveFlagIsStillLive() throws Exception {
        String json = "[{\"title\":\"L\",\"videoId\":\"live2\",\"lengthSeconds\":0,\"liveNow\":false,\"isUpcoming\":false,"
                + "\"author\":\"A\",\"viewCount\":0,\"publishedText\":\"hace 0 segundos\"}]";

        MediaItem item = InvidiousFeed.parseVideos(json, ES).get(0);

        assertTrue(item.isLive());
        assertEquals("A", item.getSecondTitle().toString());
    }

    @Test
    public void aNewUploadWithNoViewsYetIsNotLive() throws Exception {
        String json = "[{\"title\":\"N\",\"videoId\":\"new1\",\"lengthSeconds\":90,\"liveNow\":false,"
                + "\"author\":\"A\",\"viewCount\":0,\"publishedText\":\"hace 1 minuto\"}]";

        MediaItem item = InvidiousFeed.parseVideos(json, ES).get(0);

        assertFalse(item.isLive());
        assertEquals("A \u2022 0 vistas \u2022 hace 1 minuto", item.getSecondTitle().toString());
    }

    @Test
    public void anUpcomingPremiereIsNotLive() throws Exception {
        String json = "[{\"title\":\"P\",\"videoId\":\"up1\",\"lengthSeconds\":0,\"liveNow\":false,\"isUpcoming\":true,"
                + "\"author\":\"A\",\"viewCount\":0}]";

        assertFalse(InvidiousFeed.parseVideos(json, ES).get(0).isLive());
    }

    @Test
    public void viewsAreACompactCountInTheAppsOwnWords() throws Exception {
        String json = "[{\"title\":\"T\",\"videoId\":\"v\",\"author\":\"A\",\"viewCount\":1500}]";

        assertEquals("A \u2022 1.5 k vistas", InvidiousFeed.parseVideos(json, ES).get(0).getSecondTitle().toString());
        assertEquals("999 vistas", InvidiousFeed.views(999, ES));
        assertEquals("4 M de vistas", InvidiousFeed.views(4018404, ES));
        assertEquals("120 k vistas", InvidiousFeed.views(120_400, ES));
        assertEquals("1.5 mil M de vistas", InvidiousFeed.views(1_500_000_000L, ES));
        assertEquals("120K views", InvidiousFeed.views(120_400, EN));
        assertEquals("1.2M views", InvidiousFeed.views(1_200_000, EN));
    }

    @Test
    public void dropsEntriesWithoutIdOrTitleAndRepeats() throws Exception {
        String json = list(video("a", "One"), "{\"title\":\"no id\"}", "{\"videoId\":\"x\"}", video("a", "One again"),
                video("b", "Two"));

        List<MediaItem> items = InvidiousFeed.parseVideos(json, EN);

        assertEquals(2, items.size());
        assertEquals("a", items.get(0).getVideoId());
        assertEquals("b", items.get(1).getVideoId());
    }

    @Test
    public void theThreeRowsAreFetchedFromTheRightRoutes() {
        assertEquals(Arrays.asList(
                INSTANCE + "/api/v1/popular?hl=es",
                INSTANCE + "/api/v1/trending?region=AR&hl=es",
                INSTANCE + "/api/v1/trending?type=gaming&region=AR&hl=es"),
                InvidiousFeed.urls(INSTANCE, "AR", "es"));
    }

    @Test
    public void regionIsTheDeviceCountryOrUruguay() {
        assertEquals("AR", InvidiousFeed.region("ar"));
        assertEquals("UY", InvidiousFeed.region(""));
        assertEquals("UY", InvidiousFeed.region(null));
        assertEquals("UY", InvidiousFeed.region("419"));
    }

    private static class FakeFetcher implements InvidiousFeed.Fetcher {
        final Map<String, String> bodies = new HashMap<>();
        final List<String> asked = new ArrayList<>();

        @Override
        public String get(String url) throws IOException {
            asked.add(url);
            String body = bodies.get(url);
            if (body == null) {
                throw new IOException("HTTP 500");
            }
            return body;
        }
    }

    @Test
    public void loadBuildsTheRowsInOrderWithTheirTitles() {
        FakeFetcher fetcher = new FakeFetcher();
        List<String> urls = InvidiousFeed.urls(INSTANCE, "UY", "es");
        fetcher.bodies.put(urls.get(0), list(video("p1", "P1"), video("p2", "P2")));
        fetcher.bodies.put(urls.get(1), list(video("t1", "T1")));
        fetcher.bodies.put(urls.get(2), list(video("g1", "G1")));

        List<MediaGroup> groups = InvidiousFeed.load(fetcher, INSTANCE, "uy", "es", "Populares", "Tendencias", "Videojuegos", ES);

        assertEquals(3, groups.size());
        assertEquals("Populares", groups.get(0).getTitle());
        assertEquals("Tendencias", groups.get(1).getTitle());
        assertEquals("Videojuegos", groups.get(2).getTitle());
        assertEquals(2, groups.get(0).getMediaItems().size());
        assertEquals("g1", groups.get(2).getMediaItems().get(0).getVideoId());
        assertEquals("Chan \u2022 4 M de vistas \u2022 8 hours ago", groups.get(2).getMediaItems().get(0).getSecondTitle().toString());
        assertNull(groups.get(0).getNextPageKey());
        assertEquals(urls, fetcher.asked);
    }

    @Test
    public void aFailingRowDoesNotHideTheOthers() {
        FakeFetcher fetcher = new FakeFetcher();
        List<String> urls = InvidiousFeed.urls(INSTANCE, "UY", "en");
        fetcher.bodies.put(urls.get(0), list(video("p1", "P1")));
        fetcher.bodies.put(urls.get(1), "not json");
        fetcher.bodies.put(urls.get(2), "[]");

        List<MediaGroup> groups = InvidiousFeed.load(fetcher, INSTANCE, "UY", "en", "P", "T", "G", EN);

        assertEquals(1, groups.size());
        assertEquals("P", groups.get(0).getTitle());
    }

    @Test
    public void whenEverythingFailsThereAreNoRows() {
        assertTrue(InvidiousFeed.load(new FakeFetcher(), INSTANCE, "UY", "en", "P", "T", "G", EN).isEmpty());
    }

    // leanview: with no Invidious instance (no server connected, or it reports none) the feed is off:
    // no rows and, above all, no request to anywhere.
    @Test
    public void withNoInstanceThereAreNoRowsAndNoRequests() {
        FakeFetcher fetcher = new FakeFetcher();

        assertTrue(InvidiousFeed.load(fetcher, null, "UY", "en", "P", "T", "G", EN).isEmpty());
        assertTrue(InvidiousFeed.load(fetcher, "  ", "UY", "en", "P", "T", "G", EN).isEmpty());
        assertTrue(fetcher.asked.isEmpty());
    }
}
