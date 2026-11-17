package com.liskovsoft.smartyoutubetv2.common.leanview;

/**
 * leanview: the player view that can draw the "Up next" end screen. The controller calls
 * showEndScreen again every second with the new model (the view keeps its focus and selection
 * while the mode stays the same); views that do not implement it (the embed player) never get one.
 */
public interface EndScreenHost {
    void showEndScreen(EndScreenModel model);

    void hideEndScreen();

    /** The card is on screen right now (its view exists and is open). */
    boolean isEndScreenShown();

    /** The related videos row has something to show below the card. */
    boolean hasEndScreenRelated();
}
