package com.liskovsoft.smartyoutubetv2.tv.ui.leanview;

import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.RelativeLayout;

import com.liskovsoft.smartyoutubetv2.common.leanview.PhoneMode;
import com.liskovsoft.smartyoutubetv2.common.leanview.TouchTarget;

/**
 * leanview: the player's on-screen controls on a phone. The TV layout puts the format and clock texts on the
 * same line as the first row of buttons, and "Remaining" and the time on the same line as the second row;
 * upright the screen is too narrow for both and the texts overprint the buttons. On a phone the two rows of
 * buttons sit in a horizontal scroller (a finger swipes along the row, like YouTube's chips) and, upright:
 * the format and clock go on one line above the first row, "Remaining" and the time go below the second
 * row, the side margins shrink so more buttons fit, and the row ends with a PARTLY visible button so it is
 * plain that there is more to swipe to. Held sideways everything is the layout file's own (the scrollers
 * are as wide as their buttons, nothing scrolls). Never installed off a phone: the TV keeps its views.
 */
public final class PhoneOsdLayout {
    private final View mRoot;
    private final HorizontalScrollView mControlsScroll;
    private final View mAdditionalInfo;
    private final View mQualityInfo;
    private final View mDateTime;
    private final HorizontalScrollView mSecondaryScroll;
    private final View mTimeInfo;
    private final View mTransportRow;
    private final View mControlsCard;
    private final int mRowPaddingStart;
    private final int mRowPaddingEnd;
    private final int mCardPaddingStart;
    private final int mCardPaddingEnd;
    private boolean mPortraitApplied;
    private boolean mApplied;

    private PhoneOsdLayout(View root, HorizontalScrollView controlsScroll, View additionalInfo, View qualityInfo,
                           View dateTime, HorizontalScrollView secondaryScroll, View timeInfo) {
        mRoot = root;
        mControlsScroll = controlsScroll;
        mAdditionalInfo = additionalInfo;
        mQualityInfo = qualityInfo;
        mDateTime = dateTime;
        mSecondaryScroll = secondaryScroll;
        mTimeInfo = timeInfo;
        mTransportRow = byName(root, "transport_row");
        mControlsCard = byName(root, "controls_card");
        mRowPaddingStart = mTransportRow != null ? mTransportRow.getPaddingStart() : 0;
        mRowPaddingEnd = mTransportRow != null ? mTransportRow.getPaddingEnd() : 0;
        mCardPaddingStart = mControlsCard != null ? mControlsCard.getPaddingStart() : 0;
        mCardPaddingEnd = mControlsCard != null ? mControlsCard.getPaddingEnd() : 0;
    }

    private static View byName(View root, String idName) {
        int id = root.getResources().getIdentifier(idName, "id", root.getContext().getPackageName());
        return id != 0 ? root.findViewById(id) : null;
    }

