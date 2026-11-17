package com.liskovsoft.smartyoutubetv2.common.leanview;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.smartyoutubetv2.common.R;

/**
 * leanview: a section whose load ends with nothing to show is either empty (the server answered and
 * there is nothing in it) or failed (no answer, an error, a network problem). Only the first
 * deserves a plain message; the generic "can't load content" screen stays for the second.
 * "My videos" of a brand channel that never uploaded anything is the empty case.
 */
public final class SectionLoad {
    private SectionLoad() {
    }

    /**
     * @param answered a group arrived from the server (even with no items)
     * @param error    what the load ended with, null if it completed normally
     * @return the message to show instead of the error screen, or 0 to keep the error screen
     */
    public static int emptyMessage(int sectionId, boolean answered, Throwable error) {
        if (!answered || error != null) {
            return 0;
        }

        if (sectionId == MediaGroup.TYPE_MY_VIDEOS) {
            return R.string.leanview_my_videos_empty;
        }

        return 0;
    }
}
