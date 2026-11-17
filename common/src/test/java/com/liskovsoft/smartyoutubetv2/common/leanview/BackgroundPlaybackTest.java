package com.liskovsoft.smartyoutubetv2.common.leanview;

import static com.liskovsoft.smartyoutubetv2.common.leanview.BackgroundPlayback.Action.AUDIO;
import static com.liskovsoft.smartyoutubetv2.common.leanview.BackgroundPlayback.Action.BEHIND;
import static com.liskovsoft.smartyoutubetv2.common.leanview.BackgroundPlayback.Action.CLOSE;
import static com.liskovsoft.smartyoutubetv2.common.leanview.BackgroundPlayback.Action.PIP;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.liskovsoft.smartyoutubetv2.common.leanview.BackgroundPlayback.Action;
import com.liskovsoft.smartyoutubetv2.common.leanview.BackgroundPlayback.FinishDecision;
import com.liskovsoft.smartyoutubetv2.common.leanview.BackgroundPlayback.HomeDecision;
import org.junit.Test;

// leanview: BACK and HOME in the player each have their own choice.
public class BackgroundPlaybackTest {
    private static void assertFinish(FinishDecision d, boolean enterPip, boolean keepEngine, boolean close) {
        assertEquals("enterPip", enterPip, d.enterPip);
        assertEquals("keepEngine", keepEngine, d.keepEngine);
        assertEquals("close", close, d.close);
    }

    private static void assertHome(HomeDecision d, boolean enterPip, boolean keepEngine, boolean playBehind) {
        assertEquals("enterPip", enterPip, d.enterPip);
        assertEquals("keepEngine", keepEngine, d.keepEngine);
        assertEquals("playBehind", playBehind, d.playBehind);
    }

    @Test
    public void defaultsAreBackClosesHomeCornerWindow() {
        assertEquals(CLOSE, BackgroundPlayback.DEFAULT_BACK);
        assertEquals(PIP, BackgroundPlayback.DEFAULT_HOME);
    }

    @Test
    public void pipWithoutSupportBecomesClose() {
        assertEquals(CLOSE, BackgroundPlayback.effective(PIP, false));
        assertEquals(PIP, BackgroundPlayback.effective(PIP, true));
    }

    @Test
    public void otherActionsDoNotDependOnPipSupport() {
        for (boolean supported : new boolean[] {true, false}) {
            assertEquals(CLOSE, BackgroundPlayback.effective(CLOSE, supported));
            assertEquals(AUDIO, BackgroundPlayback.effective(AUDIO, supported));
            assertEquals(BEHIND, BackgroundPlayback.effective(BEHIND, supported));
        }
    }

    @Test
    public void codesRoundTripAndUnknownFallsBack() {
        for (Action a : Action.values()) {
            assertEquals(a, Action.fromCode(a.code, CLOSE));
        }
        assertEquals(PIP, Action.fromCode(99, PIP));
        assertEquals(AUDIO, Action.fromCode(-1, AUDIO));
    }

    @Test
    public void codesAreStable() {
        assertEquals(0, CLOSE.code);
        assertEquals(1, PIP.code);
        assertEquals(2, AUDIO.code);
        assertEquals(3, BEHIND.code);
    }

    // finish()

    @Test
    public void pipButtonAlwaysGoesToPipWhateverBackIs() {
        for (Action back : Action.values()) {
            for (boolean backPressed : new boolean[] {true, false}) {
                assertFinish(BackgroundPlayback.decideFinish(backPressed, true, back, true), true, true, false);
            }
        }
    }

    @Test
    public void backWithCloseCloses() {
        assertFinish(BackgroundPlayback.decideFinish(true, false, CLOSE, true), false, false, true);
    }

    @Test
    public void backWithPipEntersPipAndKeepsEngine() {
        assertFinish(BackgroundPlayback.decideFinish(true, false, PIP, true), true, true, false);
    }

    @Test
    public void backWithPipOnATvWithoutPipCloses() {
        assertFinish(BackgroundPlayback.decideFinish(true, false, PIP, false), false, false, true);
    }

