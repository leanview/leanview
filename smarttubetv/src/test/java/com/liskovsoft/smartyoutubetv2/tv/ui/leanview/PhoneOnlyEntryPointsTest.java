package com.liskovsoft.smartyoutubetv2.tv.ui.leanview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.liskovsoft.smartyoutubetv2.common.leanview.ScreenType;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.misc.SeekBar;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

// leanview (review I-2, M-1, M-3): every entry point of the phone changes leaves the TV alone. The contexts are
// the two that matter: a TV that ALSO reports a touchscreen (touch + leanback + television), and a phone
// (touch only). Each test shows the phone side changes something (so the test can fail) and the TV side does not.
// ConscryptMode OFF: this module has no conscrypt-openjdk native library for the JVM (only the Android one), and the
// views need no TLS; no dependency was added for it.
@RunWith(RobolectricTestRunner.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = 28, qualifiers = "420dpi")
public class PhoneOnlyEntryPointsTest {
    private static Context tv() {
        return device(true, true, true);
    }

    private static Context phone() {
        return device(true, false, false);
    }

    private static Context device(boolean touch, boolean leanback, boolean television) {
        Context context = RuntimeEnvironment.getApplication();
        PackageManager pm = context.getPackageManager();
        shadowOf(pm).setSystemFeature(PackageManager.FEATURE_TOUCHSCREEN, touch);
        shadowOf(pm).setSystemFeature(PackageManager.FEATURE_LEANBACK, leanback);
        shadowOf(pm).setSystemFeature(PackageManager.FEATURE_TELEVISION, television);
        ScreenType.set(context, ScreenType.AUTOMATIC);
        return context;
    }

    // --- cards: the first tap opens ---

    @Test
    public void aCardKeepsTouchModeFocusOnTheTv() {
        View card = new View(tv());
        card.setFocusableInTouchMode(true);
        PhoneTouch.tapOpensDirectly(card);
        assertTrue(card.isFocusableInTouchMode());
    }

    @Test
    public void aCardOpensOnTheFirstTapOnAPhone() {
        View card = new View(phone());
        card.setFocusableInTouchMode(true);
        PhoneTouch.tapOpensDirectly(card);
        assertFalse(card.isFocusableInTouchMode());
    }

    // --- buttons: 48 real dp ---

    private static View buttonOf(Context context, int px) {
        View button = new View(context);
        button.setLayoutParams(new FrameLayout.LayoutParams(px, px));
        return button;
    }

    @Test
    public void theTvButtonsKeepTheirSize() {
        View button = buttonOf(tv(), 117);
        PhoneTouch.growToTouchTarget(button);
        assertEquals(117, button.getLayoutParams().width);
        assertEquals(117, button.getLayoutParams().height);
    }

    @Test
    public void aPhoneButtonGrowsTo48RealDp() {
        View button = buttonOf(phone(), 117);
        PhoneTouch.growToTouchTarget(button);
        assertEquals(126, button.getLayoutParams().width); // 48 dp at 420 dpi
        assertEquals(126, button.getLayoutParams().height);
    }

    // --- the seek bar ---

    private static class Recorder extends SeekBar.AccessibilitySeekListener {
        int starts, moves, ends, endsCancelled, legacy;
        boolean allow = true;
        int lastProgress = -1;

        @Override
        public boolean onAccessibilitySeekForward() {
            return false;
        }

        @Override
        public boolean onAccessibilitySeekBackward() {
            return false;
        }

        @Override
        public boolean onAccessibilitySeekProgress(int progress) {
            legacy++;
            return true;
        }

        @Override
        public boolean onTouchSeekStart() {
            starts++;
            return allow;
        }

        @Override
        public void onTouchSeekMove(int progress) {
            moves++;
            lastProgress = progress;
        }

        @Override
        public void onTouchSeekEnd(boolean cancelled) {
            ends++;
            if (cancelled) {
                endsCancelled++;
            }
        }
    }

    private static SeekBar barOf(Context context, Recorder recorder) {
        SeekBar bar = new SeekBar(context, null);
        bar.setLayoutParams(new LinearLayout.LayoutParams(808, 68));
        bar.setMax(Integer.MAX_VALUE);
        bar.setAccessibilitySeekListener(recorder);
        PhoneTouch.setUpSeekBar(bar);
        bar.layout(0, 0, 808, 126);
        return bar;
    }

