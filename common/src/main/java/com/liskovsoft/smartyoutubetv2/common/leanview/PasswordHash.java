package com.liskovsoft.smartyoutubetv2.common.leanview;

import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * leanview: a profile's PIN is kept as a salted PBKDF2 hash instead of the plain text upstream
 * SmartTube stored. HMAC-SHA1 because PBKDF2WithHmacSHA256 needs Android 8 and the app still
 * runs on 4.2. Values saved before this build are plain text: they still open, and
 * {@link #upgrade} turns them into a hash.
 */
public final class PasswordHash {
    private static final String PREFIX = "pbkdf2-sha1";
    private static final int ITERATIONS = 50_000;
    private static final int MAX_ITERATIONS = 1_000_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;
    private static final int FLAGS = Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP;

    private PasswordHash() {
    }

    public static String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        return PREFIX + "$" + ITERATIONS + "$" + Base64.encodeToString(salt, FLAGS) + "$"
                + Base64.encodeToString(derive(password, salt, ITERATIONS), FLAGS);
    }

    public static boolean isHash(String stored) {
        return stored != null && stored.startsWith(PREFIX + "$");
    }

    /** A hash a PIN can be checked against: the prefix, a usable iteration count, salt and hash. */
    public static boolean isWellFormed(String stored) {
        if (!isHash(stored)) {
            return false;
        }
        String[] parts = stored.split("\\$");
        if (parts.length != 4 || parts[2].isEmpty() || parts[3].isEmpty()) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            return iterations >= 1 && iterations <= MAX_ITERATIONS
                    && Base64.decode(parts[2], FLAGS).length > 0 && Base64.decode(parts[3], FLAGS).length > 0;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static boolean matches(String stored, String typed) {
        if (stored == null || typed == null) {
            return false;
        }
        if (!isHash(stored)) {
            return MessageDigest.isEqual(stored.getBytes(StandardCharsets.UTF_8), typed.getBytes(StandardCharsets.UTF_8));
        }
        String[] parts = stored.split("\\$");
        if (parts.length != 4) {
            return false;
        }
        // A damaged value (a TV unplugged while saving) is a wrong PIN, not a crash on every try.
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.decode(parts[2], FLAGS);
            byte[] expected = Base64.decode(parts[3], FLAGS);
            if (iterations < 1 || iterations > MAX_ITERATIONS || salt.length == 0 || expected.length == 0) {
                return false;
            }
            return MessageDigest.isEqual(expected, derive(typed, salt, iterations));
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** A plain-text value from an older build becomes a hash; a hash or null stays as it is. */
    public static String upgrade(String stored) {
        return stored == null || isHash(stored) ? stored : hash(stored);
    }

    private static byte[] derive(String password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
