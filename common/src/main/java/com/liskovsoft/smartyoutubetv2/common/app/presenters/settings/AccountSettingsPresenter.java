package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;

import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ProfileUnlockPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.YTSignInPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ServerSignInPresenter;
import com.liskovsoft.smartyoutubetv2.common.leanview.InvidiousInstance;
import com.liskovsoft.smartyoutubetv2.common.leanview.PasswordAttempts;
import com.liskovsoft.smartyoutubetv2.common.leanview.ServerAccount;
import com.liskovsoft.smartyoutubetv2.common.leanview.ServerAddress;
import com.liskovsoft.smartyoutubetv2.common.leanview.AccountPicker;
import com.liskovsoft.smartyoutubetv2.common.leanview.ServerSync;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.AccountSelectionPresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.AccountsData;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;
import com.liskovsoft.smartyoutubetv2.common.utils.AppDialogUtil;
import com.liskovsoft.smartyoutubetv2.common.utils.GlideIconFetcher;
import com.liskovsoft.smartyoutubetv2.common.utils.SimpleEditDialog;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import java.util.ArrayList;
import java.util.List;

public class AccountSettingsPresenter extends BasePresenter<Void> {
    private static final String TAG = AccountSettingsPresenter.class.getSimpleName();
    @SuppressLint("StaticFieldLeak")
    private static AccountSettingsPresenter sInstance;
    private final MediaServiceManager mMediaServiceManager;

    public AccountSettingsPresenter(Context context) {
        super(context);
        mMediaServiceManager = MediaServiceManager.instance();
    }

