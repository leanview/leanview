package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;

import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.leanview.InvidiousInstance;
import com.liskovsoft.smartyoutubetv2.common.leanview.QrCode;
import com.liskovsoft.smartyoutubetv2.common.leanview.QuickConnect;
import com.liskovsoft.smartyoutubetv2.common.leanview.ServerAccount;
import com.liskovsoft.smartyoutubetv2.common.leanview.ServerAddress;
import com.liskovsoft.smartyoutubetv2.common.leanview.ServerApi;
import com.liskovsoft.smartyoutubetv2.common.leanview.ServerSync;
import com.liskovsoft.smartyoutubetv2.common.utils.SimpleEditDialog;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

/**
 * leanview: connects the TV to the person's own server with Quick Connect, on SmartTube's own
 * sign-in screen (code, QR, link). The APK carries no server: {@link #connect()} first asks for its
 * address and checks it answers as a compatible backend; the QR then opens the approval page on
 * that server from a signed-in phone, and once approved the token and master key are kept by
 * {@link ServerAccount}.
 */
public class ServerSignInPresenter extends SignInPresenter {
    private static final String TAG = ServerSignInPresenter.class.getSimpleName();
    private static final long POLL_SECONDS = 2;
    @SuppressLint("StaticFieldLeak")
    private static ServerSignInPresenter sInstance;

    /** Asks the person for the server address; replaced in tests (a dialog needs an activity). */
    interface Asker {
        void ask(ServerSignInPresenter presenter, String suggestion);
    }

    /** Opens the Quick Connect session; replaced in tests (it is a network request). */
    interface Opener {
        QuickConnect open(String backend) throws IOException;
    }

    interface Starter {
        void start(ServerSignInPresenter presenter);
    }

    static ServerAddress.Fetcher sFetcher = ServerAddress.http();
    static Starter sStarter = presenter -> presenter.start();
    static Asker sAsker = (presenter, suggestion) -> presenter.showAddressDialog(suggestion);
    static Opener sOpener = QuickConnect::open;

    private Disposable mOpenAction;
    private Disposable mPollAction;
    private Disposable mCheckAction;
    // The server the person chose, already checked. Null until they do: nothing is built in.
    private String mBackend;

    private ServerSignInPresenter(Context context) {
        super(context);
    }