    private static void touch(SeekBar bar, int action, float x) {
        long now = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(now, now, action, x, 60, 0);
        bar.dispatchTouchEvent(event);
        event.recycle();
    }

    @Test
    public void theTvBarKeepsItsOldTouchCodeAndItsHeight() {
        Recorder recorder = new Recorder();
        SeekBar bar = barOf(tv(), recorder);

        touch(bar, MotionEvent.ACTION_DOWN, 300);
        touch(bar, MotionEvent.ACTION_MOVE, 400);
        touch(bar, MotionEvent.ACTION_UP, 400);

        assertEquals(0, recorder.starts);
        assertEquals(0, recorder.moves);
        assertEquals(0, recorder.ends);
        assertTrue(recorder.legacy > 0); // the old path, which seeks on every event
        assertEquals(68, bar.getLayoutParams().height);
    }

    @Test
    public void aPhoneBarSeeksOnReleaseAndIsTall() {
        Recorder recorder = new Recorder();
        SeekBar bar = barOf(phone(), recorder);

        touch(bar, MotionEvent.ACTION_DOWN, 300);
        touch(bar, MotionEvent.ACTION_MOVE, 400);
        touch(bar, MotionEvent.ACTION_UP, 400);

        assertEquals(1, recorder.starts);
        assertTrue(recorder.moves >= 2);
        assertEquals(1, recorder.ends);
        assertEquals(0, recorder.endsCancelled);
        assertEquals(0, recorder.legacy);
        assertEquals(126, bar.getLayoutParams().height);
    }

    @Test
    public void aCancelledTouchTellsTheListenerItWasCancelled() {
        Recorder recorder = new Recorder();
        SeekBar bar = barOf(phone(), recorder);

        touch(bar, MotionEvent.ACTION_DOWN, 300);
        touch(bar, MotionEvent.ACTION_CANCEL, 300);

        assertEquals(1, recorder.endsCancelled);
    }

    // M-3: the knob really is drawn under the finger (SeekBar.calculate's own geometry, not a helper's inverse)
    @Test
    public void theKnobIsDrawnUnderTheFinger() {
        SeekBar bar = barOf(phone(), new Recorder());

        for (float x : new float[]{100f, 333f, 560f, 700f}) {
            touch(bar, MotionEvent.ACTION_DOWN, x);
            assertEquals("finger at " + x, x, bar.getKnobX(), 1.5f);
            touch(bar, MotionEvent.ACTION_UP, x);
        }
    }

    @Test
    public void theKnobStopsAtTheEndsOfTheBar() {
        SeekBar bar = barOf(phone(), new Recorder());
        int radius = RuntimeEnvironment.getApplication().getResources().getDimensionPixelSize(
                androidx.leanback.R.dimen.lb_playback_transport_progressbar_active_radius);

        touch(bar, MotionEvent.ACTION_DOWN, 5000);
        assertEquals(808 - radius, bar.getKnobX(), 1.5f);
        touch(bar, MotionEvent.ACTION_MOVE, -300);
        assertEquals(radius, bar.getKnobX(), 1.5f);
        touch(bar, MotionEvent.ACTION_UP, -300);
    }

    // M-1: nothing to seek (live): the knob does not follow the finger and the player is not asked
    @Test
    public void aRefusedGestureMovesNothing() {
        Recorder recorder = new Recorder();
        recorder.allow = false;
        SeekBar bar = barOf(phone(), recorder);

        touch(bar, MotionEvent.ACTION_DOWN, 600);
        touch(bar, MotionEvent.ACTION_MOVE, 700);
        touch(bar, MotionEvent.ACTION_UP, 700);

        assertEquals(1, recorder.starts);
        assertEquals(0, recorder.moves);
        assertEquals(0, recorder.ends);
        assertEquals(0, bar.getProgress());
        // and the next gesture is asked again
        recorder.allow = true;
        touch(bar, MotionEvent.ACTION_DOWN, 600);
        assertEquals(2, recorder.starts);
        assertEquals(1, recorder.moves);
    }

    // --- the rows of buttons ---

    private static class Rows {
        final LinearLayout root;
        final RelativeLayout first, second;
        final FrameLayout dock, secondaryDock;
        final View additionalInfo, quality, date, timeInfo;

