package com.liskovsoft.smartyoutubetv2.common.proxy;

import java.net.SocketAddress;

/**
 * leanview: a proxy setting: a kind (direct, HTTP or SOCKS) and, unless it is direct, the address of the proxy.
 * It is not java.net.Proxy because the address here carries the user name and password
 * ({@link PasswdInetSocketAddress}), which java.net.Proxy does not accept.
 */
public class Proxy {

    public enum Type {
        DIRECT,
        HTTP,
        SOCKS
    }

    /** No proxy at all. */
    public static final Proxy NO_PROXY = new Proxy();

    private final Type mType;
    private final SocketAddress mAddress;

    private Proxy() {
        mType = Type.DIRECT;
        mAddress = null;
    }

    /**
     * @throws IllegalArgumentException for a DIRECT proxy (use {@link #NO_PROXY}) or an address that is not a {@link PasswdInetSocketAddress}
     */
    public Proxy(Type type, SocketAddress address) {
        if (type == Type.DIRECT || !(address instanceof PasswdInetSocketAddress)) {
            throw new IllegalArgumentException("type " + type + " is not compatible with address " + address);
        }

        mType = type;
        mAddress = address;
    }

    public Type type() {
        return mType;
    }

    /** The address of the proxy, or null for {@link #NO_PROXY}. */
    public SocketAddress address() {
        return mAddress;
    }

    @Override
    public String toString() {
        return mType == Type.DIRECT ? "DIRECT" : mType + " @ " + mAddress;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Proxy)) {
            return false;
        }

        Proxy proxy = (Proxy) other;

        return mType == proxy.mType && (mAddress == null ? proxy.mAddress == null : mAddress.equals(proxy.mAddress));
    }

    @Override
    public int hashCode() {
        return mAddress == null ? mType.hashCode() : mType.hashCode() + mAddress.hashCode();
    }
}
