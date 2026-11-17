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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

// leanview: no Invidious address is built in. It comes from the connected server (the web keeps it in
// the encrypted key "invidiousInstance"; /api/config does not carry it) or from a preference, and
// without one the features that need it stay off. The migration tests start from stored data shaped
// as the TV has it today (no instance stored, backend https://yt.example.com).
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class InvidiousInstanceTest {
    private static final String ALICE = "Alice@example.com";
    private static final String BOB = "bob@example.com";
    private static final byte[] KEY = new byte[32];

    private Context mContext;
    private ServerAccount.Protector mKeystore;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.getApplication();
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

    private static ServerAccount account(String backend, String token, String google) {
        return new ServerAccount(backend, token, KEY, "user", google);
    }

    // The TV as it is today: stored before this change, so no "invidiousInstance" key exists.
    private void storeTodaysShape() {
        SharedPreferences prefs = mContext.getSharedPreferences("leanview_server_account", Context.MODE_PRIVATE);
        prefs.edit()
                .putStringSet("profiles", new java.util.HashSet<>(java.util.Collections.singleton(ALICE)))
                .putString(ALICE + "|backendUrl", "https://yt.example.com")
                .putString(ALICE + "|token", "x:tokenA")
                .putString(ALICE + "|masterKey", "x:" + ServerCrypto.toBase64(KEY))
                .putString(ALICE + "|username", "Alice")
                .commit();
    }

    private static InvidiousInstance.Source reporting(String value, List<String> asked) {
        return account -> {
            asked.add(account.backendUrl);
            return value;
        };
    }

    @Test
    public void todaysStoredAccountKeepsItsBackendAndHasNoInstanceYet() {
        storeTodaysShape();

        ServerAccount loaded = ServerAccount.forGoogle(mContext, ALICE);

        assertEquals("https://yt.example.com", loaded.backendUrl);
        assertEquals("tokenA", loaded.token);
        assertNull(loaded.invidiousInstance);
    }

    @Test
    public void theInstanceTheServerReportsIsFilledInOnTheNextStart() {
        storeTodaysShape();
        List<String> asked = new ArrayList<>();

        int filled = InvidiousInstance.fillMissing(mContext, reporting("https://invidious.example.org/", asked));

        assertEquals(1, filled);
        assertEquals("https://yt.example.com", asked.get(0)); // asked of the account's own server
        ServerAccount loaded = ServerAccount.forGoogle(mContext, ALICE);
        assertEquals("https://invidious.example.org", loaded.invidiousInstance);
        assertEquals("https://yt.example.com", loaded.backendUrl);
        assertEquals("tokenA", loaded.token);
    }

    @Test
    public void filledOnceIsNotAskedAgainAndTheStoredValueIsKept() {
        storeTodaysShape();
        InvidiousInstance.fillMissing(mContext, reporting("https://invidious.example.org", new ArrayList<>()));
        List<String> asked = new ArrayList<>();

        int again = InvidiousInstance.fillMissing(mContext, reporting("https://other.example.org", asked));

        assertEquals(0, again);
        assertEquals(0, asked.size());
        assertEquals("https://invidious.example.org", ServerAccount.forGoogle(mContext, ALICE).invidiousInstance);
    }

    @Test
    public void aServerWithNoInstanceLeavesItEmptyWithoutErrors() {
        storeTodaysShape();

        assertEquals(0, InvidiousInstance.fillMissing(mContext, account -> null));

        assertNull(ServerAccount.forGoogle(mContext, ALICE).invidiousInstance);
    }

    @Test
    public void aBlankOrGarbageValueFillsNothing() {
        storeTodaysShape();

        assertEquals(0, InvidiousInstance.fillMissing(mContext, account -> "  "));

        assertNull(ServerAccount.forGoogle(mContext, ALICE).invidiousInstance);
    }

    @Test
    public void aServerThatFailsLeavesItEmptyWithoutErrorsAndIsAskedAgainNextTime() {
        storeTodaysShape();

        assertEquals(0, InvidiousInstance.fillMissing(mContext, account -> {
            throw new IOException("server down");
        }));
        List<String> asked = new ArrayList<>();
        int filled = InvidiousInstance.fillMissing(mContext, reporting("https://invidious.example.org", asked));

        assertEquals(1, filled);
        assertEquals(1, asked.size()); // the failed read did not use up the one try
    }

    // Alice's ruling (2026-11-14): the typed preference is the source of truth; the web's saved key is
    // only a convenience read once per connection, never on every start.
    @Test
    public void aServerThatAnsweredWithNoInstanceIsNotAskedAgain() {
        storeTodaysShape();
        InvidiousInstance.fillMissing(mContext, account -> null);
        List<String> asked = new ArrayList<>();

        int filled = InvidiousInstance.fillMissing(mContext, reporting("https://invidious.example.org", asked));

        assertEquals(0, filled);
        assertEquals(0, asked.size());
        assertNull(ServerAccount.forGoogle(mContext, ALICE).invidiousInstance);
    }

    @Test
    public void aClearedPreferenceIsNotRefilledFromTheServer() {
        storeTodaysShape();
        ServerAccount Alice = ServerAccount.forGoogle(mContext, ALICE);
        InvidiousInstance.set(mContext, Alice, "mine.example.org");
        InvidiousInstance.set(mContext, Alice, null);
        List<String> asked = new ArrayList<>();

        InvidiousInstance.fillMissing(mContext, reporting("https://invidious.example.org", asked));

        assertEquals(0, asked.size());
        assertNull(ServerAccount.forGoogle(mContext, ALICE).invidiousInstance);
    }

    @Test
    public void connectingToAnotherServerGetsItsOwnOneTimeRead() {
        storeTodaysShape();
        InvidiousInstance.fillMissing(mContext, account -> null);
        // Same server again: still used up. Another server: a new connection, a new try.
        account("https://yt.example.com", "tokenNew", ALICE).save(mContext);
        List<String> asked = new ArrayList<>();
        InvidiousInstance.fillMissing(mContext, reporting("https://invidious.example.org", asked));
        assertEquals(0, asked.size());

        account("https://yt.other.example", "tokenOther", ALICE).save(mContext);
        InvidiousInstance.fillMissing(mContext, reporting("https://invidious.other.example", asked));

        assertEquals(1, asked.size());
        assertEquals("https://invidious.other.example", ServerAccount.forGoogle(mContext, ALICE).invidiousInstance);
    }

    @Test
    public void aReadForAConnectionThatChangedMeanwhileIsDropped() {
        storeTodaysShape();

        // While the old server answers, the person connects to another one.
        int filled = InvidiousInstance.fillMissing(mContext, account -> {
            account("https://yt.other.example", "tokenOther", ALICE).save(mContext);
            return "https://invidious.old.example";
        });

        assertEquals(0, filled);
        assertNull(ServerAccount.forGoogle(mContext, ALICE).invidiousInstance);
    }

    // Re-review M-B: the read can take up to 15 s; what the person types meanwhile is the truth.
    @Test
    public void anInstanceTypedWhileTheServerReadIsInFlightIsNotOverwritten() {
        storeTodaysShape();

        int filled = InvidiousInstance.fillMissing(mContext, account -> {
            InvidiousInstance.set(mContext, ServerAccount.forGoogle(mContext, ALICE), "mine.example.org");
            return "https://invidious.example.org";
        });

        assertEquals(0, filled);
        assertEquals("https://mine.example.org", ServerAccount.forGoogle(mContext, ALICE).invidiousInstance);
    }

    @Test
    public void aPreferenceTypedAndClearedDuringTheReadIsNotRefilledEither() {
        storeTodaysShape();

        int filled = InvidiousInstance.fillMissing(mContext, account -> {
            ServerAccount typing = ServerAccount.forGoogle(mContext, ALICE);
            InvidiousInstance.set(mContext, typing, "mine.example.org");
            InvidiousInstance.set(mContext, typing, null);
            return "https://invidious.example.org";
        });

        assertEquals(0, filled);
        assertNull(ServerAccount.forGoogle(mContext, ALICE).invidiousInstance);
    }

    @Test
    public void typingTheInstanceOrFillingItAsksTheQueueToFlush() {
        storeTodaysShape();
        int before = ServerSync.flushRequests;
        ServerAccount Alice = ServerAccount.forGoogle(mContext, ALICE);

        InvidiousInstance.set(mContext, Alice, "mine.example.org");
        assertEquals(before + 1, ServerSync.flushRequests);

        InvidiousInstance.set(mContext, Alice, "not a host"); // refused: nothing changed
        assertEquals(before + 1, ServerSync.flushRequests);

        InvidiousInstance.fillMissing(mContext, account -> "https://x.example.org"); // already has one
        assertEquals(before + 1, ServerSync.flushRequests);
    }

    @Test
    public void fillingAnInstanceAsksTheQueueToFlush() {
        storeTodaysShape();
        int before = ServerSync.flushRequests;

        InvidiousInstance.fillMissing(mContext, account -> "https://invidious.example.org");

        assertEquals(before + 1, ServerSync.flushRequests);
    }

    @Test
    public void anInstanceTheWebStoredAsJsonTextIsUnquoted() {
        storeTodaysShape();

        InvidiousInstance.fillMissing(mContext, account -> "\"invidious.example.org\"");

        assertEquals("https://invidious.example.org", ServerAccount.forGoogle(mContext, ALICE).invidiousInstance);
    }

    @Test
    public void thePreferenceWinsOverWhatTheServerReports() {
        storeTodaysShape();
        ServerAccount Alice = ServerAccount.forGoogle(mContext, ALICE);

        assertEquals("https://mine.example.org", InvidiousInstance.set(mContext, Alice, "mine.example.org/"));
        InvidiousInstance.fillMissing(mContext, account -> "https://invidious.example.org");

        assertEquals("https://mine.example.org", ServerAccount.forGoogle(mContext, ALICE).invidiousInstance);
    }

    @Test
    public void thePreferenceCanBeClearedAndRejectsNonsense() {
        storeTodaysShape();
        ServerAccount Alice = ServerAccount.forGoogle(mContext, ALICE);
        InvidiousInstance.set(mContext, Alice, "mine.example.org");

        assertNull(InvidiousInstance.set(mContext, Alice, "not a host"));
        assertEquals("https://mine.example.org", ServerAccount.forGoogle(mContext, ALICE).invidiousInstance); // unchanged

        InvidiousInstance.set(mContext, Alice, null);
        assertNull(ServerAccount.forGoogle(mContext, ALICE).invidiousInstance);
    }

    @Test
    public void connectingAgainKeepsTheInstanceOfTheSameServer() {
        storeTodaysShape();
        InvidiousInstance.fillMissing(mContext, reporting("https://invidious.example.org", new ArrayList<>()));

        // Quick Connect again against the same server: the new account has no instance of its own.
        account("https://yt.example.com", "tokenNew", ALICE).save(mContext);

        assertEquals("tokenNew", ServerAccount.forGoogle(mContext, ALICE).token);
        assertEquals("https://invidious.example.org", ServerAccount.forGoogle(mContext, ALICE).invidiousInstance);

        // A different server is a different Invidious: nothing is carried over.
        account("https://yt.other.example", "tokenOther", ALICE).save(mContext);
        assertNull(ServerAccount.forGoogle(mContext, ALICE).invidiousInstance);
    }

    @Test
    public void theFeedHasNoInstanceWithNoServerOrWithAServerThatReportsNone() {
        assertNull(InvidiousInstance.forFeed(mContext)); // fresh install: nothing stored

        account("https://yt.example.com", "tokenA", ALICE).save(mContext);
        assertNull(InvidiousInstance.forFeed(mContext));
    }

    @Test
    public void theFeedUsesTheInstanceOfAConnectedProfileEvenWithNoAccountSelected() {
        account("https://yt.example.com", "tokenA", ALICE).save(mContext);
        account("https://yt.example.com", "tokenB", BOB).save(mContext);
        InvidiousInstance.set(mContext, ServerAccount.forGoogle(mContext, BOB), "invidious.example.org");

        // "Ninguna": no Google account selected, so no profile is the current one.
        assertEquals("https://invidious.example.org", InvidiousInstance.forFeed(mContext));
    }

    @Test
    public void invidiousSyncStaysOffWithoutAnInstanceAndMakesNoRequest() {
        ServerAccount noInstance = account("https://yt.invalid", "tokenA", ALICE);

        try {
            assertNull(InvidiousApi.fromServer(noInstance)); // would throw if it tried the network
        } catch (IOException e) {
            fail("no request expected: " + e);
        }
    }
}
