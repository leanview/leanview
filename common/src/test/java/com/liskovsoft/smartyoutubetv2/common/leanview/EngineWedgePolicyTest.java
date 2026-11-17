package com.liskovsoft.smartyoutubetv2.common.leanview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.liskovsoft.smartyoutubetv2.common.leanview.EngineWedgePolicy.Action;

import org.junit.After;
import org.junit.Test;

/** leanview: the app ends its own process for a wedged engine only in the background with nothing playing and no backup running. */
public class EngineWedgePolicyTest {
    private static Action decide(boolean wedged, boolean away, boolean playing, boolean pip, boolean backup) {
        return EngineWedgePolicy.decide(wedged, away, playing, pip, backup);
    }

    @After
    public void noBackupLeftRunning() {
        while (RunningBackups.any()) {
            RunningBackups.finished();
        }
    }

    @Test
    public void beforeTheCapNothingHappensEvenInTheBackgroundAndIdle() {
        assertEquals(Action.NONE, decide(false, true, false, false, false));
        assertEquals(Action.NONE, decide(false, true, true, false, true));
    }

    @Test
    public void pastTheCapInFrontNothingHappensNow() {
        // a screen is started: its stop schedules the next check, so there is nothing to poll
        assertEquals(Action.NONE, decide(true, false, false, false, false));
        assertEquals(Action.NONE, decide(true, false, true, false, false));
        assertEquals(Action.NONE, decide(true, false, false, false, true));
    }

    @Test
    public void pastTheCapInTheBackgroundAndIdleItEnds() {
        assertEquals(Action.END_PROCESS, decide(true, true, false, false, false));
    }

    // M1 (final review): a wedge while background audio plays was never looked at again when the audio stopped
    @Test
    public void pastTheCapInTheBackgroundWhilePlayingItChecksAgainLater() {
        assertEquals("playing behind", Action.CHECK_AGAIN, decide(true, true, true, false, false));
        assertEquals("corner window", Action.CHECK_AGAIN, decide(true, true, false, true, false));
        assertEquals("both", Action.CHECK_AGAIN, decide(true, true, true, true, false));
    }

    // M3 (final review): an exit in the middle of a Drive upload leaves a mixed backup
    @Test
    public void pastTheCapInTheBackgroundWhileABackupRunsItWaitsAndChecksAgain() {
        assertEquals(Action.CHECK_AGAIN, decide(true, true, false, false, true));
        assertEquals(Action.END_PROCESS, decide(true, true, false, false, false));
    }

    @Test
    public void theBackupCountFollowsTheWorkersAndNeverGoesBelowZero() {
        assertFalse(RunningBackups.any());
        RunningBackups.started();
        RunningBackups.started();
        assertTrue(RunningBackups.any());
        RunningBackups.finished();
        assertTrue("the second worker still runs", RunningBackups.any());
        RunningBackups.finished();
        assertFalse(RunningBackups.any());
        RunningBackups.finished();   // an extra finish (a worker that never counted) does not go negative
        RunningBackups.started();
        assertTrue(RunningBackups.any());
    }
}
