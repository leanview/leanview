package com.liskovsoft.smartyoutubetv2.common.leanview;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.leanview.EndScreenSettings.NextSource;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;

import java.util.ArrayList;
import java.util.List;

/**
 * leanview: the player settings of the "Up next" end screen (what happens when a video ends). They
 * are saved per profile in the player data, like the playback mode they follow in the list.
 */
public final class EndScreenSettingsUi {
    private EndScreenSettingsUi() {
    }

    public static void append(Context context, AppDialogPresenter dialog, PlayerData playerData) {
        appendSwitches(context, dialog, playerData);
        appendRelatedSeconds(context, dialog, playerData);
        appendPlaylistSeconds(context, dialog, playerData);
        appendNextSource(context, dialog, playerData);
        appendStillWatching(context, dialog, playerData);
    }

    private static void appendSwitches(Context context, AppDialogPresenter dialog, PlayerData playerData) {
        List<OptionItem> options = new ArrayList<>();
        EndScreenSettings current = playerData.getEndScreen();

        options.add(UiOptionItem.from(context.getString(R.string.leanview_end_enable),
                option -> playerData.setEndScreen(playerData.getEndScreen().withEnabled(option.isSelected())),
                current.enabled));
        options.add(UiOptionItem.from(context.getString(R.string.leanview_end_skip_cast),
                option -> playerData.setEndScreen(playerData.getEndScreen().withSkipForCast(option.isSelected())),
                current.skipForCast));

        dialog.appendCheckedCategory(context.getString(R.string.leanview_end_category), options);
    }

    private static void appendRelatedSeconds(Context context, AppDialogPresenter dialog, PlayerData playerData) {
        List<OptionItem> options = new ArrayList<>();

        for (int seconds : EndScreenSettings.RELATED_OPTIONS) {
            options.add(UiOptionItem.from(secondsTitle(context, seconds),
                    option -> playerData.setEndScreen(playerData.getEndScreen().withRelatedCountdownSec(seconds)),
                    playerData.getEndScreen().relatedCountdownSec == seconds));
        }

        dialog.appendRadioCategory(context.getString(R.string.leanview_end_related_title), options);
    }

    private static void appendPlaylistSeconds(Context context, AppDialogPresenter dialog, PlayerData playerData) {
        List<OptionItem> options = new ArrayList<>();

        for (int seconds : EndScreenSettings.PLAYLIST_OPTIONS) {
            options.add(UiOptionItem.from(secondsTitle(context, seconds),
                    option -> playerData.setEndScreen(playerData.getEndScreen().withPlaylistCountdownSec(seconds)),
                    playerData.getEndScreen().playlistCountdownSec == seconds));
        }

        dialog.appendRadioCategory(context.getString(R.string.leanview_end_playlist_title), options);
    }

    private static void appendNextSource(Context context, AppDialogPresenter dialog, PlayerData playerData) {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(context.getString(R.string.leanview_end_source_playlist_related),
                option -> playerData.setEndScreen(playerData.getEndScreen().withNextSource(NextSource.PLAYLIST_THEN_RELATED)),
                playerData.getEndScreen().nextSource == NextSource.PLAYLIST_THEN_RELATED));
        options.add(UiOptionItem.from(context.getString(R.string.leanview_end_source_playlist_only),
                option -> playerData.setEndScreen(playerData.getEndScreen().withNextSource(NextSource.PLAYLIST_ONLY)),
                playerData.getEndScreen().nextSource == NextSource.PLAYLIST_ONLY));

        dialog.appendRadioCategory(context.getString(R.string.leanview_end_source_title), options);
    }

    private static void appendStillWatching(Context context, AppDialogPresenter dialog, PlayerData playerData) {
        List<OptionItem> options = new ArrayList<>();

        for (int hours : EndScreenSettings.STILL_WATCHING_OPTIONS) {
            String title = hours == EndScreenSettings.STILL_NEVER ?
                    context.getString(R.string.leanview_picker_never) : context.getString(R.string.leanview_picker_hours, hours);
            options.add(UiOptionItem.from(title,
                    option -> playerData.setEndScreen(playerData.getEndScreen().withStillWatchingHours(hours)),
                    playerData.getEndScreen().stillWatchingHours == hours));
        }

        dialog.appendRadioCategory(context.getString(R.string.leanview_end_still_title), options);
    }

    private static String secondsTitle(Context context, int seconds) {
        if (seconds == EndScreenSettings.NEVER_AUTO) {
            return context.getString(R.string.leanview_end_never_auto);
        } else if (seconds == 0) {
            return context.getString(R.string.leanview_end_immediate);
        }

        return context.getString(R.string.leanview_end_seconds, seconds);
    }
}
