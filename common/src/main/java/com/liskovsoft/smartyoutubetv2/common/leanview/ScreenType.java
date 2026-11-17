package com.liskovsoft.smartyoutubetv2.common.leanview;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * leanview: the person's choice of "Screen type" (Settings > UI): Automatic (detect, the default), TV or Phone.
 * PhoneMode reads it first, so a device that detection gets wrong (a TV box that reports a touchscreen and no
 * leanback; a phone used as a TV) can be forced. Kept in its own small preference file.
 */
public enum ScreenType {
    AUTOMATIC(0), TV(1), PHONE(2);

    private static final String PREFS = "leanview_screen_type";
    private static final String KEY = "type";

    public final int value;

    ScreenType(int value) {
        this.value = value;
    }

    public static ScreenType get(Context context) {
        int stored = prefs(context).getInt(KEY, AUTOMATIC.value);
        for (ScreenType type : values()) {
            if (type.value == stored) {
                return type;
            }
        }
        return AUTOMATIC;
    }

    public static void set(Context context, ScreenType type) {
        prefs(context).edit().putInt(KEY, type.value).commit();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
