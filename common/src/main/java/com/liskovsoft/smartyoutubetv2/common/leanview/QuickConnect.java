package com.liskovsoft.smartyoutubetv2.common.leanview;

import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * leanview: the receiver side of the server's Quick Connect (see the web client): the TV opens a session with a
 * fresh crypto_box public key and shows its code as a QR; a signed-in device (yt.example.com)
 * approves it and seals {token, masterKey, username} for that key; the TV reads and opens it.
 * Nothing is typed with the remote and no password reaches the TV.
 */
public final class QuickConnect {
    private final String mBackendUrl;
    private final byte[] mPublicKey;
    private final byte[] mSecretKey;
    public final String code;
    public final long expiresAt;

    private QuickConnect(String backendUrl, byte[][] keyPair, String code, long expiresAt) {
        mBackendUrl = backendUrl;
        mPublicKey = keyPair[0];
        mSecretKey = keyPair[1];
        this.code = code;
        this.expiresAt = expiresAt;
    }

    /** Opens a receiver session. Blocking. */
    public static QuickConnect open(String backendUrl) throws IOException {
        byte[][] keyPair = ServerCrypto.newBoxKeyPair();
        JSONObject body = new JSONObject();
        try {
            body.put("publicKey", ServerCrypto.toBase64(keyPair[0]));
            body.put("captchaPayload", JSONObject.NULL);
        } catch (Exception e) {
            throw new IOException(e);
        }
        ServerHttp.Response response = ServerHttp.request("POST", backendUrl + "/api/user/quickConnect/receiver", null, body.toString());
        if (!response.ok()) {
            throw new IOException("Quick Connect is not available on " + backendUrl + " (HTTP " + response.status + ")");
        }
        try {
            JSONObject session = new JSONObject(response.body);
            long expires = parseIsoMillis(session.optString("expires"), System.currentTimeMillis() + 5 * 60_000);
            return new QuickConnect(backendUrl, keyPair, session.getString("code"), expires);
        } catch (Exception e) {
            throw new IOException("unexpected Quick Connect answer", e);
        }
    }

    /** The page a signed-in device opens (the QR) to send its credentials to this TV. */
    public String link() {
        return mBackendUrl + "/quickconnect/" + encodedCode();
    }

    /** The account once approved on the other device, null while still waiting. Blocking. */
    public ServerAccount poll() throws IOException {
        ServerHttp.Response response = ServerHttp.request("GET",
                mBackendUrl + "/api/user/quickConnect/" + encodedCode() + "/credentials", null, null);
        if (response.status == 404) {
            return null;
        }
        if (!response.ok()) {
            throw new IOException("Quick Connect failed (HTTP " + response.status + ")");
        }
        try {
            String cipher = new JSONObject(response.body).getString("cipher");
            byte[] opened = ServerCrypto.sealOpen(ServerCrypto.fromBase64(cipher), mPublicKey, mSecretKey);
            if (opened == null) {
                throw new IOException("the credentials were not sealed for this TV");
            }
            JSONObject credentials = new JSONObject(new String(opened, StandardCharsets.UTF_8));
            return new ServerAccount(mBackendUrl, credentials.getString("token"),
                    ServerCrypto.fromBase64(credentials.getString("masterKey")),
                    credentials.optString("username", null));
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("unexpected Quick Connect credentials", e);
        }
    }

    private String encodedCode() {
        try {
            return URLEncoder.encode(code, "UTF-8");
        } catch (Exception e) {
            return code;
        }
    }

    static long parseIsoMillis(String iso, long fallback) {
        try {
            java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US);
            format.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            java.util.Date date = format.parse(iso.length() >= 19 ? iso.substring(0, 19) : iso);
            return date != null ? date.getTime() : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }
}
