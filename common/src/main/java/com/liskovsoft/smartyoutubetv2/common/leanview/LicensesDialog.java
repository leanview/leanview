package com.liskovsoft.smartyoutubetv2.common.leanview;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;

import java.util.List;

/**
 * leanview: Settings > About > Licenses. Shows LeanView's LICENSE (with SmartTube's MIT notice) and the third-party notices,
 * the very files of public-repo/ that the flavor build bundles as assets ({@link LegalTexts}). One line in each About
 * presenter calls {@link #append}; a build without the assets (other flavors) shows no button.
 */
public final class LicensesDialog {
    private LicensesDialog() {
    }

    public static void append(AppDialogPresenter about, Context context) {
        append(about, context, LegalTexts.fromAssets(context));
    }

    static void append(AppDialogPresenter about, Context context, LegalTexts.Source source) {
        String license = source.read(LegalTexts.LICENSE);
        if (license == null || license.trim().isEmpty()) {
            return;
        }
        about.appendSingleButton(UiOptionItem.from(context.getString(R.string.leanview_licenses),
                option -> show(AppDialogPresenter.instance(context), context, source, LegalTexts.PAGE_CHARS)));
    }

    static void show(AppDialogPresenter dialog, Context context, LegalTexts.Source source, int pageChars) {
        String license = source.read(LegalTexts.LICENSE);
        if (license == null) {
            return;
        }
        appendPages(dialog, context, context.getString(R.string.leanview_licenses_license), license, pageChars);
        appendPages(dialog, context, context.getString(R.string.leanview_licenses_notice), source.read(LegalTexts.NOTICE), pageChars);
        appendPages(dialog, context, context.getString(R.string.leanview_licenses_third_party), source.read(LegalTexts.NOTICES), pageChars);
        dialog.showDialog(context.getString(R.string.leanview_licenses));
    }

    /** One long-text category per page of {@code text}: "Title", or "Title (1/3)" when it takes several. Nothing for an absent text. */
    private static void appendPages(AppDialogPresenter dialog, Context context, String title, String text, int pageChars) {
        List<String> pages = LegalTexts.pages(text, pageChars);
        for (int i = 0; i < pages.size(); i++) {
            String name = pages.size() == 1 ? title : String.format(context.getString(R.string.leanview_licenses_page), title, i + 1, pages.size());
            dialog.appendLongTextCategory(name, UiOptionItem.from(pages.get(i)));
        }
    }
}
