package com.liskovsoft.smartyoutubetv2.common.leanview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// leanview: Settings > About > Licenses (Leanview legal texts, public-repo/ bundled as assets by the flavor build).
// The pure pieces (paging, the dialog contents, the missing-asset case) are tested here against a fake text source;
// the real assets of the built flavor are checked by LegalAssetsTest in src/testLeanview.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class LicensesDialogTest {
    private static class Recorder extends AppDialogPresenter {
        final List<OptionItem> buttons = new ArrayList<>();
        final Map<String, String> longTexts = new LinkedHashMap<>();
        CharSequence shownTitle;

        Recorder(Context context) {
            super(context);
        }

        @Override
        public void appendSingleButton(OptionItem optionItem) {
            buttons.add(optionItem);
        }

        @Override
        public void appendLongTextCategory(CharSequence categoryTitle, OptionItem item) {
            longTexts.put(String.valueOf(categoryTitle), String.valueOf(item.getTitle()));
        }

        @Override
        public void showDialog(CharSequence dialogTitle) {
            shownTitle = dialogTitle;
        }
    }

    private static Context context() {
        return RuntimeEnvironment.getApplication();
    }

    private static LegalTexts.Source source(final String license, final String notices) {
        return path -> LegalTexts.LICENSE.equals(path) ? license : LegalTexts.NOTICES.equals(path) ? notices
                : LegalTexts.NOTICE.equals(path) ? "Leanview is based on SmartTube (MIT)." : null;
    }

    // ---- paging

    @Test
    public void aShortTextIsOnePage() {
        assertEquals(1, LegalTexts.pages("one\n\ntwo\n", 100).size());
    }

    @Test
    public void pagesBreakAtBlankLinesAndLoseNothing() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            text.append("paragraph ").append(i).append(" line one\nline two\n\n");
        }
        List<String> pages = LegalTexts.pages(text.toString(), 400);
        assertTrue(pages.size() > 3);
        StringBuilder joined = new StringBuilder();
        for (String page : pages) {
            assertTrue("page of " + page.length(), page.length() <= 400);
            assertTrue("a page starts with a paragraph, not with a blank line", !page.startsWith("\n"));
            joined.append(page).append("\n\n");
        }
        assertEquals(text.toString().replaceAll("\n+$", ""), joined.toString().replaceAll("\n+$", ""));
    }

    @Test
    public void aParagraphLongerThanThePageIsSplitAtALineEndOrHard() {
        StringBuilder long1 = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            long1.append("a line of the license number ").append(i).append("\n");
        }
        List<String> pages = LegalTexts.pages(long1.toString(), 300);
        assertTrue(pages.size() > 3);
        for (String page : pages) {
            assertTrue(page.length() <= 300);
        }
        StringBuilder noNewline = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            noNewline.append('x');
        }
        for (String page : LegalTexts.pages(noNewline.toString(), 300)) {
            assertTrue(page.length() <= 300);
        }
        assertEquals(1000, String.join("", LegalTexts.pages(noNewline.toString(), 300)).length());
    }

    @Test
    public void nothingToShowGivesNoPages() {
        assertTrue(LegalTexts.pages("", 100).isEmpty());
        assertTrue(LegalTexts.pages(null, 100).isEmpty());
        assertTrue(LegalTexts.pages("\n\n  \n", 100).isEmpty());
    }

    // ---- the dialog

    @Test
    public void withTheTextsThereIsOneLicensesButtonInAbout() {
        Recorder recorder = new Recorder(context());
        LicensesDialog.append(recorder, context(), source("MIT License\nCopyright (c) 2026 Leanview contributors\n", "# Notices\n"));
        assertEquals(1, recorder.buttons.size());
        assertEquals(context().getString(R.string.leanview_licenses), String.valueOf(recorder.buttons.get(0).getTitle()));
    }

    @Test
    public void withoutTheTextsThereIsNoButton() {
        Recorder none = new Recorder(context());
        LicensesDialog.append(none, context(), source(null, null));
        assertTrue("a build without the legal assets (other flavors) shows nothing", none.buttons.isEmpty());

        Recorder empty = new Recorder(context());
        LicensesDialog.append(empty, context(), source("   \n", "# Notices\n"));
        assertTrue("an empty LICENSE is a lost build step, not a license", empty.buttons.isEmpty());
    }

    @Test
    public void theButtonOpensTheLicenseAndEveryNoticesPage() {
        StringBuilder notices = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            notices.append("### component ").append(i).append("\nLicense: MIT\n\n");
        }
        Recorder recorder = new Recorder(context());
        LicensesDialog.show(recorder, context(), source("MIT License\nCopyright (c) 2026 Leanview contributors\n", notices.toString()), 300);
        assertEquals(context().getString(R.string.leanview_licenses), String.valueOf(recorder.shownTitle));
        List<String> titles = new ArrayList<>(recorder.longTexts.keySet());
        assertEquals(context().getString(R.string.leanview_licenses_license), titles.get(0));
        assertTrue(recorder.longTexts.get(titles.get(0)).contains("Copyright (c) 2026 Leanview contributors"));
        assertEquals(context().getString(R.string.leanview_licenses_notice), titles.get(1));
        assertTrue(recorder.longTexts.get(titles.get(1)).contains("SmartTube"));
        int pages = titles.size() - 2;
        assertTrue("the notices span several pages", pages > 2);
        for (int i = 2; i < titles.size(); i++) {
            assertEquals(String.format(context().getString(R.string.leanview_licenses_page),
                    context().getString(R.string.leanview_licenses_third_party), i - 1, pages), titles.get(i));
        }
        StringBuilder all = new StringBuilder();
        for (int i = 2; i < titles.size(); i++) {
            all.append(recorder.longTexts.get(titles.get(i)));
        }
        for (int i = 0; i < 40; i++) {
            assertTrue("component " + i + " is on a page", all.toString().contains("### component " + i + "\n"));
        }
    }

    @Test
    public void aSingleNoticesPageHasNoNumber() {
        Recorder recorder = new Recorder(context());
        LicensesDialog.show(recorder, context(), source("MIT License\n", "# Notices\n"), 300);
        assertTrue(recorder.longTexts.containsKey(context().getString(R.string.leanview_licenses_third_party)));
        assertEquals(3, recorder.longTexts.size());
    }

    // ---- the About screens call it (a hook that was removed must fail a test)

    private static String sourceOf(String name) throws Exception {
        File base = new File("src/main/java/com/liskovsoft/smartyoutubetv2/common/app/presenters/settings/" + name);
        return new String(Files.readAllBytes(base.toPath()), StandardCharsets.UTF_8);
    }

    @Test
    public void bothAboutScreensOfferTheLicenses() throws Exception {
        for (String name : new String[]{"AboutSettingsPresenter.java", "AboutSimpleSettingsPresenter.java"}) {
            // a real statement on its own line: a commented-out call must fail
            assertTrue(name + " must call LicensesDialog.append",
                    java.util.regex.Pattern.compile("(?m)^\\s*LicensesDialog\\.append\\(settingsPresenter, getContext\\(\\)\\);").matcher(sourceOf(name)).find());
        }
        assertFalse(sourceOf("AboutSettingsPresenter.java").isEmpty());
        assertNull(LegalTexts.fromAssets(context()).read("leanview_legal/NOT_THERE"));
    }
}
