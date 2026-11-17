package com.liskovsoft.smartyoutubetv2.common.app.views;

public interface SignInView {
    void showCode(String userCode, String signInUrl);
    void showCode(String userCode, String signInUrl, String fullSignInUrl);
    /** leanview: the same screen asking the owner of a locked profile to let this TV in. */
    void showUnlock(String code, String description, String qrDataUri, String actionText);
    void close();
}
