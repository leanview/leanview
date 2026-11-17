package com.liskovsoft.smartyoutubetv2.common.leanview;

import android.content.Context;
import android.content.SharedPreferences;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;

import org.json.JSONObject;

/**
 * leanview: each profile's video left unfinished (2026-11-09: switching profiles or leaving
 * the app lost it, and he had to look for it in the history). What is offered and when:
 * ResumeOffer. Written at once (commit) on every save of the player, since nothing is told before
 * Android kills the app.
 */
public final class ContinueWatching {
    private static final String PREFS = "leanview_continue_watching";
    private static final String NO_ACCOUNT = "none";

    private ContinueWatching() {
    }

    static final class Entry {
        final String video;
        final String videoId;
        final long positionMs;
        final long durationMs;
        final long leftAtMs;
        final boolean autoOpened;

        Entry(String video, String videoId, long positionMs, long durationMs, long leftAtMs, boolean autoOpened) {
            this.video = video;
            this.videoId = videoId;
            this.positionMs = positionMs;
            this.durationMs = durationMs;
            this.leftAtMs = leftAtMs;
            this.autoOpened = autoOpened;
        }
    }

    /** The selected profile, as kept here (ServerSync.profileKey: a channel by its page id). */
    public static String currentOwner() {
        String key = ServerSync.selectedGoogleAccount();
        return key != null ? key : NO_ACCOUNT;
    }

    /** The player saved where owner is in video. */
    public static void record(Context context, String owner, Video video, long positionMs, long durationMs, long nowMs) {
        if (context == null || owner == null || video == null || video.videoId == null) {
            return;
        }
        SharedPreferences prefs = prefs(context);
        Entry old = read(prefs, owner);
        if (ResumeOffer.isUnfinished(positionMs, durationMs, video.isLive)) {
            boolean sameStop = old != null && ResumeOffer.isSameStop(old.videoId, old.positionMs, video.videoId, positionMs);
            write(prefs, owner, new Entry(cardOf(video), video.videoId, positionMs, durationMs,
                    sameStop ? old.leftAtMs : nowMs, sameStop && old.autoOpened));
        } else if (old != null && video.videoId.equals(old.videoId) && ResumeOffer.isFinished(positionMs, durationMs)) {
            prefs.edit().remove(owner).commit();
        }
    }

    /** Arrived at the selected profile: opens its video paused when it was left a moment ago. */
    public static boolean openIfJustLeft(Context context, long nowMs) {
        Video video = takeJustLeft(context, currentOwner(), nowMs);
        if (video == null) {
            return false;
        }
        PlaybackPresenter.instance(context).openVideo(video);
        return true;
    }

    /** owner's video to open paused now, or null; once taken it does not open by itself again. */
    static Video takeJustLeft(Context context, String owner, long nowMs) {
        SharedPreferences prefs = prefs(context);
        Entry entry = read(prefs, owner);
        if (entry == null || ResumeOffer.onArrival(entry.leftAtMs, nowMs, entry.autoOpened) != ResumeOffer.Action.OPEN_PAUSED) {
            return null;
        }
        Video video = Video.fromString(entry.video);
        if (video == null || video.videoId == null) {
            return null;
        }
        write(prefs, owner, new Entry(entry.video, entry.videoId, entry.positionMs, entry.durationMs, entry.leftAtMs, true));
        video.pendingPosMs = entry.positionMs;
        video.fromCrashRestorer = true; // a video under 6 minutes keeps its position too
        video.leanviewOpenPaused = true;
        return video;
    }

    /** The first card of Home for the selected profile, or null. */
    public static Video homeCard(Context context, long nowMs) {
        return homeCard(context, currentOwner(), nowMs);
    }

    static Video homeCard(Context context, String owner, long nowMs) {
        Entry entry = read(prefs(context), owner);
        if (entry == null || !ResumeOffer.showsOnHome(entry.leftAtMs, nowMs)) {
            return null;
        }
        Video video = Video.fromString(entry.video);
        if (video == null || video.videoId == null) {
            return null;
        }
        float percent = ResumeOffer.percent(entry.positionMs, entry.durationMs);
        // Exactly the "closed at once" mark is hidden from Home's first row (VideoGroup)
        video.percentWatched = percent == Video.MIN_WATCHED_PERCENT ? percent + 0.5f : percent;
        // The position comes from the player's own state; this only keeps it for a video under 6 minutes
        video.fromCrashRestorer = true;
        return video;
    }

    /**
     * The video as a Home card: opened from a link it only has the title read from its page and no
     * thumbnail, which made a blank card (emulator, 2026-11-09). The player's own video is not changed.
     */
    private static String cardOf(Video video) {
        Video card = Video.fromString(video.toString());
        if (card == null) {
            return video.toString();
        }
        if (card.title == null) {
            card.title = video.getTitleFull();
        }
        if (card.secondTitle == null) {
            card.secondTitle = video.getSecondTitleFull();
        }
        if (card.cardImageUrl == null) {
            card.cardImageUrl = "https://i.ytimg.com/vi/" + video.videoId + "/hqdefault.jpg";
        }
        return card.toString();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static Entry read(SharedPreferences prefs, String owner) {
        String text = prefs.getString(owner, null);
        if (text == null) {
            return null;
        }
        try {
            JSONObject json = new JSONObject(text);
            return new Entry(json.getString("video"), json.getString("videoId"), json.getLong("positionMs"),
                    json.getLong("durationMs"), json.getLong("leftAtMs"), json.optBoolean("autoOpened"));
        } catch (Exception e) {
            return null;
        }
    }

    private static void write(SharedPreferences prefs, String owner, Entry entry) {
        try {
            JSONObject json = new JSONObject()
                    .put("video", entry.video)
                    .put("videoId", entry.videoId)
                    .put("positionMs", entry.positionMs)
                    .put("durationMs", entry.durationMs)
                    .put("leftAtMs", entry.leftAtMs)
                    .put("autoOpened", entry.autoOpened);
            prefs.edit().putString(owner, json.toString()).commit();
        } catch (Exception e) {
            // nothing kept: Home and the history still have it
        }
    }
}
