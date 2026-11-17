package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.os.SystemClock;

import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.AccountSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.leanview.ProfileUnlock;
import com.liskovsoft.smartyoutubetv2.common.leanview.QrCode;
import com.liskovsoft.smartyoutubetv2.common.leanview.ServerAccount;
import com.liskovsoft.smartyoutubetv2.common.leanview.ServerSync;
import com.liskovsoft.smartyoutubetv2.common.prefs.AccountsData;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

/**
 * leanview: a locked profile asks its owner to let this TV in (see the web client).
 * The sign-in screen shows a QR of the approval page; the profile owner answers on their phone; the TV
 * takes the answer, once. The PIN stays available from the same screen.
 */
public class ProfileUnlockPresenter extends SignInPresenter {
    private static final String TAG = ProfileUnlockPresenter.class.getSimpleName();
    private static final long POLL_SECONDS = 3;
    private static final long REQUEST_LIFETIME_MS = 5 * 60_000;
    private static final int MAX_NEW_CODES = 3;
    @SuppressLint("StaticFieldLeak")
    private static ProfileUnlockPresenter sInstance;
    private Disposable mOpenAction;
    private Disposable mPollAction;
    // The profile this screen asks for: an approval lets only that one in.
    private String mAccountName;
    private int mNewCodes;

    private ProfileUnlockPresenter(Context context) {
        super(context);
    }

    public static ProfileUnlockPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new ProfileUnlockPresenter(context);
        }

        sInstance.setContext(context);

        return sInstance;
    }

    /** The server account of the selected profile's owner, or null: then only the PIN is offered. */
    public static ServerAccount ownerAccount(Context context) {
        try {
            Account account = YouTubeServiceManager.instance().getSignInService().getSelectedAccount();
            String key = ServerSync.profileKey(account); // a channel has no email: its page id
            if (key == null) {
                return null;
            }
            ServerAccount server = ServerAccount.forGoogle(context, key);
            // Not the server account kept for "no Google account": it belongs to nobody in particular.
            return server != null && key.equals(server.googleAccount) ? server : null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void unhold() {
        RxHelper.disposeActions(mOpenAction, mPollAction);
        sInstance = null;
    }

    @Override
    public void onViewDestroyed() {
        super.onViewDestroyed();
        unhold();
    }

    @Override
    public void onViewInitialized() {
        super.onViewInitialized();
        mAccountName = selectedAccountName();
        mNewCodes = 0;
        openRequest();
    }

    /** The screen's only action: the PIN instead. Nothing keeps waiting for the phone after it. */
    @Override
    public void onActionClicked() {
        Context context = getContext();
        RxHelper.disposeActions(mOpenAction, mPollAction);
        if (getView() != null) {
            getView().close();
        }
        AccountSettingsPresenter.instance(context).showPinDialog();
    }

    private static String selectedAccountName() {
        try {
            Account account = YouTubeServiceManager.instance().getSignInService().getSelectedAccount();
            return account != null ? account.getName() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void openRequest() {
        RxHelper.disposeActions(mOpenAction, mPollAction);
        ServerAccount owner = ownerAccount(getContext());
        if (owner == null) {
            usePinInstead(null);
            return;
        }
        mOpenAction = Observable.fromCallable(() -> ProfileUnlock.open(owner, Build.MODEL))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(request -> {
                    if (getView() == null) {
                        unhold();
                        return;
                    }
                    getView().showUnlock(formatCode(request.code),
                            getContext().getString(R.string.leanview_unlock_description),
                            QrCode.dataUri(request.link(), 512),
                            getContext().getString(R.string.leanview_unlock_use_pin));
                    waitForAnswer(request, SystemClock.elapsedRealtime());
                }, error -> {
                    Log.e(TAG, "Profile unlock request: %s", error.getMessage());
                    usePinInstead(error.getMessage());
                });
    }

    // The lifetime is counted on this TV's own uptime clock: a wrong date must not make every code
    // look expired at once. The server's own answer ("gone") ends it too.
    private void waitForAnswer(ProfileUnlock request, long openedAt) {
        mPollAction = Observable.interval(POLL_SECONDS, TimeUnit.SECONDS, Schedulers.io())
                .map(tick -> SystemClock.elapsedRealtime() - openedAt > REQUEST_LIFETIME_MS ? ProfileUnlock.GONE : request.claim())
                .filter(status -> !ProfileUnlock.PENDING.equals(status))
                .take(1)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(status -> {
                    if (getView() == null) {
                        unhold(); // the screen is gone: an answer now opens nothing
                        return;
                    }
                    if (ProfileUnlock.APPROVED.equals(status)) {
                        letIn();
                    } else if (ProfileUnlock.DENIED.equals(status)) {
                        MessageHelpers.showLongMessage(getContext(), R.string.leanview_unlock_denied);
                        getView().close();
                    } else if (++mNewCodes <= MAX_NEW_CODES) {
                        openRequest(); // expired: a new code
                    } else {
                        usePinInstead(null); // nobody answered in a while: the PIN
                    }
                }, error -> {
                    Log.e(TAG, "Profile unlock answer: %s", error.getMessage());
                    usePinInstead(error.getMessage());
                });
    }

    private void letIn() {
        Context context = getContext();
        // Approved for the profile this screen was opened for, and only while it is still selected.
        if (mAccountName == null || !mAccountName.equals(selectedAccountName())) {
            if (getView() != null) {
                getView().close();
            }
            return;
        }
        AccountsData accountsData = AccountsData.instance(context);
        accountsData.setPasswordAccepted(true);
        accountsData.setLastUnlockMethod(AccountsData.UNLOCK_BY_PHONE);
        if (getView() != null) {
            getView().close();
        }
        BrowsePresenter.instance(context).updateSections();
    }

    // The server does not answer: the PIN is the way in. Only while this screen is up.
    private void usePinInstead(String reason) {
        Context context = getContext();
        if (getView() == null) {
            unhold();
            return;
        }
        if (reason != null) {
            MessageHelpers.showLongMessage(context, context.getString(R.string.leanview_unlock_unavailable, reason));
        }
        if (getView() != null) {
            getView().close();
        }
        AccountSettingsPresenter.instance(context).showPinDialog();
    }

    /** "ABCDEFGH" -> "ABCD-EFGH", easier to read across the room. */
    private static String formatCode(String code) {
        return code.length() == 8 ? code.substring(0, 4) + "-" + code.substring(4) : code;
    }
}
