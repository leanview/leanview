package com.liskovsoft.smartyoutubetv2.common.leanview;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.liskovsoft.sharedutils.mylogger.Log;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * leanview: where you left each video, shared with the web (yt.example.com) through the user's
 * server, end-to-end encrypted exactly as the web stores it (see the web client): the entry is named by a keyed hash of the video id, and title,
 * channel, thumbnail and id are sealed with the master key. Start on the TV, continue on the
 * phone where you left, and the other way round. Only for the person whose server account is
 * paired with the selected Google account.
 */
public final class ServerHistory {
    private static final String TAG = ServerHistory.class.getSimpleName();
    private static final ExecutorService sWorker = Executors.newSingleThreadExecutor();
    private static final Handler sMain = new Handler(Looper.getMainLooper());

    public interface PositionCallback {
        void onPosition(long positionMs);
    }

    private ServerHistory() {
    }

    /** The connected account if it belongs to whoever is watching now, else null. */
    static ServerAccount activeAccount(Context context) {
        ServerAccount account = ServerAccount.load(context);
        return account != null && account.googleAccount != null && account.googleAccount.equals(ServerSync.selectedGoogleAccount()) ? account : null;
    }

    /**
     * The connected account of the profile owner (ServerSync.profileKey), else null. A video is saved
     * for the profile that opened it: the player saves it again as it closes, which after a switch to
     * another profile happens with that one already selected (Carol's video must not land in Bob's).
     */
    static ServerAccount ownedBy(Context context, String owner) {
        ServerAccount account = ServerAccount.forGoogle(context, owner);
        return account != null && owner.equals(account.googleAccount) ? account : null;
    }

    private static String isoNow() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date());
    }

    private static JSONObject sealed(String text, byte[] masterKey) throws Exception {
        String[] parts = ServerCrypto.encrypt(text != null ? text : "", masterKey);
        return new JSONObject().put("nonce", parts[0]).put("cipher", parts[1]);
    }

    /** Saves where owner (the profile that opened the video) left it. Fire and forget. */
    public static void save(Context context, String owner, String videoId, String title, String author, String channelId,
                            String thumbnail, long positionMs, long durationMs) {
        if (videoId == null || owner == null) {
            return;
        }
        Context app = context.getApplicationContext();
        sWorker.execute(() -> {
            ServerAccount account = ownedBy(app, owner);
            if (account == null) {
                return;
            }
            try {
                String hash = ServerCrypto.secureHash(videoId, account.masterKey);
                String url = account.backendUrl + "/api/user/history/" + hash;
                long progress = Math.max(0, positionMs / 1000);
                ServerHttp.Response existing = ServerHttp.request("GET", url, account.token, null);
                if (existing.ok()) {
                    JSONObject update = new JSONObject().put("watched", isoNow()).put("progress", progress);
                    ServerHttp.request("POST", url, account.token, update.toString());
                    return;
                }
                JSONObject entry = new JSONObject()
                        .put("id", hash)
                        .put("watched", isoNow())
                        .put("progress", progress)
                        .put("lengthSeconds", Math.max(0, durationMs / 1000))
                        .put("title", sealed(truncate(title, 150), account.masterKey))
                        .put("author", sealed(truncate(author, 150), account.masterKey))
                        .put("thumbnail", sealed(thumbnail, account.masterKey))
                        .put("videoId", sealed(videoId, account.masterKey));
                if (channelId != null) {
                    entry.put("authorId", sealed(channelId, account.masterKey));
                }
                ServerHttp.request("POST", account.backendUrl + "/api/user/history", account.token, entry.toString());
            } catch (Exception e) {
                Log.e(TAG, "History not saved on the server: %s", e.getMessage());
            }
        });
    }

    /** Where the video was left on any device (ms), on the main thread; nothing when unknown. */
    public static void position(Context context, String videoId, PositionCallback callback) {
        if (videoId == null) {
            return;
        }
        Context app = context.getApplicationContext();
        sWorker.execute(() -> {
            ServerAccount account = activeAccount(app);
            if (account == null) {
                return;
            }
            try {
                String hash = ServerCrypto.secureHash(videoId, account.masterKey);
                ServerHttp.Response response = ServerHttp.request("GET", account.backendUrl + "/api/user/history/" + hash, account.token, null);
                if (!response.ok()) {
                    return;
                }
                long progressMs = new JSONObject(response.body).optLong("progress", -1) * 1000;
                if (progressMs > 0) {
                    sMain.post(() -> callback.onPosition(progressMs));
                }
            } catch (Exception e) {
                Log.e(TAG, "Position not read from the server: %s", e.getMessage());
            }
        });
    }

    // The server takes sealed fields up to 255 base64 characters (about 170 bytes of text), as for
    // the web: long titles are cut on a character boundary under that many UTF-8 bytes.
    private static String truncate(String text, int maxBytes) {
        if (text == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        int bytes = 0;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            int size = new String(Character.toChars(codePoint)).getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            if (bytes + size > maxBytes) {
                break;
            }
            out.appendCodePoint(codePoint);
            bytes += size;
            i += Character.charCount(codePoint);
        }
        return out.toString();
    }
}
