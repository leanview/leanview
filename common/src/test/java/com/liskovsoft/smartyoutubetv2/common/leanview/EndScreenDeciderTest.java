package com.liskovsoft.smartyoutubetv2.common.leanview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerConstants;
import com.liskovsoft.smartyoutubetv2.common.leanview.EndScreenDecider.Decision;
import com.liskovsoft.smartyoutubetv2.common.leanview.EndScreenDecider.Input;
import com.liskovsoft.smartyoutubetv2.common.leanview.EndScreenDecider.Kind;
import com.liskovsoft.smartyoutubetv2.common.leanview.EndScreenSettings.NextSource;

import org.junit.Test;

// leanview: what happens when a video ends. Only the endings that used to jump to the next video are
// candidates; everything else keeps the old behaviour (LEGACY).
public class EndScreenDeciderTest {
    private static final long HOUR_MS = 3_600_000L;

    /** A recommendation is next, mode "play all", a person who pressed a key a minute ago. */
    private static Input recommended() {
        Input in = new Input();
        in.playbackMode = PlayerConstants.PLAYBACK_MODE_ALL;
        in.hasNext = true;
        in.nextFromPlaylist = false;
        in.idleMs = 60_000;
        return in;
    }

    private static Input playlist() {
        Input in = recommended();
        in.nextFromPlaylist = true;
        in.hasPlaylistNext = true;
        return in;
    }

    private static void assertDecision(Kind kind, int seconds, Decision actual) {
        assertEquals(kind, actual.kind);
        assertEquals(seconds, actual.seconds);
    }

    // --- which endings are candidates

    @Test
    public void recommendationCountsDownEightSecondsByDefault() {
        assertDecision(Kind.COUNTDOWN, 8, EndScreenDecider.decide(recommended()));
    }

    @Test
    public void playlistCountsDownThreeSecondsByDefault() {
        assertDecision(Kind.COUNTDOWN, 3, EndScreenDecider.decide(playlist()));
    }

    @Test
    public void disabledKeepsTheOldBehaviour() {
        Input in = recommended();
        in.settings = EndScreenSettings.defaults().withEnabled(false);

        assertEquals(Kind.LEGACY, EndScreenDecider.decide(in).kind);
    }

    @Test
    public void shuffleAndReverseListAreCandidates() {
        Input in = recommended();
        in.playbackMode = PlayerConstants.PLAYBACK_MODE_SHUFFLE;
        assertEquals(Kind.COUNTDOWN, EndScreenDecider.decide(in).kind);

        in.playbackMode = PlayerConstants.PLAYBACK_MODE_REVERSE_LIST;
        assertEquals(Kind.COUNTDOWN, EndScreenDecider.decide(in).kind);
    }

    @Test
    public void repeatOneIsLegacy() {
        Input in = recommended();
        in.playbackMode = PlayerConstants.PLAYBACK_MODE_ONE;

        assertEquals(Kind.LEGACY, EndScreenDecider.decide(in).kind);
    }

    @Test
    public void listModeNeedsANextInThePlaylistOrTheQueue() {
        Input in = recommended();
        in.playbackMode = PlayerConstants.PLAYBACK_MODE_LIST;
        assertEquals(Kind.LEGACY, EndScreenDecider.decide(in).kind); // only a recommendation is next

        in.hasPlaylistNext = true;
        in.nextFromPlaylist = true;
        assertEquals(Kind.COUNTDOWN, EndScreenDecider.decide(in).kind);

        in.hasPlaylistNext = false;
        in.hasQueueNext = true;
        assertEquals(Kind.COUNTDOWN, EndScreenDecider.decide(in).kind);
    }

    @Test
    public void closeAndPauseModesNeedAQueueNext() {
        for (int mode : new int[] {PlayerConstants.PLAYBACK_MODE_CLOSE, PlayerConstants.PLAYBACK_MODE_PAUSE}) {
            Input in = recommended();
            in.playbackMode = mode;
            assertEquals(Kind.LEGACY, EndScreenDecider.decide(in).kind);

            in.hasQueueNext = true;
            in.nextFromPlaylist = true;
            assertDecision(Kind.COUNTDOWN, 3, EndScreenDecider.decide(in));
        }
    }

