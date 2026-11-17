package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.annotation.SuppressLint;
import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.AccountSelectionPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.AccountPickerView;
import com.liskovsoft.smartyoutubetv2.common.leanview.AccountPickerTiles;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import java.util.List;

/**
 * leanview: the "Who's watching?" window (2026-10-31). Opened by AccountSelectionPresenter,
 * which keeps the rules for when to open it; choosing goes through its selectAccount() so the
 * profile lock still applies.
 */
public class AccountPickerPresenter extends BasePresenter<AccountPickerView> {
    @SuppressLint("StaticFieldLeak")
    private static AccountPickerPresenter sInstance;

    private AccountPickerPresenter(Context context) {
        super(context);
    }

    public static AccountPickerPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new AccountPickerPresenter(context);
        }

        sInstance.setContext(context);

        return sInstance;
    }

    public void unhold() {
        sInstance = null;
    }

    public void start() {
        getViewManager().startView(AccountPickerView.class);
    }

    @Override
    public void onViewInitialized() {
        AccountPickerView view = getView();

        if (view == null) {
            return;
        }

        List<Account> accounts = YouTubeServiceManager.instance().getSignInService().getAccounts();
        List<AccountPickerTiles.Tile> tiles = AccountPickerTiles.build(accounts);

        view.showTiles(tiles, AccountPickerTiles.initialFocus(tiles));
    }

    @Override
    public void onViewDestroyed() {
        super.onViewDestroyed();
        unhold();
    }

    public void onTileClicked(AccountPickerTiles.Tile tile) {
        Context context = getContext();
        AccountPickerView view = getView();

        switch (tile.kind) {
            case ACCOUNT:
                AccountSelectionPresenter.instance(context).selectAccount(tile.account);
                break;
            case NONE:
                AccountSelectionPresenter.instance(context).selectAccount(null);
                break;
            case ADD:
                if (view != null) {
                    view.close();
                }
                YTSignInPresenter.instance(context).start();
                return;
        }

        if (view != null) {
            view.close();
        }
    }
}
