package com.liskovsoft.smartyoutubetv2.common.leanview;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

// leanview: the TV side of the profile lock against a stand-in for the server routes of
// the web client's /api/user/profileUnlock route (the request, its claim) and the synced setting.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ProfileUnlockClientTest {
    private ServerSocket mServer;
    private ServerAccount mAccount;
    private final List<String> mCalls = new ArrayList<>();
    private final List<String> mClaimAnswers = new ArrayList<>();

    // A minimal HTTP/1.1 server (one request per connection): enough for ServerHttp.
    @Before
    public void setUp() throws Exception {
        mServer = new ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"));
        Thread thread = new Thread(() -> {
            while (!mServer.isClosed()) {
                try (Socket socket = mServer.accept()) {
                    answer(socket);
                } catch (Exception ignored) {
                    // Closed by tearDown.
                }
            }
        });
        thread.setDaemon(true);
        thread.start();
        mAccount = new ServerAccount("http://127.0.0.1:" + mServer.getLocalPort(), "owner-token",
                new byte[32], "Alice");
    }

    private void answer(Socket socket) throws Exception {
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        String[] requestLine = in.readLine().split(" ");
        String auth = null;
        int length = 0;
        String line;
        while ((line = in.readLine()) != null && !line.isEmpty()) {
            String lower = line.toLowerCase();
            if (lower.startsWith("authorization:")) {
                auth = line.substring(line.indexOf(':') + 1).trim();
            } else if (lower.startsWith("content-length:")) {
                length = Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
            }
        }
        char[] body = new char[length];
        int read = 0;
        while (read < length) {
            read += in.read(body, read, length - read);
        }
        String path = requestLine[1];
        mCalls.add(requestLine[0] + " " + path + " " + auth + " " + new String(body));

        int status = 200;
        String reply;
        if (path.equals("/api/user/profileUnlock")) {
            reply = "{\"code\":\"ABCD2345\",\"expires\":\"2026-10-30T12:05:00.000Z\"}";
        } else if (path.equals("/api/user/profileUnlock/ABCD2345/claim")) {
            String next = mClaimAnswers.isEmpty() ? "404" : mClaimAnswers.remove(0);
            if (next.equals("404")) {
                status = 404;
                reply = "{}";
            } else {
                reply = "{\"status\":\"" + next + "\"}";
            }
        } else {
            status = 404;
            reply = "{}";
        }
        byte[] bytes = reply.getBytes(StandardCharsets.UTF_8);
        OutputStream out = socket.getOutputStream();
        String head = "HTTP/1.1 " + status + " X\r\nContent-Type: application/json\r\nContent-Length: "
                + bytes.length + "\r\nConnection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
        out.flush();
    }

    @After
    public void tearDown() throws Exception {
        mServer.close();
    }

    @Test
    public void opensTheRequestWithTheOwnersAccountAndLinksTheApprovalPage() throws Exception {
        ProfileUnlock unlock = ProfileUnlock.open(mAccount, "Google TV Streamer");

        assertEquals("ABCD2345", unlock.code);
        assertEquals(mAccount.backendUrl + "/profile-unlock/ABCD2345", unlock.link());
        assertEquals(java.time.Instant.parse("2026-10-30T12:05:00.000Z").toEpochMilli(), unlock.expiresAt);
        String call = mCalls.get(0);
        assertTrue(call, call.startsWith("POST /api/user/profileUnlock Bearer owner-token"));
        assertTrue(call, call.contains("\"device\":\"Google TV Streamer\""));
    }

    @Test
    public void takesTheAnswerUntilItIsGone() throws Exception {
        ProfileUnlock unlock = ProfileUnlock.open(mAccount, "TV");
        mClaimAnswers.add("pending");
        mClaimAnswers.add("approved");
        mClaimAnswers.add("used");
        mClaimAnswers.add("404");

        assertEquals(ProfileUnlock.PENDING, unlock.claim());
        assertEquals(ProfileUnlock.APPROVED, unlock.claim());
        // Used by this TV, or by another: no second approval.
        assertEquals(ProfileUnlock.GONE, unlock.claim());
        assertEquals(ProfileUnlock.GONE, unlock.claim());
        assertTrue(mCalls.get(2).startsWith("POST /api/user/profileUnlock/ABCD2345/claim Bearer owner-token"));
    }

    @Test
    public void aDenialIsAnAnswer() throws Exception {
        ProfileUnlock unlock = ProfileUnlock.open(mAccount, "TV");
        mClaimAnswers.add("denied");

        assertEquals(ProfileUnlock.DENIED, unlock.claim());
    }

    @Test
    public void readsThePersonsLockSetting() {
        String hash = "pbkdf2-sha1$50000$AAECAwQFBgcICQoLDA0ODw$tmCSTqFycGZdzbLesZ4rN8vW2J264dZaE9BRgWLuWAo";
        ServerProfileLock lock = ServerProfileLock.parse("{\"enabled\":true,\"pinHash\":\"" + hash + "\"}");

        assertTrue(lock.locks());
        assertEquals(hash, lock.pinHash);
        // The web may store the object as a JSON string of it.
        assertTrue(ServerProfileLock.parse("\"{\\\"enabled\\\":true,\\\"pinHash\\\":\\\"" + hash + "\\\"}\"").locks());
        assertFalse(ServerProfileLock.parse("{\"enabled\":false,\"pinHash\":\"" + hash + "\"}").locks());
        // Turned off on the web: an explicit "no".
        assertFalse(ServerProfileLock.parse("{\"enabled\":false,\"pinHash\":null}").locks());
    }

    @Test
    public void aMissingOrDamagedSettingChangesNothing() {
        assertNull(ServerProfileLock.parse(null));
        assertNull(ServerProfileLock.parse("not json"));
        assertNull(ServerProfileLock.parse("{\"pinHash\":null}"));
        // Locked on the web but without a PIN the TV can check (a bug, a damaged value): treated as
        // never set, so the profile keeps what it has instead of opening.
        assertNull(ServerProfileLock.parse("{\"enabled\":true,\"pinHash\":\"4821\"}"));
        assertNull(ServerProfileLock.parse("{\"enabled\":true,\"pinHash\":\"pbkdf2-sha1$x\"}"));
        assertNull(ServerProfileLock.parse("{\"enabled\":true,\"pinHash\":null}"));
        assertNull(ServerProfileLock.parse("{\"enabled\":true}"));
        assertNull(ServerProfileLock.parse("{\"enabled\":true,\"pinHash\":\"pbkdf2-sha1$$$x\"}"));
        assertNull(ServerProfileLock.parse("{\"enabled\":true,\"pinHash\":\"pbkdf2-sha1$abc$AAAA$BBBB\"}"));
        // Only an explicit false turns the lock off: a damaged "enabled" changes nothing.
        assertNull(ServerProfileLock.parse("{\"enabled\":null,\"pinHash\":null}"));
        assertNull(ServerProfileLock.parse("{\"enabled\":\"yes\",\"pinHash\":null}"));
    }
}
