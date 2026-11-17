package com.liskovsoft.smartyoutubetv2.common.leanview;

/**
 * leanview: where a finger on the seek bar means to be. The knob is drawn between {@code inset} and
 * {@code width - inset} (SeekBar.calculate), so the finger maps through the same span and the knob ends up
 * under it (smarttubetv PhoneOnlyEntryPointsTest.theKnobIsDrawnUnderTheFinger checks that on the real bar); outside the bar the position is clamped to the
 * start or the end.
 */
public final class SeekTouch {
    private SeekTouch() {
    }

    /** The bar progress (0..max) for a finger at {@code x} pixels from the left of a bar {@code width} wide. */
    public static int progressForX(float x, int width, int inset, int max) {
        float span = width - 2f * inset;
        if (span <= 0 || max <= 0) {
            return 0;
        }
        double ratio = (x - inset) / span;
        if (ratio <= 0) {
            return 0;
        }
        if (ratio >= 1) {
            return max;
        }
        return (int) Math.round(ratio * max);
    }

    /** The bar progress (0..max) of a playback position; 0 while the length is unknown. */
    public static int progressForPosition(long positionMs, long durationMs, int max) {
        if (durationMs <= 0 || max <= 0 || positionMs <= 0) {
            return 0;
        }
        if (positionMs >= durationMs) {
            return max;
        }
        return (int) Math.round((double) positionMs / durationMs * max);
    }

    /** The playback position (ms) for a bar progress; 0 while the length is unknown. */
    public static long positionMs(int progress, int max, long durationMs) {
        if (durationMs <= 0 || max <= 0) {
            return 0;
        }
        long ms = Math.round((double) progress / max * durationMs);
        return Math.max(0, Math.min(durationMs, ms));
    }
}
