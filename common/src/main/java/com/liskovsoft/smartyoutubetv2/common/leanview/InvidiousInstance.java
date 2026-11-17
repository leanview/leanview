package com.liskovsoft.smartyoutubetv2.common.leanview;

import android.content.Context;

import com.liskovsoft.sharedutils.mylogger.Log;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * leanview: where the Invidious instance comes from. The APK carries none. The web keeps its instance
 * in the encrypted key "invidiousInstance" of the person's server (see the web client), so the TV reads it from
 * there as a convenience and stores it with the connection; the person can type it in Settings >
 * Accounts, and that preference is the source of truth. Decided 2026-11-14: the app is
 * universal, nothing changes on any server, and whoever has an Invidious sets it in Settings. The
 * read of the web's key is done AT MOST ONCE per connection, and only while no preference exists.
 * With no instance, the features that need an Invidious (its Home feed, ServerSync) stay off.
 */
public final class InvidiousInstance {
    private static final String TAG = InvidiousInstance.class.getSimpleName();
    static final String SERVER_KEY = "invidiousInstance";

    /** Reads the instance the server keeps for this account; null when it has none. Blocking. */
    public interface Source {
        String read(ServerAccount account) throws IOException;
    }

    private InvidiousInstance() {
    }

    /** The real source: the connected server's encrypted key-value store. */
    public static Source fromServer() {
        return account -> ServerApi.getKeyValue(account, SERVER_KEY);
    }

    /**
     * For every connected profile with no instance and no earlier read, asks its server ONCE and
     * stores what it reports. A stored value or preference is never asked for nor replaced, and a
     * server that answered (with a value or with none) is not asked again for that connection. A
     * server that is down or fails raises nothing and is tried again next time. A read that comes
     * back after the connection changed is dropped. Returns how many were filled in; each one asks
     * the subscription queue to flush. Blocking: call off the main thread.
     */
    public static int fillMissing(Context context, Source source) {
        int filled = 0;
        for (ServerAccount account : ServerAccount.all(context)) {
            if (account.invidiousInstance != null || account.invidiousFillTried(context)) {
                continue;
            }
            try {
                String instance = clean(source.read(account)); // the server answered, with a value or none
                if (instance != null) {
                    if (account.fillInvidiousInstance(context, instance)) { // never over what was typed meanwhile
                        filled++;
                    }
                } else {
                    account.markInvidiousFillTried(context);
                }
            } catch (Exception e) {
                Log.e(TAG, "Could not read the Invidious instance of %s: %s", account.backendUrl, e.getMessage());
            }
        }
        if (filled > 0) {
            ServerSync.requestFlush();
        }
        return filled;
    }

    /**
     * The person's own choice for this connection: stored when it is an address, cleared when blank
     * or null. Anything else is refused and nothing changes. Returns what is stored now.
     */
    public static String set(Context context, ServerAccount account, String input) {
        if (input == null || input.trim().isEmpty()) {
            account.saveInvidiousInstance(context, null);
            return null;
        }
        String instance = ServerAddress.normalize(input);
        if (instance == null || !account.saveInvidiousInstance(context, instance)) {
            return null;
        }
        ServerSync.requestFlush(); // what was queued while there was no instance goes out now
        return instance;
    }

    /**
     * The instance for public feeds (Home with no account selected): the current profile's, else
     * any connected profile's, else null. The feed lists nothing personal, so any will do.
     */
    public static String forFeed(Context context) {
        ServerAccount current = null;
        try {
            current = ServerAccount.load(context);
        } catch (Exception e) {
            // no selected account known here: fall through to the other profiles
        }
        if (current != null && current.invidiousInstance != null) {
            return current.invidiousInstance;
        }
        List<String> found = new java.util.ArrayList<>();
        for (ServerAccount account : ServerAccount.all(context)) {
            if (account.invidiousInstance != null) {
                found.add(account.invidiousInstance);
            }
        }
        if (found.isEmpty()) {
            return null;
        }
        Collections.sort(found); // the same one every time, whatever order the profiles come in
        return found.get(0);
    }

    // The web stores a plain string as is; an instance saved as JSON text comes quoted.
    private static String clean(String stored) {
        if (stored == null) {
            return null;
        }
        String value = stored.trim();
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        return ServerAddress.normalize(value);
    }
}
