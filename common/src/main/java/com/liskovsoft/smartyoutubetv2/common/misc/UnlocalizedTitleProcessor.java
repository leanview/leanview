package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;
import android.util.Pair;

import com.liskovsoft.mediaserviceinterfaces.MediaItemService;
import com.liskovsoft.mediaserviceinterfaces.ServiceManager;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.prefs.common.DataChangeBase.OnDataChange;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;

public class UnlocalizedTitleProcessor implements OnDataChange, BrowseProcessor {
    private static final String TAG = UnlocalizedTitleProcessor.class.getSimpleName();
    private final OnItemReady mOnItemReady;
    private final TitleLookup mTitleLookup;
    private final MainUIData mMainUIData;
    private boolean mIsUnlocalizedTitlesEnabled;
    private Disposable mResult;

    interface TitleLookup {
        Observable<String> lookUp(String videoId);
    }

    public UnlocalizedTitleProcessor(Context context, OnItemReady onItemReady) {
        this(context, onItemReady, lookUpWith(YouTubeServiceManager.instance()));
    }

    UnlocalizedTitleProcessor(Context context, OnItemReady onItemReady, TitleLookup titleLookup) {
        mOnItemReady = onItemReady;
        mTitleLookup = titleLookup;
        mMainUIData = MainUIData.instance(context);
        mMainUIData.setOnChange(this);
        initData();
    }

    private static TitleLookup lookUpWith(ServiceManager service) {
        MediaItemService itemService = service.getMediaItemService();
        return itemService::getUnlocalizedTitleObserve;
    }

    @Override
    public void onDataChange() {
        initData();
    }

    private void initData() {
        mIsUnlocalizedTitlesEnabled = mMainUIData.isUnlocalizedTitlesEnabled();
    }

    @Override
    public void process(VideoGroup videoGroup) {
        if (!mIsUnlocalizedTitlesEnabled || videoGroup == null || videoGroup.isEmpty()) {
            return;
        }

        List<String> videoIds = getVideoIds(videoGroup);
        mResult = Observable.fromIterable(videoIds)
                // leanview: one video without an oEmbed title (embedding off, removed) must not end the
                // lookups of the rest of the row
                .flatMap(videoId -> mTitleLookup.lookUp(videoId)
                        .map(newTitle -> new Pair<>(videoId, newTitle))
                        .onErrorResumeNext(Observable.empty()))
                .subscribe(title -> {
                    Video video = videoGroup.findVideoById(title.first);
                    if (video == null) {
                        return;
                    }
                    video.deArrowTitle = title.second; // sometimes player titles localized while cards not
                    if (!Helpers.equals(video.title, video.deArrowTitle)) {
                        mOnItemReady.onItemReady(video);
                    }
                },
                error -> {
                    Log.e(TAG, "Unlocalized title: Cannot process the video", error);
                });
    }

    @Override
    public void dispose() {
        RxHelper.disposeActions(mResult);
    }

    private List<String> getVideoIds(VideoGroup videoGroup) {
        List<String> result = new ArrayList<>();

        for (Video video : videoGroup.getVideos()) {
            // leanview: playlists and channels in a row have no video id, and a null here ends every lookup
            if (video.deArrowProcessed || video.videoId == null) {
                continue;
            }
            video.deArrowProcessed = true;
            result.add(video.videoId);
        }

        return result;
    }
}
