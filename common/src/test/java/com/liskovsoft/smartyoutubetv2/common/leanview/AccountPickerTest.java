package com.liskovsoft.smartyoutubetv2.common.leanview;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

// leanview: the account picker opens by itself when the app comes back after being away long enough,
// never while moving between the app's own screens, and only once per return.
public class AccountPickerTest {
    private static final long HOUR = 60 * 60_000;

    private static AccountPicker startedOnce() {
        AccountPicker picker = new AccountPicker();
        picker.onScreenStarted(0, 2 * HOUR);
        return picker;
    }

    @Test
    public void theFirstStartIsAnOpeningNotAReturn() {
        assertFalse(startedOnce().takeBack());
    }

    @Test
    public void comingBackAfterTheAwayTimeOpensItOnce() {
        AccountPicker picker = startedOnce();
        picker.onScreenStopped(HOUR);

        picker.onScreenStarted(3 * HOUR, 2 * HOUR);

        assertTrue(picker.takeBack());
        assertFalse(picker.takeBack());
    }

    @Test
    public void comingBackSoonerDoesNot() {
        AccountPicker picker = startedOnce();
        picker.onScreenStopped(HOUR);

        picker.onScreenStarted(2 * HOUR, 2 * HOUR);

        assertFalse(picker.takeBack());
    }

    @Test
    public void theAppsOwnScreensAreNotAway() {
        AccountPicker picker = startedOnce();
        // Browse -> player: the player starts before browse stops; hours later browse comes back.
        picker.onScreenStarted(HOUR, 2 * HOUR);
        picker.onScreenStopped(HOUR);
        picker.onScreenStarted(5 * HOUR, 2 * HOUR);
        picker.onScreenStopped(5 * HOUR);

        assertFalse(picker.takeBack());
    }

    @Test
    public void shownByTheStartScreenOnTheWayBackItDoesNotOpenAgain() {
        AccountPicker picker = startedOnce();
        picker.onScreenStopped(HOUR);

        // Opened from the TV's home: the start screen shows it before Android reports the start.
        picker.onShown(4 * HOUR);
        picker.onScreenStarted(4 * HOUR, 2 * HOUR);

        assertFalse(picker.takeBack());
    }

    @Test
    public void shownBeforeGoingAwayStillOpensOnTheNextReturn() {
        AccountPicker picker = startedOnce();
        picker.onShown(HOUR / 2);
        picker.onScreenStopped(HOUR);

        picker.onScreenStarted(4 * HOUR, 2 * HOUR);

        assertTrue(picker.takeBack());
    }

    @Test
    public void neverTurnsTheReturnOff() {
        AccountPicker picker = startedOnce();
        picker.onScreenStopped(HOUR);

        picker.onScreenStarted(10 * HOUR, AccountPicker.NEVER);

        assertFalse(picker.takeBack());
    }

    @Test
    public void choosingSomeoneElsesProfileIsAnotherProfile() {
        assertTrue(AccountPicker.isAnotherProfile("Alice@example.com", "bob@example.com"));
        assertTrue(AccountPicker.isAnotherProfile("Alice@example.com", null));
        assertTrue(AccountPicker.isAnotherProfile(null, "bob@example.com"));
        assertFalse(AccountPicker.isAnotherProfile("Alice@example.com", "Alice@example.com"));
        assertFalse(AccountPicker.isAnotherProfile(null, null));
    }

    // 2026-11-01 on the TV: "Carol" is a brand channel with no email, so telling profiles apart by
    // email took it for "no account" and the corner-window video kept playing after the switch.
    @Test
    public void aChannelWithNoEmailIsStillItsOwnProfile() {
        String carol = AccountPicker.profileKey(null, "Carol");
        String Alice = AccountPicker.profileKey("Alice@example.com", "Alice Smith");

        assertTrue(AccountPicker.isAnotherProfile(null, carol));
        assertTrue(AccountPicker.isAnotherProfile(carol, null));
        assertTrue(AccountPicker.isAnotherProfile(Alice, carol));
        assertTrue(AccountPicker.isAnotherProfile(AccountPicker.profileKey("Alice@example.com", "Carol"), Alice));
        assertFalse(AccountPicker.isAnotherProfile(carol, AccountPicker.profileKey(null, "Carol")));
    }
}
