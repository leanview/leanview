package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

import android.os.SystemClock;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Playlist;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.BasePlayerController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerConstants;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.leanview.EndScreenCountdown;
import com.liskovsoft.smartyoutubetv2.common.leanview.EndScreenCountdown.State;
import com.liskovsoft.smartyoutubetv2.common.leanview.EndScreenDecider;
import com.liskovsoft.smartyoutubetv2.common.leanview.EndScreenDecider.Decision;
import com.liskovsoft.smartyoutubetv2.common.leanview.EndScreenDecider.Input;
import com.liskovsoft.smartyoutubetv2.common.leanview.EndScreenHost;
import com.liskovsoft.smartyoutubetv2.common.leanview.EndScreenKeys.Action;
import com.liskovsoft.smartyoutubetv2.common.leanview.EndScreenKeys.Mode;
import com.liskovsoft.smartyoutubetv2.common.leanview.EndScreenModel;
import com.liskovsoft.smartyoutubetv2.common.leanview.EndScreenWait;
import com.liskovsoft.smartyoutubetv2.common.leanview.EndScreenWait.Outcome;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

/**
 * leanview: the "Up next" end screen. When a video ends and the player would jump to the next one,
 * VideoLoaderController asks {@link #onAutoAdvance} first. Here the decision is made
 * ({@link EndScreenDecider}); the answer is either "go on as before" (false) or "I took over" (true):
 * a card with the next video, a countdown (or a wait for a choice, or "still watching?"), and the
 * player's own related row below. When the time is up, or "play now" is pressed, this controller
 * calls loadNext() / loadPrevious() itself.
 * <br>
 * The engine reports one ending several times, so every report after the first (while counting,
 * cancelled or fired) is swallowed. It starts again only after a new video, a replay, or a seek.
 * <br>
 * When the video ends before its suggestions have loaded (a short one), the next video is not known
 * yet: the card opens as "looking for the next one" (Mode.WAITING) and polls; when the next video
 * arrives, the same decider chooses between the countdown and the rest ({@link EndScreenWait}).
 */
public class EndScreenController extends BasePlayerController {
    private static final long TICK_MS = 1_000;
    private static final long POLL_MS = 500;
    private final EndScreenCountdown mCountdown = new EndScreenCountdown();
    private final Runnable mTick = this::onTick;
    private final Runnable mPoll = this::onPoll;
    private Mode mMode; // null: no screen
    private Video mNext;
    private boolean mPrevious;
    private int mTotalSeconds;
    private long mLastTickMs;
    private long mWaitStartMs;
    private int mWaitPlaybackMode;
    private long mLastInteractionMs = SystemClock.elapsedRealtime();

    // ---- the seam used by VideoLoaderController

    /**
     * @return true when the end screen took over (or this ending was already handled), false when
     * the player must do what it always did
     */
    public boolean onAutoAdvance(int playbackMode, boolean previous) {
        Video video = getVideo();
        PlaybackView player = getPlayer();

        if (player == null || video == null) {
            return false;
        }

        if (mCountdown.state() != State.IDLE) {
            return true; // the same ending reported again
        }

        SuggestionsController suggestions = getController(SuggestionsController.class);

        if (!(player instanceof EndScreenHost) || suggestions == null) {
            return false;
        }

        Video next = previous ? suggestions.getPrevious() : suggestions.getNext();
        Decision decision = EndScreenDecider.decide(createInput(playbackMode, previous, video, next, player));

        switch (decision.kind) {
            case COUNTDOWN:
                begin(Mode.COUNTDOWN, next, previous, decision.seconds);
                return true;
            case WAIT_MANUAL:
                begin(Mode.MANUAL, next, previous, 0);
                return true;
            case STILL_WATCHING:
                begin(Mode.STILL_WATCHING, next, previous, 0);
                return true;
            case WAIT_FOR_NEXT:
                mWaitPlaybackMode = playbackMode;
                mWaitStartMs = SystemClock.elapsedRealtime();
                begin(Mode.WAITING, null, previous, 0);
                Utils.postDelayed(mPoll, POLL_MS);
                return true;
            default: // LEGACY and ADVANCE_NOW: the old way is already "go now"
                return false;
        }
    }

