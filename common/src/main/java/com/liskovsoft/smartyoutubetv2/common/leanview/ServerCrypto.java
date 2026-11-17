package com.liskovsoft.smartyoutubetv2.common.leanview;

import android.util.Base64;

import com.goterl.lazysodium.LazySodiumAndroid;
import com.goterl.lazysodium.SodiumAndroid;
import com.goterl.lazysodium.interfaces.Box;
import com.goterl.lazysodium.interfaces.GenericHash;
import com.goterl.lazysodium.interfaces.SecretBox;

import java.nio.charset.StandardCharsets;

/**
 * leanview: the same libsodium primitives the compatible web uses for the user's server
 * (see the web client), so data written by the TV
 * and by the web read alike. libsodium-wrappers' to_base64/from_base64 default is URL-safe
 * without padding; so is this.
 */
public final class ServerCrypto {
    private static LazySodiumAndroid sSodium;

    private ServerCrypto() {
    }

    private static synchronized LazySodiumAndroid sodium() {
        if (sSodium == null) {
            sSodium = new LazySodiumAndroid(new SodiumAndroid());
        }
        return sSodium;
    }

    public static String toBase64(byte[] bytes) {
        return Base64.encodeToString(bytes, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
    }

    public static byte[] fromBase64(String text) {
        return Base64.decode(text, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
    }

    /** A crypto_box key pair: [publicKey, secretKey]. */
    public static byte[][] newBoxKeyPair() {
        byte[] publicKey = new byte[Box.PUBLICKEYBYTES];
        byte[] secretKey = new byte[Box.SECRETKEYBYTES];
        if (!sodium().cryptoBoxKeypair(publicKey, secretKey)) {
            throw new IllegalStateException("crypto_box_keypair failed");
        }
        return new byte[][] {publicKey, secretKey};
    }

    /** crypto_box_seal_open: what the signed-in device sealed for this key pair, or null. */
    public static byte[] sealOpen(byte[] cipher, byte[] publicKey, byte[] secretKey) {
        if (cipher.length < Box.SEALBYTES) {
            return null;
        }
        byte[] message = new byte[cipher.length - Box.SEALBYTES];
        return sodium().cryptoBoxSealOpen(message, cipher, cipher.length, publicKey, secretKey) ? message : null;
    }

    /** crypto_secretbox_easy with the master key: {nonce, cipher} in base64. */
    public static String[] encrypt(String text, byte[] masterKey) {
        byte[] message = text.getBytes(StandardCharsets.UTF_8);
        byte[] nonce = sodium().randomBytesBuf(SecretBox.NONCEBYTES);
        byte[] cipher = new byte[message.length + SecretBox.MACBYTES];
        if (!sodium().cryptoSecretBoxEasy(cipher, message, message.length, nonce, masterKey)) {
            throw new IllegalStateException("crypto_secretbox_easy failed");
        }
        return new String[] {toBase64(nonce), toBase64(cipher)};
    }

    /** crypto_secretbox_open_easy with the master key, or null when it does not open. */
    public static String decrypt(String nonce, String cipher, byte[] masterKey) {
        byte[] cipherBytes = fromBase64(cipher);
        if (cipherBytes.length < SecretBox.MACBYTES) {
            return null;
        }
        byte[] message = new byte[cipherBytes.length - SecretBox.MACBYTES];
        boolean opened = sodium().cryptoSecretBoxOpenEasy(message, cipherBytes, cipherBytes.length, fromBase64(nonce), masterKey);
        return opened ? new String(message, StandardCharsets.UTF_8) : null;
    }

    /** crypto_generichash keyed with the master key: how the server names a video or channel. */
    public static String secureHash(String text, byte[] masterKey) {
        byte[] in = text.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[GenericHash.BYTES];
        if (!sodium().cryptoGenericHash(out, out.length, in, in.length, masterKey, masterKey.length)) {
            throw new IllegalStateException("crypto_generichash failed");
        }
        return toBase64(out);
    }
}
