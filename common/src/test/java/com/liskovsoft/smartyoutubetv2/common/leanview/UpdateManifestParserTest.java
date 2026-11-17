package com.liskovsoft.smartyoutubetv2.common.leanview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.Uri;

import com.liskovsoft.appupdatechecker2.core.AppVersionChecker;
import com.liskovsoft.appupdatechecker2.core.AppVersionCheckerListener;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// leanview (review I-5): the update.json that scripts/leanview/make_update_json.py writes goes through the app's REAL parser,
// AppVersionChecker.triggerFromJson (private: called by reflection, the HTTP fetch around it needs a server). The fixture
// is checked against the generator by scripts/leanview/test_make_update_json.py, so neither side can drift alone.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class UpdateManifestParserTest {
    private static class Result implements AppVersionCheckerListener {
        Boolean isLatest;
        String latestName;
        int latestCode;
        List<String> changelog;
        Uri[] downloadUris;
        Exception error;

        @Override
        public void onChangelogReceived(boolean isLatestVersion, String latestVersionName, int latestVersionNumber, List<String> changelog, Uri[] downloadUris) {
            this.isLatest = isLatestVersion;
            this.latestName = latestVersionName;
            this.latestCode = latestVersionNumber;
            this.changelog = changelog;
            this.downloadUris = downloadUris;
        }

        @Override
        public void onCheckError(Exception e) {
            error = e;
        }

        @Override
        public void processDownloadUrls(Uri[] downloadUrls) {
        }
    }

    private static JSONObject fixture() throws Exception {
        File file = new File("src/test/resources/leanview/update.json");
        if (!file.isFile()) {
            file = new File("common/src/test/resources/leanview/update.json");
        }
        return new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
    }

    private static Result parse(JSONObject manifest, int installedVersionCode) throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        Result result = new Result();
        AppVersionChecker checker = new AppVersionChecker(context, result);
        Field installed = AppVersionChecker.class.getDeclaredField("mCurrentAppVersion");
        installed.setAccessible(true);
        installed.setInt(checker, installedVersionCode);
        Method trigger = AppVersionChecker.class.getDeclaredMethod("triggerFromJson", JSONObject.class);
        trigger.setAccessible(true);
        trigger.invoke(checker, manifest);
        return result;
    }

    @Test
    public void version10000SeesTheNewerReleaseAndItsChangelog() throws Exception {
        Result result = parse(fixture(), 10000);
        assertFalse(result.isLatest);
        assertEquals("1.0.1", result.latestName);
        assertEquals(10001, result.latestCode);
        assertEquals(Arrays.asList("Fixes"), result.changelog);
        assertEquals(1, result.downloadUris.length);
        assertTrue(result.downloadUris[0].toString().startsWith("https://github.com/leanview/leanview/releases/download/v1.0.1/Leanview_1.0.1_"));
        assertTrue(result.downloadUris[0].toString().endsWith(".apk"));
    }

    @Test
    public void aVersionBeforeBothGetsBothChangelogsNewestFirst() throws Exception {
        Result result = parse(fixture(), 9999);
        assertFalse(result.isLatest);
        assertEquals(Arrays.asList("Fixes", "First release"), new ArrayList<>(result.changelog));
    }

    @Test
    public void theInstalledLatestVersionNeedsNoUpdate() throws Exception {
        Result result = parse(fixture(), 10001);
        assertTrue(result.isLatest);
        assertEquals("1.0.1", result.latestName);
    }

    @Test
    public void theDownloadUrlIsTheOneOfTheDevicesAbiOrTheUniversalApk() throws Exception {
        String url = parse(fixture(), 10000).downloadUris[0].toString();
        assertTrue(url, url.endsWith("_arm64-v8a.apk") || url.endsWith("_armeabi-v7a.apk") || url.endsWith("_universal.apk"));
    }
}
