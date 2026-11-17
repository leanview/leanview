package com.liskovsoft.smartyoutubetv2.common.leanview;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import com.liskovsoft.sharedutils.mylogger.Log;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * leanview: the TV's connection to the person's own server (a compatible backend, whose address
 * they typed: the APK carries none), received through Quick Connect: the session token and the
 * master key that opens their encrypted history and settings. Both stay in app-private storage,
 * encrypted with a key that never leaves the Android keystore.
 */
public final class ServerAccount {
    private static final String PREFS = "leanview_server_account";
    private static final String KEY_ALIAS = "leanview_server_account";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";

    public final String backendUrl;
    public final String token;
    public final byte[] masterKey;
    public final String username;
    /**
     * The Google account (email) this server account is paired with: one profile per person
     * (decided 2026-10-28). Syncing runs only while that Google account is the selected
     * one, so another person's subscriptions never reach this server. Null: not paired yet.
     */
    public final String googleAccount;
    /**
     * The Invidious instance of this server, as the server reported it or the person typed it
     * (InvidiousInstance); null when none is known, which keeps what needs one off.
     */
    public final String invidiousInstance;

    public ServerAccount(String backendUrl, String token, byte[] masterKey, String username) {
        this(backendUrl, token, masterKey, username, null);
    }

    public ServerAccount(String backendUrl, String token, byte[] masterKey, String username, String googleAccount) {
        this(backendUrl, token, masterKey, username, googleAccount, null);
    }

    public ServerAccount(String backendUrl, String token, byte[] masterKey, String username, String googleAccount,
                         String invidiousInstance) {
        this.backendUrl = backendUrl;
        this.token = token;
        this.masterKey = masterKey;
        this.username = username;
        this.googleAccount = googleAccount;
        this.invidiousInstance = invidiousInstance;
    }

