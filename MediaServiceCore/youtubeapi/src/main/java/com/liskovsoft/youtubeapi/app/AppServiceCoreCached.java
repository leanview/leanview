package com.liskovsoft.youtubeapi.app;

import androidx.annotation.Nullable;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.youtubeapi.app.models.AppInfo;
import com.liskovsoft.youtubeapi.app.models.ClientData;
import com.liskovsoft.youtubeapi.app.models.cached.AppInfoCached;
import com.liskovsoft.youtubeapi.app.models.cached.ClientDataCached;
import com.liskovsoft.youtubeapi.app.playerdata.PlayerDataExtractor;
import com.liskovsoft.youtubeapi.app.prewarm.CurrentPlayerUrl;
import com.liskovsoft.youtubeapi.app.prewarm.ExtractorRebuild;
import com.liskovsoft.youtubeapi.app.prewarm.PlayerPrewarm;
import com.liskovsoft.youtubeapi.app.prewarm.PrewarmReason;
import com.liskovsoft.youtubeapi.app.prewarm.SolvedPlayers;
import com.liskovsoft.youtubeapi.common.helpers.AppClient;
import com.liskovsoft.youtubeapi.common.helpers.AppConstants;

class AppServiceCoreCached extends AppServiceCore {
    private static final String TAG = AppServiceCoreCached.class.getSimpleName();
    private static final long CACHE_REFRESH_PERIOD_MS = 10 * 60 * 60 * 1_000; // check updated core files every 10 hours
    private AppInfoCached mAppInfo;
    private ClientDataCached mClientData;
    // leanview: volatile and read without a lock by isPlayerCacheActual and isPlayerSolved: the lock is held for the whole solve of a player
    private volatile PlayerDataExtractor mWebPlayerDataExtractor;
    private volatile PlayerDataExtractor mTclPlayerDataExtractor;
    private final SolvedPlayers mSolvedPlayers = new SolvedPlayers();
    private long mAppInfoUpdateTimeMs;
    private final Object mAppInfoSync = new Object();
    private final Object mPlayerSync = new Object();
    private final Object mClientDataSync = new Object();
    private AppClient mRecentClient;

    @Override
    public AppInfo getAppInfo(String userAgent) {
        synchronized (mAppInfoSync) {
            return getAppInfoSync(userAgent);
        }
    }

    private AppInfo getAppInfoSync(String userAgent) {
        if (mAppInfo != null && System.currentTimeMillis() - mAppInfoUpdateTimeMs < CACHE_REFRESH_PERIOD_MS) {
            return mAppInfo;
        }

        Log.d(TAG, "updateAppInfoData");

        String previousPlayerUrl = mAppInfo != null ? mAppInfo.getPlayerUrl() : null;

        AppInfo appInfo = super.getAppInfo(userAgent);

        mAppInfo = AppInfoCached.from(appInfo);
        mAppInfoUpdateTimeMs = System.currentTimeMillis();

        // leanview: YouTube serves another player now: solve it in the background, before the next video asks for it
        String playerUrl = mAppInfo != null ? mAppInfo.getPlayerUrl() : null;
        CurrentPlayerUrl.set(playerUrl); // leanview: lock-free, for the solvers (a late solve is kept only for this player)
        if (previousPlayerUrl != null && playerUrl != null && !previousPlayerUrl.equals(playerUrl)) {
            PlayerPrewarm.instance().request(PrewarmReason.PLAYER_CHANGED, playerUrl);
        }

        return mAppInfo;
    }

    @Override
    public PlayerDataExtractor getPlayerDataExtractor(String playerUrl) {
        return getPlayerDataExtractorSync(null, playerUrl);
    }

    @Override
    public PlayerDataExtractor getPlayerDataExtractor(@Nullable AppClient client) {
        return getPlayerDataExtractorSync(client, getPlayerUrl());
    }

    @Override
    public PlayerDataExtractor getPlayerDataExtractor(@Nullable AppClient client, String playerUrl) {
        return getPlayerDataExtractorSync(client, playerUrl);
    }

    private PlayerDataExtractor getPlayerDataExtractorSync(@Nullable AppClient client, String playerUrl) {
        synchronized (mPlayerSync) {
            return firstValidExtractor(
                    client,
                    playerUrl,
                    check(getData().getAppInfo()) ? getData().getAppInfo().getPlayerUrl() : null,
                    AppConstants.playerUrls.get(0)
            );
        }
    }

    @Override
    protected ClientData getClientData(String clientUrl) {
        synchronized (mClientDataSync) {
            return getClientDataSync(clientUrl);
        }
    }

    private ClientData getClientDataSync(String clientUrl) {
        if (mClientData != null && Helpers.equals(clientUrl, mClientData.getClientUrl())) {
            return mClientData;
        }

        ClientDataCached clientDataCached = getData().getClientData();

        if (clientDataCached != null && Helpers.equals(clientUrl, clientDataCached.getClientUrl())) {
            mClientData = clientDataCached;
            return mClientData;
        }

        Log.d(TAG, "updateClientData");

        ClientData clientData = super.getClientData(clientUrl);

        mClientData = ClientDataCached.from(clientUrl, clientData);

        if (check(mClientData)) {
            getData().setClientData(mClientData);
        }

        return mClientData;
    }

