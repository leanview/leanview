/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.tweaks;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.ColorInt;
import androidx.leanback.R;
import androidx.leanback.widget.AbstractDetailsDescriptionPresenter;
import androidx.leanback.widget.Action;
import androidx.leanback.widget.ObjectAdapter;
import androidx.leanback.widget.OnActionClickedListener;
import androidx.leanback.widget.PlaybackControlsRow;
import androidx.leanback.widget.PlaybackRowPresenter;
import androidx.leanback.widget.PlaybackSeekDataProvider;
import androidx.leanback.widget.PlaybackSeekUi;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.RowPresenter;

import com.liskovsoft.sharedutils.helpers.DateHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.SeekBarSegment;
import com.liskovsoft.smartyoutubetv2.common.leanview.SeekThumbs;
import com.liskovsoft.smartyoutubetv2.common.leanview.SeekTouch;
import com.liskovsoft.smartyoutubetv2.tv.ui.leanview.PhoneOsdLayout;
import com.liskovsoft.smartyoutubetv2.tv.ui.leanview.PhoneTouch;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.misc.SeekBar;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.tooltips.ControlButtonPresenterSelector;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.tweaks.ControlBarPresenter.OnControlClickedListener;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.tweaks.ControlBarPresenter.OnControlLongClickedListener;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.tweaks.ControlBarPresenter.OnControlSelectedListener;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.seekpreview.ThumbsBar;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.widget.OnActionLongClickedListener;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.List;

/**
 * A PlaybackTransportRowPresenter renders a {@link PlaybackControlsRow} to display a
 * series of playback control buttons. Typically this row will be the first row in a fragment
 * such as the {@link androidx.leanback.app.PlaybackSupportFragment}.
 *
 * <p>The detailed description is rendered using a {@link Presenter} passed in
 * {@link #setDescriptionPresenter(Presenter)}.  This can be an instance of
 * {@link AbstractDetailsDescriptionPresenter}.  The application can access the
 * detailed description ViewHolder from {@link ViewHolder#getDescriptionViewHolder()}.
 * </p>
 */
public class PlaybackTransportRowPresenter extends PlaybackRowPresenter {
    static class BoundData extends PlaybackControlsPresenter.BoundData {
        ViewHolder mRowViewHolder;
    }

    public interface TopEdgeFocusListener {
        void onTopEdgeFocused();
    }

    /**
     * A ViewHolder for the PlaybackControlsRow supporting seek UI.
     */
    public class ViewHolder extends PlaybackRowPresenter.ViewHolder implements PlaybackSeekUi {
        private static final long SPEED_INCREASE_PERIOD_MS = 1000;
        private static final double SPEED_INCREASE_FACTOR = 1.5;
        private static final int CONTROLS_MODE_FULL = 0;
        private static final int CONTROLS_MODE_COMPACT = 1;
        final Presenter.ViewHolder mDescriptionViewHolder;
        final ImageView mImageView;
        final ViewGroup mDescriptionDock;
        final ViewGroup mControlsDock;
        final ViewGroup mSecondaryControlsDock;
        final TextView mTotalTime;
        final TextView mCurrentTime;
        final TextView mEndingTime;
        final TextView mQualityInfo;
        final TextView mDateTime;
        final ViewGroup mAdditionalInfo;
        final ViewGroup mTimeInfo;
        final ViewGroup mTopEdge;
        final SeekBar mProgressBar;
        final ThumbsBar mThumbsBar;
        final TextView mThumbsBarTitle;
        final ViewGroup mThumbsBarWrapper;
        final String mRemainingTimeFormat;
        final String mEndingTimeFormat;
        long mTotalTimeInMs = Long.MIN_VALUE;
        long mCurrentTimeInMs = Long.MIN_VALUE;
        long mRemainingTimeInMs = Long.MIN_VALUE;
        long mSecondaryProgressInMs;
        final StringBuilder mTempBuilder = new StringBuilder();
        ControlBarPresenter.ViewHolder mControlsVh;
        ControlBarPresenter.ViewHolder mSecondaryControlsVh;
        BoundData mControlsBoundData = new BoundData();
        BoundData mSecondaryBoundData = new BoundData();
        Presenter.ViewHolder mSelectedViewHolder;
        Object mSelectedItem;
        PlaybackControlsRow.PlayPauseAction mPlayPauseAction;
        int mThumbHeroIndex = -1;

        Client mSeekClient;
        boolean mInSeek;
        // leanview: hide the seek preview thumbnails shortly after the seek is committed (they used to
        // stay until the whole overlay faded out). Pure timing logic lives in SeekThumbs.
        // 1 s, not the 400 ms default: on the Streamer a tap's frame (a whole storyboard sheet to fetch and
        // decode) arrives after 400 ms, so with taps the preview was never seen (measured on the TV 2026-11-02).
        final SeekThumbs mSeekThumbs = new SeekThumbs(1000, SeekThumbs.DEFAULT_LOST_KEY_UP_MS);
        final Runnable mSeekThumbsTick = this::onSeekThumbsTick;
        int mThumbsGeneration;
        boolean mIsPlaying = true;
        PlaybackSeekDataProvider mSeekDataProvider;
        long[] mPositions;
        int mPositionsLength;
        long mSeekIncrementMs = -1;
        long mSeekStartTimeMs;
        final PlayerData mPlayerData;

        // MOD: update quality info
        private WeakReference<TopEdgeFocusListener> mTopEdgeFocusListener = null;

        final PlaybackControlsRow.OnPlaybackProgressCallback mListener =
                new PlaybackControlsRow.OnPlaybackProgressCallback() {
            @Override
            public void onCurrentPositionChanged(PlaybackControlsRow row, long ms) {
                setCurrentPosition(ms);
                setEndingTime(ms);
                updateTotalTime();
            }

            @Override
            public void onDurationChanged(PlaybackControlsRow row, long ms) {
                setTotalTime(ms);
            }

            @Override
            public void onBufferedPositionChanged(PlaybackControlsRow row, long ms) {
                setBufferedPosition(ms);
            }
        };

