package com.liskovsoft.smartyoutubetv2.common.leanview;

import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.smartyoutubetv2.common.leanview.AccountPickerTiles.Kind;
import com.liskovsoft.smartyoutubetv2.common.leanview.AccountPickerTiles.Tile;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

// leanview: the "Who's watching?" window lists the accounts, then "none", then "add"; it opens with
// the account in use focused, or "none" when there is none.
public class AccountPickerTilesTest {
    private static Account account(String name, String email, boolean selected) {
        return new Account() {
            @Override public int getId() { return name.hashCode(); }
            @Override public String getName() { return name; }
            @Override public String getEmail() { return email; }
            @Override public String getAvatarImageUrl() { return "https://img/" + name; }
            @Override public boolean isSelected() { return selected; }
            @Override public boolean isEmpty() { return false; }
        };
    }

    private static List<Kind> kinds(List<Tile> tiles) {
        List<Kind> kinds = new ArrayList<>();
        for (Tile tile : tiles) {
            kinds.add(tile.kind);
        }
        return kinds;
    }

    @Test
    public void accountsComeFirstThenNoneThenAdd() {
        List<Tile> tiles = AccountPickerTiles.build(Arrays.asList(account("A", null, false), account("B", null, false)));

        assertEquals(Arrays.asList(Kind.ACCOUNT, Kind.ACCOUNT, Kind.NONE, Kind.ADD), kinds(tiles));
        assertEquals("A", tiles.get(0).name);
        assertEquals("B", tiles.get(1).name);
    }

    @Test
    public void anAccountWithoutEmailIsAChannel() {
        List<Tile> tiles = AccountPickerTiles.build(Arrays.asList(account("Carol", null, false), account("A", "a@x.com", false)));

        assertTrue(tiles.get(0).channel);
        assertFalse(tiles.get(1).channel);
        assertFalse(tiles.get(2).channel);
        assertFalse(tiles.get(3).channel);
    }

    @Test
    public void noAccountsStillOffersNoneAndAdd() {
        assertEquals(Arrays.asList(Kind.NONE, Kind.ADD), kinds(AccountPickerTiles.build(Collections.<Account>emptyList())));
        assertEquals(Arrays.asList(Kind.NONE, Kind.ADD), kinds(AccountPickerTiles.build(null)));
    }

    @Test
    public void anAccountTileCarriesItsAccountAvatarAndEmail() {
        Account a = account("A", "a@x.com", false);
        Tile tile = AccountPickerTiles.build(Collections.singletonList(a)).get(0);

        assertSame(a, tile.account);
        assertEquals("https://img/A", tile.avatarUrl);
        assertEquals("a@x.com", tile.email);
    }

    @Test
    public void aMissingOrBlankEmailIsNull() {
        List<Tile> tiles = AccountPickerTiles.build(Arrays.asList(account("A", null, false), account("B", "  ", false)));

        assertNull(tiles.get(0).email);
        assertNull(tiles.get(1).email);
    }

    @Test
    public void focusStartsOnTheSelectedAccount() {
        List<Tile> tiles = AccountPickerTiles.build(
                Arrays.asList(account("A", null, false), account("B", null, true), account("C", null, false)));

        assertEquals(1, AccountPickerTiles.initialFocus(tiles));
        assertTrue(tiles.get(1).selected);
        assertFalse(tiles.get(3).selected); // "none" is not the one in use
    }

    @Test
    public void focusStartsOnNoneWhenNoAccountIsSelected() {
        List<Tile> tiles = AccountPickerTiles.build(Arrays.asList(account("A", null, false), account("B", null, false)));

        assertEquals(2, AccountPickerTiles.initialFocus(tiles));
        assertEquals(Kind.NONE, tiles.get(2).kind);
        assertTrue(tiles.get(2).selected);
    }

    @Test
    public void focusStartsOnNoneWithNoAccountsAtAll() {
        List<Tile> tiles = AccountPickerTiles.build(null);

        assertEquals(0, AccountPickerTiles.initialFocus(tiles));
    }
}
