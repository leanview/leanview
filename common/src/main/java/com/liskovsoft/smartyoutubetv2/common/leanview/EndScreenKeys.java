package com.liskovsoft.smartyoutubetv2.common.leanview;

import com.liskovsoft.smartyoutubetv2.common.leanview.EndScreenSettings.DefaultFocus;

/**
 * leanview: what each remote key does on the "Up next" end screen.
 * The screen has two buttons (zone BUTTONS) and, below it, the player's own related videos row
 * (zone RELATED). Keys on the buttons are handled here and swallowed; on the related row the
 * player's own navigation works as always, except that BACK still cancels the countdown (and
 * is swallowed, so the same press cannot also close the player) and UP from the top row comes
 * back to the buttons. No Android code: the view maps key codes to {@link Key}.
 */
public final class EndScreenKeys {
    public enum Mode { COUNTDOWN, MANUAL, STILL_WATCHING, WAITING }

    public enum Zone { BUTTONS, RELATED }

    public enum Key { LEFT, RIGHT, UP, DOWN, OK, BACK, OTHER }

    public enum Action {
        NONE,
        /** Go to the next video now. */
        PLAY_NOW,
        /** Stop the countdown; the card stays with "play now" and "watch again". */
        CANCEL,
        /** Play the finished video again. */
        REPLAY,
        STILL_YES,
        STILL_NO,
        /** Close the card (and the player's overlay); the next BACK leaves the player as usual. */
        DISMISS
    }

    public static final class Result {
        /** The key must not reach the player. */
        public final boolean consume;
        public final Action action;
        public final Zone zone;
        public final int selected;

        Result(boolean consume, Action action, Zone zone, int selected) {
            this.consume = consume;
            this.action = action;
            this.zone = zone;
            this.selected = selected;
        }
    }

    private EndScreenKeys() {
    }

    /** The left and the right button of a mode. */
    public static Action[] buttons(Mode mode) {
        switch (mode) {
            case MANUAL:
                return new Action[] {Action.PLAY_NOW, Action.REPLAY};
            case STILL_WATCHING:
                return new Action[] {Action.STILL_YES, Action.STILL_NO};
            case WAITING:
                return new Action[] {Action.CANCEL, Action.REPLAY}; // there is nothing to play now yet
            case COUNTDOWN:
            default:
                return new Action[] {Action.PLAY_NOW, Action.CANCEL};
        }
    }

    /** 0 = left button, 1 = right button. Only the countdown lets the user pick the other one. */
    public static int initialSelection(Mode mode, DefaultFocus focus) {
        return mode == Mode.COUNTDOWN && focus == DefaultFocus.CANCEL ? 1 : 0; // WAITING: "Cancel" is the left button
    }

    public static Result handle(Mode mode, Zone zone, int selected, Key key, boolean relatedAvailable, boolean relatedAtTop) {
        if (key == Key.BACK) {
            return new Result(true, backAction(mode), Zone.BUTTONS, selected);
        }

        if (zone == Zone.RELATED) {
            if (key == Key.UP && relatedAtTop) {
                return new Result(true, Action.NONE, Zone.BUTTONS, selected);
            }

            return new Result(false, Action.NONE, Zone.RELATED, selected); // the row's own navigation
        }

        switch (key) {
            case LEFT:
                return new Result(true, Action.NONE, Zone.BUTTONS, 0);
            case RIGHT:
                return new Result(true, Action.NONE, Zone.BUTTONS, 1);
            case OK:
                return new Result(true, buttons(mode)[selected == 1 ? 1 : 0], Zone.BUTTONS, selected);
            case DOWN:
                return new Result(true, Action.NONE, relatedAvailable ? Zone.RELATED : Zone.BUTTONS, selected);
            case UP:
                return new Result(true, Action.NONE, Zone.BUTTONS, selected); // nothing above: don't scroll to the controls
            default:
                return new Result(false, Action.NONE, Zone.BUTTONS, selected);
        }
    }

    private static Action backAction(Mode mode) {
        switch (mode) {
            case MANUAL:
                return Action.DISMISS;
            case STILL_WATCHING:
                return Action.STILL_NO;
            case COUNTDOWN:
            default:
                return Action.CANCEL;
        }
    }
}
