package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.content.Context;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.leanview.PasswordAttempts;
import com.liskovsoft.smartyoutubetv2.common.leanview.PasswordHash;
import com.liskovsoft.smartyoutubetv2.common.leanview.ServerProfileLockTestAccess;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

// leanview: a password an older build saved in plain text is stored as a hash the first time the
// app reads it, and it still opens the account.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class AccountsDataPasswordUpgradeTest {
    private Context mContext;

    @Before
    public void setUp() throws Exception {
        mContext = RuntimeEnvironment.getApplication();
        forget(AccountsData.class);
        forget(AppPrefs.class);
    }

    private static void forget(Class<?> singleton) throws Exception {
        Field instance = singleton.getDeclaredField("sInstance");
        instance.setAccessible(true);
        instance.set(null, null);
    }

    @Test
    public void aPlainPasswordIsStoredHashedOnFirstRead() throws Exception {
        // What upstream SmartTube wrote: select-on-boot, two unused fields, the passwords array.
        String plain = Helpers.mergeData(false, null, null,
                Helpers.mergeArray(new Object[] {Helpers.mergeObj("Valeria", "4821")}));
        AppPrefs.instance(mContext).setData("accounts_data", plain);

        AccountsData.instance(mContext);

        String stored = AppPrefs.instance(mContext).getData("accounts_data");
        assertFalse(stored.contains("4821"));
        String password = Helpers.splitObj(Helpers.parseArray(Helpers.splitData(stored), 3)[0])[1];
        assertTrue(PasswordHash.isHash(password));
        assertTrue(PasswordHash.matches(password, "4821"));

        // Read again after a restart: still the same hash, nothing re-written.
        forget(AccountsData.class);
        AccountsData.instance(mContext);
        assertTrue(AppPrefs.instance(mContext).getData("accounts_data").equals(stored));
    }

    @Test
    public void wrongGuessesStillCountAfterTheAppRestarts() throws Exception {
        AccountsData data = AccountsData.instance(mContext);
        data.setAccountPassword("Valeria", "4821");
        long now = 1_000_000L;
        for (int i = 0; i < 5; i++) {
            assertEquals(PasswordAttempts.WRONG, data.checkAccountPassword("Valeria", "0000", now));
        }

        forget(AccountsData.class);
        AccountsData restarted = AccountsData.instance(mContext);

        assertEquals(PasswordAttempts.WAIT, restarted.checkAccountPassword("Valeria", "4821", now + 1_000));
        assertEquals(PasswordAttempts.ACCEPTED, restarted.checkAccountPassword("Valeria", "4821", now + 30_000));
    }

    @Test
    public void theRightPinOfAnotherProfileDoesNotResetTheCount() throws Exception {
        AccountsData data = AccountsData.instance(mContext);
        data.setAccountPassword("Valeria", "4821");
        data.setAccountPassword("Alice", "1111");
        long now = 1_000_000L;
        for (int i = 0; i < 4; i++) {
            data.checkAccountPassword("Valeria", "0000", now);
        }
        assertEquals(PasswordAttempts.ACCEPTED, data.checkAccountPassword("Alice", "1111", now));
        assertEquals(PasswordAttempts.WRONG, data.checkAccountPassword("Valeria", "0000", now));

        assertEquals(PasswordAttempts.WAIT, data.checkAccountPassword("Valeria", "4821", now + 1_000));
    }

    @Test
    public void anAccountWithoutPasswordIsNotAWrongGuess() throws Exception {
        AccountsData data = AccountsData.instance(mContext);
        for (int i = 0; i < 6; i++) {
            assertEquals(PasswordAttempts.ACCEPTED, data.checkAccountPassword("Valeria", "0000", 1_000_000L));
        }
    }

    private static final String FROM_THE_WEB =
            "pbkdf2-sha1$50000$AAECAwQFBgcICQoLDA0ODw$tmCSTqFycGZdzbLesZ4rN8vW2J264dZaE9BRgWLuWAo";

    @Test
    public void theLockSetOnTheWebDecidesThePinOnTheTv() throws Exception {
        AccountsData data = AccountsData.instance(mContext);

        data.applyServerLock("Valeria", ServerProfileLockTestAccess.of(true, FROM_THE_WEB));
        assertEquals(PasswordAttempts.ACCEPTED, data.checkAccountPassword("Valeria", "4821", 1_000_000L));
        assertEquals(PasswordAttempts.WRONG, data.checkAccountPassword("Valeria", "1111", 1_000_000L));

        // Turned off on the web: the profile opens without a PIN.
        data.applyServerLock("Valeria", ServerProfileLockTestAccess.of(false, FROM_THE_WEB));
        forget(AccountsData.class);
        assertEquals(PasswordAttempts.ACCEPTED,
                AccountsData.instance(mContext).checkAccountPassword("Valeria", "anything", 1_000_000L));
    }

    @Test
    public void withoutASettingOnTheServerTheTvPinStays() throws Exception {
        AccountsData data = AccountsData.instance(mContext);
        data.setAccountPassword("Valeria", "4821");

        data.applyServerLock("Valeria", null);

        assertEquals(PasswordAttempts.WRONG, data.checkAccountPassword("Valeria", "1111", 1_000_000L));
    }

    @Test
    public void rememberesTheLastWayEachProfileWasOpened() throws Exception {
        AccountsData data = AccountsData.instance(mContext);
        assertEquals(AccountsData.UNLOCK_BY_PHONE, data.getLastUnlockMethod("Valeria"));

        data.setLastUnlockMethod("Valeria", AccountsData.UNLOCK_BY_PIN);
        forget(AccountsData.class);
        AccountsData restarted = AccountsData.instance(mContext);

        assertEquals(AccountsData.UNLOCK_BY_PIN, restarted.getLastUnlockMethod("Valeria"));
        assertEquals(AccountsData.UNLOCK_BY_PHONE, restarted.getLastUnlockMethod("Alice"));
    }
}

