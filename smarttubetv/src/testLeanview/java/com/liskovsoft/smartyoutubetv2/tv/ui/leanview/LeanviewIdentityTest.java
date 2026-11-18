package com.liskovsoft.smartyoutubetv2.tv.ui.leanview;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.util.TypedValue;

import com.liskovsoft.smartyoutubetv2.common.leanview.BackupFolders;
import com.liskovsoft.smartyoutubetv2.tv.BuildConfig;
import com.liskovsoft.smartyoutubetv2.tv.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// leanview: the leanview flavor is "LeanView" (decided 2026-11-14): its own package, its own version, its own
// update channel, and no visible "SmartTube" or personal handle anywhere in its text. This test lives in the flavor's
// own test source set (src/testLeanview), so it runs with testLeanviewDebugUnitTest only: the other flavors keep
// upstream's identity. The built APK is checked the same way by scripts/scan_apk_for_host.py --text.
// ConscryptMode OFF: this module has no conscrypt-openjdk native library for the JVM (only the Android one).
@RunWith(RobolectricTestRunner.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = 28)
public class LeanviewIdentityTest {
    /** The manifest that never moves: GitHub redirects "latest" to the newest release's update.json. */
    private static final String MANIFEST_URL = "https://github.com/leanview/leanview/releases/latest/download/update.json";

    private static Context context() {
        return RuntimeEnvironment.getApplication();
    }

    @Test
    public void theApplicationIdIsLeanview() {
        assertEquals("app.leanview.tv", context().getPackageName());
        assertEquals("app.leanview.tv", BuildConfig.APPLICATION_ID);
    }

    @Test
    public void theFirstReleaseIsOnePointZeroPointZero() {
        assertEquals("1.0.0", BuildConfig.VERSION_NAME);
        // major * 10000 + minor * 100 + patch: 1.0.0 = 10000, 1.0.1 = 10001, 1.1.0 = 10100, 2.0.0 = 20000
        assertEquals(10000, BuildConfig.VERSION_CODE);
    }

    @Test
    public void theUpdateUrlIsExactlyTheManifest() {
        assertArrayEquals(new String[]{MANIFEST_URL}, context().getResources().getStringArray(R.array.update_urls));
    }

    @Test
    public void noLinkLeadsToTheOfficialChannelOrTheAuthor() {
        Context c = context();
        List<String> links = new ArrayList<>();
        links.add(c.getString(R.string.releases_url));
        links.add(c.getString(R.string.sources_url));
        links.add(c.getString(R.string.web_site_url));
        // review M-7: the lists are EMPTY in this flavor (the upstream ones are SmartTube's author's); an empty loop would pass
        assertEquals(0, c.getResources().getStringArray(R.array.donations).length);
        assertEquals(0, c.getResources().getStringArray(R.array.feedback).length);
        for (String link : links) {
            assertTrue("a link to another channel: " + link, !mentionsSmartTubeOrAuthor(link));
        }
        assertTrue(c.getString(R.string.releases_url).startsWith("https://github.com/leanview/leanview"));
        assertTrue(c.getString(R.string.sources_url).startsWith("https://github.com/leanview/leanview"));
    }

    @Test
    public void theSearchProviderAuthorityIsThePackage() {
        assertEquals("app.leanview.tv", context().getString(R.string.search_authority));
    }

    @Test
    public void backupsGoToLeanviewBackupAndARestoreAlsoFindsTheOldFolder() {
        // review I-2: Documents / Drive show this name
        assertEquals("LeanviewBackup", BackupFolders.primary(context()));
        assertEquals(java.util.Arrays.asList("LeanviewBackup", "SmartTubeBackup"), BackupFolders.restoreRoots(BackupFolders.primary(context())));
    }

    @Test
    public void theAppIsCalledLeanview() {
        assertEquals("LeanView", context().getString(R.string.app_name));
        assertEquals("LeanView", context().getString(R.string.browse_title));
        assertEquals("LeanView", context().getString(R.string.search_label));
    }

    @Test
    @Config(sdk = 28, qualifiers = "es")
    public void theSearchSettingsAreNamedInSpanishToo() {
        assertEquals("LeanView", context().getString(R.string.search_label));
        assertEquals("Videos de LeanView", context().getString(R.string.settings_description));
    }

    @Test
    public void theOfficialChannelItemsAreHidden() {
        // R.bool.leanview_hide_official_channel: the "install the bridge" button downloads from SmartTube's releases
        int id = context().getResources().getIdentifier("leanview_hide_official_channel", "bool", context().getPackageName());
        assertTrue("the flag does not exist", id != 0);
        assertTrue(context().getResources().getBoolean(id));
    }

