package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.reactivex.Observable;

import static org.junit.Assert.assertEquals;

// leanview: a row of the TV (a channel's videos, the home) shows each video's title as its creator wrote
// it even when the row also holds a playlist or a channel, or a video YouTube has no oEmbed title for.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class UnlocalizedTitleProcessorTest {
    private Context mContext;

    @Before
    public void setUp() throws Exception {
        mContext = RuntimeEnvironment.getApplication();
        forget(MainUIData.class);
        forget(AppPrefs.class);
    }

    private static void forget(Class<?> singleton) throws Exception {
        Field instance = singleton.getDeclaredField("sInstance");
        instance.setAccessible(true);
        instance.set(null, null);
    }

    private static Video video(String videoId, String title) {
        Video video = new Video();
        video.videoId = videoId;
        video.title = title;
        return video;
    }

    @Test
    public void everyVideoOfTheRowGetsItsOriginalTitle() {
        Video translated = video("RE3_PGwDwhw", "¡Pierdan 68 kg Juntos y Ganen 250,000 Dólares!");
        Video playlist = video(null, "Mix");
        Video noEmbed = video("noembed0001", "Video sin embed");
        Video alsoTranslated = video("zCA-brcFeAU", "Construí una Ciudad");
        VideoGroup row = VideoGroup.from(new ArrayList<>(Arrays.asList(translated, playlist, noEmbed, alsoTranslated)));
        List<Video> ready = new ArrayList<>();

        UnlocalizedTitleProcessor processor = new UnlocalizedTitleProcessor(mContext, ready::add, videoId ->
                videoId.equals("noembed0001")
                        ? Observable.error(new IllegalStateException("fromNullable result is null"))
                        : Observable.just("Original " + videoId));
        processor.process(row);

        assertEquals("Original RE3_PGwDwhw", translated.getTitle());
        assertEquals("Original zCA-brcFeAU", alsoTranslated.getTitle());
        assertEquals("Video sin embed", noEmbed.getTitle());
        assertEquals(Arrays.asList(translated, alsoTranslated), ready);
    }
}