        void updateProgressInSeek(boolean forward) {
            long newPos;
            long pos = mCurrentTimeInMs;
            if (mPositionsLength > 0) {
                int index = Arrays.binarySearch(mPositions, 0, mPositionsLength, pos);
                int thumbHeroIndex;
                if (forward) {
                    if (index >= 0) {
                        // found it, seek to neighbour key position at higher side
                        if (index < mPositionsLength - 1) {
                            newPos = mPositions[index + 1];
                            thumbHeroIndex = index + 1;
                        } else {
                            newPos = mTotalTimeInMs;
                            thumbHeroIndex = index;
                        }
                    } else {
                        // not found, seek to neighbour key position at higher side.
                        int insertIndex = -1 - index;
                        if (insertIndex <= mPositionsLength - 1) {
                            newPos = mPositions[insertIndex];
                            thumbHeroIndex = insertIndex;
                        } else {
                            newPos = mTotalTimeInMs;
                            thumbHeroIndex = insertIndex > 0 ? insertIndex - 1 : 0;
                        }
                    }
                } else {
                    if (index >= 0) {
                        // found it, seek to neighbour key position at lower side.
                        if (index > 0) {
                            newPos = mPositions[index - 1];
                            thumbHeroIndex = index - 1;
                        } else {
                            newPos = 0;
                            thumbHeroIndex = 0;
                        }
                    } else {
                        // not found, seek to neighbour key position at lower side.
                        int insertIndex = -1 - index;

                        // MOD: Fix no seek backwards bug: keyframe is close to current position
                        if (insertIndex > 0) {
                            newPos = mPositions[insertIndex - 1];
                            if (Math.abs(mCurrentTimeInMs - newPos) < 3_000) {
                                insertIndex--;
                            }
                        }

                        if (insertIndex > 0) {
                            newPos = mPositions[insertIndex - 1];
                            thumbHeroIndex = insertIndex - 1;
                        } else {
                            newPos = 0;
                            thumbHeroIndex = 0;
                        }
                    }
                }
                updateThumbsInSeek(thumbHeroIndex, forward);
            } else {
                long interval = calculateSeekIncrement();
                newPos = pos + (forward ? interval : -interval);
                if (newPos > mTotalTimeInMs) {
                    newPos = mTotalTimeInMs;
                } else if (newPos < 0) {
                    newPos = 0;
                }
            }
            double ratio = (double) newPos / mTotalTimeInMs;     // Range: [0, 1]
            mProgressBar.setProgress((int) (ratio * Integer.MAX_VALUE)); // Could safely cast to int
            mSeekClient.onSeekPositionChanged(newPos);
        }

        /**
         * MOD: implement fast seeking for videos with storyboard (indexed thumbnails)
         */
        void updateProgressInSeekMod(boolean forward) {
            long newPos;
            long pos = mCurrentTimeInMs;

            // Use common seeking calculations for all cases
            long interval = calculateSeekIncrement();
            newPos = pos + (forward ? interval : -interval);
            if (newPos > mTotalTimeInMs) {
                newPos = mTotalTimeInMs;
            } else if (newPos < 0) {
                newPos = 0;
            }

            if (mPositionsLength > 0) {
                int thumbHeroIndex;
                int newPosIndex = Arrays.binarySearch(mPositions, 0, mPositionsLength, newPos);

                if (forward) {
                    if (newPosIndex >= 0) {
                        // found it, seek to neighbour key position at higher side
                        thumbHeroIndex = newPosIndex;
                    } else {
                        // not found, seek to neighbour key position at higher side.
                        int insertIndex = -newPosIndex;
                        if (insertIndex <= mPositionsLength - 1) {
                            thumbHeroIndex = insertIndex;
                        } else {
                            thumbHeroIndex = insertIndex > 0 ? insertIndex - 1 : 0;
                        }
                    }
                } else {
                    if (newPosIndex >= 0) {
                        // found it, seek to neighbour key position at lower side.
                        if (newPosIndex > 0) {
                            thumbHeroIndex = newPosIndex;
                        } else {
                            thumbHeroIndex = 0;
                        }
                    } else {
                        // not found, seek to neighbour key position at lower side.
                        int insertIndex = -newPosIndex;
                        if (insertIndex > 0) {
                            thumbHeroIndex = insertIndex - 1;
                        } else {
                            thumbHeroIndex = 0;
                        }
                    }
                }
                updateThumbsInSeek(forward ? thumbHeroIndex - 1 : thumbHeroIndex, forward);
            }

            double ratio = (double) newPos / mTotalTimeInMs;     // Range: [0, 1]
            mProgressBar.setProgress((int) (ratio * Integer.MAX_VALUE)); // Could safely cast to int
            mSeekClient.onSeekPositionChanged(newPos);
        }

        void resetSeekIncrement() {
            mSeekIncrementMs = -1;
        }

        /**
         * Implement non-linear seek speed<br/>
         * By increasing seek speed by 1.5 every 1 second.
         */
        long calculateSeekIncrement() {
            if (mSeekIncrementMs == -1) {
                mSeekStartTimeMs = System.currentTimeMillis();
                mSeekIncrementMs = mPlayerData.getSeekIncrementMs();
            } else {
                // increase seek speed by 1.5 every 1 second
                long timePassed = System.currentTimeMillis() - mSeekStartTimeMs;
                long timeFactor = timePassed / SPEED_INCREASE_PERIOD_MS;
                if (timeFactor == 1) {
                    mSeekStartTimeMs = System.currentTimeMillis();
                    mSeekIncrementMs *= SPEED_INCREASE_FACTOR;
                }
            }

            return mSeekIncrementMs;
        }