    @Test
    public void noNextVideoIsLegacy() {
        Input in = recommended();
        in.hasNext = false;

        assertEquals(Kind.LEGACY, EndScreenDecider.decide(in).kind);
    }

    @Test
    public void unknownModeIsLegacy() {
        Input in = recommended();
        in.playbackMode = 99;

        assertEquals(Kind.LEGACY, EndScreenDecider.decide(in).kind);
    }

    @Test
    public void embedAndChildModeAreLegacy() {
        Input in = recommended();
        in.embed = true;
        assertEquals(Kind.LEGACY, EndScreenDecider.decide(in).kind);

        in = recommended();
        in.childMode = true;
        assertEquals(Kind.LEGACY, EndScreenDecider.decide(in).kind);
    }

    // --- cast, corner window, audio only

    @Test
    public void castAdvancesAtOnceUnlessTheOwnerAsksOtherwise() {
        Input in = recommended();
        in.remote = true;
        assertEquals(Kind.ADVANCE_NOW, EndScreenDecider.decide(in).kind);

        in.settings = EndScreenSettings.defaults().withSkipForCast(false);
        assertEquals(Kind.COUNTDOWN, EndScreenDecider.decide(in).kind);
    }

    @Test
    public void cornerWindowAndBackgroundAudioAdvanceAtOnce() {
        Input in = recommended();
        in.inPip = true;
        assertEquals(Kind.ADVANCE_NOW, EndScreenDecider.decide(in).kind);

        in = recommended();
        in.backgroundAudio = true;
        assertEquals(Kind.ADVANCE_NOW, EndScreenDecider.decide(in).kind);
    }

    @Test
    public void castKeepsAdvancingEvenAfterHoursWithoutKeys() {
        Input in = recommended();
        in.remote = true;
        in.idleMs = 10 * HOUR_MS;

        assertEquals(Kind.ADVANCE_NOW, EndScreenDecider.decide(in).kind);
    }

    // --- seconds

    @Test
    public void chosenSecondsAreUsedForEachSource() {
        Input in = recommended();
        in.settings = EndScreenSettings.defaults().withRelatedCountdownSec(20).withPlaylistCountdownSec(15);
        assertDecision(Kind.COUNTDOWN, 20, EndScreenDecider.decide(in));

        in.nextFromPlaylist = true;
        in.hasPlaylistNext = true;
        assertDecision(Kind.COUNTDOWN, 15, EndScreenDecider.decide(in));
    }

    @Test
    public void zeroSecondsAdvancesAtOnce() {
        Input in = recommended();
        in.settings = EndScreenSettings.defaults().withRelatedCountdownSec(0);
        assertEquals(Kind.ADVANCE_NOW, EndScreenDecider.decide(in).kind);

        in = playlist();
        in.settings = EndScreenSettings.defaults().withPlaylistCountdownSec(0);
        assertEquals(Kind.ADVANCE_NOW, EndScreenDecider.decide(in).kind);
    }

    @Test
    public void neverAutoWaitsForAChoice() {
        Input in = recommended();
        in.settings = EndScreenSettings.defaults().withRelatedCountdownSec(EndScreenSettings.NEVER_AUTO);

        assertEquals(Kind.WAIT_MANUAL, EndScreenDecider.decide(in).kind);
    }

    @Test
    public void playlistOnlyWaitsWhenTheNextIsARecommendation() {
        Input in = recommended();
        in.settings = EndScreenSettings.defaults().withNextSource(NextSource.PLAYLIST_ONLY);
        assertEquals(Kind.WAIT_MANUAL, EndScreenDecider.decide(in).kind);

        in = playlist();
        in.settings = EndScreenSettings.defaults().withNextSource(NextSource.PLAYLIST_ONLY);
        assertDecision(Kind.COUNTDOWN, 3, EndScreenDecider.decide(in));
    }

    // --- still watching

