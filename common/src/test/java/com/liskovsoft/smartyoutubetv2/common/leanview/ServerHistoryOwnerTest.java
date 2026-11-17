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
import static org.junit.Assert.assertNull;

// A video is saved in the server history of the profile that opened it, whoever is selected when
// the player saves it (it saves again as it closes, after a switch to another profile).
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ServerHistoryOwnerTest {
    private static final String CAROL = "page:carol";
    private static final String BOB = "bob@example.com";
    private Context mContext;
    private ServerAccount.Protector mKeystore;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.getApplication();
        mContext.getSharedPreferences("leanview_server_account", Context.MODE_PRIVATE).edit().clear().commit();
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
        new ServerAccount("https://yt.example.com", "tokenC", new byte[32], "leanview", CAROL).save(mContext);
        new ServerAccount("https://yt.example.com", "tokenB", new byte[32], "bob", BOB).save(mContext);
    }

    @After
    public void tearDown() {
        ServerAccount.sProtector = mKeystore;
    }

    @Test
    public void eachVideoGoesToItsOwnersAccount() {
        assertEquals("leanview", ServerHistory.ownedBy(mContext, CAROL).username);
        assertEquals("bob", ServerHistory.ownedBy(mContext, BOB).username);
    }

    @Test
    public void aProfileWithNoConnectionSavesNowhere() {
        assertNull(ServerHistory.ownedBy(mContext, "none"));
        assertNull(ServerHistory.ownedBy(mContext, "page:another"));
    }
}
