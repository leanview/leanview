package com.liskovsoft.smartyoutubetv2.common.leanview;

import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerConstants;
import com.liskovsoft.smartyoutubetv2.common.leanview.EndScreenSettings.NextSource;

/**
 * leanview: what to do when a video ends and the player would jump to the next one.
 * Only the endings that used to auto-advance are candidates; every other case is LEGACY and the
 * player does exactly what it did before. No Android code (the imports are int constants).
 */
public final class EndScreenDecider {
    public enum Kind {
        /** Do what the player always did. */
        LEGACY,
        /** Go to the next video right now, no screen. */
        ADVANCE_NOW,
        /** Show the end screen and count down {@link Decision#seconds}. */
        COUNTDOWN,
        /** Show the end screen and wait for a choice (no timer). */
        WAIT_MANUAL,
        /** Show "still watching?" and do not advance by itself. */
        STILL_WATCHING,
        /** The next video is not known yet: show the screen "looking for the next one" and decide again when it arrives. */
        WAIT_FOR_NEXT
    }

    public static final class Decision {
        public final Kind kind;
        public final int seconds;

        private Decision(Kind kind, int seconds) {
            this.kind = kind;
            this.seconds = seconds;
        }

        public static Decision of(Kind kind) {
            return new Decision(kind, 0);
        }

        public static Decision countdown(int seconds) {
            return new Decision(Kind.COUNTDOWN, seconds);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Decision)) {
                return false;
            }

            Decision other = (Decision) o;
            return kind == other.kind && seconds == other.seconds;
        }

        @Override
        public int hashCode() {
            return kind.hashCode() * 31 + seconds;
        }

        @Override
        public String toString() {
            return kind + (kind == Kind.COUNTDOWN ? "(" + seconds + ")" : "");
        }
    }

    /** Everything the decision depends on; the caller fills it from the player state. */
    public static final class Input {
        public EndScreenSettings settings = EndScreenSettings.defaults();
        /** One of the PlayerConstants.PLAYBACK_MODE_* values. */
        public int playbackMode = PlayerConstants.PLAYBACK_MODE_ALL;
        public boolean embed;
        public boolean childMode;
        /** The video was cast from a phone. */
        public boolean remote;
        public boolean inPip;
        /** The player is hidden and only the sound plays. */
        public boolean backgroundAudio;
        /** There is a next video to go to. */
        public boolean hasNext;
        /** The queue has a video after this one (and the queue ignores the playback mode). */
        public boolean hasQueueNext;
        /** The playlist of this video has a next one. */
        public boolean hasPlaylistNext;
        /** The next video comes from a queue or a playlist. */
        public boolean nextFromPlaylist;
        /** Time since the last key press. */
        public long idleMs;
        /** leanview: there is no next video yet, but the suggestions of this video are still loading, so one may arrive. */
        public boolean nextPending;
        /** leanview: a short opened from the Shorts section: a feed that moves on by itself, as it always did. */
        public boolean shortsFeed;
    }

    private static final long HOUR_MS = 3_600_000L;

    private EndScreenDecider() {
    }

    public static Decision decide(Input in) {
        EndScreenSettings settings = in.settings;

        // leanview: the Shorts feed keeps moving on by itself, with no screen (a looped short never gets here: mode ONE)
        if (!settings.enabled || in.embed || in.childMode || in.shortsFeed) {
            return Decision.of(Kind.LEGACY);
        }

        if (!in.hasNext) {
            return decideWithoutNext(in);
        }

        if (!isCandidate(in)) {
            return Decision.of(Kind.LEGACY);
        }

        if (in.remote && settings.skipForCast) {
            return Decision.of(Kind.ADVANCE_NOW);
        }

        if (in.inPip || in.backgroundAudio) {
            return Decision.of(Kind.ADVANCE_NOW); // the screen cannot be seen
        }

        int seconds;

        if (in.nextFromPlaylist) {
            seconds = settings.playlistCountdownSec;
        } else if (settings.nextSource == NextSource.PLAYLIST_ONLY || settings.relatedCountdownSec == EndScreenSettings.NEVER_AUTO) {
            return Decision.of(Kind.WAIT_MANUAL); // nothing will advance, so there is no one to ask
        } else {
            seconds = settings.relatedCountdownSec;
        }

        if (settings.stillWatchingHours > 0 && in.idleMs >= settings.stillWatchingHours * HOUR_MS) {
            return Decision.of(Kind.STILL_WATCHING);
        }

        return seconds == 0 ? Decision.of(Kind.ADVANCE_NOW) : Decision.countdown(seconds);
    }

    /**
     * leanview: the video ended and there is no next one yet. When the suggestions are still loading
     * the old player retried every second and opened the next video the moment it arrived, with no
     * screen. Here the screen waits for it instead, and decide() runs again with hasNext when it
     * arrives. The answers that need no screen are the same ones decide() gives with a next video.
     */
    private static Decision decideWithoutNext(Input in) {
        EndScreenSettings settings = in.settings;

        if (!in.nextPending || !movesOnByItself(in.playbackMode)) {
            return Decision.of(Kind.LEGACY); // nothing will arrive, or this mode would not have moved on
        }

        if ((in.remote && settings.skipForCast) || in.inPip || in.backgroundAudio) {
            return Decision.of(Kind.ADVANCE_NOW); // nobody can see the screen
        }

        boolean askStillWatching = settings.stillWatchingHours > 0 && in.idleMs >= settings.stillWatchingHours * HOUR_MS;
        boolean everythingIsImmediate = settings.relatedCountdownSec == 0 && settings.playlistCountdownSec == 0
                && settings.nextSource != NextSource.PLAYLIST_ONLY;

        if (everythingIsImmediate && !askStillWatching) {
            return Decision.of(Kind.ADVANCE_NOW);
        }

        return Decision.of(Kind.WAIT_FOR_NEXT);
    }

    /** The modes that go to another video by themselves once its identity is known. */
    private static boolean movesOnByItself(int playbackMode) {
        return playbackMode == PlayerConstants.PLAYBACK_MODE_ALL
                || playbackMode == PlayerConstants.PLAYBACK_MODE_SHUFFLE
                || playbackMode == PlayerConstants.PLAYBACK_MODE_REVERSE_LIST;
    }

    /** Is this ending one that moves on to another video by itself? */
    private static boolean isCandidate(Input in) {
        if (!in.hasNext) {
            return false;
        }

        switch (in.playbackMode) {
            case PlayerConstants.PLAYBACK_MODE_ALL:
            case PlayerConstants.PLAYBACK_MODE_SHUFFLE:
            case PlayerConstants.PLAYBACK_MODE_REVERSE_LIST:
                return true;
            case PlayerConstants.PLAYBACK_MODE_LIST:
                return in.hasPlaylistNext || in.hasQueueNext;
            case PlayerConstants.PLAYBACK_MODE_CLOSE:
            case PlayerConstants.PLAYBACK_MODE_PAUSE:
                return in.hasQueueNext;
            default: // repeat one (and anything unknown) never moves on
                return false;
        }
    }

    /**
     * A next video from a list the person chose (queue, playlist) counts as "playlist". The next tile of
     * the section it was opened from (a Home row) does not: YouTube offers that one like a recommendation,
     * with the longer countdown (2026-11-04: from the Popular row it waited only 3 s).
     */
    public static boolean isPlaylistSource(boolean queueNext, boolean nextInSamePlaylist, boolean nextFromSection) {
        return queueNext || nextInSamePlaylist;
    }
}
