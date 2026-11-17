package com.liskovsoft.smartyoutubetv2.common.leanview;

import com.liskovsoft.smartyoutubetv2.common.leanview.EndScreenDecider.Decision;

/**
 * leanview: the end screen in its "looking for the next one" state polls until the suggestions of
 * the finished video arrive. What to do after each poll is decided here. No Android code.
 */
public final class EndScreenWait {
    public enum Outcome {
        /** Nothing new: keep the waiting card and poll again. */
        KEEP_WAITING,
        /** The next video is known and the decider wants a screen: switch the card to it. */
        SHOW,
        /** The next video is known and it must start right now. */
        ADVANCE,
        /** Nothing is going to arrive: close the card and leave the video at its end. */
        GIVE_UP
    }

    /** How long the card waits for the suggestions before it gives up. */
    public static final long TIMEOUT_MS = 30_000L;

    private EndScreenWait() {
    }

    /**
     * @param decision the decider's answer with the information available now
     * @param hasNext  the next video is known now
     * @param waitedMs time spent on the waiting card
     */
    public static Outcome afterPoll(Decision decision, boolean hasNext, long waitedMs) {
        switch (decision.kind) {
            case COUNTDOWN:
            case WAIT_MANUAL:
            case STILL_WATCHING:
                return hasNext ? Outcome.SHOW : timedOut(waitedMs);
            case ADVANCE_NOW:
                return hasNext ? Outcome.ADVANCE : timedOut(waitedMs);
            case LEGACY:
                // the old way is "go now" when there is a video to go to; when the suggestions are in and
                // there is none, nothing is going to arrive
                return hasNext ? Outcome.ADVANCE : Outcome.GIVE_UP;
            case WAIT_FOR_NEXT:
            default:
                return timedOut(waitedMs);
        }
    }

    private static Outcome timedOut(long waitedMs) {
        return waitedMs >= TIMEOUT_MS ? Outcome.GIVE_UP : Outcome.KEEP_WAITING;
    }
}