    private Input createInput(int playbackMode, boolean previous, Video video, Video next, PlaybackView player) {
        boolean queueNext = Playlist.instance().getNext() != null;
        boolean sameList = video.hasNextPlaylist();
        // The next one is neither the suggestion of the metadata nor from a list: it comes from the section it was opened from
        boolean fromSection = next != null && !queueNext && !sameList && video.nextMediaItem != null
                && !Helpers.equals(next.videoId, video.nextMediaItem.getVideoId());

        Input in = new Input();
        in.settings = getPlayerData().getEndScreen();
        in.playbackMode = playbackMode;
        in.embed = isEmbedPlayer();
        in.childMode = getGeneralData().isChildModeEnabled();
        in.remote = video.isRemote;
        in.inPip = player.isInPIPMode();
        in.backgroundAudio = player.isEngineBlocked();
        in.hasNext = next != null;
        in.hasQueueNext = queueNext && (playbackMode == PlayerConstants.PLAYBACK_MODE_LIST || !getPlayerTweaksData().isQueueRespectsPlaybackMode());
        in.hasPlaylistNext = sameList;
        in.nextFromPlaylist = previous || EndScreenDecider.isPlaylistSource(queueNext, sameList, fromSection);
        in.idleMs = SystemClock.elapsedRealtime() - mLastInteractionMs;
        // The Shorts feed (a short opened from the Shorts section) moves on by itself, with no screen.
        // A looped short never gets here: getPlaybackMode() turns it into mode ONE, which does not advance.
        in.shortsFeed = video.isShorts && video.belongsToShorts();
        // Nothing is known about the next video yet, but the suggestions are still loading
        in.nextPending = !previous && next == null && !video.isSynced && video.nextMediaItem == null;
        return in;
    }

    private void begin(Mode mode, Video next, boolean previous, int seconds) {
        Utils.removeCallbacks(mPoll);
        mMode = mode;
        mNext = next;
        mPrevious = previous;
        mTotalSeconds = seconds;
        mCountdown.start(mode == Mode.COUNTDOWN ? seconds * 1_000L : EndScreenCountdown.NO_TIMER);
        mLastTickMs = SystemClock.elapsedRealtime();

        render();

        if (mode == Mode.COUNTDOWN) {
            Utils.postDelayed(mTick, TICK_MS);
        }
    }

    // ---- the clock

    private void onTick() {
        PlaybackView player = getPlayer();

        if (mMode != Mode.COUNTDOWN || mCountdown.state() != State.COUNTING) {
            return;
        }

        if (player == null) {
            releaseAndHide();
            return;
        }

        long now = SystemClock.elapsedRealtime();
        long delta = now - mLastTickMs;
        mLastTickMs = now;

        // Nobody can see the card (corner window, only the sound): go on as the old player did
        if (player.isInPIPMode() || player.isEngineBlocked()) {
            if (mCountdown.fireNow()) {
                advance();
            }
            return;
        }

        // The card is not on screen (its view is gone or was closed): a countdown nobody can see must never
        // load a video. Hold, as if cancelled; a new video or "play now" is the only way on.
        EndScreenHost host = getHost();

        if (host != null && !host.isEndScreenShown()) {
            holdUnseen();
            return;
        }

        AppDialogPresenter dialog = getAppDialogPresenter();
        mCountdown.setPaused(EndScreenCountdown.PAUSE_DIALOG, dialog.isDialogShown() && !dialog.isOverlay());

        if (mCountdown.tick(delta)) {
            advance();
            return;
        }

        render();
        Utils.postDelayed(mTick, TICK_MS);
    }

    // ---- waiting for the next video (it was not known when the video ended)

    private void onPoll() {
        PlaybackView player = getPlayer();
        Video video = getVideo();
        SuggestionsController suggestions = getController(SuggestionsController.class);

        if (mMode != Mode.WAITING || mCountdown.state() != State.COUNTING) {
            return;
        }

        if (player == null || video == null || suggestions == null) {
            releaseAndHide();
            return;
        }

        Video next = suggestions.getNext();
        Decision decision = EndScreenDecider.decide(createInput(mWaitPlaybackMode, false, video, next, player));
        long waitedMs = SystemClock.elapsedRealtime() - mWaitStartMs;
        Outcome outcome = EndScreenWait.afterPoll(decision, next != null, waitedMs);

        switch (outcome) {
            case SHOW:
                mCountdown.reset(); // a new card: the clock starts from the beginning
                showDecision(decision, next);
                break;
            case ADVANCE:
                if (mCountdown.fireNow()) {
                    advance();
                }
                break;
            case GIVE_UP:
                dismiss(); // nothing is going to arrive: leave the video at its end
                break;
            case KEEP_WAITING:
            default:
                Utils.postDelayed(mPoll, POLL_MS);
                break;
        }
    }

    private void showDecision(Decision decision, Video next) {
        switch (decision.kind) {
            case COUNTDOWN:
                begin(Mode.COUNTDOWN, next, false, decision.seconds);
                break;
            case STILL_WATCHING:
                begin(Mode.STILL_WATCHING, next, false, 0);
                break;
            default: // WAIT_MANUAL
                begin(Mode.MANUAL, next, false, 0);
                break;
        }
    }

    private void advance() {
        // The only ways here fire the countdown first. A cancelled, idle or already fired one never loads a video.
        if (mCountdown.state() != State.FIRED) {
            return;
        }

        Utils.removeCallbacks(mTick);
        boolean previous = mPrevious;
        PlaybackView player = getPlayer();
        hide();

        if (player != null) {
            player.showOverlay(false); // the related row was opened by the end screen
        }

        VideoLoaderController loader = getController(VideoLoaderController.class);

        if (loader == null) {
            return;
        }

        if (previous) {
            loader.loadPrevious();
        } else {
            loader.loadNext();
        }
    }

