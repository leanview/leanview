package com.liskovsoft.smartyoutubetv2.common.leanview;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

// Alice, 2026-11-09: back to a profile within an hour, its unfinished video opens paused where it
// was; up to a day it is the first card of Home; finished or older, only the history has it.
public class ResumeOfferTest {
    private static final long MIN = 60_000;
    private static final long LEFT = 1_000_000_000L;

    @Test
    public void aShortPauseOpensItPaused() {
        assertEquals(ResumeOffer.Action.OPEN_PAUSED, ResumeOffer.onArrival(LEFT, LEFT + 59 * MIN, false));
        assertEquals(ResumeOffer.Action.OPEN_PAUSED, ResumeOffer.onArrival(LEFT, LEFT, false));
    }

    @Test
    public void fromAnHourItIsTheHomeCard() {
        assertEquals(ResumeOffer.Action.HOME_CARD, ResumeOffer.onArrival(LEFT, LEFT + 60 * MIN, false));
        assertEquals(ResumeOffer.Action.HOME_CARD, ResumeOffer.onArrival(LEFT, LEFT + 24 * 60 * MIN - 1, false));
    }

    @Test
    public void aDayLaterItIsNotOffered() {
        assertEquals(ResumeOffer.Action.NONE, ResumeOffer.onArrival(LEFT, LEFT + 24 * 60 * MIN, false));
        assertFalse(ResumeOffer.showsOnHome(LEFT, LEFT + 24 * 60 * MIN));
    }

    @Test
    public void opensByItselfOnlyOnce() {
        // Opened by itself and closed again without watching: Home keeps it, it does not open again
        assertEquals(ResumeOffer.Action.HOME_CARD, ResumeOffer.onArrival(LEFT, LEFT + 5 * MIN, true));
    }

    @Test
    public void aClockSetBackOffersNothing() {
        assertEquals(ResumeOffer.Action.NONE, ResumeOffer.onArrival(LEFT, LEFT - 1, false));
        assertFalse(ResumeOffer.showsOnHome(LEFT, LEFT - 1));
    }

    @Test
    public void homeShowsItTheWholeDay() {
        assertTrue(ResumeOffer.showsOnHome(LEFT, LEFT + MIN));
        assertTrue(ResumeOffer.showsOnHome(LEFT, LEFT + 23 * 60 * MIN));
    }

    @Test
    public void unfinishedMeansStartedAndBeforeTheEnd() {
        long tenMinutes = 10 * MIN;
        assertFalse("barely started", ResumeOffer.isUnfinished(9_999, tenMinutes, false));
        assertTrue(ResumeOffer.isUnfinished(10_000, tenMinutes, false));
        assertTrue(ResumeOffer.isUnfinished(569_000, tenMinutes, false)); // 94.8 %
        assertFalse("credits", ResumeOffer.isUnfinished(570_000, tenMinutes, false)); // 95 %
        assertFalse("live", ResumeOffer.isUnfinished(5 * MIN, tenMinutes, true));
        assertFalse("a short", ResumeOffer.isUnfinished(30_000, 59_000, false));
        assertFalse("no length", ResumeOffer.isUnfinished(30_000, 0, false));
    }

    @Test
    public void finishedFromNinetyFivePercent() {
        assertTrue(ResumeOffer.isFinished(570_000, 600_000));
        assertFalse(ResumeOffer.isFinished(569_000, 600_000));
        assertFalse(ResumeOffer.isFinished(0, 0));
    }

    @Test
    public void barelyMovingIsTheSameStop() {
        assertTrue(ResumeOffer.isSameStop("v1", 100_000, "v1", 104_999));
        assertTrue(ResumeOffer.isSameStop("v1", 100_000, "v1", 95_001));
        assertFalse(ResumeOffer.isSameStop("v1", 100_000, "v1", 105_000));
        assertFalse(ResumeOffer.isSameStop("v1", 100_000, "v2", 100_000));
        assertFalse(ResumeOffer.isSameStop(null, 100_000, "v1", 100_000));
    }
}
