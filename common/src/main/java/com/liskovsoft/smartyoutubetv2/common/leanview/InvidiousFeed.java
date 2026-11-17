package com.liskovsoft.smartyoutubetv2.common.leanview;

import com.liskovsoft.mediaserviceinterfaces.data.FeedbackEndpoint;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * leanview: Home rows for when no account is selected ("Ninguna" in the picker), from the connected
 * server's Invidious (public routes, no auth; none connected, no rows): popular, trending for the device country and gaming trending.
 * Only these three work: type=music and type=movies answer the default list (measured 2026-10-31).
 * The items are plain MediaItem/MediaGroup, so a click plays through the normal path (the player
 * resolves the streams by videoId). Blocking: call off the main thread.
 */
public final class InvidiousFeed {
    public static final String DEFAULT_REGION = "UY";

    /** Gets a URL and returns the body of a successful answer; throws on any other. */
    public interface Fetcher {
        String get(String url) throws IOException;
    }

    private InvidiousFeed() {
    }

    /** The real fetcher, through the app's ServerHttp. */
    public static Fetcher http() {
        return url -> {
            ServerHttp.Response response = ServerHttp.request("GET", url, null, null);
            if (!response.ok()) {
                throw new IOException("Invidious " + url + ": HTTP " + response.status);
            }
            return response.body;
        };
    }

    /** The device country as an Invidious region (two letters), or UY when it has none. */
    public static String region(String country) {
        if (country != null && country.length() == 2 && Character.isLetter(country.charAt(0))
                && Character.isLetter(country.charAt(1))) {
            return country.toUpperCase(Locale.ROOT);
        }
        return DEFAULT_REGION;
    }

    /** The URLs of the three rows, in order: popular, trending, gaming. */
    public static List<String> urls(String instance, String region, String language) {
        String hl = language != null && language.matches("[A-Za-z]{2,3}") ? language.toLowerCase(Locale.ROOT) : "en";
        List<String> urls = new ArrayList<>();
        urls.add(instance + "/api/v1/popular?hl=" + hl);
        urls.add(instance + "/api/v1/trending?region=" + region + "&hl=" + hl);
        urls.add(instance + "/api/v1/trending?type=gaming&region=" + region + "&hl=" + hl);
        return urls;
    }

    /**
     * The non-empty rows. A row that fails is skipped; when all fail the list is empty (the caller
     * then falls back to the sign-in prompt, and retries later).
     */
    public static List<MediaGroup> load(Fetcher fetcher, String instance, String country, String language,
                                        String popularTitle, String trendingTitle, String gamingTitle, String[] viewsFormats) {
        if (instance == null || instance.trim().isEmpty()) {
            return new ArrayList<>(); // no Invidious known: nothing is requested
        }
        List<String> urls = urls(instance, region(country), language);
        String[] titles = {popularTitle, trendingTitle, gamingTitle};
        List<MediaGroup> groups = new ArrayList<>();
        for (int i = 0; i < urls.size(); i++) {
            try {
                List<MediaItem> items = parseVideos(fetcher.get(urls.get(i)), viewsFormats);
                if (!items.isEmpty()) {
                    groups.add(new Group(titles[i], items));
                }
            } catch (Exception e) {
                // a failing row must not take the others down
            }
        }
        return groups;
    }

