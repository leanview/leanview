package com.liskovsoft.smartyoutubetv2.common.leanview;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

// leanview: a finger on the seek bar. The knob is drawn between inset and width-inset (SeekBar.calculate),
// so the same mapping in reverse puts the knob under the finger and the time where the knob is.
public class SeekTouchTest {
    private static final int MAX = Integer.MAX_VALUE;
    private static final int WIDTH = 808; // the bar of a 1080 px wide phone
    private static final int INSET = 7;

    @Test
    public void theEndsOfTheBarAreTheStartAndTheEnd() {
        assertEquals(0, SeekTouch.progressForX(INSET, WIDTH, INSET, MAX));
        assertEquals(MAX, SeekTouch.progressForX(WIDTH - INSET, WIDTH, INSET, MAX));
    }

    @Test
    public void theMiddleIsHalf() {
        long half = SeekTouch.progressForX(WIDTH / 2f, WIDTH, INSET, MAX);
        assertEquals(MAX / 2.0, half, 2.0);
    }

    @Test
    public void outsideTheBarIsClampedNotWrapped() {
        assertEquals(0, SeekTouch.progressForX(-50, WIDTH, INSET, MAX));
        assertEquals(0, SeekTouch.progressForX(0, WIDTH, INSET, MAX));
        assertEquals(MAX, SeekTouch.progressForX(WIDTH + 90, WIDTH, INSET, MAX));
    }

    @Test
    public void aBarNotMeasuredYetSeeksNowhere() {
        assertEquals(0, SeekTouch.progressForX(100, 0, INSET, MAX));
        assertEquals(0, SeekTouch.progressForX(100, 2 * INSET, INSET, MAX));
    }

    // The knob under the finger is checked on the real bar (smarttubetv PhoneOnlyEntryPointsTest.theKnobIsDrawnUnderTheFinger).

    @Test
    public void aPositionMapsBackToTheBar() {
        long twentyMinutes = 1_203_000L;
        assertEquals(0, SeekTouch.progressForPosition(0, twentyMinutes, MAX));
        assertEquals(MAX, SeekTouch.progressForPosition(twentyMinutes, twentyMinutes, MAX));
        assertEquals(MAX / 2.0, SeekTouch.progressForPosition(601_500L, twentyMinutes, MAX), 2.0);
        assertEquals(MAX, SeekTouch.progressForPosition(twentyMinutes + 5000, twentyMinutes, MAX));
        assertEquals(0, SeekTouch.progressForPosition(-5, twentyMinutes, MAX));
        assertEquals(0, SeekTouch.progressForPosition(1000, 0, MAX)); // unknown length
    }

    @Test
    public void thePositionOfAProgressRoundTrips() {
        long length = 1_203_000L;
        for (long ms : new long[]{0L, 1000L, 331_000L, 842_100L, length}) {
            assertEquals(ms, SeekTouch.positionMs(SeekTouch.progressForPosition(ms, length, MAX), MAX, length), 1.0);
        }
    }

    @Test
    public void theTimeFollowsTheKnob() {
        long twentyMinutes = 1_203_000L;
        assertEquals(0L, SeekTouch.positionMs(0, MAX, twentyMinutes));
        assertEquals(twentyMinutes, SeekTouch.positionMs(MAX, MAX, twentyMinutes));
        assertEquals(601_500L, SeekTouch.positionMs(MAX / 2, MAX, twentyMinutes), 1.0);
        // 70 % of the bar = 14:02, what the phone showed
        int seventy = SeekTouch.progressForX(INSET + 0.7f * (WIDTH - 2 * INSET), WIDTH, INSET, MAX);
        assertEquals(842_100L, SeekTouch.positionMs(seventy, MAX, twentyMinutes), 500.0);
    }

    @Test
    public void anUnknownLengthSeeksToTheStart() {
        assertEquals(0L, SeekTouch.positionMs(MAX / 2, MAX, 0));
        assertEquals(0L, SeekTouch.positionMs(MAX / 2, MAX, -1));
    }
}
