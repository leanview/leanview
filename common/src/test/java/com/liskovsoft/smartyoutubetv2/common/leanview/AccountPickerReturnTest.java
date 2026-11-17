package com.liskovsoft.smartyoutubetv2.common.leanview;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

// leanview: any return from the launcher, however short, is a return (the video left unfinished may
// open again, ContinueWatching); the first start and moving between the app's screens are not.
public class AccountPickerReturnTest {
    private static final long MIN = 60_000;

    @Test
    public void theFirstStartIsNotAReturn() {
        AccountPicker picker = new AccountPicker();
        picker.onScreenStarted(0, AccountPicker.DEFAULT_AWAY_MS);
        assertFalse(picker.takeReturned());
    }

    @Test
    public void aShortTripToTheLauncherIsAReturnOnce() {
        AccountPicker picker = new AccountPicker();
        picker.onScreenStarted(0, AccountPicker.DEFAULT_AWAY_MS);
        picker.onScreenStopped(MIN);

        picker.onScreenStarted(2 * MIN, AccountPicker.DEFAULT_AWAY_MS);

        assertTrue(picker.takeReturned());
        assertFalse(picker.takeReturned());
        assertFalse("too short for the picker", picker.takeBack());
    }

    @Test
    public void movingBetweenTheAppsScreensIsNotAReturn() {
        AccountPicker picker = new AccountPicker();
        picker.onScreenStarted(0, AccountPicker.DEFAULT_AWAY_MS);
        // The player starts before the browse screen stops
        picker.onScreenStarted(MIN, AccountPicker.DEFAULT_AWAY_MS);
        picker.onScreenStopped(MIN);

        assertFalse(picker.takeReturned());
    }

    @Test
    public void evenWithThePickerOffItIsAReturn() {
        AccountPicker picker = new AccountPicker();
        picker.onScreenStarted(0, AccountPicker.NEVER);
        picker.onScreenStopped(MIN);

        picker.onScreenStarted(3 * MIN, AccountPicker.NEVER);

        assertTrue(picker.takeReturned());
    }
}
