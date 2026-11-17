package com.liskovsoft.smartyoutubetv2.common.app.views;

import com.liskovsoft.smartyoutubetv2.common.leanview.AccountPickerTiles;

import java.util.List;

/** leanview: the full-screen "Who's watching?" window. */
public interface AccountPickerView {
    void showTiles(List<AccountPickerTiles.Tile> tiles, int focusIndex);
    void close();
}
