package com.liskovsoft.smartyoutubetv2.common.leanview;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.liskovsoft.smartyoutubetv2.common.leanview.EndScreenKeys.Action;
import com.liskovsoft.smartyoutubetv2.common.leanview.EndScreenKeys.Key;
import com.liskovsoft.smartyoutubetv2.common.leanview.EndScreenKeys.Mode;
import com.liskovsoft.smartyoutubetv2.common.leanview.EndScreenKeys.Result;
import com.liskovsoft.smartyoutubetv2.common.leanview.EndScreenKeys.Zone;

import org.junit.Test;

// leanview: what each remote key does on the end screen. The rule that matters most: BACK cancels the
// countdown and is swallowed, so the same press cannot also close the player.
public class EndScreenKeysTest {
    private static Result press(Mode mode, Zone zone, int selected, Key key) {
        return EndScreenKeys.handle(mode, zone, selected, key, true, true);
    }

    // --- buttons per mode

    @Test
    public void everyModeHasItsTwoButtons() {
        assertArrayEquals(new Action[] {Action.PLAY_NOW, Action.CANCEL}, EndScreenKeys.buttons(Mode.COUNTDOWN));
        assertArrayEquals(new Action[] {Action.PLAY_NOW, Action.REPLAY}, EndScreenKeys.buttons(Mode.MANUAL));
        assertArrayEquals(new Action[] {Action.STILL_YES, Action.STILL_NO}, EndScreenKeys.buttons(Mode.STILL_WATCHING));
    }

    @Test
    public void playNowIsFocusedFirstUnlessTheOwnerPicksCancel() {
        assertEquals(0, EndScreenKeys.initialSelection(Mode.COUNTDOWN, EndScreenSettings.DefaultFocus.PLAY_NOW));
        assertEquals(1, EndScreenKeys.initialSelection(Mode.COUNTDOWN, EndScreenSettings.DefaultFocus.CANCEL));
        assertEquals(0, EndScreenKeys.initialSelection(Mode.MANUAL, EndScreenSettings.DefaultFocus.CANCEL));
        assertEquals(0, EndScreenKeys.initialSelection(Mode.STILL_WATCHING, EndScreenSettings.DefaultFocus.CANCEL));
    }

    // --- on the buttons

    @Test
    public void okPressesTheSelectedButton() {
        Result r = press(Mode.COUNTDOWN, Zone.BUTTONS, 0, Key.OK);
        assertTrue(r.consume);
        assertEquals(Action.PLAY_NOW, r.action);

        r = press(Mode.COUNTDOWN, Zone.BUTTONS, 1, Key.OK);
        assertTrue(r.consume);
        assertEquals(Action.CANCEL, r.action);

        assertEquals(Action.REPLAY, press(Mode.MANUAL, Zone.BUTTONS, 1, Key.OK).action);
        assertEquals(Action.STILL_NO, press(Mode.STILL_WATCHING, Zone.BUTTONS, 1, Key.OK).action);
    }

    @Test
    public void leftAndRightMoveBetweenTheButtonsWithoutPressing() {
        Result r = press(Mode.COUNTDOWN, Zone.BUTTONS, 0, Key.RIGHT);
        assertTrue(r.consume);
        assertEquals(1, r.selected);
        assertEquals(Action.NONE, r.action);

        r = press(Mode.COUNTDOWN, Zone.BUTTONS, 1, Key.LEFT);
        assertTrue(r.consume);
        assertEquals(0, r.selected);

        // already at the edge
        assertEquals(1, press(Mode.COUNTDOWN, Zone.BUTTONS, 1, Key.RIGHT).selected);
        assertEquals(0, press(Mode.COUNTDOWN, Zone.BUTTONS, 0, Key.LEFT).selected);
    }

    @Test
    public void backCancelsTheCountdownAndIsSwallowed() {
        for (int selected = 0; selected < 2; selected++) {
            Result r = press(Mode.COUNTDOWN, Zone.BUTTONS, selected, Key.BACK);

            assertTrue("the press must not reach the player", r.consume);
            assertEquals(Action.CANCEL, r.action);
        }
    }

    @Test
    public void backOnTheWaitingCardJustClosesTheCard() {
        Result r = press(Mode.MANUAL, Zone.BUTTONS, 0, Key.BACK);

        assertTrue(r.consume);
        assertEquals(Action.DISMISS, r.action);
    }

