package com.liskovsoft.smartyoutubetv2.common.leanview;

/** Lets tests of other packages build the app's package-private HTTP answer. */
public final class ServerHttpTestAccess {
    private ServerHttpTestAccess() {
    }

    public static ServerHttp.Response response(int status, String body) {
        return new ServerHttp.Response(status, body);
    }
}