    @Test
    public void backWithAudioKeepsEngineWithoutPip() {
        assertFinish(BackgroundPlayback.decideFinish(true, false, AUDIO, true), false, true, false);
        assertFinish(BackgroundPlayback.decideFinish(true, false, AUDIO, false), false, true, false);
    }

    @Test
    public void backWithBehindIsNotABackChoiceAndCloses() {
        assertFinish(BackgroundPlayback.decideFinish(true, false, BEHIND, true), false, false, true);
    }

    // bug A: stop key, remote stop, embed errors call finish() without BACK

    @Test
    public void finishWithoutBackAlwaysClosesWhateverBackIs() {
        for (Action back : Action.values()) {
            for (boolean supported : new boolean[] {true, false}) {
                assertFinish(BackgroundPlayback.decideFinish(false, false, back, supported), false, false, true);
            }
        }
    }

    // bug B: with audio on BACK the stop key must still be able to close the player

    @Test
    public void stopKeyClosesEvenWhenBackKeepsTheAudio() {
        FinishDecision d = BackgroundPlayback.decideFinish(false, false, AUDIO, true);
        assertTrue(d.close);
        assertFalse(d.keepEngine);
    }

    // HOME

    @Test
    public void homeWithCloseDoesNothing() {
        assertHome(BackgroundPlayback.decideHome(CLOSE, true), false, false, false);
    }

    @Test
    public void homeWithPipEntersPipAndKeepsEngine() {
        assertHome(BackgroundPlayback.decideHome(PIP, true), true, true, false);
    }

    @Test
    public void homeWithPipOnATvWithoutPipDoesNothing() {
        assertHome(BackgroundPlayback.decideHome(PIP, false), false, false, false);
    }

    @Test
    public void homeWithAudioKeepsEngine() {
        assertHome(BackgroundPlayback.decideHome(AUDIO, true), false, true, false);
    }

    @Test
    public void homeWithBehindPlaysBehind() {
        assertHome(BackgroundPlayback.decideHome(BEHIND, true), false, false, true);
    }

    // screen off

    @Test
    public void screenOffKeepsOnlyWhenSomethingIsChosen() {
        assertFalse(BackgroundPlayback.keepOnScreenOff(CLOSE, CLOSE, true));
        assertTrue(BackgroundPlayback.keepOnScreenOff(CLOSE, PIP, true));
        assertTrue(BackgroundPlayback.keepOnScreenOff(AUDIO, CLOSE, true));
        assertTrue(BackgroundPlayback.keepOnScreenOff(CLOSE, BEHIND, true));
    }

    @Test
    public void screenOffIgnoresPipWhenUnsupported() {
        assertFalse(BackgroundPlayback.keepOnScreenOff(PIP, PIP, false));
        assertTrue(BackgroundPlayback.keepOnScreenOff(PIP, AUDIO, false));
    }

    // old fields

    @Test
    public void legacyModeFollowsTheStrongestChoice() {
        assertEquals(0, BackgroundPlayback.legacyMode(CLOSE, CLOSE));
        assertEquals(2, BackgroundPlayback.legacyMode(CLOSE, PIP));
        assertEquals(2, BackgroundPlayback.legacyMode(PIP, CLOSE));
        assertEquals(1, BackgroundPlayback.legacyMode(AUDIO, CLOSE));
        assertEquals(1, BackgroundPlayback.legacyMode(CLOSE, AUDIO));
        assertEquals(3, BackgroundPlayback.legacyMode(CLOSE, BEHIND));
        assertEquals(2, BackgroundPlayback.legacyMode(AUDIO, PIP));
    }

    @Test
    public void legacyShortcutSaysWhichKeysKeepPlaying() {
        assertEquals(0, BackgroundPlayback.legacyShortcut(CLOSE, PIP)); // HOME
        assertEquals(0, BackgroundPlayback.legacyShortcut(CLOSE, CLOSE));
        assertEquals(2, BackgroundPlayback.legacyShortcut(PIP, CLOSE)); // BACK
        assertEquals(1, BackgroundPlayback.legacyShortcut(PIP, PIP)); // HOME or BACK
        assertEquals(1, BackgroundPlayback.legacyShortcut(AUDIO, AUDIO));
    }
}
