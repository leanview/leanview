package com.liskovsoft.smartyoutubetv2.common.leanview;

/**
 * leanview: can this video be offered as "next"? An upcoming premiere or scheduled live cannot play yet,
 * so the end screen must never count down to it. YouTube often marks such a tile with the LIVE badge
 * instead of the UPCOMING one, with no length and often no schedule text either (the Hak5 premiere on
 * 2026-11-02 only said "Hak5"), so from a row it looks exactly like a stream on air. Hence nothing
 * marked live without a length is offered as next; an item whose length is simply unknown (no live
 * mark) still is. No Android code.
 */
public final class NextPlayable {
    private static final String[] SCHEDULE_WORDS = {
            "premiere", "estrena", "estreia", "scheduled", "programad", "programmat",
            "planifi", "geplant", "uraufgef", "upcoming", "proximamente", "prochainement"
    };

    private NextPlayable() {
    }

    public static boolean isPlayable(boolean hasVideo, boolean upcoming, boolean unplayable, boolean live,
                                     long durationMs, CharSequence secondTitle, CharSequence badge) {
        if (!hasVideo || upcoming || unplayable) {
            return false;
        }

        if (durationMs > 0) {
            return true;
        }

        return !(live || isLiveBadge(badge) || mentionsSchedule(secondTitle) || mentionsSchedule(badge));
    }

    /** A badge that is not a length ("10:00", "1:02:03") on an item with no length: LIVE, EN VIVO... */
    static boolean isLiveBadge(CharSequence badge) {
        if (badge == null) {
            return false;
        }

        String text = badge.toString().trim();

        return !text.isEmpty() && !text.matches("\\d{1,2}(:\\d{2}){1,2}");
    }

    static boolean mentionsSchedule(CharSequence text) {
        if (text == null) {
            return false;
        }

        String lower = text.toString().toLowerCase(java.util.Locale.ROOT);

        for (String word : SCHEDULE_WORDS) {
            if (lower.contains(word)) {
                return true;
            }
        }

        return false;
    }
}
