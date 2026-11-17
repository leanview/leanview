package com.liskovsoft.smartyoutubetv2.common.leanview;

/**
 * leanview: geometry of the "Up next" end screen on top of the player's own rows. The end screen is an
 * opaque band across the top of the screen; the related rows must start below it. No Android code:
 * the caller measures in pixels.
 */
public final class EndScreenLayout {
    private EndScreenLayout() {
    }

    /**
     * How far the rows must be moved down so that the selected row starts a gap below the band.
     *
     * @param bandBottomPx bottom edge of the band
     * @param rowTopPx     top edge of the selected related row (its header included)
     * @param gapPx        free space wanted between the band and the row
     * @param maxShiftPx   never move the rows further than this (the lower rows would run off the screen)
     */
    public static int rowsShiftPx(int bandBottomPx, int rowTopPx, int gapPx, int maxShiftPx) {
        int shift = bandBottomPx + gapPx - rowTopPx;

        return Math.max(0, Math.min(shift, maxShiftPx));
    }
}
