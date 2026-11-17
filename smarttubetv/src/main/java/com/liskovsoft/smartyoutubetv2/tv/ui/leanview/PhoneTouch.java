package com.liskovsoft.smartyoutubetv2.tv.ui.leanview;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import com.liskovsoft.smartyoutubetv2.common.leanview.PhoneMode;
import com.liskovsoft.smartyoutubetv2.common.leanview.TouchTarget;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.misc.SeekBar;

/**
 * leanview: view tweaks for a finger. Every method is a no-op unless {@link PhoneMode#isPhone} says the
 * device is a phone, so the TV is never touched.
 */
public final class PhoneTouch {
    private PhoneTouch() {
    }

    /**
     * Makes the view at least the touch minimum (48 dp of the real screen) wide and tall by growing its
     * layout size; whatever it draws inside keeps its size (the icons are centred in their button).
     */
    public static void growToTouchTarget(View view) {
        if (view == null || !PhoneMode.isPhone(view.getContext())) {
            return;
        }
        int min = TouchTarget.minPx(view.getContext());
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp == null) {
            return;
        }
        if (lp.width > 0) {
            lp.width = TouchTarget.atLeast(lp.width, min);
        }
        if (lp.height > 0) {
            lp.height = TouchTarget.atLeast(lp.height, min);
        }
        view.setLayoutParams(lp);
    }

    /** Makes only the height at least the touch minimum (a wide, thin target such as the seek bar). */
    public static void growHeightToTouchTarget(View view) {
        if (view == null || !PhoneMode.isPhone(view.getContext())) {
            return;
        }
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp != null && lp.height > 0) {
            lp.height = TouchTarget.atLeast(lp.height, TouchTarget.minPx(view.getContext()));
            view.setLayoutParams(lp);
        }
    }

    /**
     * The seek bar on a phone: finger seeking on (SeekBar.setTouchSeek) and a bar at least 48 dp tall. The one
     * place that decides it, so the TV never gets it (a test builds both kinds of context).
     */
    public static void setUpSeekBar(SeekBar bar) {
        if (bar == null || !PhoneMode.isPhone(bar.getContext())) {
            return;
        }
        bar.setTouchSeek(true);
        growHeightToTouchTarget(bar);
    }

    /** Cards and chips: a first tap opens them, it does not just move the focus there. */
    public static void tapOpensDirectly(View view) {
        Context context = view.getContext();
        if (PhoneMode.isPhone(context)) {
            view.setFocusableInTouchMode(false);
        }
    }
}
