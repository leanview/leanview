package com.liskovsoft.smartyoutubetv2.common.leanview;

import com.liskovsoft.mediaserviceinterfaces.oauth.Account;

import java.util.ArrayList;
import java.util.List;

/**
 * leanview: what the "Who's watching?" window shows, in order: each account, then "none", then "add
 * an account". Plain Java so it is testable; the window only draws it.
 */
public final class AccountPickerTiles {
    public enum Kind { ACCOUNT, NONE, ADD }

    public static final class Tile {
        public final Kind kind;
        /** Only for ACCOUNT tiles. */
        public final Account account;
        public final String name;
        /** Null when the account has none (or it is blank). */
        public final String email;
        public final String avatarUrl;
        /**
         * A channel (brand account) rather than a Google account: it has no email of its own, so the
         * window says "Channel" where the email goes (2026-10-31).
         */
        public final boolean channel;
        /** The account in use now; for NONE, that no account is in use. */
        public final boolean selected;

        private Tile(Kind kind, Account account, String name, String email, String avatarUrl, boolean selected) {
            this.kind = kind;
            this.account = account;
            this.name = name;
            this.email = email;
            this.avatarUrl = avatarUrl;
            this.channel = kind == Kind.ACCOUNT && email == null;
            this.selected = selected;
        }
    }

    private AccountPickerTiles() {
    }

    public static List<Tile> build(List<Account> accounts) {
        List<Tile> tiles = new ArrayList<>();
        boolean anySelected = false;

        if (accounts != null) {
            for (Account account : accounts) {
                if (account == null) {
                    continue;
                }

                String email = account.getEmail();
                boolean selected = account.isSelected();
                anySelected |= selected;
                tiles.add(new Tile(Kind.ACCOUNT, account, account.getName(),
                        email == null || email.trim().isEmpty() ? null : email, account.getAvatarImageUrl(), selected));
            }
        }

        tiles.add(new Tile(Kind.NONE, null, null, null, null, !anySelected));
        tiles.add(new Tile(Kind.ADD, null, null, null, null, false));

        return tiles;
    }

    /** The selected account, or "none" when no account is selected. */
    public static int initialFocus(List<Tile> tiles) {
        int none = 0;

        for (int i = 0; i < tiles.size(); i++) {
            Tile tile = tiles.get(i);

            if (tile.kind == Kind.ACCOUNT && tile.selected) {
                return i;
            }

            if (tile.kind == Kind.NONE) {
                none = i;
            }
        }

        return none;
    }
}