        void updateThumbsInSeek(int thumbHeroIndex, boolean forward) {
            if (mThumbHeroIndex == thumbHeroIndex) {
                return;
            }

            final int totalNum = mThumbsBar.getChildCount();
            if (totalNum < 0 || (totalNum & 1) == 0) {
                throw new RuntimeException();
            }
            final int heroChildIndex = totalNum / 2;
            final int start = Math.max(thumbHeroIndex - (totalNum / 2), 0);
            final int end = Math.min(thumbHeroIndex + (totalNum / 2), mPositionsLength - 1);
            final int newRequestStart;
            final int newRequestEnd;

            if (mThumbHeroIndex < 0) {
                // first time
                newRequestStart = start;
                newRequestEnd = end;
            } else {
                forward = thumbHeroIndex > mThumbHeroIndex;
                final int oldStart = Math.max(mThumbHeroIndex - (totalNum / 2), 0);
                final int oldEnd = Math.min(mThumbHeroIndex + (totalNum / 2),
                        mPositionsLength - 1);
                if (forward) {
                    newRequestStart = Math.max(oldEnd + 1, start);
                    newRequestEnd = end;
                    // overlapping area directly assign bitmap from previous result
                    for (int i = start; i <= newRequestStart - 1; i++) {
                        mThumbsBar.setThumbBitmap(heroChildIndex + (i - thumbHeroIndex),
                                mThumbsBar.getThumbBitmap(heroChildIndex + (i - mThumbHeroIndex)));
                    }
                } else {
                    newRequestEnd = Math.min(oldStart - 1, end);
                    newRequestStart = start;
                    // overlapping area directly assign bitmap from previous result in backward
                    for (int i = end; i >= newRequestEnd + 1; i--) {
                        mThumbsBar.setThumbBitmap(heroChildIndex + (i - thumbHeroIndex),
                                mThumbsBar.getThumbBitmap(heroChildIndex + (i - mThumbHeroIndex)));
                    }
                }
            }
            // processing new requests with mThumbHeroIndex updated
            mThumbHeroIndex = thumbHeroIndex;
            if (forward) {
                for (int i = newRequestStart; i <= newRequestEnd; i++) {
                    mSeekDataProvider.getThumbnail(i, mThumbResult);
                }
            } else {
                for (int i = newRequestEnd; i >= newRequestStart; i--) {
                    mSeekDataProvider.getThumbnail(i, mThumbResult);
                }
            }
            // set thumb bitmaps outside (start , end) to null
            for (int childIndex = 0; childIndex < heroChildIndex - mThumbHeroIndex + start;
                    childIndex++) {
                mThumbsBar.setThumbBitmap(childIndex, null);
            }
            for (int childIndex = heroChildIndex + end - mThumbHeroIndex + 1;
                    childIndex < totalNum; childIndex++) {
                mThumbsBar.setThumbBitmap(childIndex, null);
            }
        }

        PlaybackSeekDataProvider.ResultCallback mThumbResult = newThumbResult(0);

        // leanview: late Glide callbacks of an earlier seek must not replace what is on screen. A tap commits
        // the seek on key up, before its frame has loaded, so the latest seek's frame is still wanted while
        // the thumbs are visible (rejecting it whenever !mInSeek hid the preview for every tap, 2026-11-02);
        // the carousel needs mInSeek because its slot math uses mThumbHeroIndex, reset by stopSeek.
        PlaybackSeekDataProvider.ResultCallback newThumbResult(final int generation) {
            return new PlaybackSeekDataProvider.ResultCallback() {
                    @Override
                    public void onThumbnailLoaded(Bitmap bitmap, int index) {
                        boolean single = mThumbsBar.getChildCount() == 1;
                        if (!mSeekThumbs.isCurrentGeneration(generation) || mThumbsBarWrapper.getVisibility() != View.VISIBLE
                                || (!mInSeek && !single)) {
                            return;
                        }
                        int childIndex = mThumbsBar.getChildCount() == 1 ? // single frame carousel check
                                0 : index - (mThumbHeroIndex - mThumbsBar.getChildCount() / 2);
                        if (childIndex < 0 || childIndex >= mThumbsBar.getChildCount()) {
                            return;
                        }
                        mThumbsBar.setThumbBitmap(childIndex, bitmap);
                    }
            };
        }

        // leanview: the finger is at this progress of the bar: the knob and the time show it; the seek itself
        // happens on release (stopSeek), the same path the arrow keys use.
        long mTouchStartPositionMs; // leanview: where the playback was when the finger came down

        void touchSeekTo(int progress) {
            if (!mInSeek && !startSeek()) {
                return; // seeking is not possible now (e.g. a live stream with no rewind)
            }
            mSeekClient.onSeekPositionChanged(SeekTouch.positionMs(progress, Integer.MAX_VALUE, mTotalTimeInMs));
        }

        boolean onForward() {
            return onForward(true);
        }

        boolean onBackward() {
            return onForward(false);
        }

        boolean onForward(boolean forward) {
            if (!startSeek()) {
                return false;
            }

            switch (mPlayerData.getSeekPreviewMode()) {
                case PlayerData.SEEK_PREVIEW_CAROUSEL_SLOW:
                    // Calculate thumbs size based on width
                    mThumbsBar.setNumberOfThumbs(-1);
                    updateProgressInSeek(forward);
                    break;
                case PlayerData.SEEK_PREVIEW_CAROUSEL_FAST:
                    // Calculate thumbs size based on width
                    mThumbsBar.setNumberOfThumbs(-1);
                    updateProgressInSeekMod(forward);
                    break;
                default:
                    mThumbsBar.setNumberOfThumbs(1);
                    updateProgressInSeekMod(forward);
                    break;
            }

            mThumbsBar.setThumbsRotation(mPlayerData.getRotationAngle());

            return true;
        }

