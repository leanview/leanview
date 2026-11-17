package com.liskovsoft.smartyoutubetv2.tv.ui.leanview;

import android.view.View;

import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.leanview.PhoneMode;
import com.liskovsoft.smartyoutubetv2.common.leanview.TouchTarget;

/**
 * leanview: the captions of a phone held upright use the whole video width. The layout keeps 150sp of
 * padding on each side for the TV, which on a 411 dp wide phone leaves a column about a third of the
 * screen wide, one word per line. Held sideways, and on the TV, the padding is the layout file's own.
 */
public final class PhoneCaptions {
    private PhoneCaptions() {
    }

    /** Does nothing off a phone. */
    public static void install(View subtitleView) {
        if (subtitleView == null || !PhoneMode.isPhone(subtitleView.getContext())) {
            return;
        }
        final int layoutPadding = subtitleView.getPaddingLeft();
        final int portraitDp = subtitleView.getResources().getInteger(R.integer.leanview_caption_side_padding_portrait_dp);
        subtitleView.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> apply(v, layoutPadding, portraitDp));
    }

    // The side padding for the way the screen is held now (the window's shape), set when it is not that already.
    static void apply(View v, int layoutPadding, int portraitDp) {
        View decor = v.getRootView();
        if (decor == null || decor.getWidth() <= 0 || decor.getHeight() <= 0) {
            return;
        }
        boolean portrait = PhoneMode.isPortrait(decor.getWidth(), decor.getHeight());
        int side = PhoneMode.captionSidePaddingPx(true, portrait, TouchTarget.realDensity(), layoutPadding, portraitDp);
        if (v.getPaddingLeft() != side || v.getPaddingRight() != side) {
            Runnable set = () -> v.setPadding(side, v.getPaddingTop(), side, v.getPaddingBottom());
            if (v.isAttachedToWindow()) {
                v.post(set); // not inside the window's layout pass
            } else {
                set.run();
            }
        }
    }
}