    public static ServerSignInPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new ServerSignInPresenter(context);
        }

        sInstance.setContext(context);

        return sInstance;
    }

    @Override
    public void unhold() {
        RxHelper.disposeActions(mOpenAction, mPollAction, mCheckAction);
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
        openSession();
    }

    @Override
    public void onActionClicked() {
        if (getView() != null) {
            getView().close();
        }
    }

    /** Entry point of Settings > Accounts: asks for the server address, then goes on to Quick Connect. */
    public void connect() {
        askAddress(null);
    }

    /** The server to connect to, as chosen and checked; null when none yet. */
    String address() {
        return mBackend;
    }

    void useAddress(String address) {
        mBackend = address;
    }

    private void askAddress(String typed) {
        sAsker.ask(this, typed != null ? typed : suggestion());
    }

    // The server this TV is already connected to, if any: the person usually reconnects to it.
    private String suggestion() {
        List<ServerAccount> accounts = ServerAccount.all(getContext());
        return accounts.isEmpty() ? null : accounts.get(0).backendUrl;
    }

    private void showAddressDialog(String suggestion) {
        Context context = getContext();
        SimpleEditDialog.show(
                context,
                context.getString(R.string.leanview_server_address_title),
                context.getString(R.string.leanview_server_address_hint),
                suggestion,
                typed -> {
                    String address = ServerAddress.normalize(typed);
                    if (address == null) {
                        MessageHelpers.showLongMessage(context, context.getString(ServerAddress.needsHttps(typed)
                                ? R.string.leanview_server_needs_https : R.string.leanview_server_address_invalid));
                        return false; // the field stays open to correct it
                    }
                    check(address, typed);
                    return true;
                });
    }

    // Probes /api/config off the main thread; on success goes on to Quick Connect, else says why and asks again.
    // A newer check replaces an older one (disposed), and nothing is done once the person left the screen
    // or the presenter was released: no sign-in screen started over another screen, no dialog on a dead Activity.
    void check(String address, String typed) {
        Context context = getContext();
        MessageHelpers.showMessage(context, context.getString(R.string.leanview_server_checking, address));
        RxHelper.disposeActions(mCheckAction);
        mCheckAction = Observable.fromCallable(() -> {
                    ServerAddress.check(address, sFetcher);
                    return address;
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(checked -> {
                    if (!isCurrent()) {
                        return;
                    }
                    mBackend = checked;
                    if (getView() != null) {
                        openSession(); // the sign-in screen is already open: it goes on from here
                    } else {
                        sStarter.start(this);
                    }
                }, error -> {
                    if (!isCurrent()) {
                        return;
                    }
                    Log.e(TAG, "Server check failed: %s", error.getMessage());
                    Context now = getContext();
                    MessageHelpers.showLongMessage(now, now.getString(messageFor(error), address));
                    askAddress(typed);
                });
    }

    // Still the presenter in use, and the screen it works from is alive (getContext() is an Activity only then).
    private boolean isCurrent() {
        return sInstance == this && getContext() instanceof Activity;
    }

    private static int messageFor(Throwable error) {
        if (error instanceof ServerAddress.Problem) {
            switch (((ServerAddress.Problem) error).reason) {
                case NOT_COMPATIBLE:
                    return R.string.leanview_server_not_compatible;
                case CERTIFICATE:
                    return R.string.leanview_server_certificate;
                case REDIRECT:
                    return R.string.leanview_server_redirect;
                default:
                    break;
            }
        }
        return R.string.leanview_server_unreachable;
    }

    private void openSession() {
        RxHelper.disposeActions(mOpenAction, mPollAction);
        String backend = mBackend;
        if (backend == null) {
            askAddress(null); // nothing is built in: no address, no request
            return;
        }
        mOpenAction = Observable.fromCallable(() -> sOpener.open(backend))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(session -> {
                    if (getView() == null) {
                        return;
                    }
                    String shownUrl = backend.replaceFirst("^https?://", "") + "/quickconnect";
                    getView().showCode(formatCode(session.code), shownUrl, QrCode.dataUri(session.link(), 512));
                    waitForApproval(session);
                }, error -> {
                    Log.e(TAG, "Quick Connect error: %s", error.getMessage());
                    if (getView() != null) {
                        getView().showCode(error.getMessage(), "");
                    }
                });
    }

    private void waitForApproval(QuickConnect session) {
        mPollAction = Observable.interval(POLL_SECONDS, TimeUnit.SECONDS, Schedulers.io())
                .flatMapMaybe(tick -> {
                    if (System.currentTimeMillis() > session.expiresAt) {
                        throw new SessionExpired();
                    }
                    return Maybe.fromCallable(session::poll);
                })
                .take(1)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(approved -> {
                    // Paired with the Google account selected right now (one profile per person).
                    ServerAccount account = approved.pairedWith(ServerSync.selectedGoogleAccount());
                    account.save(getContext());
                    if (getView() != null) {
                        getView().close();
                    }
                    confirmConnection(account);
                }, error -> {
                    if (error instanceof SessionExpired) {
                        openSession(); // a new code, as the old one stops working after 5 minutes
                        return;
                    }
                    Log.e(TAG, "Quick Connect polling error: %s", error.getMessage());
                    if (getView() != null) {
                        getView().showCode(error.getMessage(), "");
                    }
                });
    }

    // Reads the Invidious account the web keeps on the server: it is encrypted with the master key,
    // so reading it proves the TV opens the person's data like the web does. It also picks up the
    // Invidious instance the server keeps, so the TV needs no typing for it.
    private void confirmConnection(ServerAccount account) {
        Context context = getContext().getApplicationContext();
        String who = account.username != null ? account.username : account.backendUrl;
        // Not tied to the screen: it closes right away and would dispose the check.
        //noinspection ResultOfMethodCallIgnored
        Observable.fromCallable(() -> {
                    InvidiousInstance.fillMissing(context, InvidiousInstance.fromServer());
                    org.json.JSONObject invidious = ServerApi.invidiousAuth(account);
                    return invidious != null ? invidious.optString("username", "?") : "";
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(invidiousUser -> MessageHelpers.showLongMessage(context,
                                context.getString(R.string.leanview_server_connected, invidiousUser.isEmpty() ? who : who + " (Invidious: " + invidiousUser + ")")),
                        error -> {
                            Log.e(TAG, "Server data check failed: %s", error.getMessage());
                            MessageHelpers.showLongMessage(context, context.getString(R.string.leanview_server_connected, who) + " - " + error.getMessage());
                        });
    }

    /** "ABCDEFGH" -> "ABCD-EFGH", easier to read across the room. */
    private static String formatCode(String code) {
        return code.length() == 8 ? code.substring(0, 4) + "-" + code.substring(4) : code;
    }

    private static final class SessionExpired extends RuntimeException {
    }
}
