package com.liskovsoft.smartyoutubetv2.common.leanview;

/**
 * leanview: settings of the "Up next" end screen (what happens when a video ends and another would
 * start). Immutable. Kept per profile (PlayerData) as one text slot of ';' separated fields:
 * only append new fields at the END, and a field that is missing, garbled or not one of the
 * allowed choices simply takes its default, so older saved data never breaks.
 * No Android code.
 */
public final class EndScreenSettings {
    /** Where the next video may come from. */
    public enum NextSource {
        /** The playlist or the queue first, a recommendation when there is no list. */
        PLAYLIST_THEN_RELATED(0),
        /** Only a list advances by itself; after a recommendation the end screen waits for a choice. */
        PLAYLIST_ONLY(1);

        public final int code;

        NextSource(int code) {
            this.code = code;
        }

        public static NextSource fromCode(int code, NextSource fallback) {
            for (NextSource value : values()) {
                if (value.code == code) {
                    return value;
                }
            }

            return fallback;
        }
    }

    /** The button that has the focus when the end screen opens. */
    public enum DefaultFocus {
        PLAY_NOW(0), CANCEL(1);

        public final int code;

        DefaultFocus(int code) {
            this.code = code;
        }

        public static DefaultFocus fromCode(int code, DefaultFocus fallback) {
            for (DefaultFocus value : values()) {
                if (value.code == code) {
                    return value;
                }
            }

            return fallback;
        }
    }

    /** Seconds value of "never start by itself, wait for a choice". */
    public static final int NEVER_AUTO = -1;
    /** Hours value of "never ask if the viewer is still there". */
    public static final int STILL_NEVER = 0;

    public static final int[] RELATED_OPTIONS = {NEVER_AUTO, 0, 5, 8, 10, 15, 20};
    public static final int[] PLAYLIST_OPTIONS = {0, 3, 5, 8, 10, 15};
    public static final int[] STILL_WATCHING_OPTIONS = {1, 2, 3, 4, STILL_NEVER};

    private static final int DEFAULT_RELATED_SEC = 8;
    private static final int DEFAULT_PLAYLIST_SEC = 3;
    private static final int DEFAULT_STILL_HOURS = 3;
    private static final String DELIMITER = ";";

    public final boolean enabled;
    public final int relatedCountdownSec;
    public final int playlistCountdownSec;
    public final NextSource nextSource;
    public final boolean skipForCast;
    public final DefaultFocus defaultFocus;
    public final int stillWatchingHours;

    private EndScreenSettings(boolean enabled, int relatedCountdownSec, int playlistCountdownSec, NextSource nextSource,
                              boolean skipForCast, DefaultFocus defaultFocus, int stillWatchingHours) {
        this.enabled = enabled;
        this.relatedCountdownSec = relatedCountdownSec;
        this.playlistCountdownSec = playlistCountdownSec;
        this.nextSource = nextSource;
        this.skipForCast = skipForCast;
        this.defaultFocus = defaultFocus;
        this.stillWatchingHours = stillWatchingHours;
    }

    public static EndScreenSettings defaults() {
        return new EndScreenSettings(true, DEFAULT_RELATED_SEC, DEFAULT_PLAYLIST_SEC, NextSource.PLAYLIST_THEN_RELATED,
                true, DefaultFocus.PLAY_NOW, DEFAULT_STILL_HOURS);
    }

    public EndScreenSettings withEnabled(boolean value) {
        return new EndScreenSettings(value, relatedCountdownSec, playlistCountdownSec, nextSource, skipForCast, defaultFocus, stillWatchingHours);
    }

    public EndScreenSettings withRelatedCountdownSec(int value) {
        return new EndScreenSettings(enabled, choice(RELATED_OPTIONS, value, DEFAULT_RELATED_SEC), playlistCountdownSec, nextSource, skipForCast, defaultFocus, stillWatchingHours);
    }

    public EndScreenSettings withPlaylistCountdownSec(int value) {
        return new EndScreenSettings(enabled, relatedCountdownSec, choice(PLAYLIST_OPTIONS, value, DEFAULT_PLAYLIST_SEC), nextSource, skipForCast, defaultFocus, stillWatchingHours);
    }

    public EndScreenSettings withNextSource(NextSource value) {
        return new EndScreenSettings(enabled, relatedCountdownSec, playlistCountdownSec, value != null ? value : NextSource.PLAYLIST_THEN_RELATED, skipForCast, defaultFocus, stillWatchingHours);
    }

    public EndScreenSettings withSkipForCast(boolean value) {
        return new EndScreenSettings(enabled, relatedCountdownSec, playlistCountdownSec, nextSource, value, defaultFocus, stillWatchingHours);
    }

    public EndScreenSettings withDefaultFocus(DefaultFocus value) {
        return new EndScreenSettings(enabled, relatedCountdownSec, playlistCountdownSec, nextSource, skipForCast, value != null ? value : DefaultFocus.PLAY_NOW, stillWatchingHours);
    }

    public EndScreenSettings withStillWatchingHours(int value) {
        return new EndScreenSettings(enabled, relatedCountdownSec, playlistCountdownSec, nextSource, skipForCast, defaultFocus, choice(STILL_WATCHING_OPTIONS, value, DEFAULT_STILL_HOURS));
    }

    public String serialize() {
        return (enabled ? "1" : "0") + DELIMITER + relatedCountdownSec + DELIMITER + playlistCountdownSec + DELIMITER
                + nextSource.code + DELIMITER + (skipForCast ? "1" : "0") + DELIMITER + defaultFocus.code + DELIMITER + stillWatchingHours;
    }

    /** Never fails: null, empty, short or garbled data gives the defaults field by field. */
    public static EndScreenSettings parse(String data) {
        EndScreenSettings d = defaults();

        if (data == null || data.isEmpty()) {
            return d;
        }

        String[] f = data.split(DELIMITER, -1);

        return new EndScreenSettings(
                parseFlag(f, 0, d.enabled),
                choice(RELATED_OPTIONS, parseInt(f, 1, d.relatedCountdownSec), d.relatedCountdownSec),
                choice(PLAYLIST_OPTIONS, parseInt(f, 2, d.playlistCountdownSec), d.playlistCountdownSec),
                NextSource.fromCode(parseInt(f, 3, d.nextSource.code), d.nextSource),
                parseFlag(f, 4, d.skipForCast),
                DefaultFocus.fromCode(parseInt(f, 5, d.defaultFocus.code), d.defaultFocus),
                choice(STILL_WATCHING_OPTIONS, parseInt(f, 6, d.stillWatchingHours), d.stillWatchingHours));
    }

    private static int choice(int[] options, int value, int fallback) {
        for (int option : options) {
            if (option == value) {
                return value;
            }
        }

        return fallback;
    }

    private static int parseInt(String[] fields, int index, int fallback) {
        if (index >= fields.length) {
            return fallback;
        }

        try {
            return Integer.parseInt(fields[index].trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static boolean parseFlag(String[] fields, int index, boolean fallback) {
        if (index >= fields.length) {
            return fallback;
        }

        String value = fields[index].trim();

        if ("1".equals(value)) {
            return true;
        } else if ("0".equals(value)) {
            return false;
        }

        return fallback;
    }
}
