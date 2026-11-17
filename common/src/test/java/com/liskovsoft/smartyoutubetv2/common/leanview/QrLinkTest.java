package com.liskovsoft.smartyoutubetv2.common.leanview;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

// leanview: every QR code of the app (the sign-in screens, "share by QR", the About links) is drawn on the device. None of them is
// requested from a QR web service, so neither the one-time sign-in code nor a shared link leaves the device for that.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class QrLinkTest {
    @Test
    public void aQrLinkIsAnImageDrawnOnTheDevice() {
        String link = Utils.toQrCodeLink("https://example.org/activate/ABCD-EFGH");
        assertTrue(link, link.startsWith("data:image/png;base64,"));
        assertFalse(link.contains("http"));
    }

    @Test
    public void noSourceFileNamesAQrWebService() throws IOException {
        File main = new File("src/main");
        if (!main.isDirectory()) {
            main = new File("common/src/main");
        }
        assertTrue(main.isDirectory());
        List<String> hits = new ArrayList<>();
        try (Stream<Path> files = Files.walk(main.toPath())) {
            files.filter(p -> p.toString().endsWith(".java") || p.toString().endsWith(".kt")).forEach(p -> {
                try {
                    if (new String(Files.readAllBytes(p), StandardCharsets.UTF_8).contains("qrserver")) {
                        hits.add(p.toString());
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        assertTrue("a QR web service is named in: " + hits, hits.isEmpty());
    }
}