    @Test
    public void afterThreeHoursWithoutKeysItAsksInsteadOfCountingDown() {
        Input in = recommended();
        in.idleMs = 3 * HOUR_MS;

        assertEquals(Kind.STILL_WATCHING, EndScreenDecider.decide(in).kind);
    }

    @Test
    public void justUnderTheLimitStillCountsDown() {
        Input in = recommended();
        in.idleMs = 3 * HOUR_MS - 1;

        assertEquals(Kind.COUNTDOWN, EndScreenDecider.decide(in).kind);
    }

    @Test
    public void stillWatchingAlsoAppliesToPlaylistsAndImmediateAdvance() {
        Input in = playlist();
        in.idleMs = 5 * HOUR_MS;
        assertEquals(Kind.STILL_WATCHING, EndScreenDecider.decide(in).kind);

        in.settings = EndScreenSettings.defaults().withPlaylistCountdownSec(0);
        assertEquals(Kind.STILL_WATCHING, EndScreenDecider.decide(in).kind);
    }

    @Test
    public void stillWatchingHoursAreConfigurableAndCanBeOff() {
        Input in = recommended();
        in.settings = EndScreenSettings.defaults().withStillWatchingHours(1);
        in.idleMs = HOUR_MS;
        assertEquals(Kind.STILL_WATCHING, EndScreenDecider.decide(in).kind);

        in.settings = EndScreenSettings.defaults().withStillWatchingHours(EndScreenSettings.STILL_NEVER);
        in.idleMs = 100 * HOUR_MS;
        assertDecision(Kind.COUNTDOWN, 8, EndScreenDecider.decide(in));
    }

    @Test
    public void aWaitingScreenDoesNotAskIfTheViewerIsStillThere() {
        Input in = recommended();
        in.settings = EndScreenSettings.defaults().withRelatedCountdownSec(EndScreenSettings.NEVER_AUTO);
        in.idleMs = 9 * HOUR_MS;

        assertEquals(Kind.WAIT_MANUAL, EndScreenDecider.decide(in).kind);
    }

    // --- where the next video comes from

    @Test
    public void queueOrPlaylistNextIsAListNotARecommendation() {
        assertTrue(EndScreenDecider.isPlaylistSource(true, false, false));
        assertTrue(EndScreenDecider.isPlaylistSource(false, true, false));
        assertFalse(EndScreenDecider.isPlaylistSource(false, false, false));
    }

    // 2026-11-04 on the TV: a video opened from the Popular row counted down only 3 s, as a playlist.
    // A row of Home is not a list the person chose; its next tile is offered like a recommendation.
    @Test
    public void theNextTileOfTheSectionIsOfferedLikeARecommendation() {
        assertFalse(EndScreenDecider.isPlaylistSource(false, false, true));
    }

    @Test
    public void decisionsCompareByValue() {
        assertEquals(Decision.countdown(8), Decision.countdown(8));
        assertFalse(Decision.countdown(8).equals(Decision.countdown(3)));
        assertEquals(Decision.of(Kind.LEGACY), Decision.of(Kind.LEGACY));
    }

    // --- the next video is not known yet (a short ends before its suggestions load)

    /** Mode "play all", the suggestions of the finished video have not arrived. */
    private static Input pending() {
        Input in = recommended();
        in.hasNext = false;
        in.nextPending = true;
        return in;
    }

    @Test
    public void nextNotKnownYetWaitsForItInsteadOfTheOldRetryLoop() {
        assertEquals(Kind.WAIT_FOR_NEXT, EndScreenDecider.decide(pending()).kind);
    }

    @Test
    public void whenTheNextArrivesTheSameDeciderShowsTheCountdown() {
        Input in = pending();
        in.hasNext = true; // the suggestions arrived
        in.nextPending = false;

        assertDecision(Kind.COUNTDOWN, 8, EndScreenDecider.decide(in));

        in.nextFromPlaylist = true; // and it came from a playlist
        assertDecision(Kind.COUNTDOWN, 3, EndScreenDecider.decide(in));
    }

