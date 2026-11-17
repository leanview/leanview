package com.liskovsoft.smartyoutubetv2.common.leanview;

import org.json.JSONObject;

import java.io.IOException;

/**
 * leanview: reads and writes on the user's server with the connected account. Values are encrypted
 * with the master key exactly as the compatible web does (src/lib/api/backend/keyvalue.ts).
 * Blocking: call off the main thread.
 */
public final class ServerApi {
    private ServerApi() {
    }

    /** A stored setting, decrypted; null when not stored. */
    public static String getKeyValue(ServerAccount account, String key) throws IOException {
        ServerHttp.Response response = ServerHttp.request("GET", account.backendUrl + "/api/user/keyValue/" + key, account.token, null);
        if (response.status == 404) {
            return null;
        }
        if (!response.ok()) {
            throw new IOException("keyValue " + key + ": HTTP " + response.status);
        }
        try {
            JSONObject json = new JSONObject(response.body);
            String value = ServerCrypto.decrypt(json.getString("valueNonce"), json.getString("valueCipher"), account.masterKey);
            if (value == null) {
                throw new IOException("keyValue " + key + " does not open with this master key");
            }
            return value;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("unexpected keyValue answer", e);
        }
    }

    /** The Invidious account the web keeps on the server ("authToken": {username, token}), or null. */
    public static JSONObject invidiousAuth(ServerAccount account) throws IOException {
        String value = getKeyValue(account, "authToken");
        if (value == null) {
            return null;
        }
        try {
            Object parsed = new org.json.JSONTokener(value).nextValue();
            if (parsed instanceof String) {
                parsed = new org.json.JSONTokener((String) parsed).nextValue();
            }
            return parsed instanceof JSONObject ? (JSONObject) parsed : null;
        } catch (Exception e) {
            return null;
        }
    }
}