    // ---- what the person does on the screen (called by the view)

    public void onEndScreenAction(Action action) {
        onUserInteraction();

        switch (action) {
            case PLAY_NOW:
            case STILL_YES:
                if (mCountdown.fireNow()) {
                    advance();
                }
                break;
            case CANCEL:
            case STILL_NO:
                if (mMode == Mode.WAITING) {
                    dismiss(); // there is no next video to hold on to: just close the card
                    break;
                }

                Utils.removeCallbacks(mTick);
                mCountdown.cancel();
                mMode = Mode.MANUAL;
                render();
                break;
            case REPLAY:
                replay();
                break;
            case DISMISS:
                dismiss();
                break;
            default:
                break;
        }
    }

    /** The focus went to a related video (the countdown holds) or came back to the card. */
    public void onEndScreenRelatedFocus(boolean focused) {
        mCountdown.setPaused(EndScreenCountdown.PAUSE_FOCUS, focused);

        if (mMode == Mode.COUNTDOWN) {
            render();
        }
    }

    /** Any key press: the proof that someone is watching. */
    public void onUserInteraction() {
        mLastInteractionMs = SystemClock.elapsedRealtime();
    }

    private void replay() {
        PlaybackView player = getPlayer();
        resetAndHide();

        if (player != null) {
            player.setPositionMs(0);
            player.setPlayWhenReady(true);
            player.showOverlay(false);
        }
    }

    private void dismiss() {
        PlaybackView player = getPlayer();
        Utils.removeCallbacks(mTick);
        mCountdown.cancel(); // stays cancelled: the same ending must not bring the card back
        hide();

        if (player != null) {
            player.showOverlay(false);
        }
    }

    // ---- showing and hiding

    private void render() {
        EndScreenHost host = getHost();

        if (host == null || mMode == null) {
            return;
        }

        host.showEndScreen(new EndScreenModel(
                mMode,
                mNext != null && mNext.getTitle() != null ? mNext.getTitle() : "",
                mNext != null && mNext.getAuthor() != null ? mNext.getAuthor() : "",
                mNext != null ? mNext.getCardImageUrl() : null,
                mCountdown.remainingSeconds(),
                mTotalSeconds,
                mCountdown.isPaused(),
                getPlayerData().getEndScreen().defaultFocus));
    }

    private void hide() {
        Utils.removeCallbacks(mPoll);
        mMode = null;
        mNext = null;
        EndScreenHost host = getHost();

        if (host != null) {
            host.hideEndScreen();
        }
    }

    private void resetAndHide() {
        Utils.removeCallbacks(mTick);
        mCountdown.reset();
        hide();
    }

    /**
     * The screen goes away without the person deciding anything (view destroyed, engine released, activity
     * finished): a cancel (BACK) is kept, so the same ending reported again cannot start a new countdown.
     */
    private void releaseAndHide() {
        Utils.removeCallbacks(mTick);
        mCountdown.softReset();
        hide();
    }

    private void holdUnseen() {
        Utils.removeCallbacks(mTick);
        mCountdown.cancel();
        hide();
    }

    private EndScreenHost getHost() {
        PlaybackView player = getPlayer();
        return player instanceof EndScreenHost ? (EndScreenHost) player : null;
    }

    // ---- events that end or change the situation

    @Override
    public void onNewVideo(Video item) {
        resetAndHide();
    }

    @Override
    public void onEngineReleased() {
        releaseAndHide();
    }

    @Override
    public void onViewDestroyed() {
        releaseAndHide();
    }

    @Override
    public void onFinish() {
        PlaybackView player = getPlayer();

        // BACK that keeps only the sound: nobody is going to press anything, so the countdown still goes on
        if (mMode == Mode.COUNTDOWN && mCountdown.state() == State.COUNTING && player != null && player.isEngineBlocked()) {
            if (mCountdown.fireNow()) {
                advance();
            }
            return;
        }

        releaseAndHide();
    }

    @Override
    public void onViewCreated() {
        onUserInteraction();
    }

    @Override
    public void onViewResumed() {
        onUserInteraction();
    }

    @Override
    public void onPlay() {
        // The video plays again (replay, seek, or the stream came back): the ending is over.
        // A "playing" report while the position is still at the end is not that (it must not wipe a cancel).
        PlaybackView player = getPlayer();

        if (mCountdown.state() != State.IDLE && !(player != null && EndScreenCountdown.isAtEnd(player.getPositionMs(), player.getDurationMs()))) {
            resetAndHide();
        }
    }

    @Override
    public void onPlayClicked() {
        if (mCountdown.state() != State.IDLE) {
            resetAndHide();
        }
    }

    @Override
    public void onSeekEnd() {
        PlaybackView player = getPlayer();

        if (mCountdown.state() != State.IDLE && player != null && player.getPositionMs() < player.getDurationMs() - 500) {
            resetAndHide();
        }
    }
}
