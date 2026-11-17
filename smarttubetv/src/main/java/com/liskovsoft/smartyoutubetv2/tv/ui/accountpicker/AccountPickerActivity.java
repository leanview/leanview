package com.liskovsoft.smartyoutubetv2.tv.ui.accountpicker;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AccountPickerPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.AccountPickerView;
import com.liskovsoft.smartyoutubetv2.common.leanview.AccountPickerTiles.Tile;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;

import java.util.List;

/**
 * leanview: full-screen "Who's watching?" (2026-10-31): a centred row of round avatars, the
 * focused one grows and gets a white ring, the one in use has a grey ring. Extends LeanbackActivity
 * like the other screens, so AccountPicker still sees it start and stop.
 */
public class AccountPickerActivity extends LeanbackActivity implements AccountPickerView {
    private static final float FOCUS_SCALE = 1.12f;
    private static final float DIM_ALPHA = 0.65f;
    private AccountPickerPresenter mPresenter;
    private LinearLayout mRow;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.leanview_account_picker);
        mRow = findViewById(R.id.account_picker_row);

        mPresenter = AccountPickerPresenter.instance(this);
        mPresenter.setView(this);
        mPresenter.onViewInitialized();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mPresenter != null) {
            mPresenter.onViewDestroyed();
        }
    }

    @Override
    public void showTiles(List<Tile> tiles, int focusIndex) {
        mRow.removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(this);

        for (Tile tile : tiles) {
            View item = inflater.inflate(R.layout.leanview_account_picker_item, mRow, false);
            bind(item, tile);
            mRow.addView(item);
        }

        View focused = mRow.getChildAt(focusIndex);

        if (focused != null) {
            focused.requestFocus();
        }
    }

    @Override
    public void close() {
        finish();
    }

    private void bind(View item, Tile tile) {
        ImageView avatar = item.findViewById(R.id.account_picker_avatar);
        TextView name = item.findViewById(R.id.account_picker_name);
        TextView email = item.findViewById(R.id.account_picker_email);

        switch (tile.kind) {
            case ACCOUNT:
                name.setText(tile.name);
                if (tile.email != null || tile.channel) {
                    email.setText(tile.channel ? getString(R.string.leanview_account_channel) : tile.email);
                    email.setVisibility(View.VISIBLE);
                }
                Glide.with(this)
                        .load(tile.avatarUrl)
                        .circleCrop()
                        .placeholder(R.drawable.leanview_account_picker_person)
                        .error(R.drawable.leanview_account_picker_person)
                        .into(avatar);
                break;
            case NONE:
                name.setText(R.string.dialog_account_none);
                avatar.setImageResource(R.drawable.leanview_account_picker_person);
                break;
            case ADD:
                name.setText(R.string.dialog_add_account);
                avatar.setImageResource(R.drawable.leanview_account_picker_add);
                break;
        }

        // the ring of the one in use; the focus ring is the same drawable's focused state
        item.setSelected(tile.selected);
        setFocusLook(item, name, email, false);
        item.setOnFocusChangeListener((v, hasFocus) -> setFocusLook(v, name, email, hasFocus));
        item.setOnClickListener(v -> mPresenter.onTileClicked(tile));
    }

    private static void setFocusLook(View item, TextView name, TextView email, boolean focused) {
        float alpha = focused ? 1f : DIM_ALPHA;
        float scale = focused ? FOCUS_SCALE : 1f;

        item.animate().scaleX(scale).scaleY(scale).setDuration(120).start();
        name.setAlpha(alpha);
        email.setAlpha(alpha);
    }
}
