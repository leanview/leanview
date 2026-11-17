package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.app.Activity;
import android.content.Context;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import com.liskovsoft.smartyoutubetv2.common.leanview.ServerAddress;
import com.liskovsoft.smartyoutubetv2.common.leanview.ServerHttpTestAccess;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

// leanview: the APK carries no server address, so the connection screen has none until the person
// types one. With none it asks for one and sends nothing; with one it opens Quick Connect against
// exactly that address. The address check (an Rx probe off the main thread) must not act on a
// screen the person already left: no sign-in screen started, no dialog on a dead Activity.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ServerSignInPresenterTest {
    private static final String OK = "{\"internalAuth\":true,\"version\":\"1.18.11\",\"backend\":\"some-server\"}";

    private Context mContext;
    private ServerSignInPresenter.Asker mAsker;
    private ServerSignInPresenter.Opener mOpener;
    private ServerSignInPresenter.Starter mStarter;
    private ServerAddress.Fetcher mFetcher;
    private final List<String> mSuggestions = Collections.synchronizedList(new ArrayList<>());
    private final List<String> mOpened = Collections.synchronizedList(new ArrayList<>());
    private final List<String> mStarted = Collections.synchronizedList(new ArrayList<>());
    private CountDownLatch mOpenedLatch;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.getApplication();
        mAsker = ServerSignInPresenter.sAsker;
        mOpener = ServerSignInPresenter.sOpener;
        mStarter = ServerSignInPresenter.sStarter;
        mFetcher = ServerSignInPresenter.sFetcher;
        mOpenedLatch = new CountDownLatch(1);
        ServerSignInPresenter.sAsker = (presenter, suggestion) -> mSuggestions.add(suggestion == null ? "<none>" : suggestion);
        ServerSignInPresenter.sOpener = backend -> {
            mOpened.add(backend);
            mOpenedLatch.countDown();
            throw new IOException("no network in tests");
        };
        ServerSignInPresenter.sStarter = presenter -> mStarted.add(String.valueOf(presenter.address()));
        ServerSignInPresenter.sFetcher = url -> ServerHttpTestAccess.response(200, OK);
        ServerSignInPresenter.instance(mContext).unhold();
    }

    @After
    public void tearDown() {
        ServerSignInPresenter.instance(mContext).unhold();
        ServerSignInPresenter.sAsker = mAsker;
        ServerSignInPresenter.sOpener = mOpener;
        ServerSignInPresenter.sStarter = mStarter;
        ServerSignInPresenter.sFetcher = mFetcher;
    }

    @Test
    public void withNoAddressItAsksForOneAndSendsNothing() throws Exception {
        ServerSignInPresenter presenter = ServerSignInPresenter.instance(mContext);

        presenter.onViewInitialized();

        assertEquals(1, mSuggestions.size());
        assertEquals("<none>", mSuggestions.get(0)); // fresh install: nothing to suggest
        Thread.sleep(300); // an accidental request would run on another thread
        assertEquals(0, mOpened.size());
    }

    @Test
    public void connectAsksForTheAddressFirst() {
        ServerSignInPresenter.instance(mContext).connect();

        assertEquals(1, mSuggestions.size());
        assertEquals(0, mOpened.size());
    }

    @Test
    public void withAnAddressItOpensQuickConnectAgainstThatAddress() throws Exception {
        ServerSignInPresenter presenter = ServerSignInPresenter.instance(mContext);
        presenter.useAddress("https://yt.example.com");

        presenter.onViewInitialized();

        assertTrue(mOpenedLatch.await(5, TimeUnit.SECONDS));
        assertEquals("https://yt.example.com", mOpened.get(0));
        assertEquals(0, mSuggestions.size());
    }

    @Test
    public void theAddressIsForgottenWhenTheScreenIsReleased() {
        ServerSignInPresenter presenter = ServerSignInPresenter.instance(mContext);
        presenter.useAddress("https://yt.example.com");
        presenter.unhold();

        assertNull(ServerSignInPresenter.instance(mContext).address());
    }

    // ---- the address check (Rx glue) ----

    private static void idleUntil(BooleanSupplier done) throws InterruptedException {
        for (int i = 0; i < 100 && !done.getAsBoolean(); i++) {
            ShadowLooper.idleMainLooper();
            Thread.sleep(30);
        }
        ShadowLooper.idleMainLooper();
    }

    private static void idleFor(long ms) throws InterruptedException {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            ShadowLooper.idleMainLooper();
            Thread.sleep(30);
        }
    }

    @Test
    public void aGoodAddressGoesOnToTheSignInScreen() throws Exception {
        ActivityController<Activity> screen = Robolectric.buildActivity(Activity.class).setup();
        ServerSignInPresenter presenter = ServerSignInPresenter.instance(screen.get());

        presenter.check("https://yt.example.com", "yt.example.com");
        idleUntil(() -> !mStarted.isEmpty());

        assertEquals(1, mStarted.size());
        assertEquals("https://yt.example.com", mStarted.get(0));
        assertEquals(0, mSuggestions.size());
    }

    @Test
    public void aBadAddressAsksAgainWithWhatWasTyped() throws Exception {
        ServerSignInPresenter.sFetcher = url -> {
            throw new IOException("refused");
        };
        ActivityController<Activity> screen = Robolectric.buildActivity(Activity.class).setup();
        ServerSignInPresenter presenter = ServerSignInPresenter.instance(screen.get());

        presenter.check("https://yt.example.com", "Yt.Example.com");
        idleUntil(() -> !mSuggestions.isEmpty());

        assertEquals(Collections.singletonList("Yt.Example.com"), mSuggestions);
        assertEquals(0, mStarted.size());
        assertNull(presenter.address());
    }

    @Test
    public void aResultAfterTheScreenWasLeftDoesNothing() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        ServerSignInPresenter.sFetcher = url -> {
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                throw new IOException(e);
            }
            return ServerHttpTestAccess.response(200, OK);
        };
        ActivityController<Activity> screen = Robolectric.buildActivity(Activity.class).setup();
        ServerSignInPresenter presenter = ServerSignInPresenter.instance(screen.get());

        presenter.check("https://yt.example.com", "yt.example.com");
        screen.pause().stop().destroy(); // the person left Settings while the probe was running
        release.countDown();
        idleFor(600);

        assertEquals(0, mStarted.size());
        assertEquals(0, mSuggestions.size());
    }

    @Test
    public void aFailureAfterTheScreenWasLeftDoesNotReopenTheDialog() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        ServerSignInPresenter.sFetcher = url -> {
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                // fall through to the failure
            }
            throw new IOException("refused");
        };
        ActivityController<Activity> screen = Robolectric.buildActivity(Activity.class).setup();
        ServerSignInPresenter presenter = ServerSignInPresenter.instance(screen.get());

        presenter.check("https://yt.example.com", "yt.example.com");
        screen.pause().stop().destroy();
        release.countDown();
        idleFor(600);

        assertEquals(0, mSuggestions.size());
        assertEquals(0, mStarted.size());
    }

    @Test
    public void aResultAfterThePresenterWasReleasedDoesNothing() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        ServerSignInPresenter.sFetcher = url -> {
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                throw new IOException(e);
            }
            return ServerHttpTestAccess.response(200, OK);
        };
        ActivityController<Activity> screen = Robolectric.buildActivity(Activity.class).setup();
        ServerSignInPresenter presenter = ServerSignInPresenter.instance(screen.get());

        presenter.check("https://yt.example.com", "yt.example.com");
        presenter.unhold();
        release.countDown();
        idleFor(600);

        assertEquals(0, mStarted.size());
        assertNull(presenter.address());
    }

    @Test
    public void onlyTheLatestCheckCounts() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        ServerSignInPresenter.sFetcher = url -> {
            if (url.startsWith("https://slow.example.com")) {
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    // the first probe was dropped
                }
            }
            return ServerHttpTestAccess.response(200, OK);
        };
        ActivityController<Activity> screen = Robolectric.buildActivity(Activity.class).setup();
        ServerSignInPresenter presenter = ServerSignInPresenter.instance(screen.get());

        presenter.check("https://slow.example.com", "slow.example.com");
        presenter.check("https://fast.example.com", "fast.example.com");
        idleUntil(() -> !mStarted.isEmpty());
        release.countDown();
        idleFor(400);

        assertEquals(Collections.singletonList("https://fast.example.com"), mStarted);
        assertEquals("https://fast.example.com", presenter.address());
    }
}
