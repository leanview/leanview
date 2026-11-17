package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;

import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

// leanview: About must not offer what is downloaded from SmartTube's releases (the voice-search "bridge" APKs) in a flavor
// that sets R.bool.leanview_hide_official_channel (leanview). This builds the real About dialogs against a recording dialog
// presenter and looks for the button by its title, so removing either guard in the presenters fails it.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class OfficialChannelAboutTest {
    private static class Recorder extends AppDialogPresenter {
        final List<String> titles = new ArrayList<>();

        Recorder(Context context) {
            super(context);
        }

        private void add(OptionItem item) {
            titles.add(String.valueOf(item.getTitle()));
        }

        @Override
        public void appendSingleButton(OptionItem optionItem) {
            add(optionItem);
        }

        @Override
        public void appendSingleSwitch(OptionItem optionItem) {
            add(optionItem);
        }

        @Override
        public void appendRadioCategory(CharSequence categoryTitle, List<OptionItem> items) {
            titles.add(String.valueOf(categoryTitle));
        }

        @Override
        public void appendStringsCategory(CharSequence categoryTitle, List<OptionItem> items) {
            titles.add(String.valueOf(categoryTitle));
        }

        @Override
        public void showDialog(CharSequence dialogTitle) {
            // nothing: no screen in a unit test
        }
    }

    private Object savedInstance;
    private Recorder recorder;

    private static Field instanceField() throws Exception {
        Field field = AppDialogPresenter.class.getDeclaredField("sInstance");
        field.setAccessible(true);
        return field;
    }

    @Before
    public void installTheRecorder() throws Exception {
        savedInstance = instanceField().get(null);
        recorder = new Recorder(RuntimeEnvironment.getApplication());
        instanceField().set(null, recorder);
    }

    @After
    public void putTheDialogPresenterBack() throws Exception {
        instanceField().set(null, savedInstance);
    }

    /** A context whose resources answer R.bool.leanview_hide_official_channel with {@code hide}. */
    private static Context flavor(final boolean hide) {
        final Context app = RuntimeEnvironment.getApplication();
        final Resources base = app.getResources();
        final Resources resources = new Resources(base.getAssets(), base.getDisplayMetrics(), base.getConfiguration()) {
            @Override
            public boolean getBoolean(int id) {
                return id == R.bool.leanview_hide_official_channel ? hide : super.getBoolean(id);
            }
        };
        return new ContextWrapper(app) {
            @Override
            public Resources getResources() {
                return resources;
            }

            @Override
            public Context getApplicationContext() {
                return this;
            }
        };
    }

    @Test
    public void theFullAboutOffersTheBridgeInAFlavorThatDoesNotHideIt() {
        Context context = flavor(false);
        AboutSettingsPresenter.instance(context).show();
        assertTrue(recorder.titles.toString(), recorder.titles.contains(context.getString(R.string.install_bridge)));
    }

    @Test
    public void theFullAboutHidesTheBridgeInLeanview() {
        Context context = flavor(true);
        AboutSettingsPresenter.instance(context).show();
        assertFalse(recorder.titles.toString(), recorder.titles.contains(context.getString(R.string.install_bridge)));
        assertTrue("the rest of About is still there", recorder.titles.contains(context.getString(R.string.check_for_updates)));
    }

    @Test
    public void theSimpleAboutOffersTheBridgeInAFlavorThatDoesNotHideIt() {
        Context context = flavor(false);
        AboutSimpleSettingsPresenter.instance(context).show();
        assertTrue(recorder.titles.toString(), recorder.titles.contains(context.getString(R.string.enable_voice_search)));
    }

    @Test
    public void theSimpleAboutHidesTheBridgeInLeanview() {
        Context context = flavor(true);
        AboutSimpleSettingsPresenter.instance(context).show();
        assertFalse(recorder.titles.toString(), recorder.titles.contains(context.getString(R.string.enable_voice_search)));
        assertTrue("the rest of About is still there", recorder.titles.contains(context.getString(R.string.check_for_updates)));
    }
}