    /** Does nothing off a phone, or when a piece of the layout is missing. */
    public static void install(View root, View controlsDock, View additionalInfo, View qualityInfo, View dateTime,
                               View secondaryDock, View timeInfo) {
        if (root == null || !PhoneMode.isPhone(root.getContext())
                || controlsDock == null || additionalInfo == null || qualityInfo == null || dateTime == null
                || secondaryDock == null || timeInfo == null
                || !(controlsDock.getParent() instanceof RelativeLayout)
                || !(secondaryDock.getParent() instanceof RelativeLayout)) {
            return;
        }
        HorizontalScrollView controlsScroll = wrapInScroller(controlsDock);
        HorizontalScrollView secondaryScroll = wrapInScroller(secondaryDock);
        PhoneOsdLayout layout = new PhoneOsdLayout(root, controlsScroll, additionalInfo, qualityInfo, dateTime,
                secondaryScroll, timeInfo);
        root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> layout.update());
        controlsScroll.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> layout.showThereIsMore(controlsScroll));
        secondaryScroll.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> layout.showThereIsMore(secondaryScroll));
        layout.update();
    }

    // The dock keeps its place in the row's RelativeLayout through the scroller, which takes over its layout
    // rules; the dock (and the presenter's reference to it) become the scroller's only child.
    private static HorizontalScrollView wrapInScroller(View dock) {
        ViewGroup parent = (ViewGroup) dock.getParent();
        int index = parent.indexOfChild(dock);
        ViewGroup.LayoutParams rules = dock.getLayoutParams();
        HorizontalScrollView scroll = new HorizontalScrollView(dock.getContext());
        scroll.setId(View.generateViewId());
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setFocusable(false); // the buttons keep the (keyboard) focus handling they have
        parent.removeView(dock);
        parent.addView(scroll, index, rules);
        scroll.addView(dock, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    private void update() {
        View decor = mRoot.getRootView();
        if (decor == null || decor.getWidth() <= 0 || decor.getHeight() <= 0) {
            return;
        }
        boolean portrait = PhoneMode.isPortrait(decor.getWidth(), decor.getHeight());
        if (mApplied && portrait == mPortraitApplied) {
            return;
        }
        mApplied = true;
        mPortraitApplied = portrait;
        mRoot.post(() -> apply(portrait));
    }

    private void apply(boolean portrait) {
        // First row: buttons, with format and clock at the end of the line (landscape) or above it (portrait).
        RelativeLayout.LayoutParams info = params(mAdditionalInfo);
        RelativeLayout.LayoutParams dock = params(mControlsScroll);
        RelativeLayout.LayoutParams quality = params(mQualityInfo);
        RelativeLayout.LayoutParams date = params(mDateTime);
        // Second row: more buttons, with "Remaining" and the time (landscape) or the time block below (portrait).
        RelativeLayout.LayoutParams time = params(mTimeInfo);
        RelativeLayout.LayoutParams secondary = params(mSecondaryScroll);
        if (info == null || dock == null || quality == null || date == null || time == null || secondary == null) {
            return;
        }
        if (portrait) {
            info.addRule(RelativeLayout.ALIGN_PARENT_END);
            info.addRule(RelativeLayout.ALIGN_PARENT_TOP);
            info.removeRule(RelativeLayout.CENTER_VERTICAL);
            dock.addRule(RelativeLayout.BELOW, mAdditionalInfo.getId());
            dock.addRule(RelativeLayout.ALIGN_PARENT_START);
            // format and clock on one line: "Sat Nov 14 7:25 PM   720/30/VP9"
            quality.addRule(RelativeLayout.ALIGN_PARENT_END);
            date.removeRule(RelativeLayout.BELOW);
            date.removeRule(RelativeLayout.ALIGN_PARENT_END);
            date.addRule(RelativeLayout.START_OF, mQualityInfo.getId());
            date.setMarginEnd(Math.round(16 * TouchTarget.realDensity()));

            // "Remaining" and the time (as they are in the layout: one above the other, at the end) below the
            // second row of buttons
            time.addRule(RelativeLayout.BELOW, mSecondaryScroll.getId());
            time.addRule(RelativeLayout.ALIGN_PARENT_END);
            time.removeRule(RelativeLayout.CENTER_VERTICAL);
            secondary.addRule(RelativeLayout.ALIGN_PARENT_START);
            // the rows of buttons use the whole width and scroll
            dock.width = ViewGroup.LayoutParams.MATCH_PARENT;
            secondary.width = ViewGroup.LayoutParams.MATCH_PARENT;
        } else {
            // the layout file's own rules
            info.addRule(RelativeLayout.ALIGN_PARENT_END);
            info.removeRule(RelativeLayout.ALIGN_PARENT_TOP);
            info.addRule(RelativeLayout.CENTER_VERTICAL);
            dock.removeRule(RelativeLayout.BELOW);
            dock.addRule(RelativeLayout.ALIGN_PARENT_START);
            quality.addRule(RelativeLayout.ALIGN_PARENT_END);
            date.removeRule(RelativeLayout.START_OF);
            date.setMarginEnd(0);
            date.addRule(RelativeLayout.BELOW, mQualityInfo.getId());
            date.addRule(RelativeLayout.ALIGN_PARENT_END);

            time.removeRule(RelativeLayout.BELOW);
            time.addRule(RelativeLayout.ALIGN_PARENT_END);
            time.addRule(RelativeLayout.CENTER_VERTICAL);
            secondary.addRule(RelativeLayout.ALIGN_PARENT_START);
            dock.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            secondary.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            dock.setMarginEnd(0);
            secondary.setMarginEnd(0);
        }
        // The TV's side margins (13 % of the width on each side) leave a phone held upright room for 5 buttons
        // only; with a small margin 7 fit, and the seek bar is longer for the finger.
        int side = Math.round(16 * TouchTarget.realDensity());
        setSidePadding(mTransportRow, portrait ? side : mRowPaddingStart, portrait ? side : mRowPaddingEnd);
        setSidePadding(mControlsCard, portrait ? side : mCardPaddingStart, portrait ? side : mCardPaddingEnd);
        mAdditionalInfo.setLayoutParams(info);
        mControlsScroll.setLayoutParams(dock);
        mQualityInfo.setLayoutParams(quality);
        mDateTime.setLayoutParams(date);
        mTimeInfo.setLayoutParams(time);
        mSecondaryScroll.setLayoutParams(secondary);
    }

    /**
     * Upright, when a row has more buttons than fit, the scroller is made to end in the middle of a button, so
     * the next one shows partly at the edge. margin = what the row is narrower than the space it has.
     */
    private void showThereIsMore(HorizontalScrollView scroll) {
        RelativeLayout.LayoutParams lp = params(scroll);
        if (lp == null || scroll.getChildCount() == 0 || !(scroll.getChildAt(0) instanceof ViewGroup)) {
            return;
        }
        ViewGroup dock = (ViewGroup) scroll.getChildAt(0);
        int margin = 0;
        if (mApplied && mPortraitApplied && dock.getChildCount() > 0 && dock.getChildAt(0) instanceof ViewGroup) {
            ViewGroup bar = (ViewGroup) dock.getChildAt(0);
            int space = scroll.getWidth() + lp.getMarginEnd();
            int pitch = bar.getChildCount() > 1 ? bar.getChildAt(1).getLeft() - bar.getChildAt(0).getLeft() : 0;
            margin = rightMarginForPartialButton(space, dock.getWidth(), pitch);
        }
        if (margin != lp.getMarginEnd()) {
            lp.setMarginEnd(margin);
            scroll.post(() -> scroll.setLayoutParams(lp));
        }
    }

    /**
     * How much narrower than {@code space} the row should be so that, when the content ({@code contentWidth})
     * does not fit, the last visible button is cut in half: whole buttons of the {@code pitch} (distance
     * between the starts of two buttons) plus half a pitch. 0 when everything fits or the pitch is unknown.
     */
    static int rightMarginForPartialButton(int space, int contentWidth, int pitch) {
        if (pitch <= 0 || contentWidth <= space) {
            return 0;
        }
        int whole = Math.max(1, (space - pitch * 2 / 5) / pitch);
        return Math.max(0, space - (whole * pitch + pitch / 2));
    }

    private static void setSidePadding(View view, int start, int end) {
        if (view != null) {
            view.setPaddingRelative(start, view.getPaddingTop(), end, view.getPaddingBottom());
        }
    }

    private static RelativeLayout.LayoutParams params(View view) {
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        return lp instanceof RelativeLayout.LayoutParams ? (RelativeLayout.LayoutParams) lp : null;
    }
}
