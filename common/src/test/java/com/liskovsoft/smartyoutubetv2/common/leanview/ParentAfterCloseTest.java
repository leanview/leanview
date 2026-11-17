package com.liskovsoft.smartyoutubetv2.common.leanview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.liskovsoft.smartyoutubetv2.common.leanview.ParentAfterClose.Next;

import org.junit.Test;

// leanview: where the app goes when a window closes. 2026-11-04 on the TV (log of release 35): a link
// launched the player and, at once, "Who's watching?"; the picker came up first, and when a profile
// was chosen it found no window to go back to, left for the launcher and sent the player, created a
// moment later, behind it too.
public class ParentAfterCloseTest {
    @Test
    public void withAParentItOpens() {
        assertEquals(Next.OPEN_PARENT, ParentAfterClose.next(true, false, false));
    }

    @Test
    public void withNothingToGoBackToTheAppLeaves() {
        assertEquals(Next.LEAVE_APP, ParentAfterClose.next(false, false, false));
        assertEquals(Next.LEAVE_APP, ParentAfterClose.next(true, true, false)); // player-only mode, as upstream
    }

    @Test
    public void aPlayerOnItsWayIsLetIn() {
        assertEquals(Next.LET_PLAYER_COME, ParentAfterClose.next(false, false, true));
        assertEquals(Next.LET_PLAYER_COME, ParentAfterClose.next(false, true, true));
        assertEquals(Next.LET_PLAYER_COME, ParentAfterClose.next(true, true, true));
        // with the app already open the picker's parent is Browse; the link's player still wins
        assertEquals(Next.LET_PLAYER_COME, ParentAfterClose.next(true, false, true));
    }

    @Test
    public void aLaunchCountsAsOnItsWayForFiveMinutes() {
        assertTrue(ParentAfterClose.onItsWay(10_000L, 10_000L));
        // a profile picked half a minute after the link
        assertTrue(ParentAfterClose.onItsWay(10_000L, 40_000L));
        assertTrue(ParentAfterClose.onItsWay(10_000L, 309_999L));
        assertFalse(ParentAfterClose.onItsWay(10_000L, 310_000L));
        assertFalse(ParentAfterClose.onItsWay(null, 10_000L));
        // a clock that went back never makes a launch look pending forever
        assertFalse(ParentAfterClose.onItsWay(10_000L, 9_000L));
    }
}
