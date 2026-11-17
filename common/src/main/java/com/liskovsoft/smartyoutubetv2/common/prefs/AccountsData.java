package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;
import androidx.annotation.NonNull;
import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.leanview.AccountPicker;
import com.liskovsoft.smartyoutubetv2.common.leanview.PasswordAttempts;
import com.liskovsoft.smartyoutubetv2.common.leanview.PasswordHash;
import com.liskovsoft.smartyoutubetv2.common.leanview.ServerProfileLock;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager.AccountChangeListener;

import java.util.HashMap;
import java.util.Map;

public class AccountsData implements AccountChangeListener {
    // leanview: how a locked profile was last opened; its unlock screen starts there
    public static final int UNLOCK_BY_PHONE = 0;
    public static final int UNLOCK_BY_PIN = 1;
    private static final String ACCOUNTS_DATA = "accounts_data";
    @SuppressLint("StaticFieldLeak")
    private static AccountsData sInstance;
    private final Context mContext;
    private final AppPrefs mAppPrefs;
    private boolean mIsSelectAccountOnBootEnabled;
    // leanview: how long away before the account picker opens by itself on coming back (AccountPicker)
    private long mPickerAwayMs;
    private boolean mIsPasswordAccepted;
    private final Map<String, PasswordItem> mPasswords = new HashMap<>();
    // leanview: wrong guesses per account, so another profile's right password does not reset them
    private final Map<String, PasswordAttempts> mAttempts = new HashMap<>();
    private final Map<String, Integer> mLastUnlockMethods = new HashMap<>();

    private static class PasswordItem {
        public String accountName;
        public String password;

        public PasswordItem(String accountName, String password) {
            this.accountName = accountName;
            this.password = password;
        }

        public static PasswordItem fromString(String specs) {
            String[] split = Helpers.splitObj(specs);

            if (split == null || split.length != 2) {
                return new PasswordItem(null, null);
            }

            return new PasswordItem(Helpers.parseStr(split[0]), Helpers.parseStr(split[1]));
        }

        @NonNull
        @Override
        public String toString() {
            return Helpers.mergeObj(accountName, password);
        }
    }

    private AccountsData(Context context) {
        mContext = context;
        mAppPrefs = AppPrefs.instance(mContext);
        MediaServiceManager.instance().addAccountListener(this);
        restoreState();
    }

    public static AccountsData instance(Context context) {
        if (sInstance == null) {
            sInstance = new AccountsData(context.getApplicationContext());
        }

        return sInstance;
    }

    public void selectAccountOnBoot(boolean select) {
        mIsSelectAccountOnBootEnabled = select;
        persistState();
    }

    public boolean isSelectAccountOnBootEnabled() {
        return mIsSelectAccountOnBootEnabled;
    }

    public long getPickerAwayMs() {
        return mPickerAwayMs;
    }

    public void setPickerAwayMs(long awayMs) {
        mPickerAwayMs = awayMs;
        persistState();
    }

    public void setAccountPassword(String password) {
        setAccountPassword(getAccountName(), password);
    }

    void setAccountPassword(String accountName, String password) {
        // leanview: kept as a hash, never as typed
        mPasswords.put(accountName, new PasswordItem(accountName, password != null ? PasswordHash.hash(password) : null));

        persistState();
    }

    /**
     * leanview: checks a typed password of the selected account, counting its wrong guesses.
     * Returns a PasswordAttempts result.
     */
    public int checkAccountPassword(String typed) {
        return checkAccountPassword(getAccountName(), typed, System.currentTimeMillis());
    }

    int checkAccountPassword(String accountName, String typed, long nowMs) {
        PasswordItem passwordItem = mPasswords.get(accountName);
        if (passwordItem == null || passwordItem.password == null) {
            return PasswordAttempts.ACCEPTED;
        }
        int result = attemptsOf(accountName).check(passwordItem.password, typed, nowMs);
        persistState();
        return result;
    }

    public long getPasswordWaitSeconds() {
        return attemptsOf(getAccountName()).waitSeconds(System.currentTimeMillis());
    }

    private PasswordAttempts attemptsOf(String accountName) {
        PasswordAttempts attempts = mAttempts.get(accountName);
        if (attempts == null) {
            attempts = new PasswordAttempts();
            mAttempts.put(accountName, attempts);
        }
        return attempts;
    }

    public String getAccountPassword() {
        if (getAccountName() == null) {
            return null;
        }

        PasswordItem passwordItem = mPasswords.get(getAccountName());

        return passwordItem != null ? passwordItem.password : null;
    }

    public boolean isPasswordAccepted() {
        return mIsPasswordAccepted || getAccountPassword() == null;
    }

    public void setPasswordAccepted(boolean accepted) {
        mIsPasswordAccepted = accepted;
    }