    @Test
    public void backOnStillWatchingMeansNo() {
        Result r = press(Mode.STILL_WATCHING, Zone.BUTTONS, 0, Key.BACK);

        assertTrue(r.consume);
        assertEquals(Action.STILL_NO, r.action);
    }

    @Test
    public void downGoesToTheRelatedRow() {
        Result r = press(Mode.COUNTDOWN, Zone.BUTTONS, 0, Key.DOWN);

        assertTrue(r.consume);
        assertEquals(Zone.RELATED, r.zone);
        assertEquals(Action.NONE, r.action);
    }

    @Test
    public void downWithNothingRelatedStaysOnTheButtons() {
        Result r = EndScreenKeys.handle(Mode.COUNTDOWN, Zone.BUTTONS, 0, Key.DOWN, false, true);

        assertTrue(r.consume);
        assertEquals(Zone.BUTTONS, r.zone);
    }

    @Test
    public void upOnTheButtonsDoesNotScrollToTheControls() {
        Result r = press(Mode.COUNTDOWN, Zone.BUTTONS, 0, Key.UP);

        assertTrue(r.consume);
        assertEquals(Zone.BUTTONS, r.zone);
        assertEquals(Action.NONE, r.action);
    }

    @Test
    public void otherKeysPassThroughToThePlayer() {
        for (Zone zone : Zone.values()) {
            Result r = press(Mode.COUNTDOWN, zone, 0, Key.OTHER);

            assertFalse(r.consume);
            assertEquals(Action.NONE, r.action);
            assertEquals(zone, r.zone);
        }
    }

    // --- on the related row

    @Test
    public void relatedRowKeepsItsOwnNavigation() {
        for (Key key : new Key[] {Key.LEFT, Key.RIGHT, Key.DOWN, Key.OK}) {
            Result r = press(Mode.COUNTDOWN, Zone.RELATED, 0, key);

            assertFalse("" + key, r.consume);
            assertEquals(Zone.RELATED, r.zone);
            assertEquals(Action.NONE, r.action);
        }
    }

    @Test
    public void upFromTheTopRelatedRowGoesBackToTheButtons() {
        Result r = EndScreenKeys.handle(Mode.COUNTDOWN, Zone.RELATED, 0, Key.UP, true, true);

        assertTrue(r.consume);
        assertEquals(Zone.BUTTONS, r.zone);
    }

    @Test
    public void upFromALowerRelatedRowIsLeftToTheRow() {
        Result r = EndScreenKeys.handle(Mode.COUNTDOWN, Zone.RELATED, 0, Key.UP, true, false);

        assertFalse(r.consume);
        assertEquals(Zone.RELATED, r.zone);
    }

    @Test
    public void backFromTheRelatedRowStillCancelsAndComesBackToTheButtons() {
        Result r = press(Mode.COUNTDOWN, Zone.RELATED, 0, Key.BACK);

        assertTrue(r.consume);
        assertEquals(Action.CANCEL, r.action);
        assertEquals(Zone.BUTTONS, r.zone);
    }

    @Test
    public void selectionIsKeptWhenNothingChangesIt() {
        assertEquals(1, press(Mode.COUNTDOWN, Zone.BUTTONS, 1, Key.DOWN).selected);
        assertEquals(1, press(Mode.COUNTDOWN, Zone.RELATED, 1, Key.OK).selected);
    }

    // --- the waiting card (the next video is not known yet)

    @Test
    public void theWaitingCardOffersCancelAndWatchAgain() {
        assertArrayEquals(new Action[] {Action.CANCEL, Action.REPLAY}, EndScreenKeys.buttons(Mode.WAITING));
        assertEquals(0, EndScreenKeys.initialSelection(Mode.WAITING, EndScreenSettings.DefaultFocus.CANCEL));
        assertEquals(0, EndScreenKeys.initialSelection(Mode.WAITING, EndScreenSettings.DefaultFocus.PLAY_NOW));
    }

    @Test
    public void backOnTheWaitingCardCancelsItAndIsSwallowed() {
        Result r = press(Mode.WAITING, Zone.BUTTONS, 0, Key.BACK);

        assertTrue("the press must not reach the player", r.consume);
        assertEquals(Action.CANCEL, r.action);
    }

    @Test
    public void okOnTheWaitingCardPressesTheSelectedButton() {
        assertEquals(Action.CANCEL, press(Mode.WAITING, Zone.BUTTONS, 0, Key.OK).action);
        assertEquals(Action.REPLAY, press(Mode.WAITING, Zone.BUTTONS, 1, Key.OK).action);
    }
}
