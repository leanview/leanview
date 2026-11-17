package com.liskovsoft.smartyoutubetv2.tv.ui.endscreen;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.liskovsoft.smartyoutubetv2.common.leanview.EndScreenKeys;
import com.liskovsoft.smartyoutubetv2.common.leanview.EndScreenKeys.Action;
import com.liskovsoft.smartyoutubetv2.common.leanview.EndScreenKeys.Key;
import com.liskovsoft.smartyoutubetv2.common.leanview.EndScreenKeys.Mode;
import com.liskovsoft.smartyoutubetv2.common.leanview.EndScreenKeys.Result;
import com.liskovsoft.smartyoutubetv2.common.leanview.EndScreenKeys.Zone;
import com.liskovsoft.smartyoutubetv2.common.leanview.EndScreenModel;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * leanview: the card of the "Up next" end screen. It never takes the real focus (the related row
 * below the card keeps it, so its own navigation works as always); the player activity hands it the
 * remote keys first through {@link #handleKey}, and what each key does is decided by
 * {@link EndScreenKeys}. The two buttons show which one the remote is on by being "selected".
 */
public class EndScreenView extends FrameLayout {
    public interface Listener {
        void onAction(Action action);

        /** The remote went down to the related videos (true) or came back up to the card (false). */
        void onRelatedFocus(boolean focused);
    }

    private static final int SELECTED_TEXT = 0xFF101010;
    private static final int NORMAL_TEXT = 0xFFFFFFFF;
    private final TextView[] mButtons = new TextView[2];
    private TextView mHeading;
    private TextView mTitle;
    private TextView mAuthor;
    private ImageView mThumb;
    private EndScreenRingView mRing;
    private Listener mListener;
    private Mode mMode;
    private Zone mZone = Zone.BUTTONS;
    private int mSelected;
    private String mImageUrl;
    private int mConsumedKeyCode = -1;

    public EndScreenView(Context context) {
        super(context);
    }

    public EndScreenView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mHeading = findViewById(R.id.leanview_end_heading);
        mTitle = findViewById(R.id.leanview_end_title);
        mAuthor = findViewById(R.id.leanview_end_author);
        mThumb = findViewById(R.id.leanview_end_thumb);
        mRing = findViewById(R.id.leanview_end_ring);
        mButtons[0] = findViewById(R.id.leanview_end_button_a);
        mButtons[1] = findViewById(R.id.leanview_end_button_b);

        // Pointer and touch still work
        for (int i = 0; i < mButtons.length; i++) {
            final int index = i;
            mButtons[i].setOnClickListener(v -> {
                if (mMode != null && mListener != null) {
                    mListener.onAction(EndScreenKeys.buttons(mMode)[index]);
                }
            });
        }
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public boolean isActive() {
        return getVisibility() == VISIBLE && mMode != null;
    }

    /**
     * Shows the card or updates it. The remote's place on the card is kept while the mode stays.
     *
     * @return true when the card just opened or changed mode
     */
    public boolean bind(EndScreenModel model) {
        boolean changed = !isActive() || mMode != model.mode;

        if (changed) {
            mMode = model.mode;
            mZone = Zone.BUTTONS;
            mSelected = EndScreenKeys.initialSelection(model.mode, model.defaultFocus);
            setVisibility(VISIBLE);
        }

        mHeading.setText(model.mode == Mode.STILL_WATCHING ? R.string.leanview_end_still_watching : R.string.leanview_end_up_next);
        // The next video is not known yet: the card says it is looking for it
        mTitle.setText(model.mode == Mode.WAITING ? getContext().getString(R.string.leanview_end_searching) : model.title);
        mAuthor.setText(model.mode == Mode.WAITING ? "" : model.author);
        showImage(model.mode == Mode.WAITING ? null : model.imageUrl);

        if (model.mode == Mode.COUNTDOWN) {
            mRing.setVisibility(VISIBLE);
            mRing.setSeconds(model.secondsLeft, model.totalSeconds);
            // A held countdown says so instead of the author line
            if (model.paused) {
                mAuthor.setText(R.string.leanview_end_paused);
            }
        } else {
            mRing.setVisibility(GONE);
        }

        Action[] actions = EndScreenKeys.buttons(model.mode);
        for (int i = 0; i < mButtons.length; i++) {
            mButtons[i].setText(label(actions[i]));
        }
        renderSelection();

        return changed;
    }

    public void hide() {
        setVisibility(GONE);
        mMode = null;
        mZone = Zone.BUTTONS;
        // mConsumedKeyCode stays: the release of the key that closed the card is still swallowed
    }

    /**
     * @return true when the key is used up here and must not reach the player. Key up events are
     * swallowed when their key down was, whatever happened in between (BACK on the card must not
     * close the player on release).
     */
    public boolean handleKey(KeyEvent event, boolean relatedAvailable, boolean relatedAtTop) {
        int code = event.getKeyCode();

        if (event.getAction() == KeyEvent.ACTION_UP) {
            if (mConsumedKeyCode == code) {
                mConsumedKeyCode = -1;
                return true;
            }

            return false;
        }

        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return false;
        }

        if (event.getRepeatCount() > 0) {
            return mConsumedKeyCode == code; // a held key keeps being swallowed, never acts twice
        }

        if (!isActive()) {
            return false;
        }

        Zone before = mZone;
        Result result = EndScreenKeys.handle(mMode, mZone, mSelected, toKey(code), relatedAvailable, relatedAtTop);

        mZone = result.zone;
        mSelected = result.selected;
        mConsumedKeyCode = result.consume ? code : -1;
        renderSelection();

        if (before != mZone && mListener != null) {
            mListener.onRelatedFocus(mZone == Zone.RELATED);
        }

        if (result.action != Action.NONE && mListener != null) {
            mListener.onAction(result.action); // may hide the card
        }

        return result.consume;
    }

    private void renderSelection() {
        for (int i = 0; i < mButtons.length; i++) {
            boolean selected = mZone == Zone.BUTTONS && mSelected == i;
            mButtons[i].setSelected(selected);
            mButtons[i].setTextColor(selected ? SELECTED_TEXT : NORMAL_TEXT);
        }
    }

    private void showImage(String url) {
        if ((url == null && mImageUrl == null) || (url != null && url.equals(mImageUrl))) {
            return;
        }

        mImageUrl = url;

        if (url == null) {
            mThumb.setImageDrawable(null);
            return;
        }

        try {
            Glide.with(getContext()).load(url).into(mThumb);
        } catch (IllegalArgumentException e) {
            // the activity is going away: no thumbnail is needed
        }
    }

    private int label(Action action) {
        switch (action) {
            case CANCEL:
                return R.string.leanview_end_cancel;
            case REPLAY:
                return R.string.leanview_end_replay;
            case STILL_YES:
                return R.string.leanview_end_still_yes;
            case STILL_NO:
                return R.string.leanview_end_still_no;
            case PLAY_NOW:
            default:
                return R.string.leanview_end_play_now;
        }
    }

    private static Key toKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return Key.LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return Key.RIGHT;
            case KeyEvent.KEYCODE_DPAD_UP:
                return Key.UP;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return Key.DOWN;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
                return Key.OK;
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
            case KeyEvent.KEYCODE_BUTTON_B:
                return Key.BACK;
            default:
                return Key.OTHER;
        }
    }
}
