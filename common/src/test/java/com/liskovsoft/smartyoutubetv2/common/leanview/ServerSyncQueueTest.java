package com.liskovsoft.smartyoutubetv2.common.leanview;

import android.content.Context;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

// leanview: the subscription mirror stays OFF while no Invidious instance is known: nothing is queued
// (the queue used to grow forever, one entry per subscribe, replayed never). Decision: do not queue.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ServerSyncQueueTest {
    private static final String ALICE = "Alice@example.com";

    private static ServerAccount account(String google, String instance) {
        return new ServerAccount("https://yt.example.com", "token", new byte[32], "user", google, instance);
    }

    @Test
    public void anActionIsQueuedForTheConnectedPersonWithAnInstance() {
        assertTrue(ServerSync.shouldQueue(account(ALICE, "https://invidious.example.org"), ALICE));
    }

    @Test
    public void nothingIsQueuedWithoutAnInstance() {
        assertFalse(ServerSync.shouldQueue(account(ALICE, null), ALICE));
    }

    @Test
    public void nothingIsQueuedWithoutAConnectionOrForAnotherPerson() {
        assertFalse(ServerSync.shouldQueue(null, ALICE));
        assertFalse(ServerSync.shouldQueue(account(null, "https://invidious.example.org"), ALICE));
        assertFalse(ServerSync.shouldQueue(account("bob@example.com", "https://invidious.example.org"), ALICE));
        assertFalse(ServerSync.shouldQueue(account(ALICE, "https://invidious.example.org"), null));
    }

    // leanview (re-review M-A): the pure shouldQueue tests above do not notice if the queue write stops
    // consulting it. These go through the real write with a stored connection.
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

    @Test
    public void theQueueWriteStaysEmptyWhileNoInstanceIsKnown() {
        account(ALICE, null).save(mContext);
        ServerSync sync = new ServerSync(mContext);

        assertFalse(sync.enqueueIfWanted("subscribe", "UC1", ALICE));

        assertEquals("[]", sync.queuedForTest());
    }

    @Test
    public void theQueueWriteKeepsTheActionOnceAnInstanceIsKnown() {
        account(ALICE, "https://invidious.example.org").save(mContext);
        ServerSync sync = new ServerSync(mContext);

        assertTrue(sync.enqueueIfWanted("subscribe", "UC1", ALICE));
        assertFalse(sync.enqueueIfWanted("subscribe", "UC2", "bob@example.com")); // another person: not this server's

        assertEquals("[{\"action\":\"subscribe\",\"channelId\":\"UC1\",\"googleAccount\":\"" + ALICE + "\"}]",
                sync.queuedForTest());
    }
}
