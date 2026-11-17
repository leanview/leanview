package com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.tooltips;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

// leanview (2026-11-08): the account name under the focused account orb sat 10 px left of the orb. A
// focused orb is drawn 1.2x around its centre, getLocationOnScreen() gives the left edge of that
// scaled drawing, and the tooltip added half the UNSCALED width to it.
public class TooltipPopupTest {
    private static final int ORB_WIDTH = 104;
    private static final int ORB_CENTRE = 308;

    // where getLocationOnScreen() puts the left edge of a view scaled around its centre
    private static int scaledLeft(float scale) {
        return Math.round(ORB_CENTRE - ORB_WIDTH * scale / 2f);
    }

    @Test
    public void theTooltipIsCentredOnAFocusedOrb() {
        float focused = 1.2f;
        assertEquals(ORB_CENTRE, scaledLeft(focused) + TooltipPopup.centreOffset(ORB_WIDTH, focused), 1);
    }

    @Test
    public void theTooltipIsCentredWhileTheOrbGrows() {
        for (float scale = 1f; scale <= 1.2f; scale += 0.05f) {
            assertEquals(ORB_CENTRE, scaledLeft(scale) + TooltipPopup.centreOffset(ORB_WIDTH, scale), 1);
        }
    }
}