    /**
     * leanview: the lock this person set on the web for their profile (null: never set there, the
     * TV's own password stays). Locked, the PIN is the hash they set; off, the profile opens freely.
     * Returns true when it changed what the profile needs.
     */
    public boolean applyServerLock(String accountName, ServerProfileLock lock) {
        if (accountName == null || lock == null) {
            return false;
        }
        String hash = lock.locks() ? lock.pinHash : null;
        PasswordItem current = mPasswords.get(accountName);
        if (Helpers.equals(current != null ? current.password : null, hash)) {
            return false;
        }
        mPasswords.put(accountName, new PasswordItem(accountName, hash));
        // Locked on the web while this profile is open: it asks now, not at the next app start.
        if (hash != null && (current == null || current.password == null) && accountName.equals(getAccountName())) {
            mIsPasswordAccepted = false;
        }
        persistState();
        return true;
    }

    public int getLastUnlockMethod() {
        return getLastUnlockMethod(getAccountName());
    }

    public void setLastUnlockMethod(int method) {
        setLastUnlockMethod(getAccountName(), method);
    }

    public int getLastUnlockMethod(String accountName) {
        Integer method = mLastUnlockMethods.get(accountName);
        return method != null ? method : UNLOCK_BY_PHONE;
    }

    public void setLastUnlockMethod(String accountName, int method) {
        mLastUnlockMethods.put(accountName, method);
        persistState();
    }

    private void restoreState() {
        String data = mAppPrefs.getData(ACCOUNTS_DATA);

        String[] split = Helpers.splitData(data);

        mIsSelectAccountOnBootEnabled = Helpers.parseBoolean(split, 0, false);
        // mIsAccountProtectedWithPassword
        // mAccountPassword
        String[] passwords = Helpers.parseArray(split, 3);

        boolean upgraded = false;

        if (passwords != null) {
            for (String passwordSpec : passwords) {
                PasswordItem item = PasswordItem.fromString(passwordSpec);
                // leanview: a password saved in plain text by an older build becomes a hash
                String hashed = PasswordHash.upgrade(item.password);
                upgraded |= !Helpers.equals(hashed, item.password);
                item.password = hashed;
                mPasswords.put(item.accountName, item);
            }
        }

        String[] attempts = Helpers.parseArray(split, 4);

        if (attempts != null) {
            for (String attemptsSpec : attempts) {
                String[] pair = Helpers.splitObj(attemptsSpec);
                if (pair != null && pair.length == 2) {
                    mAttempts.put(Helpers.parseStr(pair[0]), PasswordAttempts.fromString(Helpers.parseStr(pair[1])));
                }
            }
        }

        String[] methods = Helpers.parseArray(split, 5);

        if (methods != null) {
            for (String methodSpec : methods) {
                String[] pair = Helpers.splitObj(methodSpec);
                if (pair != null && pair.length == 2) {
                    mLastUnlockMethods.put(Helpers.parseStr(pair[0]), Helpers.parseInt(pair[1]));
                }
            }
        }

        // leanview: saved by an older build (or nothing saved): the picker opens on opening and after
        // two hours away, chosen 2026-10-30; both can be turned off in Cuentas
        mPickerAwayMs = Helpers.parseLong(split, 6, Long.MIN_VALUE);
        if (mPickerAwayMs == Long.MIN_VALUE) {
            mIsSelectAccountOnBootEnabled = true;
            mPickerAwayMs = AccountPicker.DEFAULT_AWAY_MS;
            upgraded = true;
        }

        if (upgraded) {
            persistState();
        }
    }

    private void persistState() {
        Object[] attempts = new Object[mAttempts.size()];
        int i = 0;
        for (Map.Entry<String, PasswordAttempts> entry : mAttempts.entrySet()) {
            attempts[i++] = Helpers.mergeObj(entry.getKey(), entry.getValue());
        }

        Object[] methods = new Object[mLastUnlockMethods.size()];
        i = 0;
        for (Map.Entry<String, Integer> entry : mLastUnlockMethods.entrySet()) {
            methods[i++] = Helpers.mergeObj(entry.getKey(), entry.getValue());
        }

        mAppPrefs.setData(ACCOUNTS_DATA, Helpers.mergeData(
                mIsSelectAccountOnBootEnabled, null, null, Helpers.mergeArray(mPasswords.values().toArray()),
                Helpers.mergeArray(attempts), Helpers.mergeArray(methods), mPickerAwayMs
        ));
    }

    private String getAccountName() {
        Account account = MediaServiceManager.instance().getSelectedAccount();
        return account != null ? account.getName() : null;
    }

    @Override
    public void onAccountChanged(Account account) {
        mIsPasswordAccepted = false;
    }
}