        Rows(Context context) {
            root = new LinearLayout(context);
            first = new RelativeLayout(context);
            second = new RelativeLayout(context);
            dock = new FrameLayout(context);
            secondaryDock = new FrameLayout(context);
            additionalInfo = new RelativeLayout(context);
            quality = new View(context);
            date = new View(context);
            timeInfo = new RelativeLayout(context);
            for (View v : new View[]{dock, secondaryDock, additionalInfo, quality, date, timeInfo, first, second}) {
                v.setId(View.generateViewId());
            }
            ((RelativeLayout) additionalInfo).addView(quality, new RelativeLayout.LayoutParams(-2, -2));
            ((RelativeLayout) additionalInfo).addView(date, new RelativeLayout.LayoutParams(-2, -2));
            first.addView(dock, new RelativeLayout.LayoutParams(-2, -2));
            first.addView(additionalInfo, new RelativeLayout.LayoutParams(-2, -2));
            second.addView(secondaryDock, new RelativeLayout.LayoutParams(-2, -2));
            second.addView(timeInfo, new RelativeLayout.LayoutParams(-2, -2));
            root.addView(first);
            root.addView(second);
        }

        void install() {
            PhoneOsdLayout.install(root, dock, additionalInfo, quality, date, secondaryDock, timeInfo);
        }
    }

    @Test
    public void theTvRowsAreNotTouched() {
        Rows rows = new Rows(tv());
        ViewGroup.LayoutParams dockParams = rows.dock.getLayoutParams();

        rows.install();

        assertSame(rows.first, rows.dock.getParent());
        assertSame(rows.second, rows.secondaryDock.getParent());
        assertSame(dockParams, rows.dock.getLayoutParams());
        assertEquals(2, rows.first.getChildCount());
        assertEquals(2, rows.second.getChildCount());
    }

    @Test
    public void thePhoneRowsScrollSideways() {
        Rows rows = new Rows(phone());

        rows.install();

        assertTrue(rows.dock.getParent() instanceof HorizontalScrollView);
        assertTrue(rows.secondaryDock.getParent() instanceof HorizontalScrollView);
        // the scroller took the dock's place in the row
        assertSame(rows.first, ((View) rows.dock.getParent()).getParent());
        assertEquals(0, rows.first.indexOfChild((View) rows.dock.getParent()));
        assertEquals(2, rows.first.getChildCount());
    }

    @Test
    public void theNextButtonShowsPartlyAtTheEdgeWhenTheRowDoesNotFit() {
        // 1025 px of room, buttons every 143 px, nine of them (1287 px)
        int margin = PhoneOsdLayout.rightMarginForPartialButton(1025, 1287, 143);
        int visible = 1025 - margin;
        assertEquals(143 / 2, visible % 143); // the last button is cut in half
        assertTrue(visible >= 5 * 143); // at least five whole buttons are there
    }

    @Test
    public void aRowThatFitsIsLeftAlone() {
        assertEquals(0, PhoneOsdLayout.rightMarginForPartialButton(1025, 900, 143));
        assertEquals(0, PhoneOsdLayout.rightMarginForPartialButton(1025, 1287, 0));
    }

    // --- captions ---

    private static View captionsInWindow(Context context, int w, int h) {
        FrameLayout window = new FrameLayout(context);
        View captions = new View(context);
        captions.setPadding(394, 0, 394, 0);
        window.addView(captions, new FrameLayout.LayoutParams(-1, -1));
        PhoneCaptions.install(captions);
        window.layout(0, 0, w, h);
        captions.layout(0, 0, w, h);
        shadowOf(Looper.getMainLooper()).idle();
        return captions;
    }

    @Test
    public void theTvCaptionsKeepTheirPadding() {
        View captions = captionsInWindow(tv(), 1080, 2400);
        assertEquals(394, captions.getPaddingLeft());
        assertEquals(394, captions.getPaddingRight());
    }

    @Test
    public void aPhoneHeldUprightGetsFullWidthCaptions() {
        View captions = captionsInWindow(phone(), 1080, 2400);
        assertEquals(32, captions.getPaddingLeft()); // 12 dp at 420 dpi
        assertEquals(32, captions.getPaddingRight());
    }

    @Test
    public void aPhoneHeldSidewaysKeepsTheLayoutPadding() {
        View captions = captionsInWindow(phone(), 2400, 1080);
        assertEquals(394, captions.getPaddingLeft());
    }
}