    public static AccountSettingsPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new AccountSettingsPresenter(context);
        }

        sInstance.setContext(context);

        return sInstance;
    }

    public void unhold() {
        sInstance = null;
    }

    public void show() {
        mMediaServiceManager.loadAccounts(this::fetchImagesAndShowDialog);
    }

    private void fetchImagesAndShowDialog(List<Account> accounts) {
        GlideIconFetcher.fetchDrawables(getContext(),
                Helpers.map(accounts, Account::getAvatarImageUrl),
                icons -> createAndShowDialog(accounts, icons));
    }

    private void createAndShowDialog(List<Account> accounts, List<Drawable> icons) {
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());

        appendSelectAccountSection(accounts, icons, settingsPresenter);
        appendSignInButton(settingsPresenter);
        appendServerSection(settingsPresenter);
        appendSignOutSection(accounts, icons, settingsPresenter);
        appendProtectAccountWithPassword(settingsPresenter);
        appendSeparateSettings(settingsPresenter);
        appendSelectAccountOnBoot(settingsPresenter);
        appendPickerOnReturn(settingsPresenter);

        Account account = getSignInService().getSelectedAccount();
        int accountIndex = accounts != null ? accounts.indexOf(account) : -1;
        CharSequence accountIcon = accountIndex != -1 ? Utils.icon(icons.get(accountIndex)) : null;
        settingsPresenter.showDialog(account != null ? TextUtils.concat(accountIcon, " ", account.getName())
                : getContext().getString(R.string.settings_accounts), this::unhold);
    }

    private void appendSelectAccountSection(List<Account> accounts, List<Drawable> icons, AppDialogPresenter settingsPresenter) {
        if (accounts == null || accounts.isEmpty()) {
            return;
        }

        List<OptionItem> optionItems = new ArrayList<>();

        optionItems.add(UiOptionItem.from(
                getContext().getString(R.string.dialog_account_none), optionItem -> {
                    AccountSelectionPresenter.instance(getContext()).selectAccount(null);
                    settingsPresenter.closeDialog();
                }, true
        ));

        CharSequence accountName = " (" + getContext().getString(R.string.dialog_account_none) + ")";

        int index = -1;

        for (Account account : accounts) {
            index++;
            CharSequence icon = Utils.icon(icons.get(index));
            optionItems.add(UiOptionItem.from(
                    TextUtils.concat(icon, " ", getFullName(account)), option -> {
                        AccountSelectionPresenter.instance(getContext()).selectAccount(account);
                        settingsPresenter.closeDialog();
                    }, account.isSelected()
            ));

            if (account.isSelected()) {
                accountName = " (" + getSimpleName(account) + ")";
            }
        }

        String categoryTitle = getContext().getString(R.string.dialog_account_list);
        settingsPresenter.appendRadioCategory(categoryTitle, optionItems);
    }

    private void appendSignOutSection(List<Account> accounts, List<Drawable> icons, AppDialogPresenter settingsPresenter) {
        if (accounts == null || accounts.isEmpty()) {
            return;
        }

        List<OptionItem> optionItems = new ArrayList<>();

        int index = -1;

        for (Account account : accounts) {
            index++;
            CharSequence icon = Utils.icon(icons.get(index));
            optionItems.add(UiOptionItem.from(
                    TextUtils.concat(icon, " ", getFullName(account)), option ->
                        AppDialogUtil.showConfirmationDialog(
                                getContext(), getContext().getString(R.string.dialog_remove_account), () -> {
                                    removeAccount(account);
                                    settingsPresenter.closeDialog();
                                    MessageHelpers.showMessage(getContext(), R.string.msg_done);
                                })
            ));
        }

        settingsPresenter.appendStringsCategory(getContext().getString(R.string.dialog_remove_account), optionItems);
    }

    private void appendSignInButton(AppDialogPresenter settingsPresenter) {
        settingsPresenter.appendSingleButton(UiOptionItem.from(
                getContext().getString(R.string.dialog_add_account), option -> YTSignInPresenter.instance(getContext()).start()));
    }

    // leanview: the connection to the person's own server (subscriptions, history and position shared
    // with their web), made with Quick Connect from a signed-in phone. The APK carries no server:
    // choosing it asks for the address first.
    private void connectToServer(AppDialogPresenter settingsPresenter) {
        settingsPresenter.closeDialog();
        ServerSignInPresenter.instance(getContext()).connect();
    }

    private void appendServerSection(AppDialogPresenter settingsPresenter) {
        String selected = ServerSync.selectedGoogleAccount();
        ServerAccount account = ServerAccount.load(getContext());
        ServerAccount unowned = ServerAccount.unowned(getContext());
        if (account == null && selected != null) {
            // A connection made before it had an owner is not offered here: the TV cannot tell whose
            // it is, and it was offered to Bob too (2026-11-08). Its person connects again with the
            // QR, and that replaces it (ServerAccount.save).
            settingsPresenter.appendSingleButton(UiOptionItem.from(
                    getContext().getString(R.string.leanview_server_connect), option -> connectToServer(settingsPresenter)));
            return;
        }
        if (account == null) {
            // No Google account selected: only a connection with no owner can be shown (and removed)
            account = unowned;
        }
        if (account == null) {
            settingsPresenter.appendSingleButton(UiOptionItem.from(
                    getContext().getString(R.string.leanview_server_connect), option -> connectToServer(settingsPresenter)));
            return;
        }
        ServerAccount shown = account;
        String who = account.username != null ? account.username : account.backendUrl;
        String paired = account.googleAccount != null ? ServerSync.displayName(account.googleAccount) : getContext().getString(R.string.leanview_server_no_google);
        settingsPresenter.appendSingleButton(UiOptionItem.from(
                getContext().getString(R.string.leanview_server_paired, paired), option -> {}));
        appendInvidiousInstance(settingsPresenter, shown);
        settingsPresenter.appendSingleButton(UiOptionItem.from(
                getContext().getString(R.string.leanview_server_disconnect, who), option ->
                        AppDialogUtil.showConfirmationDialog(getContext(), getContext().getString(R.string.leanview_server_disconnect_confirm), () -> {
                            shown.remove(getContext());
                            settingsPresenter.closeDialog();
                            MessageHelpers.showMessage(getContext(), R.string.msg_done);
                        })));
    }

    // leanview: the Invidious instance of the connected server, empty until the server reports one or the
    // person types it. Without one, the Home feed of "Ninguna" and the subscription mirror stay off.
    private void appendInvidiousInstance(AppDialogPresenter settingsPresenter, ServerAccount account) {
        String current = account.invidiousInstance != null ? account.invidiousInstance
                : getContext().getString(R.string.leanview_invidious_none);
        settingsPresenter.appendSingleButton(UiOptionItem.from(
                getContext().getString(R.string.leanview_invidious_instance, current), option -> {
                    settingsPresenter.closeDialog();
                    SimpleEditDialog.show(
                            getContext(),
                            getContext().getString(R.string.leanview_invidious_title),
                            getContext().getString(R.string.leanview_invidious_hint),
                            account.invidiousInstance,
                            newValue -> {
                                if (InvidiousInstance.set(getContext(), account, newValue) == null) {
                                    MessageHelpers.showLongMessage(getContext(), getContext().getString(
                                            ServerAddress.needsHttps(newValue) ? R.string.leanview_server_needs_https : R.string.leanview_invidious_invalid));
                                    return false; // the field stays open to correct it
                                }
                                MessageHelpers.showMessage(getContext(), R.string.msg_done);
                                return true;
                            });
                }));
        if (account.invidiousInstance != null) {
            settingsPresenter.appendSingleButton(UiOptionItem.from(
                    getContext().getString(R.string.leanview_invidious_clear), option -> {
                        InvidiousInstance.set(getContext(), account, null);
                        settingsPresenter.closeDialog();
                        MessageHelpers.showMessage(getContext(), R.string.msg_done);
                    }));
        }
    }

    // leanview: the picker also opens by itself when the app comes back after this long away (AccountPicker)
    private void appendPickerOnReturn(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();
        long current = AccountsData.instance(getContext()).getPickerAwayMs();
        for (long awayMs : AccountPicker.AWAY_OPTIONS_MS) {
            String title = awayMs == AccountPicker.NEVER ? getContext().getString(R.string.leanview_picker_never)
                    : awayMs < 60 * 60_000 ? getContext().getString(R.string.leanview_picker_minutes, awayMs / 60_000)
                    : getContext().getString(R.string.leanview_picker_hours, awayMs / (60 * 60_000));
            options.add(UiOptionItem.from(title,
                    option -> AccountsData.instance(getContext()).setPickerAwayMs(awayMs), awayMs == current));
        }
        settingsPresenter.appendRadioCategory(getContext().getString(R.string.leanview_picker_on_return), options);
    }

    private void appendSelectAccountOnBoot(AppDialogPresenter settingsPresenter) {
        settingsPresenter.appendSingleSwitch(UiOptionItem.from(getContext().getString(R.string.select_account_on_boot), optionItem -> {
            AccountsData.instance(getContext()).selectAccountOnBoot(optionItem.isSelected());
        }, AccountsData.instance(getContext()).isSelectAccountOnBootEnabled()));
    }

    private void appendProtectAccountWithPassword(AppDialogPresenter settingsPresenter) {
        settingsPresenter.appendSingleSwitch(UiOptionItem.from(getContext().getString(R.string.protect_account_with_password), optionItem -> {
            if (optionItem.isSelected()) {
                showAddPasswordDialog(settingsPresenter);
            } else {
                showRemovePasswordDialog(settingsPresenter);
            }
        }, AccountsData.instance(getContext()).getAccountPassword() != null));
    }

    private void appendSeparateSettings(AppDialogPresenter settingsPresenter) {
        settingsPresenter.appendSingleSwitch(UiOptionItem.from(getContext().getString(R.string.multi_profiles),
                option -> {
                    AppPrefs.instance(getContext()).enableMultiProfiles(option.isSelected());
                    BrowsePresenter.instance(getContext()).updateSections();
                },
                AppPrefs.instance(getContext()).isMultiProfilesEnabled()));
    }

    private String getFullName(Account account) {
        String format;

        if (account.getEmail() != null) {
            format = String.format("%s (%s)", account.getName(), account.getEmail());
        } else {
            format = account.getName();
        }

        return format;
    }

    private String getSimpleName(Account account) {
        return account.getName() != null ? account.getName() : account.getEmail();
    }

    private void removeAccount(Account account) {
        getSignInService().removeAccount(account);
        BrowsePresenter.instance(getContext()).refresh(false);
    }

    private void showAddPasswordDialog(AppDialogPresenter settingsPresenter) {
        settingsPresenter.closeDialog();
        SimpleEditDialog.showPassword(
                getContext(),
                getContext().getString(R.string.enter_account_password),
                null,
                newValue -> {
                    AccountsData.instance(getContext()).setAccountPassword(newValue);
                    BrowsePresenter.instance(getContext()).updateSections();
                    //onSuccess.run();
                    return true;
                });
    }

    private void showRemovePasswordDialog(AppDialogPresenter settingsPresenter) {
        String password = AccountsData.instance(getContext()).getAccountPassword();

        if (password == null) {
            return;
        }

        settingsPresenter.closeDialog();
        SimpleEditDialog.showPassword(
                getContext(),
                getContext().getString(R.string.enter_account_password),
                null,
                newValue -> {
                    if (isAccountPasswordAccepted(newValue)) {
                        AccountsData.instance(getContext()).setAccountPassword(null);
                        BrowsePresenter.instance(getContext()).updateSections();
                        //onSuccess.run();
                        return true;
                    }
                    return false;
                });
    }

    /**
     * leanview: the way into a locked profile (see the web client): approval from
     * the profile owner's phone when the profile is connected to their server, or the PIN. It opens on the
     * way used last time; the other stays one step away.
     */
    public void showCheckPasswordDialog() {
        AccountsData accountsData = AccountsData.instance(getContext());

        if (accountsData.getAccountPassword() == null) {
            return;
        }

        if (ProfileUnlockPresenter.ownerAccount(getContext()) == null) {
            showPinDialog();
        } else if (accountsData.getLastUnlockMethod() == AccountsData.UNLOCK_BY_PHONE) {
            ProfileUnlockPresenter.instance(getContext()).start();
        } else {
            showUnlockChoice();
        }
    }

    private void showUnlockChoice() {
        AppDialogPresenter dialog = AppDialogPresenter.instance(getContext());
        dialog.appendSingleButton(UiOptionItem.from(getContext().getString(R.string.leanview_unlock_use_pin), option -> {
            dialog.closeDialog();
            showPinDialog();
        }));
        dialog.appendSingleButton(UiOptionItem.from(getContext().getString(R.string.leanview_unlock_by_phone), option -> {
            dialog.closeDialog();
            ProfileUnlockPresenter.instance(getContext()).start();
        }));
        dialog.showDialog(getContext().getString(R.string.enter_account_password));
    }

    public void showPinDialog() {
        if (AccountsData.instance(getContext()).getAccountPassword() == null) {
            return;
        }

        SimpleEditDialog.showPassword(
                getContext(),
                getContext().getString(R.string.enter_account_password),
                null,
                newValue -> {
                    if (isAccountPasswordAccepted(newValue)) {
                        AccountsData.instance(getContext()).setPasswordAccepted(true);
                        AccountsData.instance(getContext()).setLastUnlockMethod(AccountsData.UNLOCK_BY_PIN);
                        BrowsePresenter.instance(getContext()).updateSections();
                        //onSuccess.run();
                        return true;
                    }
                    return false;
                });
    }

    // leanview: wrong guesses are counted; after five the TV makes each try wait
    private boolean isAccountPasswordAccepted(String typed) {
        AccountsData accountsData = AccountsData.instance(getContext());
        int result = accountsData.checkAccountPassword(typed);
        // Also on the wrong guess that starts a wait, so the user knows before the next try
        if (result != PasswordAttempts.ACCEPTED && accountsData.getPasswordWaitSeconds() > 0) {
            MessageHelpers.showMessage(getContext(),
                    getContext().getString(R.string.leanview_password_wait, accountsData.getPasswordWaitSeconds()));
        }
        return result == PasswordAttempts.ACCEPTED;
    }
}