    @Test
    public void aKnownNextAlwaysBeatsThePendingFlag() {
        Input in = recommended();
        in.nextPending = true;

        assertDecision(Kind.COUNTDOWN, 8, EndScreenDecider.decide(in));
    }

    @Test
    public void nothingIsPendingAndNothingIsNextMeansTheOldBehaviour() {
        Input in = pending();
        in.nextPending = false; // the suggestions are in and there is no next video

        assertEquals(Kind.LEGACY, EndScreenDecider.decide(in).kind);
    }

    @Test
    public void pendingNextKeepsTheOffSwitchesOfTheEndScreen() {
        Input in = pending();
        in.settings = EndScreenSettings.defaults().withEnabled(false);
        assertEquals(Kind.LEGACY, EndScreenDecider.decide(in).kind);

        in = pending();
        in.embed = true;
        assertEquals(Kind.LEGACY, EndScreenDecider.decide(in).kind);

        in = pending();
        in.childMode = true;
        assertEquals(Kind.LEGACY, EndScreenDecider.decide(in).kind);
    }

    @Test
    public void pendingNextOnlyWaitsInTheModesThatMoveOnByThemselves() {
        int[] modesThatStay = {
                PlayerConstants.PLAYBACK_MODE_ONE, PlayerConstants.PLAYBACK_MODE_LIST,
                PlayerConstants.PLAYBACK_MODE_CLOSE, PlayerConstants.PLAYBACK_MODE_PAUSE};

        for (int mode : modesThatStay) {
            Input in = pending();
            in.playbackMode = mode;
            assertEquals("mode " + mode, Kind.LEGACY, EndScreenDecider.decide(in).kind);
        }

        int[] modesThatMove = {
                PlayerConstants.PLAYBACK_MODE_ALL, PlayerConstants.PLAYBACK_MODE_SHUFFLE,
                PlayerConstants.PLAYBACK_MODE_REVERSE_LIST};

        for (int mode : modesThatMove) {
            Input in = pending();
            in.playbackMode = mode;
            assertEquals("mode " + mode, Kind.WAIT_FOR_NEXT, EndScreenDecider.decide(in).kind);
        }
    }

    @Test
    public void pendingNextWithNobodyToSeeTheCardAdvancesLikeBefore() {
        Input in = pending();
        in.inPip = true;
        assertEquals(Kind.ADVANCE_NOW, EndScreenDecider.decide(in).kind);

        in = pending();
        in.backgroundAudio = true;
        assertEquals(Kind.ADVANCE_NOW, EndScreenDecider.decide(in).kind);

        in = pending();
        in.remote = true;
        assertEquals(Kind.ADVANCE_NOW, EndScreenDecider.decide(in).kind);
    }

    @Test
    public void pendingNextWithEveryCountdownAtZeroAdvancesAtOnce() {
        Input in = pending();
        in.settings = EndScreenSettings.defaults().withRelatedCountdownSec(0).withPlaylistCountdownSec(0);

        assertEquals(Kind.ADVANCE_NOW, EndScreenDecider.decide(in).kind);
    }

    @Test
    public void pendingNextStillWaitsWhenOnlyOneKindOfCountdownIsZero() {
        // the next one may turn out to be a playlist video (3 s) even if recommendations are 0 s
        Input in = pending();
        in.settings = EndScreenSettings.defaults().withRelatedCountdownSec(0);

        assertEquals(Kind.WAIT_FOR_NEXT, EndScreenDecider.decide(in).kind);
    }

    // --- shorts feed

    @Test
    public void aShortFromTheShortsFeedKeepsTheOldBehaviour() {
        Input in = recommended();
        in.shortsFeed = true;
        assertEquals(Kind.LEGACY, EndScreenDecider.decide(in).kind);

        in = playlist();
        in.shortsFeed = true;
        assertEquals(Kind.LEGACY, EndScreenDecider.decide(in).kind);
    }

    @Test
    public void aShortFromTheShortsFeedDoesNotWaitForTheNextEither() {
        Input in = pending();
        in.shortsFeed = true;

        assertEquals(Kind.LEGACY, EndScreenDecider.decide(in).kind);
    }
}