    @Test
    public void theIconsAndTheBannerAreLeanviewsOwnVectors() {
        int[] ids = {R.mipmap.app_icon, R.mipmap.app_icon_alt, R.mipmap.app_banner, R.mipmap.app_logo,
                R.mipmap.app_logo_semi_grey, R.mipmap.app_logo_semi_red, R.mipmap.ic_launcher /* leanbackassistant, unused, review M-6 */};
        for (int id : ids) {
            TypedValue value = new TypedValue();
            context().getResources().getValue(id, value, true);
            assertTrue("not a vector drawable (still SmartTube's picture?): " + value.string,
                    value.string != null && value.string.toString().endsWith(".xml"));
        }
    }

    @Test
    public void leanviewHasItsOwnTileOnTheTvAndOnAPhone() {
        PackageManager pm = context().getPackageManager();
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(category).setPackage(context().getPackageName());
            List<ResolveInfo> tiles = pm.queryIntentActivities(intent, 0);
            assertTrue("no " + category + " entry", !tiles.isEmpty());
            assertEquals("LeanView", tiles.get(0).loadLabel(pm).toString());
        }
    }

    @Test
    public void noTextTheUserCanSeeSaysSmartTubeOrTheOwnersHandleInAnyLanguage() throws Exception {
        List<String> offenders = new ArrayList<>();
        for (Locale locale : localesOfTheSources()) {
            Configuration configuration = new Configuration(context().getResources().getConfiguration());
            configuration.setLocale(locale);
            Context localized = context().createConfigurationContext(configuration);
            for (Field field : R.string.class.getFields()) {
                String value;
                try {
                    value = localized.getResources().getString(field.getInt(null));
                } catch (RuntimeException e) {
                    continue;
                }
                if (mentionsSmartTubeOrAuthor(value)) {
                    offenders.add(locale.toLanguageTag() + " string/" + field.getName() + ": " + value);
                }
            }
            for (Field field : R.array.class.getFields()) {
                String[] values;
                try {
                    values = localized.getResources().getStringArray(field.getInt(null));
                } catch (RuntimeException e) {
                    continue;
                }
                for (String value : values) {
                    if (value != null && mentionsSmartTubeOrAuthor(value)) {
                        offenders.add(locale.toLanguageTag() + " array/" + field.getName() + ": " + value);
                    }
                }
            }
        }
        assertTrue("visible text of another identity (" + offenders.size() + "): " + offenders, offenders.isEmpty());
    }

    private static boolean mentionsSmartTubeOrAuthor(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("smarttube") || lower.contains("smart tube") || lower.contains("lg" + "obbi") || lower.contains("yuliskov")
                || lower.contains("go" + "bbi") || lower.contains("lean" + "dro") || lower.contains("t.me/") || lower.contains("patreon")
                || lower.contains("paypal");
    }

    /** English plus every language folder the sources carry (a folder name like values-b+es+419 or values-pt-rBR). */
    private static List<Locale> localesOfTheSources() {
        Set<String> tags = new LinkedHashSet<>();
        tags.add("en");
        File root = new File("..");
        String[] resRoots = {"common/src/main/res", "leanbackassistant/src/main/res", "smarttubetv/src/main/res",
                "smarttubetv/src/leanview/res", "SharedModules/appupdatechecker2/src/main/res",
                "leanback-1.0.0/src/main/res", "exoplayer-amzn-2.10.6/library/ui/src/main/res"};
        for (String resRoot : resRoots) {
            String[] folders = new File(root, resRoot).list();
            if (folders == null) {
                continue;
            }
            for (String folder : folders) {
                if (!folder.startsWith("values-")) {
                    continue;
                }
                String qualifier = folder.substring("values-".length());
                String tag = qualifier.startsWith("b+") ? qualifier.substring(2).replace('+', '-') : qualifier.replace("-r", "-");
                Locale locale = Locale.forLanguageTag(tag);
                if (!locale.getLanguage().isEmpty() && tag.matches("[a-z]{2,3}(-[A-Za-z0-9]{2,4})?")) {
                    tags.add(tag);
                }
            }
        }
        List<Locale> locales = new ArrayList<>();
        for (String tag : tags) {
            locales.add(Locale.forLanguageTag(tag));
        }
        assertTrue("the language folders were not found from " + root.getAbsolutePath(), locales.size() > 20);
        return locales;
    }
}
