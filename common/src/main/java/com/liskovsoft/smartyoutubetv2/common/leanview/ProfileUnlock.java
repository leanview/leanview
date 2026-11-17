package com.liskovsoft.smartyoutubetv2.common.leanview;

import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;

/**
 * leanview: a locked profile asks its owner to let this TV in (see the web client).
 * The TV opens the request with the profile's server account and shows its code as a QR; the profile owner
 * answers on yt.example.com/profile-unlock/CODE from a signed-in phone; the TV takes the answer,
 * which an approval gives only once. Blocking: call off the main thread.
 */
public final class ProfileUnlock {
    public static final String PENDING = "pending";
    public static final String APPROVED = "approved";
    public static final String DENIED = "denied";
    /** Expired, or already used by a TV. */
    public static final String GONE = "gone";

    private final ServerAccount mAccount;
    public final String code;
    public final long expiresAt;

    private ProfileUnlock(ServerAccount account, String code, long expiresAt) {
        mAccount = account;
        this.code = code;
        this.expiresAt = expiresAt;
    }

    public static ProfileUnlock open(ServerAccount account, String device) throws IOException {
        JSONObject body = new JSONObject();
        try {
            body.put("device", device);
        } catch (Exception e) {
            throw new IOException(e);
        }
        ServerHttp.Response response = ServerHttp.request("POST", account.backendUrl + "/api/user/profileUnlock",
                account.token, body.toString());
        if (!response.ok()) {
            throw new IOException("profile unlock is not available on " + account.backendUrl + " (HTTP " + response.status + ")");
        }
        try {
            JSONObject request = new JSONObject(response.body);
            long expires = QuickConnect.parseIsoMillis(request.optString("expires"), System.currentTimeMillis() + 5 * 60_000);
            return new ProfileUnlock(account, request.getString("code"), expires);
        } catch (Exception e) {
            throw new IOException("unexpected profile unlock answer", e);
        }
    }

    /** The page the profile owner opens from the QR. */
    public String link() {
        return mAccount.backendUrl + "/profile-unlock/" + encodedCode();
    }

    /** PENDING, APPROVED (handed out once), DENIED or GONE. */
    public String claim() throws IOException {
        ServerHttp.Response response = ServerHttp.request("POST",
                mAccount.backendUrl + "/api/user/profileUnlock/" + encodedCode() + "/claim", mAccount.token, "{}");
        if (response.status == 404) {
            return GONE;
        }
        if (!response.ok()) {
            throw new IOException("profile unlock claim: HTTP " + response.status);
        }
        try {
            String status = new JSONObject(response.body).getString("status");
            if (PENDING.equals(status) || APPROVED.equals(status) || DENIED.equals(status)) {
                return status;
            }
            return GONE;
        } catch (Exception e) {
            throw new IOException("unexpected profile unlock claim answer", e);
        }
    }

    private String encodedCode() {
        try {
            return URLEncoder.encode(code, "UTF-8");
        } catch (Exception e) {
            return code;
        }
    }
}
