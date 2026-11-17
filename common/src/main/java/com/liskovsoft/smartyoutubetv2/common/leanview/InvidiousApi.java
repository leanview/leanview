package com.liskovsoft.smartyoutubetv2.common.leanview;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * leanview: the person's Invidious account, the one their web uses for subscriptions (see the web client). The token comes from their server (keyValue
 * "authToken", written by the web) and the instance from the connection (InvidiousInstance).
 * Blocking: call off the main thread.
 */
public final class InvidiousApi {
    private final String mInstance;
    private final String mToken;

    public InvidiousApi(String instance, String token) {
        mInstance = instance;
        mToken = token;
    }

    /**
     * The Invidious account stored on the server, or null when the web never connected one or no
     * Invidious instance is known for this connection (then nothing is requested at all).
     */
    public static InvidiousApi fromServer(ServerAccount account) throws IOException {
        if (account.invidiousInstance == null) {
            return null;
        }
        JSONObject auth = ServerApi.invidiousAuth(account);
        if (auth == null || auth.optString("token", "").isEmpty()) {
            return null;
        }
        return new InvidiousApi(account.invidiousInstance, auth.optString("token"));
    }

    private ServerHttp.Response call(String method, String path) throws IOException {
        // compatible server keeps a cookie session as "SID=..."; any other token is a Bearer token.
        if (mToken.startsWith("SID=")) {
            return ServerHttp.request(method, mInstance + "/api/v1/" + path, null, null, "Cookie", mToken);
        }
        return ServerHttp.request(method, mInstance + "/api/v1/" + path, mToken, null);
    }

    public void subscribe(String channelId) throws IOException {
        ServerHttp.Response response = call("POST", "auth/subscriptions/" + channelId);
        if (!response.ok()) {
            throw new IOException("Invidious subscribe " + channelId + ": HTTP " + response.status);
        }
    }

    public void unsubscribe(String channelId) throws IOException {
        ServerHttp.Response response = call("DELETE", "auth/subscriptions/" + channelId);
        if (!response.ok() && response.status != 404) {
            throw new IOException("Invidious unsubscribe " + channelId + ": HTTP " + response.status);
        }
    }

    /** channelId -> name of every subscription. */
    public Map<String, String> subscriptions() throws IOException {
        ServerHttp.Response response = call("GET", "auth/subscriptions");
        if (!response.ok()) {
            throw new IOException("Invidious subscriptions: HTTP " + response.status);
        }
        Map<String, String> result = new LinkedHashMap<>();
        try {
            JSONArray list = new JSONArray(response.body);
            for (int i = 0; i < list.length(); i++) {
                JSONObject item = list.getJSONObject(i);
                result.put(item.getString("authorId"), item.optString("author", ""));
            }
        } catch (Exception e) {
            throw new IOException("unexpected Invidious subscriptions answer", e);
        }
        return result;
    }
}
