package com.liskovsoft.smartyoutubetv2.common.leanview;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.Assert.assertTrue;

// leanview: the APK is universal: no address of the author's servers in code or strings. Comments are
// fine (they are documentation and never reach the APK). scripts/scan_apk_for_host.py checks the
// built APK itself; this one stops a constant from coming back in the sources.
public class NoBuiltInServerTest {
    private static final String HOST = "lg" + "obbi" + ".com";

    @Test
    public void noSourceLineOutsideCommentsNamesTheAuthorsHost() throws IOException {
        File main = new File("src/main");
        if (!main.isDirectory()) {
            main = new File("common/src/main"); // run from the repo root
        }
        assertTrue("src/main not found from " + new File(".").getAbsolutePath(), main.isDirectory());
        List<String> hits = new ArrayList<>();
        try (Stream<Path> files = Files.walk(main.toPath())) {
            files.filter(p -> p.toString().endsWith(".java") || p.toString().endsWith(".kt") || p.toString().endsWith(".xml"))
                    .forEach(p -> scan(p, hits));
        }
        assertTrue("built-in host found: " + hits, hits.isEmpty());
    }

    private static void scan(Path path, List<String> hits) {
        try {
            String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            text = text.replaceAll("(?s)<!--.*?-->", "").replaceAll("(?s)/\\*.*?\\*/", "");
            String[] lines = text.split("\\R");
            for (int i = 0; i < lines.length; i++) {
                String code = lines[i].replaceAll("(?<![:\\w])//.*$", "");
                if (code.contains(HOST)) {
                    hits.add(path + ":" + (i + 1));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
