package com.liskovsoft.smartyoutubetv2.common.leanview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.liskovsoft.smartyoutubetv2.common.leanview.EndScreenCountdown.State;

import org.junit.Test;

// leanview: the countdown of the end screen. It must fire exactly once, ignore the repeated
// "playback ended" signals of one ending, and stay cancelled until something resets it.
public class EndScreenCountdownTest {
    @Test
    public void startsIdle() {
        EndScreenCountdown c = new EndScreenCountdown();

        assertEquals(State.IDLE, c.state());
        assertFalse(c.tick(1_000));
    }

    @Test
    public void countsDownAndFiresExactlyOnce() {
        EndScreenCountdown c = new EndScreenCountdown();
        assertTrue(c.start(3_000));

        assertFalse(c.tick(1_000));
        assertFalse(c.tick(1_000));
        assertEquals(State.COUNTING, c.state());
        assertTrue(c.tick(1_000));
        assertEquals(State.FIRED, c.state());
        assertFalse(c.tick(1_000));
        assertFalse(c.tick(5_000));
    }

    @Test
    public void aBigTickFiresOnceToo() {
        EndScreenCountdown c = new EndScreenCountdown();
        c.start(3_000);

        assertTrue(c.tick(60_000));
        assertFalse(c.tick(60_000));
        assertEquals(0, c.remainingMs());
    }

    @Test
    public void remainingSecondsRoundUp() {
        EndScreenCountdown c = new EndScreenCountdown();
        c.start(8_000);
        assertEquals(8, c.remainingSeconds());

        c.tick(1);
        assertEquals(8, c.remainingSeconds());
        c.tick(999);
        assertEquals(7, c.remainingSeconds());
        c.tick(6_500);
        assertEquals(1, c.remainingSeconds());
    }

    @Test
    public void startWhileCountingIsANoOp() {
        EndScreenCountdown c = new EndScreenCountdown();
        c.start(8_000);
        c.tick(5_000);

        assertFalse(c.start(8_000));

        assertEquals(3_000, c.remainingMs());
        assertEquals(State.COUNTING, c.state());
    }

    @Test
    public void startAfterFiringIsANoOpUntilReset() {
        EndScreenCountdown c = new EndScreenCountdown();
        c.start(1_000);
        c.tick(1_000);

        assertFalse(c.start(1_000));
        assertEquals(State.FIRED, c.state());

        c.reset();
        assertEquals(State.IDLE, c.state());
        assertTrue(c.start(1_000));
    }

    @Test
    public void cancelIsStickyUntilReset() {
        EndScreenCountdown c = new EndScreenCountdown();
        c.start(8_000);
        c.cancel();

        assertEquals(State.CANCELLED, c.state());
        // the engine reports the ending again: must not restart
        assertFalse(c.start(8_000));
        assertFalse(c.tick(10_000));
        assertEquals(State.CANCELLED, c.state());

        c.reset();
        assertTrue(c.start(8_000));
    }

    @Test
    public void cancelWhileIdleOrFiredChangesNothing() {
        EndScreenCountdown c = new EndScreenCountdown();
        c.cancel();
        assertEquals(State.IDLE, c.state());

        c.start(1_000);
        c.tick(1_000);
        c.cancel();
        assertEquals(State.FIRED, c.state());
    }

    @Test
    public void pausedCountdownDoesNotAdvance() {
        EndScreenCountdown c = new EndScreenCountdown();
        c.start(3_000);
        c.setPaused(EndScreenCountdown.PAUSE_DIALOG, true);

        assertFalse(c.tick(10_000));
        assertEquals(3_000, c.remainingMs());
        assertTrue(c.isPaused());

        c.setPaused(EndScreenCountdown.PAUSE_DIALOG, false);
        assertFalse(c.isPaused());
        assertTrue(c.tick(3_000));
    }

    @Test
    public void everyPauseReasonMustBeLiftedToResume() {
        EndScreenCountdown c = new EndScreenCountdown();
        c.start(3_000);
        c.setPaused(EndScreenCountdown.PAUSE_DIALOG, true);
        c.setPaused(EndScreenCountdown.PAUSE_FOCUS, true);

        c.setPaused(EndScreenCountdown.PAUSE_DIALOG, false);
        assertTrue(c.isPaused());
        assertFalse(c.tick(10_000));

        c.setPaused(EndScreenCountdown.PAUSE_FOCUS, false);
        assertTrue(c.tick(3_000));
    }

