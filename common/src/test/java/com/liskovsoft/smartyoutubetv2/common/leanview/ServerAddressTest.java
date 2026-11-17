package com.liskovsoft.smartyoutubetv2.common.leanview;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

// leanview: the APK carries no server address; the person types it. It is cleaned up the way the web
// does (https added, no trailing slash) and checked with the web's own probe: GET /api/config must
// answer with the fields the app uses (a compatible server).
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ServerAddressTest {
    private static final String OK = "{\"builtWithBackend\":true,\"internalAuth\":true,\"requireAuth\":true,"
            + "\"quickConnect\":true,\"version\":\"1.18.11\",\"backend\":\"some-server\"}";

    @Test
    public void aBareHostGetsHttps() {
        assertEquals("https://yt.example.com", ServerAddress.normalize("yt.example.com"));
    }

    @Test
    public void spacesAndATrailingSlashAreTrimmed() {
        assertEquals("https://yt.example.com", ServerAddress.normalize("  https://yt.example.com/  "));
        assertEquals("https://yt.example.com/base", ServerAddress.normalize("yt.example.com/base//"));
    }

    @Test
    public void anExplicitHttpAndPortAreKept() {
        assertEquals("http://192.168.1.5:3000", ServerAddress.normalize("http://192.168.1.5:3000/"));
    }

    @Test
    public void caseIsFoldedLikeTheWebDoes() {
        assertEquals("https://yt.example.com", ServerAddress.normalize("HTTPS://YT.Example.com"));
    }

    @Test
    public void nothingUsableIsNull() {
        assertNull(ServerAddress.normalize(null));
        assertNull(ServerAddress.normalize(""));
        assertNull(ServerAddress.normalize("   "));
        assertNull(ServerAddress.normalize("https://"));
        assertNull(ServerAddress.normalize("not a host"));
        assertNull(ServerAddress.normalize("ftp://yt.example.com"));
    }

    private static ServerAddress.Fetcher answering(List<String> asked, int status, String body) {
        return url -> {
            asked.add(url);
            return new ServerHttp.Response(status, body);
        };
    }

    private static ServerAddress.Reason failureOf(ServerAddress.Fetcher fetcher) {
        try {
            ServerAddress.check("https://yt.example.com", fetcher);
        } catch (ServerAddress.Problem problem) {
            return problem.reason;
        }
        fail("expected a problem");
        return null;
    }

    @Test
    public void aCompatibleBackendPassesAndIsProbedAtApiConfig() throws Exception {
        List<String> asked = new ArrayList<>();

        ServerAddress.check("https://yt.example.com", answering(asked, 200, OK));

        assertEquals(1, asked.size());
        assertEquals("https://yt.example.com/api/config", asked.get(0));
    }

    @Test
    public void whatCountsIsTheShapeOfTheAnswerNotTheNameOfTheServer() throws Exception {
        List<String> asked = new ArrayList<>();

        for (String name : new String[]{"some-server", "another", "x1", "Backend With Spaces"}) {
            ServerAddress.check("https://yt.example.com", answering(asked, 200, OK.replace("some-server", name)));
        }
    }

    @Test
    public void aServerThatDoesNotAnswerIsNotReachable() {
        ServerAddress.Fetcher down = url -> {
            throw new IOException("connection refused");
        };

        assertEquals(ServerAddress.Reason.NOT_REACHABLE, failureOf(down));
    }

    @Test
    public void aHostThatAnswersSomethingElseIsNotCompatible() {
        List<String> asked = new ArrayList<>();

        assertEquals(ServerAddress.Reason.NOT_COMPATIBLE, failureOf(answering(asked, 404, "Not found")));
        assertEquals(ServerAddress.Reason.NOT_COMPATIBLE, failureOf(answering(asked, 200, "<html>hi</html>")));
        assertEquals(ServerAddress.Reason.NOT_COMPATIBLE, failureOf(answering(asked, 200, "[1,2,3]")));
        assertEquals(ServerAddress.Reason.NOT_COMPATIBLE, failureOf(answering(asked, 200, "{}")));
        assertEquals(ServerAddress.Reason.NOT_COMPATIBLE, failureOf(answering(asked, 200, OK.replace("\"internalAuth\":true,", ""))));
        assertEquals(ServerAddress.Reason.NOT_COMPATIBLE, failureOf(answering(asked, 200, OK.replace(",\"version\":\"1.18.11\"", ""))));
        assertEquals(ServerAddress.Reason.NOT_COMPATIBLE, failureOf(answering(asked, 200, OK.replace(",\"backend\":\"some-server\"", ""))));
        assertEquals(ServerAddress.Reason.NOT_COMPATIBLE, failureOf(answering(asked, 200, OK.replace("\"some-server\"", "\"\""))));
        assertEquals(ServerAddress.Reason.NOT_COMPATIBLE, failureOf(answering(asked, 200, OK.replace("\"internalAuth\":true", "\"internalAuth\":false"))));
    }

    @Test
    public void aBackendOlderThanTheWebsMinimumIsNotAccepted() throws Exception {
        List<String> asked = new ArrayList<>();

        assertEquals(ServerAddress.Reason.NOT_COMPATIBLE, failureOf(answering(asked, 200, OK.replace("1.18.11", "1.17.14"))));
        ServerAddress.check("https://yt.example.com", answering(asked, 200, OK.replace("1.18.11", "1.17.15")));
        ServerAddress.check("https://yt.example.com", answering(asked, 200, OK.replace("1.18.11", "2.0.0")));
    }

    // The session token travels with every request after Quick Connect: clear text is for the home
    // network only (loopback, private ranges, CGNAT/Tailscale, .local, .lan); anything else is https.
    @Test
    public void httpIsKeptOnlyForLocalAddresses() {
        String[] local = {"http://localhost:3000", "http://127.0.0.1", "http://10.1.2.3", "http://172.16.0.1",
                "http://172.31.255.1", "http://192.168.0.9:8080", "http://100.64.0.1", "http://100.127.1.1",
                "http://nas.local", "http://home.lan:3000", "http://[::1]:8080"};
        for (String address : local) {
            assertEquals(address, ServerAddress.normalize(address));
        }
        assertEquals("https://yt.example.com", ServerAddress.normalize("https://yt.example.com"));
    }

    @Test
    public void httpOnAPublicHostIsRefusedAndAskedToUseHttps() {
        String[] publicHosts = {"http://yt.example.com", "http://172.32.0.1", "http://172.15.0.1", "http://100.128.0.1",
                "http://100.63.0.1", "http://11.0.0.1", "http://192.169.0.1", "http://notlocal.com", "http://local.example.com"};
        for (String address : publicHosts) {
            assertNull(address, ServerAddress.normalize(address));
            assertTrue(address, ServerAddress.needsHttps(address));
        }
        assertFalse(ServerAddress.needsHttps("yt.example.com"));
        assertFalse(ServerAddress.needsHttps("https://yt.example.com"));
        assertFalse(ServerAddress.needsHttps("http://192.168.1.5"));
        assertFalse(ServerAddress.needsHttps("ftp://yt.example.com"));
        assertFalse(ServerAddress.needsHttps(null));
    }

    @Test
    public void onlyTheHostIsLowerCasedAndQueryFragmentAndUserinfoAreRefused() {
        assertEquals("https://yt.example.com/Base", ServerAddress.normalize("YT.Example.com/Base/"));
        assertNull(ServerAddress.normalize("yt.example.com?x=1"));
        assertNull(ServerAddress.normalize("yt.example.com/a#frag"));
        assertNull(ServerAddress.normalize("user:pw@yt.example.com"));
        assertNull(ServerAddress.normalize("https://user@yt.example.com"));
    }

    @Test
    public void aCertificateProblemIsToldApartFromNotReachable() {
        ServerAddress.Fetcher bad = url -> {
            throw new javax.net.ssl.SSLHandshakeException("PKIX path building failed");
        };

        assertEquals(ServerAddress.Reason.CERTIFICATE, failureOf(bad));
    }

    @Test
    public void aRedirectIsToldApart() {
        List<String> asked = new ArrayList<>();

        assertEquals(ServerAddress.Reason.REDIRECT, failureOf(answering(asked, 301, "")));
        assertEquals(ServerAddress.Reason.REDIRECT, failureOf(answering(asked, 302, "")));
    }
}
