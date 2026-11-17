package com.liskovsoft.smartyoutubetv2.common.leanview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

// leanview: the end screen is an opaque band across the top; the related rows must start below it.
// Numbers are those of a Google TV Streamer: 1920x1080 at density 2.0 (960x540 dp).
public class EndScreenLayoutTest {
    private static final int DP = 2;

    @Test
    public void rowsThatAlreadyStartBelowTheBandStayWhereTheyAre() {
        assertEquals(0, EndScreenLayout.rowsShiftPx(300, 320, 16, 240));
    }

    @Test
    public void rowsThatStartInsideTheBandMoveDownToClearItWithAGap() {
        // band ends at y=300, the row header starts at y=280, wanted gap 16 -> 36 px
        assertEquals(36, EndScreenLayout.rowsShiftPx(300, 280, 16, 240));
    }

    @Test
    public void exactlyTheGapAwayNeedsNoShift() {
        assertEquals(0, EndScreenLayout.rowsShiftPx(300, 316, 16, 240));
        assertEquals(1, EndScreenLayout.rowsShiftPx(300, 315, 16, 240));
    }

    @Test
    public void theShiftIsCappedSoTheLowerRowsDoNotRunOffTheScreen() {
        assertEquals(240, EndScreenLayout.rowsShiftPx(300, 0, 16, 240));
    }

    @Test
    public void theShiftNeverGoesNegativeAndAZeroCapMeansNoShift() {
        assertEquals(0, EndScreenLayout.rowsShiftPx(300, 900, 16, 240));
        assertEquals(0, EndScreenLayout.rowsShiftPx(300, 100, 16, 0));
    }

    @Test
    public void theOldBigCardWouldHaveNeededMoreThanTheCompactBand() {
        // old card: bottom at y=450 (225 dp); the chapters row header started near y=280
        int oldShift = EndScreenLayout.rowsShiftPx(450, 280, 16 * 1, 240);
        // new band: 16 dp top + 122 dp content + 12 dp bottom = 150 dp = 300 px
        int newShift = EndScreenLayout.rowsShiftPx(150 * DP, 280, 8 * DP, 120 * DP);

        assertEquals(186, oldShift);
        assertEquals(36, newShift);
        assertTrue(newShift < oldShift);
    }
}
