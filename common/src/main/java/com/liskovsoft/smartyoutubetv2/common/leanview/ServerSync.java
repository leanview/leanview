package com.liskovsoft.smartyoutubetv2.common.leanview;

import android.content.Context;
import android.content.SharedPreferences;

import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.googlecommon.service.oauth.YouTubeAccount;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.youtubeapi.service.AccountActionMirror;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * leanview: repeats on the user's server what the paired Google account does on the TV, so the TV,
 * the web (yt.example.com) and YouTube keep the same subscriptions. Runs only while the selected
 * Google account is the one paired with the connected server account (one profile per person).
 * Every action is queued on disk first and removed only once the server took it, so nothing is
 * lost when the server or the network is down; the queue is retried on the next action and when
 * the app starts.
 */
public final class ServerSync implements AccountActionMirror.Listener {
    private static final String TAG = ServerSync.class.getSimpleName();
    private static final String PREFS = "leanview_server_sync";
    private static final String KEY_QUEUE = "queue";
    private static ServerSync sInstance;
    private final Context mContext;
    private final ExecutorService mWorker = Executors.newSingleThreadExecutor();

    ServerSync(Context context) {
        mContext = context.getApplicationContext();
    }

    // Counts the flushes asked from outside (typing or learning the Invidious instance); read by tests.
    static int flushRequests;

    /**
     * Whether an action of the selected person goes to the queue. With no Invidious instance known
     * the mirror is off and NOTHING is queued (the queue used to grow forever; what happens while it
     * is off is not replayed later). Never for another person's Google account.
     */
    static boolean shouldQueue(ServerAccount account, String selectedGoogle) {
        return account != null && account.googleAccount != null && account.googleAccount.equals(selectedGoogle)
                && account.invidiousInstance != null;
    }

    /** Sends what is queued now, e.g. right after the Invidious instance became known. No-op before install. */
    public static synchronized void requestFlush() {
        flushRequests++;
        ServerSync sync = sInstance;
        if (sync != null) {
            sync.mWorker.execute(sync::flush);
        }
    }

    /** Called once at app start. */
    public static synchronized void install(Context context) {
        if (sInstance != null) {
            return;
        }
        sInstance = new ServerSync(context);
        AccountActionMirror.setListener(sInstance);
        sInstance.mWorker.execute(() -> {
            // An account stored before the Invidious instance was kept learns it from its server.
            InvidiousInstance.fillMissing(sInstance.mContext, InvidiousInstance.fromServer());
            sInstance.flush();
        });
    }

    private static final String CHANNEL_PREFIX = "page:";

    /** The selected Google profile's key (profileKey), or null when none is selected. */
    public static String selectedGoogleAccount() {
        return profileKey(selectedAccount());
    }

    private static Account selectedAccount() {
        try {
            return YouTubeServiceManager.instance().getSignInService().getSelectedAccount();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * The key a server connection is kept under for a Google profile: its email, or for a channel
     * (brand account, no email) its page id. a user watches as a channel "Carol": keyed by email
     * only, connecting there saved a connection with no owner, which never syncs (2026-11-08).
     */
    public static String profileKey(Account account) {
        if (account == null) {
            return null;
        }
        String pageIdToken = account instanceof YouTubeAccount ? ((YouTubeAccount) account).getPageIdToken() : null;
        return profileKey(account.getEmail(), pageIdToken);
    }

    static String profileKey(String email, String pageIdToken) {
        if (email != null) {
            return email;
        }
        return pageIdToken != null && !pageIdToken.isEmpty() ? CHANNEL_PREFIX + pageIdToken : null;
    }

    public static boolean isChannelKey(String key) {
        return key != null && key.startsWith(CHANNEL_PREFIX);
    }

    /** What to show for a profile key: a channel's name instead of its page id. */
    public static String displayName(String key) {
        if (!isChannelKey(key)) {
            return key;
        }
        Account account = selectedAccount();
        return account != null && key.equals(profileKey(account)) && account.getName() != null ? account.getName() : key;
    }

    @Override
    public void onSubscribed(String channelId) {
        enqueue("subscribe", channelId);
    }

    @Override
    public void onUnsubscribed(String channelId) {
        enqueue("unsubscribe", channelId);
    }

    private void enqueue(String action, String channelId) {
        if (enqueueIfWanted(action, channelId, selectedGoogleAccount())) {
            mWorker.execute(this::flush);
        }
    }

    // leanview: the queue write behind enqueue, apart from the flush so a test can pin that shouldQueue gates it.
    boolean enqueueIfWanted(String action, String channelId, String selectedGoogle) {
        ServerAccount account = ServerAccount.forGoogle(mContext, selectedGoogle);
        if (!shouldQueue(account, selectedGoogle)) {
            return false; // not connected, no Invidious known, or another person's Google account: never mixed into this server
        }
        synchronized (this) {
            JSONArray queue = readQueue();
            try {
                queue.put(new JSONObject().put("action", action).put("channelId", channelId)
                        .put("googleAccount", account.googleAccount));
            } catch (Exception e) {
                return false;
            }
            writeQueue(queue);
        }
        return true;
    }

    // Test access: what is queued right now, as the stored JSON text.
    String queuedForTest() {
        return readQueue().toString();
    }

    /**
     * Sends queued actions in order, each to the server account of the person who did it; stops at
     * the first failure and keeps it for later. Actions of a profile removed from the TV are dropped.
     */
    private void flush() {
        Map<String, InvidiousApi> invidiousByGoogle = new HashMap<>();
        while (true) {
            JSONObject next;
            synchronized (this) {
                JSONArray queue = readQueue();
                if (queue.length() == 0) {
                    return;
                }
                next = queue.optJSONObject(0);
            }
            String googleAccount = next != null ? next.optString("googleAccount", null) : null;
            ServerAccount account = googleAccount != null ? ServerAccount.forGoogle(mContext, googleAccount) : null;
            if (account != null && googleAccount.equals(account.googleAccount)) {
                try {
                    InvidiousApi invidious = invidiousByGoogle.get(googleAccount);
                    if (invidious == null) {
                        invidious = InvidiousApi.fromServer(account);
                        if (invidious == null) {
                            return; // the web never connected an Invidious account; kept for later
                        }
                        invidiousByGoogle.put(googleAccount, invidious);
                    }
                    String channelId = next.getString("channelId");
                    if ("subscribe".equals(next.optString("action"))) {
                        invidious.subscribe(channelId);
                    } else {
                        invidious.unsubscribe(channelId);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Will retry %s: %s", next, e.getMessage());
                    return;
                }
            }
            synchronized (this) {
                JSONArray queue = readQueue();
                JSONArray rest = new JSONArray();
                for (int i = 1; i < queue.length(); i++) {
                    rest.put(queue.opt(i));
                }
                writeQueue(rest);
            }
        }
    }

    private SharedPreferences prefs() {
        return mContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private JSONArray readQueue() {
        try {
            return new JSONArray(prefs().getString(KEY_QUEUE, "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private void writeQueue(JSONArray queue) {
        prefs().edit().putString(KEY_QUEUE, queue.toString()).commit();
    }
}
