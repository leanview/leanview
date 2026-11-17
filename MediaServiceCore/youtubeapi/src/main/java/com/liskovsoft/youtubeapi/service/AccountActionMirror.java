package com.liskovsoft.youtubeapi.service;

/**
 * leanview: lets the app repeat what the signed-in Google account just did (subscribe,
 * unsubscribe) on the user's own server. This module cannot see the app, so the app registers a
 * listener; it is called after the request reached YouTube (whose answer upstream ignores), from
 * the thread that ran it: what is mirrored is what the person did.
 */
public final class AccountActionMirror {
    public interface Listener {
        void onSubscribed(String channelId);

        void onUnsubscribed(String channelId);
    }

    private static volatile Listener sListener;

    private AccountActionMirror() {
    }

    public static void setListener(Listener listener) {
        sListener = listener;
    }

    static void subscribed(String channelId) {
        Listener listener = sListener;
        if (listener != null && channelId != null) {
            listener.onSubscribed(channelId);
        }
    }

    static void unsubscribed(String channelId) {
        Listener listener = sListener;
        if (listener != null && channelId != null) {
            listener.onUnsubscribed(channelId);
        }
    }
}
