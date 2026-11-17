package com.liskovsoft.smartyoutubetv2.common.leanview;

// leanview: lets tests in other packages build a ServerProfileLock as if read from the server.
public final class ServerProfileLockTestAccess {
    private ServerProfileLockTestAccess() {
    }

    public static ServerProfileLock of(boolean enabled, String pinHash) {
        return new ServerProfileLock(enabled, pinHash);
    }
}
