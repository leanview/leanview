package com.liskovsoft.smartyoutubetv2.common.leanview;

import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

// leanview: a profile's PIN is stored hashed, opens only with itself (no master word), and wrong
// guesses make the next try wait longer and longer.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ProfilePasswordTest {
    // The same reference the web uses (see the web client), computed with
    // Python: hashlib.pbkdf2_hmac('sha1', b'4821', bytes(range(16)), 50000, 32), base64url.
    // A PIN set on the web opens the profile on the TV.
    @Test
    public void aPinSetOnTheWebOpensOnTheTv() {
        String fromTheWeb = "pbkdf2-sha1$50000$AAECAwQFBgcICQoLDA0ODw$tmCSTqFycGZdzbLesZ4rN8vW2J264dZaE9BRgWLuWAo";

        assertTrue(PasswordHash.matches(fromTheWeb, "4821"));
        assertFalse(PasswordHash.matches(fromTheWeb, "4822"));
    }

    @Test
    public void thePinIsStoredHashedAndOpensOnlyWithItself() {
        String stored = PasswordHash.hash("4821");

        assertFalse(stored.contains("4821"));
        assertTrue(PasswordHash.isHash(stored));
        assertTrue(PasswordHash.matches(stored, "4821"));
        assertFalse(PasswordHash.matches(stored, "4822"));
        assertFalse(PasswordHash.matches(stored, null));
        assertNotEquals(stored, PasswordHash.hash("4821"));
    }

    @Test
    public void aPinSavedInPlainTextStillOpensAndIsUpgraded() {
        assertTrue(PasswordHash.matches("4821", "4821"));
        assertFalse(PasswordHash.matches("4821", "1111"));

        String upgraded = PasswordHash.upgrade("4821");
        assertTrue(PasswordHash.isHash(upgraded));
        assertTrue(PasswordHash.matches(upgraded, "4821"));
        assertEquals(upgraded, PasswordHash.upgrade(upgraded));
        assertEquals(null, PasswordHash.upgrade(null));
    }

    @Test
    public void theWordSmarttubeOpensNothing() {
        assertFalse(Utils.passwordMatch(PasswordHash.hash("4821"), "smarttube"));
        assertFalse(Utils.passwordMatch(PasswordHash.hash("4821"), "SMARTTUBE"));
        assertFalse(Utils.passwordMatch("my settings password", "smarttube"));
        assertTrue(Utils.passwordMatch(PasswordHash.hash("4821"), "4821"));
        assertTrue(Utils.passwordMatch(null, "anything"));
    }

    @Test
    public void wrongGuessesMakeTheNextTryWait() {
        String stored = PasswordHash.hash("4821");
        PasswordAttempts attempts = new PasswordAttempts();
        long now = 1_000_000L;

        for (int i = 0; i < 4; i++) {
            assertEquals(PasswordAttempts.WRONG, attempts.check(stored, "0000", now));
        }
        assertEquals(PasswordAttempts.WRONG, attempts.check(stored, "0000", now));
        // Five wrong: even the right PIN waits 30 s.
        assertEquals(PasswordAttempts.WAIT, attempts.check(stored, "4821", now + 1_000));
        assertEquals(29, attempts.waitSeconds(now + 1_000));

        assertEquals(PasswordAttempts.WRONG, attempts.check(stored, "0000", now + 30_000));
        // The sixth wrong guess doubles the wait.
        assertEquals(60, attempts.waitSeconds(now + 30_000));

        assertEquals(PasswordAttempts.ACCEPTED, attempts.check(stored, "4821", now + 90_000));
        assertEquals(0, attempts.waitSeconds(now + 90_000));
        assertEquals(PasswordAttempts.WRONG, attempts.check(stored, "0000", now + 90_000));
    }

    @Test
    public void theWaitSurvivesARestartOfTheApp() {
        String stored = PasswordHash.hash("4821");
        PasswordAttempts attempts = new PasswordAttempts();
        for (int i = 0; i < 5; i++) {
            attempts.check(stored, "0000", 1_000_000L);
        }

        PasswordAttempts restored = PasswordAttempts.fromString(attempts.toString());

        assertEquals(PasswordAttempts.WAIT, restored.check(stored, "4821", 1_010_000L));
        assertEquals(PasswordAttempts.ACCEPTED, PasswordAttempts.fromString(null).check(stored, "4821", 0));
    }

    @Test
    public void aDamagedStoredHashIsAWrongPinNotACrash() {
        assertFalse(PasswordHash.matches("pbkdf2-sha1$abc$AAAA$AAAA", "4821"));
        assertFalse(PasswordHash.matches("pbkdf2-sha1$50000$$AAAA", "4821"));
        assertFalse(PasswordHash.matches("pbkdf2-sha1$0$AAAA$AAAA", "4821"));
        assertFalse(PasswordHash.matches("pbkdf2-sha1$2000000000$AAAA$AAAA", "4821"));
        assertFalse(PasswordHash.matches("pbkdf2-sha1$50000$!!!$AAAA", "4821"));
        assertFalse(PasswordHash.matches("pbkdf2-sha1$50000", "4821"));
    }

    @Test
    public void aWrongClockNeverLocksOutForMoreThanTheLongestWait() {
        String stored = PasswordHash.hash("4821");
        // A wait saved while the clock was far ahead (or a damaged value).
        PasswordAttempts attempts = PasswordAttempts.fromString("7:" + Long.MAX_VALUE);
        long now = 1_000_000L;

        assertEquals(15 * 60, attempts.waitSeconds(now));
        assertEquals(PasswordAttempts.ACCEPTED, attempts.check(stored, "4821", now + 15 * 60_000));
    }
}