    public ServerAccount pairedWith(String googleAccount) {
        return new ServerAccount(backendUrl, token, masterKey, username, googleAccount, invidiousInstance);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // One profile per person (2026-10-29): each server account is stored under the Google account it
    // is paired with ("" while not paired yet), so Alice and Bob can each connect their own server
    // account on the same TV and the selected Google account decides whose it is.
    private static final String KEY_PROFILES = "profiles";

    private static String slot(String googleAccount) {
        return googleAccount != null ? googleAccount : "";
    }

    private static Set<String> profileSlots(SharedPreferences prefs) {
        migrateSingleProfile(prefs);
        return new HashSet<>(prefs.getStringSet(KEY_PROFILES, Collections.emptySet()));
    }

    // Before profiles, one account was stored under plain keys; it becomes the profile of its pairing.
    private static void migrateSingleProfile(SharedPreferences prefs) {
        if (!prefs.contains("token")) {
            return;
        }
        String googleAccount = prefs.getString("googleAccount", null);
        String key = slot(googleAccount) + "|";
        Set<String> slots = new HashSet<>(prefs.getStringSet(KEY_PROFILES, Collections.emptySet()));
        slots.add(slot(googleAccount));
        prefs.edit()
                .putString(key + "backendUrl", prefs.getString("backendUrl", null))
                .putString(key + "token", prefs.getString("token", null))
                .putString(key + "masterKey", prefs.getString("masterKey", null))
                .putString(key + "username", prefs.getString("username", null))
                .putStringSet(KEY_PROFILES, slots)
                .remove("backendUrl").remove("token").remove("masterKey").remove("username").remove("googleAccount")
                .commit();
    }

    private static ServerAccount read(SharedPreferences prefs, String slot) {
        String key = slot + "|";
        String token = sProtector.unwrap(prefs.getString(key + "token", null));
        String masterKey = sProtector.unwrap(prefs.getString(key + "masterKey", null));
        String backendUrl = prefs.getString(key + "backendUrl", null);
        if (token == null || masterKey == null || backendUrl == null) {
            if (token != null && backendUrl == null) {
                Log.e("ServerAccount", "Profile '%s' has no server address stored: skipped, connect again", slot);
            }
            return null; // no server address is built in, so a profile without its own is not guessed at
        }
        return new ServerAccount(backendUrl, token, ServerCrypto.fromBase64(masterKey),
                prefs.getString(key + "username", null), slot.isEmpty() ? null : slot,
                prefs.getString(key + "invidiousInstance", null));
    }

    /**
     * The account of whoever is watching: the profile paired with the selected Google account. Null
     * when this person has not connected (or its data cannot be opened).
     */
    public static ServerAccount load(Context context) {
        return forGoogle(context, ServerSync.selectedGoogleAccount());
    }

    /**
     * The profile paired with this Google account, else null. A connection with no owner is nobody's
     * until someone claims it (unowned()): falling back to it sent Bob's history to Alice's
     * server account (2026-11-01).
     */
    public static ServerAccount forGoogle(Context context, String googleAccount) {
        SharedPreferences prefs = prefs(context);
        return googleAccount != null && profileSlots(prefs).contains(googleAccount) ? read(prefs, googleAccount) : null;
    }

    /** The connection made before it had an owner (no Google account paired), else null. */
    public static ServerAccount unowned(Context context) {
        SharedPreferences prefs = prefs(context);
        return profileSlots(prefs).contains("") ? read(prefs, "") : null;
    }

    /** Every profile on this TV. */
    public static List<ServerAccount> all(Context context) {
        SharedPreferences prefs = prefs(context);
        List<ServerAccount> accounts = new ArrayList<>();
        for (String slot : profileSlots(prefs)) {
            ServerAccount account = read(prefs, slot);
            if (account != null) {
                accounts.add(account);
            }
        }
        return accounts;
    }

    /**
     * Stores this profile under its Google account, replacing whatever that person had before. A
     * connection with no owner made by the same server user is that person's old one: it goes.
     */
    public void save(Context context) {
        SharedPreferences prefs = prefs(context);
        Set<String> slots = profileSlots(prefs);
        if (googleAccount != null && username != null && slots.contains("")
                && username.equals(prefs.getString("|username", null))) {
            new ServerAccount(backendUrl, token, masterKey, username, null).remove(context);
            slots = profileSlots(prefs);
        }
        String key = slot(googleAccount) + "|";
        slots.add(slot(googleAccount));
        // Connecting again to the same server keeps the Invidious instance it had (the new
        // connection comes from Quick Connect, which does not carry one).
        boolean sameServer = backendUrl.equals(prefs.getString(key + "backendUrl", null));
        String instance = invidiousInstance;
        if (instance == null && sameServer) {
            instance = prefs.getString(key + "invidiousInstance", null);
        }
        SharedPreferences.Editor editor = prefs.edit();
        if (!sameServer) {
            editor.remove(key + "invidiousFillTried"); // another server: its own one-time read
        }
        editor
                .putString(key + "backendUrl", backendUrl)
                .putString(key + "token", sProtector.wrap(token))
                .putString(key + "masterKey", sProtector.wrap(ServerCrypto.toBase64(masterKey)))
                .putString(key + "username", username)
                .putString(key + "invidiousInstance", instance)
                .putStringSet(KEY_PROFILES, slots)
                .commit();
    }

    // True while the stored profile is still this connection (same slot, same server).
    private boolean stillStored(SharedPreferences prefs) {
        return profileSlots(prefs).contains(slot(googleAccount))
                && backendUrl != null && backendUrl.equals(prefs.getString(slot(googleAccount) + "|backendUrl", null));
    }

    /**
     * Whether the web's saved Invidious instance was already read for this connection (the read is
     * a convenience, done once per connection; the typed preference is the source of truth).
     */
    public boolean invidiousFillTried(Context context) {
        SharedPreferences prefs = prefs(context);
        return stillStored(prefs) && prefs.getBoolean(slot(googleAccount) + "|invidiousFillTried", false);
    }

    public void markInvidiousFillTried(Context context) {
        SharedPreferences prefs = prefs(context);
        if (stillStored(prefs)) {
            prefs.edit().putBoolean(slot(googleAccount) + "|invidiousFillTried", true).commit();
        }
    }

    /**
     * Stores (or with null, clears) only this profile's Invidious instance, and counts it as the
     * one-time read done. False, and nothing written, when the profile was removed or connected to
     * another server meanwhile.
     */
    public boolean saveInvidiousInstance(Context context, String instance) {
        synchronized (ServerAccount.class) { // leanview: same lock as fillInvidiousInstance, which checks then writes
            SharedPreferences prefs = prefs(context);
            if (!stillStored(prefs)) {
                return false;
            }
            String key = slot(googleAccount) + "|";
            prefs.edit().putString(key + "invidiousInstance", instance).putBoolean(key + "invidiousFillTried", true).commit();
            return true;
        }
    }

    /**
     * What the one-time read of the web's key writes (leanview, re-review M-B): the instance is stored
     * only while the profile STILL has none and nobody typed or cleared it since (typing sets the
     * tried flag), so a read already in flight never replaces the person's own choice. False, and
     * nothing written, otherwise or when the profile was removed or connected to another server.
     */
    public boolean fillInvidiousInstance(Context context, String instance) {
        synchronized (ServerAccount.class) {
            SharedPreferences prefs = prefs(context);
            if (!stillStored(prefs)) {
                return false;
            }
            String key = slot(googleAccount) + "|";
            if (prefs.getString(key + "invidiousInstance", null) != null || prefs.getBoolean(key + "invidiousFillTried", false)) {
                return false;
            }
            prefs.edit().putString(key + "invidiousInstance", instance).putBoolean(key + "invidiousFillTried", true).commit();
            return true;
        }
    }

    /** Pairs this profile with a Google account: it moves there (a not-paired profile stops existing). */
    public ServerAccount pair(Context context, String googleAccount) {
        ServerAccount paired = pairedWith(googleAccount);
        remove(context);
        paired.save(context);
        return paired;
    }

    /** Removes this profile from the TV; other people's stay. */
    public void remove(Context context) {
        SharedPreferences prefs = prefs(context);
        Set<String> slots = profileSlots(prefs);
        String key = slot(googleAccount) + "|";
        slots.remove(slot(googleAccount));
        prefs.edit()
                .remove(key + "backendUrl").remove(key + "token").remove(key + "masterKey").remove(key + "username")
                .remove(key + "invidiousInstance").remove(key + "invidiousFillTried")
                .putStringSet(KEY_PROFILES, slots)
                .commit();
    }

    /** Keeps the stored token and master key unreadable outside this app; replaceable in tests. */
    interface Protector {
        String wrap(String plain);

        String unwrap(String stored);
    }

    static Protector sProtector = new Protector() {
        @Override
        public String wrap(String plain) {
            return ServerAccount.wrap(plain);
        }

        @Override
        public String unwrap(String stored) {
            return ServerAccount.unwrap(stored);
        }
    };

    private static SecretKey keystoreKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return generator.generateKey();
    }

    private static String wrap(String plain) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keystoreKey());
            byte[] iv = cipher.getIV();
            byte[] sealed = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(sealed, Base64.NO_WRAP);
        } catch (Exception e) {
            throw new IllegalStateException("could not protect the server account", e);
        }
    }

    private static String unwrap(String stored) {
        if (stored == null || !stored.contains(":")) {
            return null;
        }
        try {
            String[] parts = stored.split(":", 2);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keystoreKey(), new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
            return new String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}
