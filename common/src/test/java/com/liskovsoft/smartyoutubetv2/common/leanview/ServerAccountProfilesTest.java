package com.liskovsoft.smartyoutubetv2.common.leanview;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

// One server profile per person on the same TV (Alice and Bob): the Google account decides
// whose server account is used, and removing one leaves the other.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ServerAccountProfilesTest {
    private static final String ALICE = "Alice@example.com";
    private static final String BOB = "bob@example.com";
    private static final byte[] KEY_A = new byte[32];
    private static final byte[] KEY_B = new byte[32];

    static {
        KEY_B[0] = 7;
    }

    private Context mContext;
    private ServerAccount.Protector mKeystore;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.getApplication();
        // The Android keystore does not exist under Robolectric; storage is what is tested here.
        mKeystore = ServerAccount.sProtector;
        ServerAccount.sProtector = new ServerAccount.Protector() {
            @Override
            public String wrap(String plain) {
                return "x:" + plain;
            }

            @Override
            public String unwrap(String stored) {
                return stored != null && stored.startsWith("x:") ? stored.substring(2) : null;
            }
        };
    }

    @After
    public void tearDown() {
        ServerAccount.sProtector = mKeystore;
    }

    private static ServerAccount account(String token, byte[] key, String user, String google) {
        return new ServerAccount("https://yt.example.com", token, key, user, google);
    }

    @Test
    public void eachPersonGetsTheirOwnServerAccount() {
        account("tokenA", KEY_A, "Alice", ALICE).save(mContext);
        account("tokenB", KEY_B, "bob", BOB).save(mContext);

        assertEquals("tokenA", ServerAccount.forGoogle(mContext, ALICE).token);
        assertArrayEquals(KEY_B, ServerAccount.forGoogle(mContext, BOB).masterKey);
        assertEquals(BOB, ServerAccount.forGoogle(mContext, BOB).googleAccount);
        // Someone else signed in: nobody's server account.
        assertNull(ServerAccount.forGoogle(mContext, "guest@example.com"));
        assertNull(ServerAccount.forGoogle(mContext, null));
        assertEquals(2, ServerAccount.all(mContext).size());
    }

    @Test
    public void removingOneProfileKeepsTheOther() {
        account("tokenA", KEY_A, "Alice", ALICE).save(mContext);
        account("tokenB", KEY_B, "bob", BOB).save(mContext);

        ServerAccount.forGoogle(mContext, BOB).remove(mContext);

        assertNull(ServerAccount.forGoogle(mContext, BOB));
        assertEquals("tokenA", ServerAccount.forGoogle(mContext, ALICE).token);
    }

    @Test
    public void aNotPairedAccountMovesToThePersonItIsPairedWith() {
        account("tokenA", KEY_A, "Alice", null).save(mContext);
        assertEquals("tokenA", ServerAccount.unowned(mContext).token); // not paired yet

        ServerAccount.unowned(mContext).pair(mContext, ALICE);
        account("tokenB", KEY_B, "bob", BOB).save(mContext);

        assertEquals(ALICE, ServerAccount.forGoogle(mContext, ALICE).googleAccount);
        assertEquals("tokenB", ServerAccount.forGoogle(mContext, BOB).token);
        assertNull(ServerAccount.forGoogle(mContext, "guest@example.com"));
        assertEquals(2, ServerAccount.all(mContext).size());
        assertNull(ServerAccount.unowned(mContext));
    }

    // 2026-11-01: Alice's connection was made before profiles, so it had no owner, and every
    // profile without its own connection used it: Bob's history went into his server account.
    @Test
    public void aConnectionWithNoOwnerIsNobodysUntilSomeoneClaimsIt() {
        account("tokenA", KEY_A, "Alice", null).save(mContext);

        assertNull(ServerAccount.forGoogle(mContext, BOB));
        assertNull(ServerAccount.forGoogle(mContext, ALICE));
        assertNull(ServerAccount.forGoogle(mContext, null));
        assertEquals("tokenA", ServerAccount.unowned(mContext).token);

        // Bob connects her own: both live side by side, hers is used only for her.
        account("tokenB", KEY_B, "bob", BOB).save(mContext);
        assertEquals("tokenB", ServerAccount.forGoogle(mContext, BOB).token);
        assertNull(ServerAccount.forGoogle(mContext, ALICE));
        assertEquals("tokenA", ServerAccount.unowned(mContext).token);
    }

    // 2026-11-08: the TV cannot tell whose the connection with no owner is (it was saved without a
    // Google account, and the server did not keep the Google email), so nobody is offered it any
    // more: its person connects again with the QR, and that new connection replaces it.
    @Test
    public void connectingAgainAsTheSameServerUserReplacesTheConnectionWithNoOwner() {
        account("tokenA", KEY_A, "Alice", null).save(mContext);

        account("tokenNew", KEY_B, "Alice", ALICE).save(mContext);

        assertNull(ServerAccount.unowned(mContext));
        assertEquals("tokenNew", ServerAccount.forGoogle(mContext, ALICE).token);
        assertEquals(1, ServerAccount.all(mContext).size());
    }

    @Test
    public void theSingleAccountStoredBeforeProfilesBecomesItsPersonsProfile() {
        SharedPreferences prefs = mContext.getSharedPreferences("leanview_server_account", Context.MODE_PRIVATE);
        prefs.edit()
                .putString("backendUrl", "https://yt.example.com")
                .putString("token", "x:tokenOld")
                .putString("masterKey", "x:" + ServerCrypto.toBase64(KEY_A))
                .putString("username", "Alice")
                .putString("googleAccount", BOB)
                .commit();

        ServerAccount migrated = ServerAccount.forGoogle(mContext, BOB);

        assertEquals("tokenOld", migrated.token);
        assertEquals("https://yt.example.com", migrated.backendUrl); // the stored server is kept as it was
        assertEquals("Alice", migrated.username);
        assertArrayEquals(KEY_A, migrated.masterKey);
        assertNull(ServerAccount.forGoogle(mContext, ALICE));
        assertFalse(prefs.contains("token"));
    }

    // leanview: no server address is built in, so a stored profile without one is not guessed at:
    // it is skipped and the person connects again.
    @Test
    public void aStoredProfileWithNoServerAddressIsNeverGuessed() {
        SharedPreferences prefs = mContext.getSharedPreferences("leanview_server_account", Context.MODE_PRIVATE);
        prefs.edit()
                .putStringSet("profiles", new java.util.HashSet<>(java.util.Collections.singleton(ALICE)))
                .putString(ALICE + "|token", "x:tokenA")
                .putString(ALICE + "|masterKey", "x:" + ServerCrypto.toBase64(KEY_A))
                .commit();

        assertNull(ServerAccount.forGoogle(mContext, ALICE));
        assertEquals(0, ServerAccount.all(mContext).size());
    }
}
