package com.liskovsoft.smartyoutubetv2.common.leanview;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

// leanview: the end screen must never offer (or count down to) a premiere that has not started.
public class NextPlayableTest {
    private static boolean playable(boolean upcoming, boolean unplayable, boolean live, long durationMs, String second, String badge) {
        return NextPlayable.isPlayable(true, upcoming, unplayable, live, durationMs, second, badge);
    }

    @Test
    public void anOrdinaryVideoIsPlayable() {
        assertTrue(playable(false, false, false, 600_000, "Canal - 1 M de vistas", "10:00"));
    }

    @Test
    public void anItemWithoutAVideoIdIsNotPlayable() {
        assertFalse(NextPlayable.isPlayable(false, false, false, false, 600_000, "x", null));
    }

    @Test
    public void anUpcomingOrUnplayableItemIsNotPlayable() {
        assertFalse(playable(true, false, false, 0, null, null));
        assertFalse(playable(false, true, false, 0, null, null));
    }

    @Test
    public void thePremiereSeenOnTheTvIsNotPlayable() {
        // LIVE badge, no length, "Se estrena el 2 nov 2026": upcoming flag was false
        assertFalse(playable(false, false, true, 0, "Canal - Se estrena el 2 nov 2026", "EN VIVO"));
        assertFalse(playable(false, false, true, -1, "Canal - Se estrena el 2 nov 2026", "EN VIVO"));
    }

    @Test
    public void otherLanguagesOfAScheduledLiveAreNotPlayable() {
        assertFalse(playable(false, false, true, 0, "Channel - Premieres Nov 2, 2026", "LIVE"));
        assertFalse(playable(false, false, true, 0, "Channel - Scheduled for 11/2/26", "LIVE"));
        assertFalse(playable(false, false, true, 0, "Canal - Estreia em 2 de nov.", "AO VIVO"));
        assertFalse(playable(false, false, true, 0, "Kanal - Premiere am 2. Nov.", "LIVE"));
    }

    // 2026-11-02 on the TV (release 30): the Hak5 premiere in the Popular row had the LIVE badge, no length
    // and only "Hak5" as its second line ("Se estrena..." appears once the video is opened), so it was still
    // chosen and the player stayed black. From a row a premiere and a stream on air look the same, so
    // nothing without a length is offered as next.
    @Test
    public void aLiveTileWithNoLengthIsNeverOfferedAsNext() {
        assertFalse(playable(false, false, true, 0, "Hak5", "EN VIVO"));
        assertFalse(playable(false, false, false, 0, "Hak5", "EN VIVO"));
        assertFalse(playable(false, false, true, 0, "Canal - 12 mil espectadores", "EN VIVO"));
        assertFalse(playable(false, false, true, 0, null, null));
    }

    @Test
    public void aTileWhoseLengthIsJustNotKnownIsStillPlayable() {
        // Mix and suggestion items sometimes come without a length; only a live mark rules them out
        assertTrue(playable(false, false, false, 0, "Canal", null));
        assertTrue(playable(false, false, false, 0, "Canal", "10:00"));
        assertTrue(playable(false, false, false, 0, "Canal", "1:02:03"));
    }

    @Test
    public void aFinishedPremiereWithALengthIsPlayable() {
        assertTrue(playable(false, false, false, 600_000, "Canal - Estreno hace 2 dias", null));
        assertTrue(playable(false, false, true, 600_000, "Premiered 2 days ago", null));
    }
}