        /**
         * Constructor of ViewHolder of PlaybackTransportRowPresenter
         * @param rootView Root view of the ViewHolder.
         * @param descriptionPresenter The presenter that will be used to create description
         *                             ViewHolder. The description view will be added into tree.
         */
        public ViewHolder(View rootView, Presenter descriptionPresenter) {
            super(rootView);
            mPlayerData = PlayerData.instance(rootView.getContext());
            // MOD: switch between navigation modes
            PlayerTweaksData tweaksData = PlayerTweaksData.instance(rootView.getContext());
            PlaybackTransportRowPresenter.this.mPlaybackControlsPresenter.setSharedFocusEnabled(tweaksData.isSyncRowButtonIndexEnabled());
            PlaybackTransportRowPresenter.this.mSecondaryControlsPresenter.setSharedFocusEnabled(tweaksData.isSyncRowButtonIndexEnabled());
            mImageView = (ImageView) rootView.findViewById(R.id.image);
            mDescriptionDock = (ViewGroup) rootView.findViewById(R.id.description_dock);
            mCurrentTime = (TextView) rootView.findViewById(R.id.current_time);
            mTotalTime = (TextView) rootView.findViewById(R.id.total_time);
            mQualityInfo = (TextView) rootView.findViewById(com.liskovsoft.smartyoutubetv2.tv.R.id.quality_info);
            mDateTime = (TextView) rootView.findViewById(com.liskovsoft.smartyoutubetv2.tv.R.id.date_time);
            mEndingTime = (TextView) rootView.findViewById(com.liskovsoft.smartyoutubetv2.tv.R.id.ending_time);
            mEndingTimeFormat = rootView.getContext().getString(com.liskovsoft.smartyoutubetv2.tv.R.string.player_ending_time);
            mRemainingTimeFormat = rootView.getContext().getString(com.liskovsoft.smartyoutubetv2.tv.R.string.player_remaining_time);
            mAdditionalInfo = (ViewGroup) rootView.findViewById(com.liskovsoft.smartyoutubetv2.tv.R.id.additional_info);
            mTimeInfo = (ViewGroup) rootView.findViewById(com.liskovsoft.smartyoutubetv2.tv.R.id.time_info);
            mTopEdge = (ViewGroup) rootView.findViewById(com.liskovsoft.smartyoutubetv2.tv.R.id.top_edge);
            mTopEdge.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    mTopEdge.clearFocus();

                    if (mTopEdgeFocusListener != null && mTopEdgeFocusListener.get() != null) {
                        mTopEdgeFocusListener.get().onTopEdgeFocused();
                    }
                }
            });
            mProgressBar = (SeekBar) rootView.findViewById(R.id.playback_progress);
            mProgressBar.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    onProgressBarClicked(ViewHolder.this);
                }
            });
            mProgressBar.setOnKeyListener(new View.OnKeyListener() {

                @Override
                public boolean onKey(View view, int keyCode, KeyEvent keyEvent) {
                    // when in seek only allow this keys
                    switch (keyCode) {
                        case KeyEvent.KEYCODE_DPAD_UP:
                        case KeyEvent.KEYCODE_DPAD_DOWN:
                            if (!mInSeek) {
                                setControlsMode(CONTROLS_MODE_FULL);
                            }

                            // eat DPAD UP/DOWN in seek mode
                            return mInSeek;
                        case KeyEvent.KEYCODE_DPAD_LEFT:
                        case KeyEvent.KEYCODE_MINUS:
                        case KeyEvent.KEYCODE_MEDIA_REWIND:
                            if (keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
                                setControlsMode(CONTROLS_MODE_COMPACT);
                                onSeekKeyDown(keyCode); // leanview
                                onBackward();
                            } else {
                                // MOD: resume immediately after seeking

                                if ((mPlayerData.isSeekConfirmPauseEnabled() || mPlayerData.isSeekConfirmPlayEnabled()) && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                                    return true;
                                }

                                stopSeek(false);
                            }
                            return true;
                        case KeyEvent.KEYCODE_DPAD_RIGHT:
                        case KeyEvent.KEYCODE_PLUS:
                        case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                            if (keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
                                setControlsMode(CONTROLS_MODE_COMPACT);
                                onSeekKeyDown(keyCode); // leanview
                                onForward();
                            } else {
                                // MOD: resume immediately after seeking

                                if ((mPlayerData.isSeekConfirmPauseEnabled() || mPlayerData.isSeekConfirmPlayEnabled()) && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                                    return true;
                                }

                                stopSeek(false);
                            }
                            return true;
                        //case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE: // MOD: act as OK? (handled somewhere else, inside player glue)
                        case KeyEvent.KEYCODE_DPAD_CENTER:
                        case KeyEvent.KEYCODE_ENTER:
                            if (!mInSeek) {
                                return false; // use pause toggle handler
                            }
                            if (keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
                                stopSeek(false);
                            }
                            // Fix: is video is paused previously then you should click OK twice to play
                            return mIsPlaying; // use pause toggle handler only if playback is paused
                        case KeyEvent.KEYCODE_BACK:
                        case KeyEvent.KEYCODE_ESCAPE:
                            if (!mInSeek) {
                                return false;
                            }
                            if (keyEvent.getAction() == KeyEvent.ACTION_UP) {
                                // SeekBar does not support cancel in accessibility mode, so always
                                // "confirm" if accessibility is on.
                                stopSeek(Build.VERSION.SDK_INT >= 21
                                        ? !mProgressBar.isAccessibilityFocused() : true);
                            }
                            return true;
                    }
                    return false;
                }
            });
            mProgressBar.setAccessibilitySeekListener(new SeekBar.AccessibilitySeekListener() {
                @Override
                public boolean onAccessibilitySeekForward() {
                    return onForward();
                }

                @Override
                public boolean onAccessibilitySeekBackward() {
                    return onBackward();
                }

                @Override
                public boolean onAccessibilitySeekProgress(int progress) {
                    mSeekClient.onSeekPositionChanged((long)((double) (progress) / Integer.MAX_VALUE * mTotalTimeInMs));
                    mSeekClient.onSeekFinished(false);
                    return true;
                }

                // leanview: finger seeking on a phone (SeekBar.setTouchSeek is on only there)
                @Override
                public boolean onTouchSeekStart() {
                    mTouchStartPositionMs = mCurrentTimeInMs;
                    return startSeek(); // false: nothing to seek now (live), the bar ignores the gesture
                }

                @Override
                public void onTouchSeekMove(int progress) {
                    touchSeekTo(progress);
                }

                @Override
                public void onTouchSeekEnd(boolean cancelled) {
                    if (cancelled && mInSeek) {
                        // the touch was taken away: the knob and the time go back to where the playback is
                        mSeekClient.onSeekPositionChanged(mTouchStartPositionMs);
                        mProgressBar.setProgress(SeekTouch.progressForPosition(mTouchStartPositionMs, mTotalTimeInMs, Integer.MAX_VALUE));
                    }
                    stopSeek(cancelled);
                }
            });
            mProgressBar.setMax(Integer.MAX_VALUE); //current progress will be a fraction of this
            // leanview: on a phone the bar is a finger target (at least 48 dp tall) and a drag moves the knob
            PhoneTouch.setUpSeekBar(mProgressBar);
            mControlsDock = (ViewGroup) rootView.findViewById(R.id.controls_dock);
            mSecondaryControlsDock =
                    (ViewGroup) rootView.findViewById(R.id.secondary_controls_dock);
            mDescriptionViewHolder = descriptionPresenter == null ? null :
                    descriptionPresenter.onCreateViewHolder(mDescriptionDock);
            if (mDescriptionViewHolder != null) {
                mDescriptionDock.addView(mDescriptionViewHolder.view);
            }
            mThumbsBar = (ThumbsBar) rootView.findViewById(R.id.thumbs_row);
            mThumbsBarTitle = (TextView) rootView.findViewById(com.liskovsoft.smartyoutubetv2.tv.R.id.thumbs_row_title);
            mThumbsBarWrapper = (ViewGroup) rootView.findViewById(com.liskovsoft.smartyoutubetv2.tv.R.id.thumbs_row_wrapper);
            // leanview: on a phone the rows of buttons scroll sideways; held upright the format/clock and time texts
            // get their own lines (no overprint)
            PhoneOsdLayout.install(rootView, mControlsDock, mAdditionalInfo, mQualityInfo, mDateTime, mSecondaryControlsDock,
                    mTimeInfo);
        }

        /**
         * @return The ViewHolder for description.
         */
        public final Presenter.ViewHolder getDescriptionViewHolder() {
            return mDescriptionViewHolder;
        }

        @Override
        public void setPlaybackSeekUiClient(Client client) {
            mSeekClient = client;
        }

        boolean startSeek() {
            if (mInSeek) {
                return true;
            }
            if (mSeekClient == null || !mSeekClient.isSeekEnabled()
                    || mTotalTimeInMs <= 0) {
                return false;
            }
            mInSeek = true;
            // leanview: new seek session, so bitmaps requested by the previous one are stale
            mThumbResult = newThumbResult(mSeekThumbs.nextGeneration());
            mSeekClient.onSeekStarted();
            mSeekDataProvider = mSeekClient.getPlaybackSeekDataProvider();
            mPositions = mSeekDataProvider != null ? mSeekDataProvider.getSeekPositions() : null;
            if (mPositions != null) {
                int pos = Arrays.binarySearch(mPositions, mTotalTimeInMs);
                if (pos >= 0) {
                    mPositionsLength = pos + 1;
                } else {
                    mPositionsLength = -1 - pos;
                }
            } else {
                mPositionsLength = 0;

                // MOD: Clear thumb bar if there's no seek previews
                mThumbsBar.clearThumbBitmaps();
            }

            return true;
        }

        void stopSeek(boolean cancelled) {
            if (!mInSeek) {
                return;
            }
            mInSeek = false;
            mSeekClient.onSeekFinished(cancelled);
            if (mSeekDataProvider != null) {
                mSeekDataProvider.reset();
            }
            mThumbHeroIndex = -1;
            // MOD: always show storyboard
            //mThumbsBar.clearThumbBitmaps();
            mSeekDataProvider = null;
            mPositions = null;
            mPositionsLength = 0;
            resetSeekIncrement();
            // leanview: the seek is committed (key up, OK, back or watchdog): hide the thumbs after a short quiet
            mSeekThumbs.setConfirmMode(false);
            mSeekThumbs.onSeekKeyUp(System.currentTimeMillis());
            scheduleSeekThumbsTick();
        }

        // leanview: confirm-pause / confirm-play keep the thumbs until OK, but only for the arrow keys
        boolean isConfirmSeekKey(int keyCode) {
            return (mPlayerData.isSeekConfirmPauseEnabled() || mPlayerData.isSeekConfirmPlayEnabled())
                    && (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT);
        }

        void onSeekKeyDown(int keyCode) {
            mSeekThumbs.setConfirmMode(isConfirmSeekKey(keyCode));
            mSeekThumbs.onSeekKeyDown(System.currentTimeMillis());
            scheduleSeekThumbsTick();
        }

        void scheduleSeekThumbsTick() {
            view.removeCallbacks(mSeekThumbsTick);
            long delay = mSeekThumbs.nextDueInMs(System.currentTimeMillis());
            if (delay >= 0) {
                view.postDelayed(mSeekThumbsTick, delay);
            }
        }

        void cancelSeekThumbsTick() {
            view.removeCallbacks(mSeekThumbsTick);
            mSeekThumbs.reset();
        }

        void onSeekThumbsTick() {
            switch (mSeekThumbs.due(System.currentTimeMillis())) {
                case COMMIT_SEEK:
                    // key up never arrived: commit (stopSeek also arms the hide)
                    stopSeek(false);
                    break;
                case HIDE_THUMBS:
                    if (!mInSeek) {
                        hideSeekThumbs();
                    }
                    break;
                default:
                    break;
            }
            scheduleSeekThumbsTick();
        }

        // Never GONE (see setControlsMode): the carousel can't calculate its length then
        void hideSeekThumbs() {
            mThumbsBar.clearThumbBitmaps();
            mThumbsBarWrapper.setVisibility(View.INVISIBLE);
        }

        // MOD: seek ui tweaks
        void setControlsMode(int mode) {
            switch (mode) {
                case CONTROLS_MODE_FULL:
                    mControlsVh.view.setVisibility(View.VISIBLE);
                    mSecondaryControlsVh.view.setVisibility(View.VISIBLE);
                    mDescriptionViewHolder.view.setVisibility(View.VISIBLE);
                    mAdditionalInfo.setVisibility(View.VISIBLE);
                    //mTimeInfo.setVisibility(View.VISIBLE);
                    // Don't set to GONE or carousel will crash (can't properly calculate length)
                    mThumbsBarWrapper.setVisibility(View.INVISIBLE);
                    // MOD: fix old preview image on seek start
                    mThumbsBar.clearThumbBitmaps();
                    cancelSeekThumbsTick(); // leanview: nothing left to hide
                    break;
                case CONTROLS_MODE_COMPACT:
                    mControlsVh.view.setVisibility(View.GONE);
                    // MOD: use GONE to move previews closer to seek bar
                    mSecondaryControlsVh.view.setVisibility(View.GONE);
                    mDescriptionViewHolder.view.setVisibility(View.GONE);
                    mAdditionalInfo.setVisibility(View.GONE);
                    //mTimeInfo.setVisibility(View.GONE);
                    mThumbsBarWrapper.setVisibility(View.VISIBLE);
                    break;
            }
        }

        void dispatchItemSelection() {
            if (!isSelected()) {
                return;
            }
            if (mSelectedViewHolder == null) {
                if (getOnItemViewSelectedListener() != null) {
                    getOnItemViewSelectedListener().onItemSelected(null, null,
                            ViewHolder.this, getRow());
                }
            } else {
                if (getOnItemViewSelectedListener() != null) {
                    getOnItemViewSelectedListener().onItemSelected(mSelectedViewHolder,
                            mSelectedItem, ViewHolder.this, getRow());
                }
            }
        };

        Presenter getPresenter(boolean primary) {
            ObjectAdapter adapter = primary
                    ? ((PlaybackControlsRow) getRow()).getPrimaryActionsAdapter()
                    : ((PlaybackControlsRow) getRow()).getSecondaryActionsAdapter();
            if (adapter == null) {
                return null;
            }
            if (adapter.getPresenterSelector() instanceof ControlButtonPresenterSelector) {
                ControlButtonPresenterSelector selector =
                        (ControlButtonPresenterSelector) adapter.getPresenterSelector();
                return selector.getSecondaryPresenter();
            }
            return adapter.getPresenter(adapter.size() > 0 ? adapter.get(0) : null);
        }

        /**
         * Returns the TextView that showing total time label. This method might be used in
         * {@link #onSetDurationLabel}.
         * @return The TextView that showing total time label.
         */
        public final TextView getDurationView() {
            return mTotalTime;
        }

        /**
         * Called to update total time label. Default implementation updates the TextView
         * {@link #getDurationView()}. Subclass might override.
         * @param totalTimeMs Total duration of the media in milliseconds.
         */
        protected void onSetDurationLabel(long totalTimeMs) {
            if (mTotalTime != null) {
                formatTime(totalTimeMs, mTempBuilder);
                mTotalTime.setText(mTempBuilder.toString());
            }
        }

        void setTotalTime(long totalTimeMs) {
            if (mTotalTimeInMs != totalTimeMs) {
                mTotalTimeInMs = totalTimeMs;
                onSetDurationLabel(totalTimeMs);
            }
        }

        /**
         * Returns the TextView that showing current position label. This method might be used in
         * {@link #onSetCurrentPositionLabel}.
         * @return The TextView that showing current position label.
         */
        public final TextView getCurrentPositionView() {
            return mCurrentTime;
        }

        /**
         * Called to update current time label. Default implementation updates the TextView
         * {@link #getCurrentPositionView}. Subclass might override.
         * @param currentTimeMs Current playback position in milliseconds.
         */
        protected void onSetCurrentPositionLabel(long currentTimeMs) {
            if (mCurrentTime != null) {
                formatTime(currentTimeMs, mTempBuilder);
                mCurrentTime.setText(mTempBuilder.toString());
            }
        }

        void setCurrentPosition(long currentTimeMs) {
            if (currentTimeMs != mCurrentTimeInMs) {
                mCurrentTimeInMs = currentTimeMs;
                onSetCurrentPositionLabel(currentTimeMs);
            }
            if (!mInSeek) {
                int progressRatio = 0;
                if (mTotalTimeInMs > 0) {
                    // Use ratio to represent current progress
                    double ratio = (double) mCurrentTimeInMs / mTotalTimeInMs;     // Range: [0, 1]
                    progressRatio = (int) (ratio * Integer.MAX_VALUE);  // Could safely cast to int
                }
                mProgressBar.setProgress((int) progressRatio);
            }
        }

        void setBufferedPosition(long progressMs) {
            mSecondaryProgressInMs = progressMs;
            // Solve the progress bar by using ratio
            double ratio = (double) progressMs / mTotalTimeInMs;           // Range: [0, 1]
            double progressRatio = ratio * Integer.MAX_VALUE;   // Could safely cast to int
            mProgressBar.setSecondaryProgress((int) progressRatio);
        }

        void setEndingTime(long currentTimeMs) {
            long remainingTimeMs = mTotalTimeInMs - currentTimeMs;

            if (mRemainingTimeInMs != remainingTimeMs) {
                mRemainingTimeInMs = remainingTimeMs;
                onSetEndingTimeLabel(remainingTimeMs);
            }
        }

        protected void onSetEndingTimeLabel(long remainingTimeMs) {
            remainingTimeMs = applySpeedCorrection(remainingTimeMs);

            if (mEndingTime != null) {
                if (mPlayerData.isRemainingTimeEnabled()) {
                    formatTime(remainingTimeMs, mTempBuilder);
                    mEndingTime.setText(String.format(mRemainingTimeFormat, mTempBuilder.toString()));
                    mEndingTime.setVisibility(View.VISIBLE);
                } else if (mPlayerData.isEndingTimeEnabled()) {
                    mEndingTime.setText(String.format(mEndingTimeFormat,
                            DateHelper.toShortTime(System.currentTimeMillis() + remainingTimeMs)));
                    mEndingTime.setVisibility(View.VISIBLE);
                } else {
                    mEndingTime.setVisibility(View.GONE);
                }
            }
        }

        void setQualityInfo(String content) {
            if (content != null) {
                if (mPlayerData.isQualityInfoEnabled()) {
                    mQualityInfo.setText(content);
                    mQualityInfo.setVisibility(View.VISIBLE);
                } else {
                    mQualityInfo.setVisibility(View.GONE);
                }
            }
        }

        void setDateVisibility(boolean isVisible) {
            if (mPlayerData.isClockEnabled()) {
                mDateTime.setVisibility(View.VISIBLE);
            } else {
                mDateTime.setVisibility(View.GONE);
            }
        }

        void setSeekPreviewTitle(String title) {
            if (title != null) {
                mThumbsBarTitle.setText(title);
                mThumbsBarTitle.setVisibility(View.VISIBLE);
            } else {
                mThumbsBarTitle.setVisibility(View.GONE);
            }
        }

        void setSeekBarSegments(List<SeekBarSegment> segments) {
            mProgressBar.setSegments(segments);
        }

        void setPlay(boolean play) {
            mIsPlaying = play;
        }

        void setTopEdgeFocusListener(TopEdgeFocusListener listener) {
            mTopEdgeFocusListener = new WeakReference<>(listener);
        }

        void updateTotalTime() {
            onSetDurationLabel(mTotalTimeInMs);
        }

        /**
         * Correct time with respect of speed
         */
        long applySpeedCorrection(long timeMs) {
            timeMs = (long) (timeMs / mPlayerData.getSpeed());

            return timeMs >= 0 ? timeMs : 0;
        }
    }

    static void formatTime(long ms, StringBuilder sb) {
        sb.setLength(0);
        if (ms < 0) {
            sb.append("--");
            return;
        }
        long seconds = ms / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        seconds -= minutes * 60;
        minutes -= hours * 60;

        if (hours > 0) {
            sb.append(hours).append(':');
            if (minutes < 10) {
                sb.append('0');
            }
        }
        sb.append(minutes).append(':');
        if (seconds < 10) {
            sb.append('0');
        }
        sb.append(seconds);
    }

    float mDefaultSeekIncrement = 0.01f;
    int mProgressColor = Color.TRANSPARENT;
    int mSecondaryProgressColor = Color.TRANSPARENT;
    boolean mProgressColorSet;
    boolean mSecondaryProgressColorSet;
    Presenter mDescriptionPresenter;
    final ControlBarPresenter mPlaybackControlsPresenter;
    final ControlBarPresenter mSecondaryControlsPresenter;
    OnActionClickedListener mOnActionClickedListener;
    OnActionLongClickedListener mOnActionLongClickedListener;

    private final OnControlSelectedListener mOnControlSelectedListener =
            new OnControlSelectedListener() {
        @Override
        public void onControlSelected(Presenter.ViewHolder itemViewHolder, Object item,
                ControlBarPresenter.BoundData data) {
            ViewHolder vh = ((BoundData) data).mRowViewHolder;
            if (vh.mSelectedViewHolder != itemViewHolder || vh.mSelectedItem != item) {
                vh.mSelectedViewHolder = itemViewHolder;
                vh.mSelectedItem = item;
                vh.dispatchItemSelection();
            }
        }
    };

    private final OnControlClickedListener mOnControlClickedListener =
            new OnControlClickedListener() {
        @Override
        public void onControlClicked(Presenter.ViewHolder itemViewHolder, Object item,
                ControlBarPresenter.BoundData data) {
            ViewHolder vh = ((BoundData) data).mRowViewHolder;
            if (vh.getOnItemViewClickedListener() != null) {
                vh.getOnItemViewClickedListener().onItemClicked(itemViewHolder, item,
                        vh, vh.getRow());
            }
            if (mOnActionClickedListener != null && item instanceof Action) {
                mOnActionClickedListener.onActionClicked((Action) item);
            }
        }
    };

    private final OnControlLongClickedListener mOnControlLongClickedListener =
            new OnControlLongClickedListener() {
        @Override
        public boolean onControlLongClicked(Presenter.ViewHolder itemViewHolder, Object item,
                                     ControlBarPresenter.BoundData data) {
            if (mOnActionLongClickedListener != null && item instanceof Action) {
                return mOnActionLongClickedListener.onActionLongClicked((Action) item);
            }

            return false;
        }
    };

    public PlaybackTransportRowPresenter() {
        setHeaderPresenter(null);
        setSelectEffectEnabled(false);

        mPlaybackControlsPresenter = new ControlBarPresenter(com.liskovsoft.smartyoutubetv2.tv.R.layout.lb_control_bar);
        mPlaybackControlsPresenter.setDefaultFocusToMiddle(false);
        mPlaybackControlsPresenter.setFocusRecovery(true);
        mSecondaryControlsPresenter = new ControlBarPresenter(com.liskovsoft.smartyoutubetv2.tv.R.layout.lb_control_bar);
        mSecondaryControlsPresenter.setDefaultFocusToMiddle(false);
        mSecondaryControlsPresenter.setFocusRecovery(true);

        mPlaybackControlsPresenter.setOnControlSelectedListener(mOnControlSelectedListener);
        mSecondaryControlsPresenter.setOnControlSelectedListener(mOnControlSelectedListener);
        mPlaybackControlsPresenter.setOnControlClickedListener(mOnControlClickedListener);
        mSecondaryControlsPresenter.setOnControlClickedListener(mOnControlClickedListener);
        mPlaybackControlsPresenter.setOnControlLongClickedListener(mOnControlLongClickedListener);
        mSecondaryControlsPresenter.setOnControlLongClickedListener(mOnControlLongClickedListener);
    }

    /**
     * @param descriptionPresenter Presenter for displaying item details.
     */
    public void setDescriptionPresenter(Presenter descriptionPresenter) {
        mDescriptionPresenter = descriptionPresenter;
    }

    /**
     * Sets the listener for {@link Action} click events.
     */
    public void setOnActionClickedListener(OnActionClickedListener listener) {
        mOnActionClickedListener = listener;
    }

    /**
     * MOD: Sets the listener for {@link Action} long click events.
     */
    public void setOnActionLongClickedListener(OnActionLongClickedListener listener) {
        mOnActionLongClickedListener = listener;
    }

    /**
     * Returns the listener for {@link Action} click events.
     */
    public OnActionClickedListener getOnActionClickedListener() {
        return mOnActionClickedListener;
    }

    /**
     * Sets the primary color for the progress bar.  If not set, a default from
     * the theme will be used.
     */
    public void setProgressColor(@ColorInt int color) {
        mProgressColor = color;
        mProgressColorSet = true;
    }

    /**
     * Returns the primary color for the progress bar.  If no color was set, transparent
     * is returned.
     */
    @ColorInt
    public int getProgressColor() {
        return mProgressColor;
    }

    /**
     * Sets the secondary color for the progress bar.  If not set, a default from
     * the theme {@link R.attr#playbackProgressSecondaryColor} will be used.
     * @param color Color used to draw secondary progress.
     */
    public void setSecondaryProgressColor(@ColorInt int color) {
        mSecondaryProgressColor = color;
        mSecondaryProgressColorSet = true;
    }

    /**
     * Returns the secondary color for the progress bar.  If no color was set, transparent
     * is returned.
     */
    @ColorInt
    public int getSecondaryProgressColor() {
        return mSecondaryProgressColor;
    }
    
    @Override
    public void onReappear(RowPresenter.ViewHolder rowViewHolder) {
        ViewHolder vh = (ViewHolder) rowViewHolder;
        if (vh.view.hasFocus()) {
            // Reset position in mode 'Seek with confirmation (play while seeking)'
            vh.stopSeek(Build.VERSION.SDK_INT >= 21
                    ? !vh.mProgressBar.isAccessibilityFocused() : true);

            // player controls hidden
            vh.setControlsMode(ViewHolder.CONTROLS_MODE_FULL);
            vh.mProgressBar.requestFocus();
        }

        // leanview: whatever the focus, the overlay is coming back: no thumbs left over from the last seek
        vh.cancelSeekThumbsTick();
        vh.hideSeekThumbs();

        // MOD: reset player focus
        //vh.mControlsVh.mControlBar.resetFocus();
        //vh.mSecondaryControlsVh.mControlBar.resetFocus();
    }

    private static int getDefaultProgressColor(Context context) {
        TypedValue outValue = new TypedValue();
        if (context.getTheme()
                .resolveAttribute(R.attr.playbackProgressPrimaryColor, outValue, true)) {
            return context.getResources().getColor(outValue.resourceId);
        }
        return context.getResources().getColor(R.color.lb_playback_progress_color_no_theme);
    }

    private static int getDefaultSecondaryProgressColor(Context context) {
        TypedValue outValue = new TypedValue();
        if (context.getTheme()
                .resolveAttribute(R.attr.playbackProgressSecondaryColor, outValue, true)) {
            return context.getResources().getColor(outValue.resourceId);
        }
        return context.getResources().getColor(
                R.color.lb_playback_progress_secondary_color_no_theme);
    }

    @Override
    protected RowPresenter.ViewHolder createRowViewHolder(ViewGroup parent) {
        View v = LayoutInflater.from(parent.getContext()).inflate(
                com.liskovsoft.smartyoutubetv2.tv.R.layout.lb_playback_transport_controls_row, parent, false);
        ViewHolder vh = new ViewHolder(v, mDescriptionPresenter);
        initRow(vh);
        return vh;
    }

    private void initRow(final ViewHolder vh) {
        vh.mControlsVh = (ControlBarPresenter.ViewHolder) mPlaybackControlsPresenter
                .onCreateViewHolder(vh.mControlsDock);
        vh.mProgressBar.setProgressColor(mProgressColorSet ? mProgressColor
                : getDefaultProgressColor(vh.mControlsDock.getContext()));
        vh.mProgressBar.setSecondaryProgressColor(mSecondaryProgressColorSet
                ? mSecondaryProgressColor
                : getDefaultSecondaryProgressColor(vh.mControlsDock.getContext()));
        vh.mControlsDock.addView(vh.mControlsVh.view);

        vh.mSecondaryControlsVh = (ControlBarPresenter.ViewHolder) mSecondaryControlsPresenter
                .onCreateViewHolder(vh.mSecondaryControlsDock);
        vh.mSecondaryControlsDock.addView(vh.mSecondaryControlsVh.view);
        ((PlaybackTransportRowView) vh.view.findViewById(R.id.transport_row))
                .setOnUnhandledKeyListener(new PlaybackTransportRowView.OnUnhandledKeyListener() {
                @Override
                public boolean onUnhandledKey(KeyEvent event) {
                    if (vh.getOnKeyListener() != null) {
                        if (vh.getOnKeyListener().onKey(vh.view, event.getKeyCode(), event)) {
                            return true;
                        }
                    }
                    return false;
                }
            });
    }

    @Override
    protected void onBindRowViewHolder(RowPresenter.ViewHolder holder, Object item) {
        super.onBindRowViewHolder(holder, item);

        ViewHolder vh = (ViewHolder) holder;
        PlaybackControlsRow row = (PlaybackControlsRow) vh.getRow();

        if (row.getItem() == null) {
            vh.mDescriptionDock.setVisibility(View.GONE);
        } else {
            vh.mDescriptionDock.setVisibility(View.VISIBLE);
            if (vh.mDescriptionViewHolder != null) {
                mDescriptionPresenter.onBindViewHolder(vh.mDescriptionViewHolder, row.getItem());
            }
        }

        if (row.getImageDrawable() == null) {
            vh.mImageView.setVisibility(View.GONE);
        } else {
            vh.mImageView.setVisibility(View.VISIBLE);
        }
        vh.mImageView.setImageDrawable(row.getImageDrawable());

        vh.mControlsBoundData.adapter = row.getPrimaryActionsAdapter();
        vh.mControlsBoundData.presenter = vh.getPresenter(true);
        vh.mControlsBoundData.mRowViewHolder = vh;
        mPlaybackControlsPresenter.onBindViewHolder(vh.mControlsVh, vh.mControlsBoundData);

        vh.mSecondaryBoundData.adapter = row.getSecondaryActionsAdapter();
        vh.mSecondaryBoundData.presenter = vh.getPresenter(false);
        vh.mSecondaryBoundData.mRowViewHolder = vh;
        mSecondaryControlsPresenter.onBindViewHolder(vh.mSecondaryControlsVh,
                vh.mSecondaryBoundData);

        vh.setTotalTime(row.getDuration());
        vh.setCurrentPosition(row.getCurrentPosition());
        vh.setBufferedPosition(row.getBufferedPosition());
        row.setOnPlaybackProgressChangedListener(vh.mListener);
    }

    @Override
    protected void onUnbindRowViewHolder(RowPresenter.ViewHolder holder) {
        ViewHolder vh = (ViewHolder) holder;
        PlaybackControlsRow row = (PlaybackControlsRow) vh.getRow();

        if (vh.mDescriptionViewHolder != null) {
            mDescriptionPresenter.onUnbindViewHolder(vh.mDescriptionViewHolder);
        }
        mPlaybackControlsPresenter.onUnbindViewHolder(vh.mControlsVh);
        mSecondaryControlsPresenter.onUnbindViewHolder(vh.mSecondaryControlsVh);
        if (row != null) {
            row.setOnPlaybackProgressChangedListener(null);
        }

        super.onUnbindRowViewHolder(holder);
    }

    /**
     * Client of progress bar is clicked, default implementation delegate click to
     * PlayPauseAction.
     *
     * @param vh ViewHolder of PlaybackTransportRowPresenter
     */
    protected void onProgressBarClicked(ViewHolder vh) {
        if (vh != null) {
            if (vh.mPlayPauseAction == null) {
                vh.mPlayPauseAction = new PlaybackControlsRow.PlayPauseAction(vh.view.getContext());
            }
            if (vh.getOnItemViewClickedListener() != null) {
                vh.getOnItemViewClickedListener().onItemClicked(vh, vh.mPlayPauseAction,
                        vh, vh.getRow());
            }
            if (mOnActionClickedListener != null) {
                mOnActionClickedListener.onActionClicked(vh.mPlayPauseAction);
            }
        }
    }

    /**
     * Set default seek increment if {@link PlaybackSeekDataProvider} is null.
     * @param ratio float value between 0(inclusive) and 1(inclusive).
     */
    public void setDefaultSeekIncrement(float ratio) {
        mDefaultSeekIncrement = ratio;
    }

    /**
     * Get default seek increment if {@link PlaybackSeekDataProvider} is null.
     * @return float value between 0(inclusive) and 1(inclusive).
     */
    public float getDefaultSeekIncrement() {
        return mDefaultSeekIncrement;
    }

    @Override
    protected void onRowViewSelected(RowPresenter.ViewHolder vh, boolean selected) {
        super.onRowViewSelected(vh, selected);
        if (selected) {
            ((ViewHolder) vh).dispatchItemSelection();
        }
    }

    @Override
    protected void onRowViewAttachedToWindow(RowPresenter.ViewHolder vh) {
        super.onRowViewAttachedToWindow(vh);
        if (mDescriptionPresenter != null) {
            mDescriptionPresenter.onViewAttachedToWindow(
                    ((ViewHolder) vh).mDescriptionViewHolder);
        }
    }

    @Override
    protected void onRowViewDetachedFromWindow(RowPresenter.ViewHolder vh) {
        super.onRowViewDetachedFromWindow(vh);
        if (mDescriptionPresenter != null) {
            mDescriptionPresenter.onViewDetachedFromWindow(
                    ((ViewHolder) vh).mDescriptionViewHolder);
        }
    }

}