    /** The videos of an Invidious list; entries without id or title, and repeats, are dropped. */
    public static List<MediaItem> parseVideos(String json, String[] viewsFormats) throws JSONException {
        JSONArray array = new JSONArray(json);
        List<MediaItem> items = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject o = array.optJSONObject(i);
            if (o == null) {
                continue;
            }
            String id = o.optString("videoId", "");
            String title = o.optString("title", "");
            if (id.isEmpty() || title.isEmpty() || !seen.add(id)) {
                continue;
            }
            String author = o.optString("author", "");
            long seconds = Math.max(0, o.optLong("lengthSeconds", 0));
            items.add(new Item(id, title, author, o.optString("authorId", ""),
                    secondTitle(author, o, viewsFormats), thumbnail(o, id), seconds * 1000,
                    seconds > 0 ? clock(seconds) : null,
                    isLive(o), o.optBoolean("isUpcoming", false)));
        }
        return items;
    }

    /**
     * "Author . 4 M de vistas . 8 hours ago", as the app's YouTube rows read. The views use
     * viewsFormats (the app's own words per magnitude, "%s vistas" ... "%s M de vistas"):
     * /popular sends no viewCountText at all and /trending sends "visualizaciones", so the rows
     * would read differently. A live stream gets neither, Invidious says "0 views - 0 seconds ago".
     */
    static String secondTitle(String author, JSONObject o, String[] viewsFormats) {
        StringBuilder text = new StringBuilder(author);
        if (!isLive(o)) {
            if (o.has("viewCount")) {
                append(text, views(o.optLong("viewCount", 0), viewsFormats));
            }
            append(text, o.optString("publishedText", ""));
        }
        return text.toString();
    }

    /**
     * /trending sends its live streams with liveNow false, a zero length, zero views and "0 seconds
     * ago"; a video always has a length and an upcoming premiere says so.
     */
    static boolean isLive(JSONObject o) {
        if (o.optBoolean("liveNow", false)) {
            return true;
        }
        return !o.optBoolean("isUpcoming", false)
                && o.optLong("lengthSeconds", 0) <= 0 && o.optLong("viewCount", 0) <= 0;
    }

    private static void append(StringBuilder text, String part) {
        if (part.isEmpty()) {
            return;
        }
        if (text.length() > 0) {
            text.append(" \u2022 ");
        }
        text.append(part);
    }

    /**
     * 999 -> "999 vistas", 1500 -> "1.5 k vistas", 4018404 -> "4 M de vistas": formats holds the words
     * for units, thousands, millions and billions, since languages word them differently.
     */
    static String views(long n, String[] formats) {
        double value = Math.max(0, n);
        int unit = 0;
        while (value >= 1000 && unit < formats.length - 1) {
            value /= 1000;
            unit++;
        }
        String number = unit == 0 || value >= 100 ? String.valueOf(Math.round(value)) : String.format(Locale.ROOT, "%.1f", value);
        if (number.endsWith(".0")) {
            number = number.substring(0, number.length() - 2);
        }
        return String.format(formats[unit], number);
    }

    /** 65 -> "1:05", 3725 -> "1:02:05". */
    static String clock(long seconds) {
        long h = seconds / 3600;
        long m = seconds % 3600 / 60;
        long s = seconds % 60;
        return h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) : String.format(Locale.ROOT, "%d:%02d", m, s);
    }

    /** The 320x180 "medium" thumbnail, else the first one with a URL, else YouTube's own. */
    static String thumbnail(JSONObject o, String videoId) {
        JSONArray thumbs = o.optJSONArray("videoThumbnails");
        String first = null;
        if (thumbs != null) {
            for (int i = 0; i < thumbs.length(); i++) {
                JSONObject t = thumbs.optJSONObject(i);
                String url = t == null ? "" : t.optString("url", "");
                if (url.isEmpty()) {
                    continue;
                }
                if ("medium".equals(t.optString("quality"))) {
                    return url;
                }
                if (first == null) {
                    first = url;
                }
            }
        }
        return first != null ? first : "https://i.ytimg.com/vi/" + videoId + "/mqdefault.jpg";
    }

    static final class Group implements MediaGroup {
        private final String mTitle;
        private final List<MediaItem> mItems;

        Group(String title, List<MediaItem> items) {
            mTitle = title;
            mItems = items;
        }

        @Override public int getType() { return TYPE_HOME; }
        @Override public List<MediaItem> getMediaItems() { return mItems; }
        @Override public String getTitle() { return mTitle; }
        @Override public String getChannelId() { return null; }
        @Override public String getParams() { return null; }
        @Override public String getReloadPageKey() { return null; }
        @Override public String getNextPageKey() { return null; }
        @Override public String getChannelUrl() { return null; }
        @Override public boolean isEmpty() { return mItems.isEmpty(); }
    }

    static final class Item implements MediaItem {
        private final String mVideoId;
        private final String mTitle;
        private final String mAuthor;
        private final String mChannelId;
        private final String mSecondTitle;
        private final String mCardImageUrl;
        private final long mDurationMs;
        private final String mBadge;
        private final boolean mLive;
        private final boolean mUpcoming;

        Item(String videoId, String title, String author, String channelId, String secondTitle,
             String cardImageUrl, long durationMs, String badge, boolean live, boolean upcoming) {
            mVideoId = videoId;
            mTitle = title;
            mAuthor = author;
            mChannelId = channelId.isEmpty() ? null : channelId;
            mSecondTitle = secondTitle;
            mCardImageUrl = cardImageUrl;
            mDurationMs = durationMs;
            mBadge = badge;
            mLive = live;
            mUpcoming = upcoming;
        }

        @Override public int getType() { return TYPE_VIDEO; }
        @Override public boolean isLive() { return mLive; }
        @Override public boolean isUpcoming() { return mUpcoming; }
        @Override public boolean isShorts() { return false; }
        @Override public int getPercentWatched() { return 0; }
        @Override public int getStartTimeSeconds() { return 0; }
        @Override public String getAuthor() { return mAuthor; }
        @Override public String getFeedbackToken() { return null; }
        @Override public String getFeedbackToken2() { return null; }
        @Override public FeedbackEndpoint getFeedbackEndpoint() { return null; }
        @Override public String getPlaylistId() { return null; }
        @Override public int getPlaylistIndex() { return 0; }
        @Override public String getParams() { return null; }
        @Override public String getReloadPageKey() { return null; }
        @Override public boolean hasNewContent() { return false; }
        @Override public int getId() { return mVideoId.hashCode(); }
        @Override public String getTitle() { return mTitle; }
        @Override public CharSequence getSecondTitle() { return mSecondTitle; }
        @Override public String getVideoId() { return mVideoId; }
        @Override public String getContentType() { return null; }
        @Override public long getDurationMs() { return mDurationMs; }
        @Override public String getBadgeText() { return mBadge; }
        @Override public String getProductionDate() { return null; }
        @Override public long getPublishedDate() { return 0; }
        @Override public String getCardImageUrl() { return mCardImageUrl; }
        @Override public String getBackgroundImageUrl() { return null; }
        @Override public int getWidth() { return 0; }
        @Override public int getHeight() { return 0; }
        @Override public String getChannelId() { return mChannelId; }
        @Override public String getVideoPreviewUrl() { return null; }
        @Override public String getAudioChannelConfig() { return null; }
        @Override public String getPurchasePrice() { return null; }
        @Override public String getRentalPrice() { return null; }
        @Override public int getRatingStyle() { return 0; }
        @Override public double getRatingScore() { return 0; }
        @Override public boolean isMovie() { return false; }
        @Override public boolean hasUploads() { return false; }
        @Override public String getClickTrackingParams() { return null; }
        @Override public String getSearchQuery() { return null; }
    }
}
