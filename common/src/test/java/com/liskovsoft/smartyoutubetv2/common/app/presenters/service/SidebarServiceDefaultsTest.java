package com.liskovsoft.smartyoutubetv2.common.app.presenters.service;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.leanview.SidebarDefaults;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// leanview: the left menu is saved with the version of the defaults it was switched to. The test build
// is not the leanview flavor (R.bool.leanview_hide_topic_sections is false), so there the menu itself must
// stay as it was while the version is stored once; the leanview cases use a context that answers true.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class SidebarServiceDefaultsTest {
    private Context mContext;

    @Before
    public void setUp() throws Exception {
        mContext = RuntimeEnvironment.getApplication();
        forget(SidebarService.class);
        forget(AppPrefs.class);
    }

    private static void forget(Class<?> singleton) throws Exception {
        Field instance = singleton.getDeclaredField("sInstance");
        instance.setAccessible(true);
        instance.set(null, null);
    }

    private static List<Video> sections(int... ids) {
        List<Video> items = new ArrayList<>();
        for (int id : ids) {
            Video item = new Video();
            item.sectionId = id;
            items.add(item);
        }
        return items;
    }

    /** A context whose resources answer R.bool.leanview_hide_topic_sections with true, like leanview. */
    private Context leanview() {
        final Resources base = mContext.getResources();
        final Resources hiding = new Resources(base.getAssets(), base.getDisplayMetrics(), base.getConfiguration()) {
            @Override
            public boolean getBoolean(int id) {
                return id == R.bool.leanview_hide_topic_sections || super.getBoolean(id);
            }
        };

        return new ContextWrapper(mContext) {
            @Override
            public Resources getResources() {
                return hiding;
            }

            @Override
            public Context getApplicationContext() {
                return this;
            }
        };
    }

    private static List<Integer> ids(SidebarService service) {
        List<Integer> ids = new ArrayList<>();
        for (Video item : service.getPinnedItems()) {
            ids.add(item.sectionId);
        }
        return ids;
    }

    private static final List<Integer> FRESH = Arrays.asList(
            MediaGroup.TYPE_HOME, MediaGroup.TYPE_SHORTS, MediaGroup.TYPE_CHANNEL_UPLOADS,
            MediaGroup.TYPE_SUBSCRIPTIONS, MediaGroup.TYPE_MUSIC, MediaGroup.TYPE_HISTORY,
            MediaGroup.TYPE_USER_PLAYLISTS, MediaGroup.TYPE_MY_VIDEOS, MediaGroup.TYPE_SETTINGS);

    private String[] saved() {
        return Helpers.splitData(AppPrefs.instance(mContext).getSidebarData());
    }

    @Test
    public void aNewMenuIsSavedAtTheCurrentVersion() {
        SidebarService.instance(mContext);

        assertEquals(String.valueOf(SidebarDefaults.VERSION), saved()[3]);
    }

    @Test
    public void anOldMenuKeepsItsSectionsAndGetsTheVersionOnce() throws Exception {
        AppPrefs.instance(mContext).setSidebarData(Helpers.mergeData(
                sections(MediaGroup.TYPE_HOME, MediaGroup.TYPE_SHORTS, MediaGroup.TYPE_KIDS_HOME, MediaGroup.TYPE_SETTINGS),
                MediaGroup.TYPE_HOME, true));

        assertEquals(3, saved().length);
        SidebarService service = SidebarService.instance(mContext);

        assertEquals(4, service.getPinnedItems().size());
        assertEquals(String.valueOf(SidebarDefaults.VERSION), saved()[3]);

        // Opening it again changes nothing.
        String before = AppPrefs.instance(mContext).getSidebarData();
        forget(SidebarService.class);
        SidebarService again = SidebarService.instance(mContext);
        assertEquals(4, again.getPinnedItems().size());
        assertEquals(before, AppPrefs.instance(mContext).getSidebarData());
    }

    @Test
    public void leanviewFreshInstallHasMusicRightAfterSubscriptionsAndNoOtherTopic() {
        SidebarService service = SidebarService.instance(leanview());

        assertEquals(FRESH, ids(service));
        assertEquals(String.valueOf(SidebarDefaults.VERSION), saved()[3]);
    }

    @Test
    public void leanviewVersionOneDefaultGetsMusicAfterSubscriptionsOnce() throws Exception {
        AppPrefs.instance(mContext).setSidebarData(Helpers.mergeData(
                sections(MediaGroup.TYPE_HOME, MediaGroup.TYPE_SHORTS, MediaGroup.TYPE_CHANNEL_UPLOADS,
                        MediaGroup.TYPE_SUBSCRIPTIONS, MediaGroup.TYPE_HISTORY, MediaGroup.TYPE_USER_PLAYLISTS,
                        MediaGroup.TYPE_MY_VIDEOS, MediaGroup.TYPE_SETTINGS),
                MediaGroup.TYPE_HOME, true, 1));

        assertEquals("1", saved()[3]);
        Context leanview = leanview();
        SidebarService service = SidebarService.instance(leanview);

        assertEquals(FRESH, ids(service));
        assertEquals(String.valueOf(SidebarDefaults.VERSION), saved()[3]);

        // Opening it again changes nothing.
        String before = AppPrefs.instance(mContext).getSidebarData();
        forget(SidebarService.class);
        assertEquals(FRESH, ids(SidebarService.instance(leanview)));
        assertEquals(before, AppPrefs.instance(mContext).getSidebarData());
    }

    @Test
    public void leanviewHandEditedVersionOneMenuIsLeftAlone() {
        List<Integer> arranged = Arrays.asList(
                MediaGroup.TYPE_SHORTS, MediaGroup.TYPE_HOME, MediaGroup.TYPE_CHANNEL_UPLOADS,
                MediaGroup.TYPE_SUBSCRIPTIONS, MediaGroup.TYPE_HISTORY, MediaGroup.TYPE_SETTINGS);
        int[] ids = new int[arranged.size()];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = arranged.get(i);
        }
        AppPrefs.instance(mContext).setSidebarData(Helpers.mergeData(
                sections(ids), MediaGroup.TYPE_HOME, true, 1));

        SidebarService service = SidebarService.instance(leanview());

        assertEquals(arranged, ids(service));
        assertEquals(String.valueOf(SidebarDefaults.VERSION), saved()[3]);
    }

    @Test
    public void leanviewMenuWithMusicAlreadyAfterSubscriptionsAtVersionOneStaysTheSame() {
        List<Integer> arranged = Arrays.asList(
                MediaGroup.TYPE_HOME, MediaGroup.TYPE_SHORTS, MediaGroup.TYPE_CHANNEL_UPLOADS,
                MediaGroup.TYPE_SUBSCRIPTIONS, MediaGroup.TYPE_MUSIC, MediaGroup.TYPE_HISTORY,
                MediaGroup.TYPE_SETTINGS);
        AppPrefs.instance(mContext).setSidebarData(Helpers.mergeData(
                sections(MediaGroup.TYPE_HOME, MediaGroup.TYPE_SHORTS, MediaGroup.TYPE_CHANNEL_UPLOADS,
                        MediaGroup.TYPE_SUBSCRIPTIONS, MediaGroup.TYPE_MUSIC, MediaGroup.TYPE_HISTORY,
                        MediaGroup.TYPE_SETTINGS),
                MediaGroup.TYPE_HOME, true, 1));

        SidebarService service = SidebarService.instance(leanview());

        assertEquals(arranged, ids(service));
    }
}
