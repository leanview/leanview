package com.liskovsoft.smartyoutubetv2.common.leanview;

import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.IOException;

/**
 * leanview: what a person set on yt.example.com for their profile on the TV (Settings > Account > "Your
 * profile on the TV", setting "tvProfileLock"), synced encrypted with their master key. When the
 * server holds it, it decides the profile's lock and PIN on the TV (see the web client). Blocking: call off the main thread.
 */
public final class ServerProfileLock {
    public final boolean enabled;
    /** A PasswordHash value, or null. */
    public final String pinHash;

    ServerProfileLock(boolean enabled, String pinHash) {
        this.enabled = enabled;
        this.pinHash = pinHash;
    }

    /** True when the profile must ask: enabled and with a PIN the TV can check. */
    public boolean locks() {
        return enabled && pinHash != null;
    }

    /** The person's setting, or null when they never set one on the server. */
    public static ServerProfileLock fetch(ServerAccount account) throws IOException {
        return parse(ServerApi.getKeyValue(account, "tvProfileLock"));
    }

    /**
     * The stored value: the web keeps a JSON object, possibly as a JSON string of it. Anything that
     * does not read as one is treated as never set, so a damaged value changes nothing on the TV.
     */
    static ServerProfileLock parse(String value) {
        if (value == null) {
            return null;
        }
        try {
            Object parsed = new JSONTokener(value).nextValue();
            if (parsed instanceof String) {
                parsed = new JSONTokener((String) parsed).nextValue();
            }
            if (!(parsed instanceof JSONObject)) {
                return null;
            }
            JSONObject lock = (JSONObject) parsed;
            // Only a real true or false: a damaged "enabled" must not read as "off" and clear the PIN.
            Object enabledValue = lock.opt("enabled");
            if (!(enabledValue instanceof Boolean)) {
                return null;
            }
            boolean enabled = (Boolean) enabledValue;
            String pinHash = lock.isNull("pinHash") ? null : lock.optString("pinHash", null);
            boolean checkable = PasswordHash.isWellFormed(pinHash);
            if (enabled && !checkable) {
                // Locked but with no PIN the TV can check: never open the profile because of a
                // damaged or buggy value; keep what the TV has.
                return null;
            }
            return new ServerProfileLock(enabled, checkable ? pinHash : null);
        } catch (Exception e) {
            return null;
        }
    }
}
