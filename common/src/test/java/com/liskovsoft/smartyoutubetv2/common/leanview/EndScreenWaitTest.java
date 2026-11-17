package com.liskovsoft.smartyoutubetv2.common.leanview;

import static org.junit.Assert.assertEquals;

import com.liskovsoft.smartyoutubetv2.common.leanview.EndScreenDecider.Decision;
import com.liskovsoft.smartyoutubetv2.common.leanview.EndScreenDecider.Kind;
import com.liskovsoft.smartyoutubetv2.common.leanview.EndScreenWait.Outcome;

import org.junit.Test;

// leanview: the "looking for the next one" card polls; this is what each poll does. The rule that
// matters: once the next video is known, the same decider that would have shown a countdown decides,
// and the video is never opened directly when that decision is a countdown.
public class EndScreenWaitTest {
    private static Outcome poll(Decision decision, boolean hasNext, long waitedMs) {
        return EndScreenWait.afterPoll(decision, hasNext, waitedMs);
    }

    @Test
    public void stillNothingKeepsWaiting() {
        assertEquals(Outcome.KEEP_WAITING, poll(Decision.of(Kind.WAIT_FOR_NEXT), false, 2_000));
    }

    @Test
    public void whenTheNextArrivesACountdownIsShownNotStartedDirectly() {
        assertEquals(Outcome.SHOW, poll(Decision.countdown(8), true, 2_000));
        assertEquals(Outcome.SHOW, poll(Decision.of(Kind.WAIT_MANUAL), true, 2_000));
        assertEquals(Outcome.SHOW, poll(Decision.of(Kind.STILL_WATCHING), true, 2_000));
    }

    @Test
    public void whenTheNextArrivesAndTheDeciderSaysNowItStartsAtOnce() {
        assertEquals(Outcome.ADVANCE, poll(Decision.of(Kind.ADVANCE_NOW), true, 2_000));
    }

    @Test
    public void whenTheEndScreenWasTurnedOffMeanwhileTheOldWayIsGoNow() {
        assertEquals(Outcome.ADVANCE, poll(Decision.of(Kind.LEGACY), true, 2_000));
    }

    @Test
    public void advanceNowWithoutANextVideoCannotAdvanceSoItKeepsWaiting() {
        // e.g. the corner window opened while the card waited
        assertEquals(Outcome.KEEP_WAITING, poll(Decision.of(Kind.ADVANCE_NOW), false, 2_000));
    }

    @Test
    public void suggestionsInButNoNextVideoMeansGiveUp() {
        assertEquals(Outcome.GIVE_UP, poll(Decision.of(Kind.LEGACY), false, 2_000));
    }

    @Test
    public void thirtySecondsWithoutSuggestionsMeansGiveUp() {
        assertEquals(Outcome.KEEP_WAITING, poll(Decision.of(Kind.WAIT_FOR_NEXT), false, EndScreenWait.TIMEOUT_MS - 1));
        assertEquals(Outcome.GIVE_UP, poll(Decision.of(Kind.WAIT_FOR_NEXT), false, EndScreenWait.TIMEOUT_MS));
        assertEquals(Outcome.GIVE_UP, poll(Decision.of(Kind.ADVANCE_NOW), false, EndScreenWait.TIMEOUT_MS));
    }

    @Test
    public void aNextThatArrivesOnTheLastPollStillCounts() {
        assertEquals(Outcome.SHOW, poll(Decision.countdown(8), true, EndScreenWait.TIMEOUT_MS + 500));
    }
}
