package com.liskovsoft.smartyoutubetv2.common.leanview;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.liskovsoft.smartyoutubetv2.common.leanview.EndScreenSettings.DefaultFocus;
import com.liskovsoft.smartyoutubetv2.common.leanview.EndScreenSettings.NextSource;

import org.junit.Test;

// leanview: the "Up next" end screen settings: the leanview defaults, the allowed choices, and a
// saved form that survives missing or garbled data.
public class EndScreenSettingsTest {
    @Test
    public void defaultsAreTheOwnersChoices() {
        EndScreenSettings s = EndScreenSettings.defaults();

        assertTrue(s.enabled);
        assertEquals(8, s.relatedCountdownSec);
        assertEquals(3, s.playlistCountdownSec);
        assertEquals(NextSource.PLAYLIST_THEN_RELATED, s.nextSource);
        assertTrue(s.skipForCast);
        assertEquals(DefaultFocus.PLAY_NOW, s.defaultFocus);
        assertEquals(3, s.stillWatchingHours);
    }

    @Test
    public void choicesAreTheListedOnes() {
        assertArrayEquals(new int[] {-1, 0, 5, 8, 10, 15, 20}, EndScreenSettings.RELATED_OPTIONS);
        assertArrayEquals(new int[] {0, 3, 5, 8, 10, 15}, EndScreenSettings.PLAYLIST_OPTIONS);
        assertArrayEquals(new int[] {1, 2, 3, 4, 0}, EndScreenSettings.STILL_WATCHING_OPTIONS);
    }

    @Test
    public void savedFormRoundTrips() {
        EndScreenSettings s = EndScreenSettings.defaults()
                .withEnabled(false)
                .withRelatedCountdownSec(EndScreenSettings.NEVER_AUTO)
                .withPlaylistCountdownSec(10)
                .withNextSource(NextSource.PLAYLIST_ONLY)
                .withSkipForCast(false)
                .withStillWatchingHours(EndScreenSettings.STILL_NEVER);

        EndScreenSettings back = EndScreenSettings.parse(s.serialize());

        assertFalse(back.enabled);
        assertEquals(-1, back.relatedCountdownSec);
        assertEquals(10, back.playlistCountdownSec);
        assertEquals(NextSource.PLAYLIST_ONLY, back.nextSource);
        assertFalse(back.skipForCast);
        assertEquals(0, back.stillWatchingHours);
    }

    @Test
    public void absentOrEmptyDataGivesTheDefaults() {
        assertEquals(EndScreenSettings.defaults().serialize(), EndScreenSettings.parse(null).serialize());
        assertEquals(EndScreenSettings.defaults().serialize(), EndScreenSettings.parse("").serialize());
    }

    @Test
    public void missingTrailingFieldsGetTheirDefaults() {
        // an older build that knew only the first two fields
        EndScreenSettings s = EndScreenSettings.parse("0;15");

        assertFalse(s.enabled);
        assertEquals(15, s.relatedCountdownSec);
        assertEquals(3, s.playlistCountdownSec);
        assertTrue(s.skipForCast);
        assertEquals(3, s.stillWatchingHours);
    }

    @Test
    public void garbledFieldsFallBackToTheirDefaultsAndKeepTheRest() {
        EndScreenSettings s = EndScreenSettings.parse("1;abc;7;9;x;1;99");

        assertTrue(s.enabled);
        assertEquals(8, s.relatedCountdownSec); // not a number
        assertEquals(3, s.playlistCountdownSec); // 7 is not a choice
        assertEquals(NextSource.PLAYLIST_THEN_RELATED, s.nextSource); // unknown code
        assertTrue(s.skipForCast); // not a flag
        assertEquals(DefaultFocus.CANCEL, s.defaultFocus);
        assertEquals(3, s.stillWatchingHours); // 99 is not a choice
    }

    @Test
    public void settingAValueOutsideTheChoicesKeepsTheDefault() {
        assertEquals(8, EndScreenSettings.defaults().withRelatedCountdownSec(7).relatedCountdownSec);
        assertEquals(3, EndScreenSettings.defaults().withPlaylistCountdownSec(-1).playlistCountdownSec);
        assertEquals(3, EndScreenSettings.defaults().withStillWatchingHours(9).stillWatchingHours);
    }

    @Test
    public void settingsAreNotMutatedByTheirCopies() {
        EndScreenSettings base = EndScreenSettings.defaults();
        base.withEnabled(false);

        assertTrue(base.enabled);
    }
}
