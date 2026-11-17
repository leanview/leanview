package com.liskovsoft.smartyoutubetv2.common.leanview;

import static com.liskovsoft.smartyoutubetv2.common.leanview.SeekThumbs.Action.COMMIT_SEEK;
import static com.liskovsoft.smartyoutubetv2.common.leanview.SeekThumbs.Action.HIDE_THUMBS;
import static com.liskovsoft.smartyoutubetv2.common.leanview.SeekThumbs.Action.NONE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

// leanview: the seek preview thumbnails must not linger after the seek is done.
public class SeekThumbsTest {
    @Test
    public void keyUpThenQuietForTheDelayHidesThumbs() {
        SeekThumbs t = new SeekThumbs();
        t.onSeekKeyDown(1000);
        t.onSeekKeyUp(1100);
        assertEquals(NONE, t.due(1100));
        assertEquals(NONE, t.due(1499));
        assertEquals(HIDE_THUMBS, t.due(1500));
    }

    @Test
    public void hideIsReportedOnlyOnce() {
        SeekThumbs t = new SeekThumbs();
        t.onSeekKeyDown(0);
        t.onSeekKeyUp(10);
        assertEquals(HIDE_THUMBS, t.due(500));
        assertEquals(NONE, t.due(600));
        assertEquals(-1, t.nextDueInMs(600));
    }

    @Test
    public void keyDownWithinTheDelayCancelsTheHide() {
        SeekThumbs t = new SeekThumbs();
        t.onSeekKeyDown(0);
        t.onSeekKeyUp(100);
        t.onSeekKeyDown(300);
        assertEquals(NONE, t.due(600));
        assertEquals(NONE, t.due(900));
    }

    @Test
    public void heldRepeatsEvery50msNeverTrigger() {
        SeekThumbs t = new SeekThumbs();
        for (long now = 0; now <= 20_000; now += 50) {
            t.onSeekKeyDown(now);
            assertEquals("at " + now, NONE, t.due(now));
        }
        t.onSeekKeyUp(20_050);
        assertEquals(HIDE_THUMBS, t.due(20_450));
    }

    @Test
    public void initialRepeatGapOf500msDoesNotTriggerTheWatchdog() {
        SeekThumbs t = new SeekThumbs();
        t.onSeekKeyDown(0);
        assertEquals(NONE, t.due(500));
        assertEquals(NONE, t.due(1199));
        t.onSeekKeyDown(500);
        assertEquals(NONE, t.due(1199));
        assertEquals(NONE, t.due(1699));
    }

    @Test
    public void missingKeyUpCommitsThenHidesAfterTheDelay() {
        SeekThumbs t = new SeekThumbs();
        t.onSeekKeyDown(0);
        assertEquals(NONE, t.due(1199));
        assertEquals(COMMIT_SEEK, t.due(1200));
        assertEquals(NONE, t.due(1200));
        assertEquals(NONE, t.due(1599));
        assertEquals(HIDE_THUMBS, t.due(1600));
        assertEquals(NONE, t.due(5000));
    }

    @Test
    public void lateKeyUpAfterTheWatchdogDoesNotCommitAgain() {
        SeekThumbs t = new SeekThumbs();
        t.onSeekKeyDown(0);
        assertEquals(COMMIT_SEEK, t.due(1300));
        t.onSeekKeyUp(1350);
        assertEquals(HIDE_THUMBS, t.due(1750));
    }

    @Test
    public void confirmModeNeverCommitsNorHides() {
        SeekThumbs t = new SeekThumbs();
        t.setConfirmMode(true);
        t.onSeekKeyDown(0);
        assertEquals(NONE, t.due(5000));
        t.onSeekKeyUp(5000);
        assertEquals(NONE, t.due(9000));
        assertEquals(-1, t.nextDueInMs(9000));
    }

    @Test
    public void leavingConfirmModeAllowsHideAfterTheNextKeyUp() {
        SeekThumbs t = new SeekThumbs();
        t.setConfirmMode(true);
        t.onSeekKeyDown(0);
        t.setConfirmMode(false);
        t.onSeekKeyUp(100);
        assertEquals(HIDE_THUMBS, t.due(500));
    }

    @Test
    public void timestampsGoingBackwardsAreSafe() {
        SeekThumbs t = new SeekThumbs();
        t.onSeekKeyDown(10_000);
        assertEquals(NONE, t.due(5000));
        assertEquals(NONE, t.due(5000 + 1199));
        assertEquals(COMMIT_SEEK, t.due(5000 + 1200));

        SeekThumbs u = new SeekThumbs();
        u.onSeekKeyDown(10_000);
        u.onSeekKeyUp(10_100);
        assertEquals(NONE, u.due(2000));
        assertEquals(HIDE_THUMBS, u.due(2400));
    }

    @Test
    public void nextDueInMsTellsWhenToCheckAgain() {
        SeekThumbs t = new SeekThumbs();
        assertEquals(-1, t.nextDueInMs(0));
        t.onSeekKeyDown(100);
        assertEquals(1200, t.nextDueInMs(100));
        assertEquals(700, t.nextDueInMs(600));
        t.onSeekKeyUp(700);
        assertEquals(400, t.nextDueInMs(700));
        assertEquals(0, t.nextDueInMs(5000));
    }

    @Test
    public void customDelaysAreUsed() {
        SeekThumbs t = new SeekThumbs(100, 300);
        t.onSeekKeyDown(0);
        assertEquals(COMMIT_SEEK, t.due(300));
        assertEquals(HIDE_THUMBS, t.due(400));
    }

    @Test
    public void resetForgetsEverythingPending() {
        SeekThumbs t = new SeekThumbs();
        t.onSeekKeyDown(0);
        t.onSeekKeyUp(10);
        t.reset();
        assertEquals(NONE, t.due(10_000));
        assertEquals(-1, t.nextDueInMs(10_000));
    }

    @Test
    public void staleGenerationsAreRejected() {
        SeekThumbs t = new SeekThumbs();
        int first = t.nextGeneration();
        assertTrue(t.isCurrentGeneration(first));
        int second = t.nextGeneration();
        assertFalse(t.isCurrentGeneration(first));
        assertTrue(t.isCurrentGeneration(second));
    }
}