    @Test
    public void playNowFiresOnceWhileCounting() {
        EndScreenCountdown c = new EndScreenCountdown();
        c.start(8_000);

        assertTrue(c.fireNow());
        assertEquals(State.FIRED, c.state());
        assertFalse(c.fireNow());
        assertFalse(c.tick(10_000));
    }

    @Test
    public void playNowWorksWhilePausedAndWhileCancelled() {
        EndScreenCountdown c = new EndScreenCountdown();
        c.start(8_000);
        c.setPaused(EndScreenCountdown.PAUSE_FOCUS, true);
        assertTrue(c.fireNow());

        // after "Cancel" the card is still there with "Play now"
        EndScreenCountdown d = new EndScreenCountdown();
        d.start(8_000);
        d.cancel();
        assertTrue(d.fireNow());
        assertEquals(State.FIRED, d.state());
    }

    @Test
    public void playNowWhileIdleDoesNothing() {
        EndScreenCountdown c = new EndScreenCountdown();

        assertFalse(c.fireNow());
        assertEquals(State.IDLE, c.state());
    }

    @Test
    public void aWaitingScreenNeverFiresByItself() {
        EndScreenCountdown c = new EndScreenCountdown();
        assertTrue(c.start(EndScreenCountdown.NO_TIMER));

        assertFalse(c.tick(24L * 3_600_000L));
        assertEquals(State.COUNTING, c.state());
        assertTrue(c.fireNow());
    }

    @Test
    public void resetClearsPausesToo() {
        EndScreenCountdown c = new EndScreenCountdown();
        c.start(3_000);
        c.setPaused(EndScreenCountdown.PAUSE_DIALOG, true);
        c.reset();

        assertFalse(c.isPaused());
        assertTrue(c.start(3_000));
        assertTrue(c.tick(3_000));
    }

    @Test
    public void negativeTicksAreIgnored() {
        EndScreenCountdown c = new EndScreenCountdown();
        c.start(3_000);

        assertFalse(c.tick(-5_000));
        assertEquals(3_000, c.remainingMs());
    }

    // Bug seen on the TV: BACK cancelled, but a lifecycle event (view destroyed, engine released,
    // activity finished) wiped the cancel, the engine reported the ending again and a brand new
    // countdown (with no card on screen) loaded the next video 15 s later.
    @Test
    public void softResetKeepsACancelUntilARealReset() {
        EndScreenCountdown c = new EndScreenCountdown();
        c.start(15_000);
        c.cancel();

        c.softReset();

        assertEquals(State.CANCELLED, c.state());
        assertFalse(c.start(15_000)); // the ending reported again must not restart it
        assertFalse(c.tick(60_000));

        c.reset();
        assertEquals(State.IDLE, c.state());
        assertTrue(c.start(15_000));
    }

    @Test
    public void softResetStillDropsACountingOrFiredCountdown() {
        EndScreenCountdown c = new EndScreenCountdown();
        c.start(15_000);
        c.softReset();
        assertEquals(State.IDLE, c.state());
        assertFalse(c.isPaused());

        c.start(1_000);
        c.tick(1_000);
        c.softReset();
        assertEquals(State.IDLE, c.state());
    }

    @Test
    public void softResetKeepsPlayNowAvailableAfterACancel() {
        EndScreenCountdown c = new EndScreenCountdown();
        c.start(15_000);
        c.cancel();
        c.softReset();

        assertTrue(c.fireNow());
    }

    @Test
    public void atEndMeansWithinHalfASecondOfAKnownDuration() {
        assertTrue(EndScreenCountdown.isAtEnd(100_000, 100_000));
        assertTrue(EndScreenCountdown.isAtEnd(99_600, 100_000));
        assertFalse(EndScreenCountdown.isAtEnd(99_000, 100_000));
        assertFalse(EndScreenCountdown.isAtEnd(0, 100_000));
        assertFalse(EndScreenCountdown.isAtEnd(0, 0)); // unknown length (live): not "at the end"
        assertFalse(EndScreenCountdown.isAtEnd(5_000, -1));
    }
}
