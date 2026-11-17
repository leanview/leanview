package com.liskovsoft.smartyoutubetv2.common.leanview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.errors.SectionEmptyMessage;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.IOException;

// leanview: "My videos" of an account that never uploaded anything shows a plain message; a load that
// failed (no answer, an error) keeps the generic error screen.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class SectionLoadTest {
    @Test
    public void anAnsweredEmptyMyVideosGetsTheEmptyMessage() {
        assertEquals(R.string.leanview_my_videos_empty, SectionLoad.emptyMessage(MediaGroup.TYPE_MY_VIDEOS, true, null));
    }

    @Test
    public void noAnswerIsAFailureNotAnEmptySection() {
        // RxHelper.fromNullable turns a null result into an error ("fromNullable result is null"),
        // and a dropped connection arrives as an error too: neither sends a group.
        assertEquals(0, SectionLoad.emptyMessage(MediaGroup.TYPE_MY_VIDEOS, false, null));
        assertEquals(0, SectionLoad.emptyMessage(MediaGroup.TYPE_MY_VIDEOS, false, new IllegalStateException(new IOException("no network"))));
    }

    @Test
    public void anErrorWinsEvenIfAGroupArrivedFirst() {
        assertEquals(0, SectionLoad.emptyMessage(MediaGroup.TYPE_MY_VIDEOS, true, new RuntimeException("boom")));
    }

    @Test
    public void otherSectionsKeepTheirBehavior() {
        assertEquals(0, SectionLoad.emptyMessage(MediaGroup.TYPE_SUBSCRIPTIONS, true, null));
        assertEquals(0, SectionLoad.emptyMessage(MediaGroup.TYPE_HISTORY, true, null));
        assertEquals(0, SectionLoad.emptyMessage(MediaGroup.TYPE_USER_PLAYLISTS, true, null));
    }

    @Test
    public void theMessageHasNoButtonAndSaysWhatIsMissing() {
        Context context = RuntimeEnvironment.getApplication();
        SectionEmptyMessage message = new SectionEmptyMessage(context, R.string.leanview_my_videos_empty);

        assertNull(message.getActionText());
        assertEquals("You haven't uploaded any videos yet", message.getMessage());
    }
}
