package com.liskovsoft.smartyoutubetv2.common.leanview;

import com.liskovsoft.smartyoutubetv2.common.leanview.EndScreenKeys.Mode;
import com.liskovsoft.smartyoutubetv2.common.leanview.EndScreenSettings.DefaultFocus;

/** leanview: everything the "Up next" end screen shows. Plain data, so the controller does not know the views. */
public final class EndScreenModel {
    public final Mode mode;
    /** The next video. */
    public final String title;
    public final String author;
    public final String imageUrl;
    /** Countdown only: whole seconds left, and the seconds it started from. */
    public final int secondsLeft;
    public final int totalSeconds;
    /** The countdown is held (a related video has the focus). */
    public final boolean paused;
    public final DefaultFocus defaultFocus;

    public EndScreenModel(Mode mode, String title, String author, String imageUrl, int secondsLeft, int totalSeconds,
                          boolean paused, DefaultFocus defaultFocus) {
        this.mode = mode;
        this.title = title;
        this.author = author;
        this.imageUrl = imageUrl;
        this.secondsLeft = secondsLeft;
        this.totalSeconds = totalSeconds;
        this.paused = paused;
        this.defaultFocus = defaultFocus;
    }
}
