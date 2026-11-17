package com.liskovsoft.smartyoutubetv2.common.app.models.errors;

import android.content.Context;

import androidx.annotation.StringRes;

/**
 * leanview: a section that loaded fine and has nothing in it. Just the message: no sad cloud (the
 * fragment draws it only for the error classes) and no button.
 */
public class SectionEmptyMessage implements ErrorFragmentData {
    private final Context mContext;
    private final int mMessageResId;

    public SectionEmptyMessage(Context context, @StringRes int messageResId) {
        mContext = context;
        mMessageResId = messageResId;
    }

    @Override
    public void onAction() {
    }

    @Override
    public String getMessage() {
        return mContext.getString(mMessageResId);
    }

    @Override
    public String getActionText() {
        return null;
    }
}