    @Override
    public void invalidateCache() {
        mAppInfo = null;
        // Don't reset Player's cache. It's too heavy to recreate often.
        // Better do it inside MediaServiceData after the update
    }

    @Override
    public boolean isPlayerCacheActual() {
        // leanview: no lock: callers are on the UI thread and mPlayerSync is held while a player is solved (seconds on QuickJS)
        return mWebPlayerDataExtractor != null || mTclPlayerDataExtractor != null;
    }

    @Override
    public boolean isPlayerSolved(@Nullable AppClient client) {
        return mSolvedPlayers.solvedUrl(isTcl(effectiveClient(client))) != null;
    }

    @Nullable
    @Override
    public String prewarmPlayer(@Nullable AppClient client, @Nullable String lateSolvedUrl) {
        AppClient effective = effectiveClient(client);
        String current = getPlayerUrl(); // before mPlayerSync: it may take the app-info lock
        synchronized (mPlayerSync) {
            // An extractor that did not validate is kept by firstValidExtractor so that a failing player is not rebuilt for every
            // request; the prewarm is the one caller that is allowed to try again. A valid fallback player is dropped too when a
            // late solve of the current player is waiting: the rebuild then takes that solve.
            PlayerDataExtractor kept = restoreExtractor(effective);
            if (kept != null && ExtractorRebuild.mustDrop(kept.getPlayerUrl(), kept.validate(), current, lateSolvedUrl)) {
                persistExtractor(effective, null);
                mSolvedPlayers.clear(isTcl(effective));
            }
        }

        refreshCacheIfNeeded(effective);

        String solved = mSolvedPlayers.solvedUrl(isTcl(effective));
        // valid for the player that is current now, not for a fallback url that was tried because the current one did not validate
        return solved != null && Helpers.equals(solved, getPlayerUrl()) ? solved : null;
    }

    private AppClient effectiveClient(@Nullable AppClient client) {
        return client != null ? client : mRecentClient;
    }

    private boolean check(AppInfoCached appInfo) {
        return appInfo != null && appInfo.validate();
    }

    private boolean check(ClientDataCached clientData) {
        return clientData != null && clientData.validate();
    }

    private String getFailedPlayerUrl() {
        return getData().getFailedAppInfo() != null ? getData().getFailedAppInfo().getPlayerUrl() : null;
    }

    private PlayerDataExtractor firstValidExtractor(@Nullable AppClient client, String... playerUrls) {
        if (client == null) {
            client = mRecentClient;
        } else {
            mRecentClient = client;
        }

        int idx = -1;
        final int MAIN = 0;
        final int DATA = 1;
        final int APP_CONST = 2;
        String actualTimestamp = null;
        PlayerDataExtractor playerDataExtractor = restoreExtractor(client);

        if (playerDataExtractor != null && Helpers.equalsAny(playerUrls[MAIN], playerDataExtractor.getPlayerUrl(), getFailedPlayerUrl())) {
            mSolvedPlayers.record(isTcl(client), playerDataExtractor.getPlayerUrl(), playerDataExtractor.validate());
            return playerDataExtractor;
        }

        for (String url : playerUrls) {
            idx++;
            if (url == null) {
                continue;
            }

            playerDataExtractor = super.getPlayerDataExtractor(client, url);
            persistExtractor(client, playerDataExtractor);
            boolean valid = playerDataExtractor.validate();
            mSolvedPlayers.record(isTcl(client), playerDataExtractor.getPlayerUrl(), valid);

            if (valid) {
                switch (idx) {
                    case MAIN:
                        getData().setAppInfo(mAppInfo);
                        getData().setFailedAppInfo(null);
                        break;
                    case DATA:
                    case APP_CONST:
                        getData().setFailedAppInfo(mAppInfo);
                        getData().setAppInfo(null);
                        break;
                }

                if (actualTimestamp != null) {
                    playerDataExtractor.setSignatureTimestamp(actualTimestamp);
                }

                break;
            }

            // Try to fetch the actual timestamp for old players. Needed for history (tracking) and possibly more.
            // NOTE: the older player may not work on newer timestamp
            if (idx == MAIN) {
                actualTimestamp = playerDataExtractor.getSignatureTimestamp();
            }
        }

        return playerDataExtractor;
    }

    private PlayerDataExtractor restoreExtractor(@Nullable AppClient client) {
        return isTcl(client) ? mTclPlayerDataExtractor : mWebPlayerDataExtractor;
    }

    private void persistExtractor(@Nullable AppClient client, PlayerDataExtractor extractor) {
        if (isTcl(client)) {
            mTclPlayerDataExtractor = extractor;
        } else {
            mWebPlayerDataExtractor = extractor;
        }
    }
}
