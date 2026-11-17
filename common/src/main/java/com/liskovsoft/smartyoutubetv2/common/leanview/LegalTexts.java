package com.liskovsoft.smartyoutubetv2.common.leanview;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * leanview: the legal texts of Leanview (LICENSE and THIRD_PARTY_NOTICES.md of public-repo/) that the leanview flavor
 * bundles as assets at build time (smarttubetv/build.gradle copies them, nothing is kept twice). Other flavors have no
 * such assets: {@link #fromAssets} then answers null and Settings > About shows no Licenses button.
 */
public final class LegalTexts {
    public static final String DIR = "leanview_legal";
    public static final String LICENSE = DIR + "/LICENSE";
    public static final String NOTICE = DIR + "/NOTICE.md";
    public static final String NOTICES = DIR + "/THIRD_PARTY_NOTICES.md";
    /** A page of the notices on a TV screen: about one screen of reading at a time. */
    public static final int PAGE_CHARS = 12_000;

    /** Where the texts come from: the app's assets, or a fake in a test. null when there is no such text. */
    public interface Source {
        String read(String path);
    }

    private LegalTexts() {
    }

    public static Source fromAssets(final Context context) {
        return path -> {
            try (InputStream in = context.getAssets().open(path)) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) > 0) {
                    out.write(buffer, 0, n);
                }
                String text = new String(out.toByteArray(), StandardCharsets.UTF_8);
                return text.startsWith("﻿") ? text.substring(1) : text;
            } catch (Exception e) {
                return null; // not bundled in this flavor
            }
        };
    }

    /**
     * Splits a text into pages of at most {@code max} characters, at blank lines when it can, else at a line end, else
     * hard. Nothing is lost: the pages joined with a blank line give back the text (blank edges trimmed).
     */
    public static List<String> pages(String text, int max) {
        List<String> pages = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return pages;
        }
        String[] paragraphs = text.replace("\r\n", "\n").replaceAll("^\n+|\\s+$", "").split("\n{2,}");
        StringBuilder page = new StringBuilder();
        for (String paragraph : paragraphs) {
            if (paragraph.length() > max) {
                flush(pages, page);
                splitLong(pages, paragraph, max);
                continue;
            }
            if (page.length() > 0 && page.length() + 2 + paragraph.length() > max) {
                flush(pages, page);
            }
            if (page.length() > 0) {
                page.append("\n\n");
            }
            page.append(paragraph);
        }
        flush(pages, page);
        return pages;
    }

    private static void flush(List<String> pages, StringBuilder page) {
        if (page.length() > 0) {
            pages.add(page.toString());
            page.setLength(0);
        }
    }

    private static void splitLong(List<String> pages, String paragraph, int max) {
        int start = 0;
        while (start < paragraph.length()) {
            int end = Math.min(paragraph.length(), start + max);
            if (end < paragraph.length()) {
                int lineEnd = paragraph.lastIndexOf('\n', end - 1);
                if (lineEnd > start) {
                    end = lineEnd + 1;
                }
            }
            String piece = paragraph.substring(start, end);
            pages.add(piece.endsWith("\n") ? piece.substring(0, piece.length() - 1) : piece);
            start = end;
        }
    }
}
