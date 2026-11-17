package com.liskovsoft.smartyoutubetv2.common.leanview;

import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.AccountsData;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import java.util.concurrent.atomic.AtomicLong;

import io.reactivex.Maybe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

/**
 * leanview: brings the lock a person set on yt.example.com for their profile to the TV (see the web client), when the app opens and when the profile changes. The server account is
 * kept per Google email; the TV password per account name.
 */
public final class ProfileLockSync {
    private static final String TAG = ProfileLockSync.class.getSimpleName();
    // Only the latest refresh is applied: an older answer arriving last must not undo a newer one.
    private static final AtomicLong sLatest = new AtomicLong();

    private ProfileLockSync() {
    }

    public static void refresh(Context context) {
        Context app = context.getApplicationContext();
        Account account;
        try {
            account = YouTubeServiceManager.instance().getSignInService().getSelectedAccount();
        } catch (Exception e) {
            return;
        }
        String email = ServerSync.profileKey(account); // a channel has no email: its page id
        if (email == null) {
            return;
        }
        String name = account.getName();
        long refresh = sLatest.incrementAndGet();

        // Not tied to a screen: a profile switch must not cancel it. The server account is read off
        // the main thread (the Android keystore unwraps it).
        //noinspection ResultOfMethodCallIgnored
        Maybe.fromCallable(() -> {
                    ServerAccount server = ServerAccount.forGoogle(app, email);
                    // forGoogle falls back to the server account kept for "no Google account": that one
                    // is not this person's, so its lock must not land on their profile.
                    if (server == null || !email.equals(server.googleAccount)) {
                        return null;
                    }
                    return ServerProfileLock.fetch(server);
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(lock -> {
                    AccountsData accountsData = AccountsData.instance(app);
                    if (refresh == sLatest.get() && accountsData.applyServerLock(name, lock)) {
                        // Locked on the web while open: stop what plays, as a locked start does.
                        if (!accountsData.isPasswordAccepted()) {
                            PlaybackPresenter.instance(app).forceFinish();
                        }
                        BrowsePresenter.instance(app).updateSections();
                    }
                }, error -> Log.e(TAG, "Profile lock from the server: %s", error.getMessage()));
    }
}
