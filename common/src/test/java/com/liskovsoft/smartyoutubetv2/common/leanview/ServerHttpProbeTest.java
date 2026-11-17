package com.liskovsoft.smartyoutubetv2.common.leanview;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

// leanview: the address check talks to a host nobody has vetted yet: it must not follow a redirect
// (the person types the final address) and must not load an answer of any size into memory.
// A throwaway HTTP server on the loopback stands in for that host.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ServerHttpProbeTest {
    private ServerSocket mServer;
    private Thread mAcceptor;
    private String mBase;
    private final List<String> mRequested = new CopyOnWriteArrayList<>();

    @Before
    public void setUp() throws Exception {
        mServer = new ServerSocket(0, 10, InetAddress.getLoopbackAddress());
        mBase = "http://127.0.0.1:" + mServer.getLocalPort();
        mAcceptor = new Thread(() -> {
            while (!mServer.isClosed()) {
                try (Socket socket = mServer.accept()) {
                    answer(socket);
                } catch (Exception e) {
                    // closed, or the client stopped reading: expected
                }
            }
        });
        mAcceptor.setDaemon(true);
        mAcceptor.start();
    }

    private void answer(Socket socket) throws Exception {
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
        String requestLine = in.readLine(); // GET /path HTTP/1.1
        String path = requestLine.split(" ")[1];
        String line;
        while ((line = in.readLine()) != null && !line.isEmpty()) {
            // skip the headers
        }
        mRequested.add(path);
        OutputStream out = socket.getOutputStream();
        if (path.startsWith("/redirect")) {
            out.write(("HTTP/1.1 302 Found\r\nLocation: /target/api/config\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
        } else if (path.startsWith("/huge")) {
            out.write("HTTP/1.1 200 OK\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            byte[] chunk = new byte[8192];
            Arrays.fill(chunk, (byte) 'x');
            for (int i = 0; i < 100; i++) { // 800 KB, no Content-Length
                out.write(chunk);
            }
        } else {
            byte[] body = "{\"backend\":\"some-server\"}".getBytes(StandardCharsets.UTF_8);
            out.write(("HTTP/1.1 200 OK\r\nContent-Length: " + body.length + "\r\nConnection: close\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
            out.write(body);
        }
        out.flush();
    }

    @After
    public void tearDown() throws Exception {
        mServer.close();
    }

    @Test
    public void aRedirectIsNotFollowed() throws Exception {
        ServerHttp.Response response = ServerHttp.probe(mBase + "/redirect/api/config");

        assertEquals(302, response.status);
        assertEquals(Collections.singletonList("/redirect/api/config"), mRequested);
    }

    @Test
    public void anOrdinaryAnswerIsReadWhole() throws Exception {
        ServerHttp.Response response = ServerHttp.probe(mBase + "/target/api/config");

        assertEquals(200, response.status);
        assertEquals("{\"backend\":\"some-server\"}", response.body);
    }

    @Test
    public void aHugeAnswerIsCutAtTheLimit() throws Exception {
        ServerHttp.Response response = ServerHttp.probe(mBase + "/huge/api/config");

        assertEquals(200, response.status);
        assertTrue("read " + response.body.length(), response.body.length() <= 64 * 1024);
        assertTrue(response.body.length() > 0);
    }

    @Test
    public void readBoundedStopsAtTheLimit() throws Exception {
        byte[] data = new byte[200_000];
        Arrays.fill(data, (byte) 'a');

        String read = ServerHttp.readBounded(new ByteArrayInputStream(data), 1000);

        assertEquals(1000, read.length());
    }
}
