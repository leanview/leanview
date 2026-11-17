package com.liskovsoft.smartyoutubetv2.common.proxy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.net.InetSocketAddress;

// leanview: Proxy is the app's own small class (it replaced a copy of a JDK class): a kind and a PasswdInetSocketAddress.
public class ProxyTest {
    private static Proxy proxy(Proxy.Type type, String host, int port) {
        return new Proxy(type, PasswdInetSocketAddress.createUnresolved(host, port, "user", "secret"));
    }

    @Test
    public void noProxyIsDirectAndHasNoAddress() {
        assertEquals(Proxy.Type.DIRECT, Proxy.NO_PROXY.type());
        assertNull(Proxy.NO_PROXY.address());
        assertEquals("DIRECT", Proxy.NO_PROXY.toString());
    }

    @Test
    public void aProxyKeepsItsKindAndAddress() {
        Proxy http = proxy(Proxy.Type.HTTP, "proxy.example.com", 8080);
        assertEquals(Proxy.Type.HTTP, http.type());
        assertTrue(http.address() instanceof PasswdInetSocketAddress);
        assertEquals("proxy.example.com", ((PasswdInetSocketAddress) http.address()).getHostString());
        assertEquals(8080, ((PasswdInetSocketAddress) http.address()).getPort());
        assertTrue(http.toString().startsWith("HTTP @ "));
    }

    @Test
    public void aDirectProxyOrAPlainSocketAddressIsRefused() {
        assertRefused(Proxy.Type.DIRECT, PasswdInetSocketAddress.createUnresolved("proxy.example.com", 8080, "u", "p"));
        assertRefused(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved("proxy.example.com", 1080));
        assertRefused(Proxy.Type.HTTP, null);
    }

    private static void assertRefused(Proxy.Type type, java.net.SocketAddress address) {
        try {
            new Proxy(type, address);
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("accepted " + type + " " + address);
    }

    @Test
    public void equalityFollowsKindAndAddress() {
        Proxy a = proxy(Proxy.Type.HTTP, "proxy.example.com", 8080);
        assertEquals(a, a);
        // two proxies of the same kind that share one address object are equal, and one that differs only by its kind is not
        java.net.SocketAddress shared = PasswdInetSocketAddress.createUnresolved("shared.example.com", 3128, "user", "secret");
        assertEquals(new Proxy(Proxy.Type.HTTP, shared), new Proxy(Proxy.Type.HTTP, shared));
        assertEquals(new Proxy(Proxy.Type.HTTP, shared).hashCode(), new Proxy(Proxy.Type.HTTP, shared).hashCode());
        assertFalse(new Proxy(Proxy.Type.HTTP, shared).equals(new Proxy(Proxy.Type.SOCKS, shared)));
        assertFalse(a.equals(Proxy.NO_PROXY));
        assertFalse(a.equals("HTTP"));
        assertFalse(a.equals(proxy(Proxy.Type.SOCKS, "proxy.example.com", 8080)));
        assertFalse(a.equals(proxy(Proxy.Type.HTTP, "other.example.com", 8080)));
        assertEquals(Proxy.NO_PROXY, Proxy.NO_PROXY);
        assertEquals(Proxy.NO_PROXY.hashCode(), Proxy.NO_PROXY.hashCode());
    }
}
