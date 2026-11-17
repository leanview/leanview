package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.SystemClock;

import com.liskovsoft.mediaserviceinterfaces.ServiceManager;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.mediaserviceinterfaces.SignInService;
import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AccountPickerPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.AccountSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.leanview.AccountPicker;
import com.liskovsoft.smartyoutubetv2.common.leanview.ContinueWatching;
import com.liskovsoft.smartyoutubetv2.common.leanview.ProfileLockSync;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.AccountsData;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import java.util.List;

public class AccountSelectionPresenter extends BasePresenter<Void> {
    private static final String TAG = AccountSelectionPresenter.class.getSimpleName();
    @SuppressLint("StaticFieldLeak")
    private static AccountSelectionPresenter sInstance;
    private final SignInService mSignInService;

    public AccountSelectionPresenter(Context context) {
        super(context);
        ServiceManager service = YouTubeServiceManager.instance();
        mSignInService = service.getSignInService();
    }

    public static AccountSelectionPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new AccountSelectionPresenter(context);
        }

        sInstance.setContext(context);

        return sInstance;
    }

    public void show() {
        // leanview: opening the app while a video plays in the corner window goes back to that video;
        // asking who is watching then only waited behind the player until Back (2026-11-01)
        if (isVideoGoingOn()) {
            return;
        }

        show(false);
    }

    /**
     * leanview: when the browse screen comes back, if the app was away long enough (AccountPicker).
     * True when the picker opened.
     */
    public boolean showIfBack() {
        if (AccountPicker.instance().takeBack() && !isVideoGoingOn()) {
            show(true);
            return true;
        }
        return false;
    }

    /** leanview: arrived at the selected profile: the video it left a moment ago opens paused (ContinueWatching). */
    public void openContinueWatching() {
        if (isVideoGoingOn() || ViewManager.instance(getContext()).isPlayerInForeground()) {
            return;
        }
        ContinueWatching.openIfJustLeft(getContext(), System.currentTimeMillis());
    }

    private static String profileKey(Account account) {
        return account != null ? AccountPicker.profileKey(account.getEmail(), account.getName()) : null;
    }

    private void closeVideoGoingOn() {
        PlaybackView playback = PlaybackPresenter.instance(getContext()).getView();
        if (playback == null || !isVideoGoingOn()) {
            return;
        }
        // As when the corner window is closed by hand (PlaybackActivity.closeDismissedPip)
        ViewManager.instance(getContext()).blockTop(null);
        playback.blockEngine(false);
        playback.finishReally();
    }

    private boolean isVideoGoingOn() {
        PlaybackPresenter playback = PlaybackPresenter.instance(getContext());
        // Home from the player enters the window without blocking the engine, so check both
        return playback.isRunningInBackground() || (playback.getView() != null && playback.getView().isInPIPMode());
    }

    /** leanview: the account orb opens the picker instead of moving to the next account. */
    public void pick() {
        MediaServiceManager.instance().loadAccounts(accounts -> {
            if (accounts == null || accounts.isEmpty()) {
                AccountSettingsPresenter.instance(getContext()).show();
            } else {
                show(true);
            }
        });
    }

    /** leanview: opens the full-screen "Who's watching?" window (AccountPickerPresenter), not a side panel. */
    public void show(boolean force) {
        if (!AccountsData.instance(getContext()).isSelectAccountOnBootEnabled() && !force) {
            // user don't want to see selection dialog
            return;
        }

        AccountPicker.instance().onShown(SystemClock.elapsedRealtime());

        List<Account> accounts = mSignInService.getAccounts();

        // leanview: one account is a choice too (it or none); only with no account there is nothing to pick
        if ((accounts == null || accounts.isEmpty()) && !force) {
            return;
        }

        AccountPickerPresenter.instance(getContext()).start();
    }

    public void nextAccountOrDialog() {
        MediaServiceManager.instance().loadAccounts(this::nextAccountOrDialog);
    }

    public void unhold() {
        sInstance = null;
    }

    private void nextAccountOrDialog(List<Account> accounts) {
        if (accounts == null || accounts.isEmpty()) {
            AccountSettingsPresenter.instance(getContext()).show();
            return;
        }

        Account current = null;

        for (Account account : accounts) {
            if (account.isSelected()) {
                current = account;
                break;
            }
        }

        int index = accounts.indexOf(current);

        int nextIndex = index + 1;
        // null == 'without account'
        selectAccount(nextIndex == accounts.size() ? null : accounts.get(nextIndex));
        //selectAccount(accounts.get(nextIndex == accounts.size() ? 0 : nextIndex));
    }

    public void selectAccount(Account account) {
        // leanview: a video in the corner window belongs to the profile that started it; another
        // profile must not inherit it (2026-11-01)
        Account current = mSignInService.getSelectedAccount();
        boolean another = AccountPicker.isAnotherProfile(profileKey(current), profileKey(account));
        Log.d(TAG, "Profile switch: another profile=%s, video going on=%s", another, isVideoGoingOn());
        boolean closed = another && isVideoGoingOn();
        if (another) {
            closeVideoGoingOn();
        }

        mSignInService.selectAccount(account);
        Utils.updateChannels(getContext());
        // leanview: the lock the new profile's owner set on the web (see the web client)
        ProfileLockSync.refresh(getContext());

        // Account history might be turned off (common issue).
        GeneralData generalData = GeneralData.instance(getContext());
        int historyState = generalData.getHistoryState();
        if (historyState != GeneralData.HISTORY_AUTO) {
            MediaServiceManager.instance().enableHistory(historyState != GeneralData.HISTORY_DISABLED);
        }

        // leanview: after the picker closes, and after the other profile's video is gone
        Utils.postDelayed(this::openContinueWatching, closed ? 1_500 : 300);
    }
}
