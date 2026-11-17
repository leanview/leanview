package com.liskovsoft.smartyoutubetv2.tv.ui.leanview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.liskovsoft.smartyoutubetv2.common.leanview.LegalTexts;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

// leanview: the leanview build bundles public-repo/LICENSE and public-repo/THIRD_PARTY_NOTICES.md as assets (Settings >
// About > Licenses). The texts must be the very files of the public repo, copied at build time (not a second copy kept
// by hand), so they are compared byte for byte with the files next to the module. src/testLeanview: this flavor only.
@RunWith(RobolectricTestRunner.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = 28)
public class LegalAssetsTest {
    // snapshot-layout-fallback: the legal documents are in public-repo/ in the development tree and at the ROOT in a source snapshot
    private static String repoFile(String name) throws Exception {
        File file = new File("../public-repo/" + name);
        if (!file.isFile()) {
            file = new File("../" + name);
        }
        assertTrue(file.getAbsolutePath() + " must exist", file.isFile());
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static LegalTexts.Source assets() {
        return LegalTexts.fromAssets(RuntimeEnvironment.getApplication());
    }

    @Test
    public void theLicenseAssetIsThePublicReposLicense() throws Exception {
        String asset = assets().read(LegalTexts.LICENSE);
        assertNotNull("assets/leanview_legal/LICENSE is bundled", asset);
        assertEquals(repoFile("LICENSE"), asset);
        assertTrue(asset.contains("GNU GENERAL PUBLIC LICENSE"));
        assertTrue(asset.contains("Version 3, 29 June 2007"));
    }

    @Test
    public void theNoticeAssetKeepsSmartTubesMitNoticeAndNamesTheGplCode() throws Exception {
        String asset = assets().read(LegalTexts.NOTICE);
        assertNotNull("assets/leanview_legal/NOTICE.md is bundled", asset);
        assertEquals(repoFile("NOTICE.md"), asset);
        assertTrue("the holder is the contributors (owner ruling 2026-11-16)", asset.contains("Copyright (c) 2026 Leanview contributors"));
        assertTrue("SmartTube's MIT notice stays, verbatim", asset.contains("MIT License\n\nCopyright (c) 2020-present yuliskov\n"));
        assertTrue(asset.contains("NewPipe"));
        assertTrue(asset.contains("bgutil-ytdlp-pot-provider"));
        assertTrue(asset.contains("GPL-3.0"));
    }

    @Test
    public void theNoticesAssetIsTheGeneratedFileAndListsQuickJsInsteadOfJ2v8() throws Exception {
        String asset = assets().read(LegalTexts.NOTICES);
        assertNotNull("assets/leanview_legal/THIRD_PARTY_NOTICES.md is bundled", asset);
        assertEquals(repoFile("THIRD_PARTY_NOTICES.md"), asset);
        assertFalse("J2V8 (EPL-1.0) is gone from the build and from the notices", asset.toLowerCase().contains("j2v8"));
        assertFalse(asset.contains("Eclipse Public License"));
        int quickJs = asset.indexOf("### QuickJS JavaScript engine");
        assertTrue(quickJs > 0);
        assertTrue(asset.substring(quickJs, quickJs + 200).contains("License: MIT"));
        assertTrue(asset.contains("Fabrice Bellard"));
        assertTrue("the bundled ejs says it is modified", asset.contains("Modified by Leanview when the file is loaded"));
    }

    @Test
    public void noOwnerNameIsInTheBundledTexts() throws Exception {
        for (String path : new String[]{LegalTexts.LICENSE, LegalTexts.NOTICE, LegalTexts.NOTICES}) {
            String lower = assets().read(path).toLowerCase();
            for (String needle : new String[]{"lg" + "obbi", "le" + "ogobb", "lean" + "dro", "go" + "bbi"}) {
                assertFalse(path + " names the author (" + needle + ")", lower.contains(needle));
            }
        }
    }
}
