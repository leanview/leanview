package com.liskovsoft.smartyoutubetv2.common.leanview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/**
 * leanview: every started activity of the process counts as a screen of the app, not only MotherActivity (final review, M2):
 * opening a library's activity (the file picker) over Settings must not look like the app went away.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class StartedScreensTest {
    /** A plain activity, like filepicker-lib's FilePickerActivity or BackupReceiverActivity: NOT a MotherActivity. */
    public static class LibraryScreen extends Activity {
    }

    private Application mApp;
    private AccountPicker mPicker;
    private StartedScreens mScreens;
    private int mStops;

    @Before
    public void setUp() {
        mApp = RuntimeEnvironment.getApplication();
        mPicker = new AccountPicker();
        mScreens = new StartedScreens(mPicker, context -> AccountPicker.NEVER, activity -> mStops++);
        mApp.registerActivityLifecycleCallbacks(mScreens);
    }

    @After
    public void tearDown() {
        mApp.unregisterActivityLifecycleCallbacks(mScreens);
    }

    @Test
    public void aLibraryActivityOverTheAppKeepsItInFront() {
        ActivityController<Activity> settings = Robolectric.buildActivity(Activity.class).setup();
        assertFalse("a screen is started", mPicker.isAway());

        // the file picker starts, then the settings screen behind it stops
        ActivityController<LibraryScreen> picker = Robolectric.buildActivity(LibraryScreen.class).setup();
        settings.pause().stop();

        assertFalse("the file picker is a screen of the app: not away", mPicker.isAway());
        assertEquals("the wedge guard heard the stop", 1, mStops);

        picker.pause().stop();
        assertTrue("nothing started: away", mPicker.isAway());
        assertEquals(2, mStops);
    }

    @Test
    public void aTrampolineThatFinishesAtOnceLeavesTheCountBalanced() {
        ActivityController<LibraryScreen> trampoline = Robolectric.buildActivity(LibraryScreen.class).setup();
        ActivityController<Activity> dialog = Robolectric.buildActivity(Activity.class).setup();
        trampoline.pause().stop().destroy();
        assertFalse(mPicker.isAway());

        dialog.pause().stop();
        assertTrue(mPicker.isAway());
    }
}
