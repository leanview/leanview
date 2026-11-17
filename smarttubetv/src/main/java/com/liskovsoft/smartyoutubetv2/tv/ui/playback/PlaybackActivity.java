package com.liskovsoft.smartyoutubetv2.tv.ui.playback;

import android.annotation.TargetApi;
import android.app.PictureInPictureParams;
import android.os.Build;
import android.os.Bundle;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

import androidx.fragment.app.Fragment;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.leanview.BackgroundPlayback;
import com.liskovsoft.smartyoutubetv2.common.leanview.BackgroundPlayback.FinishDecision;
import com.liskovsoft.smartyoutubetv2.common.leanview.BackgroundPlayback.HomeDecision;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;

/**
 * Loads PlaybackFragment and delegates input from a game controller.
 * <br>
 * For more information on game controller capabilities with leanback, review the
 * <a href="https://developer.android.com/training/game-controllers/controller-input.html">docs</href>.
 */
public class PlaybackActivity extends LeanbackActivity {
    private static final String TAG = PlaybackActivity.class.getSimpleName();
    private static final float GAMEPAD_TRIGGER_INTENSITY_ON = 0.5f;
    // Off-condition slightly smaller for button debouncing.
    private static final float GAMEPAD_TRIGGER_INTENSITY_OFF = 0.45f;
    private boolean gamepadTriggerPressed = false;
    private PlaybackFragment mPlaybackFragment;
    // leanview: closing the corner window must stop the video. The engine stays blocked for the
    // window, so without this its sound kept playing with no picture (2026-11-01).
    private boolean mIsStopped;
    private boolean mWasInPip;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_playback);
        Fragment fragment =
                getSupportFragmentManager().findFragmentByTag(getString(R.string.playback_tag));
        if (fragment instanceof PlaybackFragment) {
            mPlaybackFragment = (PlaybackFragment) fragment;
        }
    }

    @Override
    protected void initTheme() {
        int playerThemeResId = MainUIData.instance(this).getColorScheme().playerThemeResId;
        if (playerThemeResId > 0) {
            setTheme(playerThemeResId);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (mPlaybackFragment != null) {
            mPlaybackFragment.onDispatchKeyEvent(event);

            // leanview: the "Up next" end screen takes its keys first (BACK cancels the countdown
            // and must not also close the player)
            if (mPlaybackFragment.onEndScreenKey(event)) {
                return true;
            }
        }

        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (mPlaybackFragment != null) {
            mPlaybackFragment.onDispatchTouchEvent(event);
        }

        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (mPlaybackFragment != null) {
            mPlaybackFragment.onDispatchGenericMotionEvent(event);
        }

        return super.dispatchGenericMotionEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BUTTON_R1) {
            mPlaybackFragment.skipToNext();
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_BUTTON_L1) {
            mPlaybackFragment.skipToPrevious();
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_BUTTON_L2) {
            mPlaybackFragment.rewind();
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_BUTTON_R2) {
            mPlaybackFragment.fastForward();
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        // This method will handle gamepad events.
        if (event.getAxisValue(MotionEvent.AXIS_LTRIGGER) > GAMEPAD_TRIGGER_INTENSITY_ON
                && !gamepadTriggerPressed) {
            mPlaybackFragment.rewind();
            gamepadTriggerPressed = true;
        } else if (event.getAxisValue(MotionEvent.AXIS_RTRIGGER) > GAMEPAD_TRIGGER_INTENSITY_ON
                && !gamepadTriggerPressed) {
            mPlaybackFragment.fastForward();
            gamepadTriggerPressed = true;
        } else if (event.getAxisValue(MotionEvent.AXIS_LTRIGGER) < GAMEPAD_TRIGGER_INTENSITY_OFF
                && event.getAxisValue(MotionEvent.AXIS_RTRIGGER) < GAMEPAD_TRIGGER_INTENSITY_OFF) {
            gamepadTriggerPressed = false;
        } else if ((event.getSource() & InputDevice.SOURCE_CLASS_POINTER) != 0 && event.getAction() == MotionEvent.ACTION_SCROLL) {
            // mouse wheel handling
            Utils.volumeUp(this, getPlaybackView(), event.getAxisValue(MotionEvent.AXIS_VSCROLL) < 0.0f);
            return true;
        }
        return super.onGenericMotionEvent(event);
    }

    // For N devices that support it, not "officially"
    // More: https://medium.com/s23nyc-tech/drop-in-android-video-exoplayer2-with-picture-in-picture-e2d4f8c1eb30
    @TargetApi(24)
    @SuppressWarnings("deprecation")
    private void enterPipMode() {
        // NOTE: When exiting PIP mode onPause is called immediately after onResume

        // Also, avoid enter pip on stop!
        // More info: https://developer.android.com/guide/topics/ui/picture-in-picture#continuing_playback

        if (Helpers.isPictureInPictureSupported(this)) {
            if (wannaEnterToPip()) {
                Log.d(TAG, "Entering PIP mode...");

                try {
                    if (Build.VERSION.SDK_INT >= 26) {
                        PictureInPictureParams.Builder params = new PictureInPictureParams.Builder();
                        enterPictureInPictureMode(params.build());
                    } else {
                        enterPictureInPictureMode();
                    }
                } catch (Exception e) {
                    // Device doesn't support picture-in-picture mode
                    Log.e(TAG, e.getMessage());
                }
            }
        }
    }

    /**
     * BACK pressed, PIP player's button pressed
     */
    @Override
    public void finish() {
        Log.d(TAG, "Finishing activity...");

        //if (isBackgroundBackEnabled()) {
        //    mPlaybackFragment.blockEngine(true);
        //}

        // NOTE: When exiting PIP mode onPause is called immediately after onResume

        // Also, avoid enter pip on stop!
        // More info: https://developer.android.com/guide/topics/ui/picture-in-picture#continuing_playback

        // leanview: only the PIP button and BACK may keep the video going, and BACK follows the profile's
        // choice. Any other finish() (stop key, remote stop, embed errors) closes it.
        boolean pipButton = isPipButtonFinish();
        FinishDecision decision = BackgroundPlayback.decideFinish(isBackPressed(), pipButton,
                getPlayerData().getBackAction(), Helpers.isPictureInPictureSupported(this));

        if (decision.enterPip) {
            enterPipMode(); // NOTE: without this call app will hangs when pressing on PIP button
        }

        // The PIP button keeps the sound even where the window could not be entered, as it always did
        if (decision.keepEngine && (!decision.enterPip || pipButton || isInPipMode())) {
            mPlaybackFragment.blockEngine(true);
            // Ensure to opening this activity when the user is returning to the app
            getViewManager().blockTop(this);
            getViewManager().startParentView(this);
        } else {
            // The video is going away: a blocked engine would keep its sound
            mPlaybackFragment.blockEngine(false);

            if (getPlayerTweaksData().isKeepFinishedActivityEnabled()) {
                //moveTaskToBack(true); // Don't do this or you'll have problems when player overlaps other apps (e.g. casting)
                getViewManager().startParentView(this);

                // Player with TextureView keeps running in background because onStop() fired with huge delay (~5sec).
                mPlaybackFragment.maybeReleasePlayer();
            } else {
                super.finish();
            }
        }
    }

    @Override
    public void finishReally() {
        super.finishReally();

        mPlaybackFragment.onFinish();
    }

    @Override
    protected void onPause() {
        boolean hasDialogBug = AppDialogPresenter.instance(this).isDialogShown() && Build.VERSION.SDK_INT <= 23;
        boolean isScreenOff = BackgroundPlayback.keepOnScreenOff(getPlayerData().getBackAction(), getPlayerData().getHomeAction(),
                Helpers.isPictureInPictureSupported(this)) && Utils.isHardScreenOff(this);

        if (hasDialogBug || isScreenOff) {
            mPlaybackFragment.blockEngine(true);
        }

        // Run the code before the contained fragment
        super.onPause();

        // leanview: a Back that kept the video must not outlive this pause, or a later stop (phone cast,
        // embed error) would still be taken for that Back and keep playing. onUserLeaveHint runs before this.
        resetBackState();
    }

    @SuppressWarnings("deprecation")
    private void enterBackgroundPlayMode() {
        if (Build.VERSION.SDK_INT >= 21 && Build.VERSION.SDK_INT < 26) {
            if (Build.VERSION.SDK_INT == 21) {
                // Playback pause fix?
                mPlaybackFragment.showOverlay(true);
            }

            if (mPlaybackFragment.isPlaying()) {
                // Argument equals true to notify the system that the activity
                // wishes to be visible behind other translucent activities
                if (!requestVisibleBehind(true)) {
                    // App-specific method to stop playback and release resources
                    // because call to requestVisibleBehind(true) failed
                    mPlaybackFragment.onDestroy();
                }
            } else {
                // Argument equals false because the activity is not playing
                requestVisibleBehind(false);
            }
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onVisibleBehindCanceled() {
        // App-specific method to stop playback and release resources
        mPlaybackFragment.onDestroy();
        super.onVisibleBehindCanceled();
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode);

        mPlaybackFragment.onPIPChanged(isInPictureInPictureMode);

        if (isInPictureInPictureMode) {
            mWasInPip = true;
        } else if (mIsStopped) {
            // Out of the window while not visible: the user closed it (expanding it starts the activity)
            closeDismissedPip();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        mIsStopped = false;
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Expanded back to full screen: the window is gone but the video goes on
        if (!isInPipMode()) {
            mWasInPip = false;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        mIsStopped = true;

        // Some versions stop the activity after leaving the window instead of before
        if (mWasInPip && !isInPipMode()) {
            closeDismissedPip();
        }
    }

    private void closeDismissedPip() {
        if (!mWasInPip) {
            return;
        }

        Log.d(TAG, "PIP window closed by the user. Stopping the playback...");
        mWasInPip = false;
        getViewManager().blockTop(null);
        mPlaybackFragment.blockEngine(false);
        // leanview: the window was closed from another app or the launcher: the player goes away there,
        // SmartTube is not brought to the front over it (it was, through finishReally()'s parent view)
        finishInBackground();
        mPlaybackFragment.onFinish();
    }

    /**
     * HOME or BACK pressed
     */
    @Override
    public void onUserLeaveHint() {
        // Check that user not open dialog/search activity instead of really leaving the activity
        // Activity may be overlapped by the dialog, back is pressed or new view started
        if (isBackPressed() || isFinishing() || getViewManager().isNewViewPending()) {
            return;
        }

        HomeDecision decision = BackgroundPlayback.decideHome(getPlayerData().getHomeAction(), Helpers.isPictureInPictureSupported(this));

        if (decision.playBehind) {
            enterBackgroundPlayMode();
            // Do we need to do something additional when running Play Behind?
        } else if (decision.enterPip) {
            enterPipMode();
            if (doNotDestroy(false)) {
                mPlaybackFragment.blockEngine(true);
                // Ensure to opening this activity when the user will return to the app
                getViewManager().blockTop(this);
                // Enable collapse app to Home launcher
                //getViewManager().enableMoveToBack(true);
            }
        } else if (decision.keepEngine) {
            if (doNotDestroy(true)) {
                // Ensure to continue a playback
                mPlaybackFragment.blockEngine(true);
                getViewManager().blockTop(this);
                //getViewManager().enableMoveToBack(true);
            }
        }
    }

    public boolean isInPipMode() {
        if (Build.VERSION.SDK_INT < 24) {
            return false;
        }

        return isInPictureInPictureMode();
    }

    public PlaybackView getPlaybackView() {
        return mPlaybackFragment;
    }

    /**
     * leanview: the PIP button blocks the engine and then calls finish() while the player is on screen.
     * An engine blocked while the activity is stopped or in the window came from HOME or the screen
     * turning off, not from the button.
     */
    private boolean isPipButtonFinish() {
        return isEngineBlocked() && !mIsStopped && !isInPipMode();
    }

    private boolean isEngineBlocked() {
        return mPlaybackFragment != null && mPlaybackFragment.isEngineBlocked();
    }

    @TargetApi(24)
    private boolean wannaEnterToPip() {
        //return mPlaybackFragment != null && mPlaybackFragment.getBackgroundMode() == PlayerEngine.BACKGROUND_MODE_PIP && !isInPictureInPictureMode();
        //return mPlaybackFragment != null && mPlaybackFragment.isEngineBlocked() && !isInPictureInPictureMode();
        // leanview: the caller already decided that the video goes to the corner window
        return !isInPictureInPictureMode();
    }

    /**
     * @param keepByChoice the profile chose to keep playing only the audio for the key that was pressed
     */
    private boolean doNotDestroy(boolean keepByChoice) {
        sIsInPipMode = isInPipMode();
        return sIsInPipMode || keepByChoice || isEngineBlocked();
    }

    //private boolean isBackgroundBackEnabled() {
    //    return getGeneralData().getBackgroundPlaybackShortcut() == GeneralData.BACKGROUND_PLAYBACK_SHORTCUT_BACK ||
    //            getGeneralData().getBackgroundPlaybackShortcut() == GeneralData.BACKGROUND_PLAYBACK_SHORTCUT_HOME_BACK;
    //}
}
