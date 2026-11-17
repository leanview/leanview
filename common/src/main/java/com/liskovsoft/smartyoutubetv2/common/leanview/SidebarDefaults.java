package com.liskovsoft.smartyoutubetv2.common.leanview;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;

import java.util.ArrayList;
import java.util.List;

/**
 * leanview: the left menu like YouTube TV's, plus Music (Home, Shorts, Channels, Subscriptions, Music,
 * History, Playlists, My videos, Settings). The other topic sections (Kids, Sports, Live, Gaming,
 * News) start hidden and can be turned on again in Settings > Edit categories. A saved menu is
 * switched only if it is still exactly one this app or upstream created by default (same
 * sections, same order, no pinned channel or playlist), which means the user never touched it.
 * No Android imports: the service only asks here.
 */
public final class SidebarDefaults {
    /**
     * Bumped when the defaults change; saved with the menu so the switch runs once.
     * 1: topic sections hidden, Music included. 2: Music kept, right after Subscriptions.
     */
    public static final int VERSION = 2;

    public static final int[] TOPIC_SECTIONS = {
            MediaGroup.TYPE_KIDS_HOME, MediaGroup.TYPE_SPORTS, MediaGroup.TYPE_LIVE,
            MediaGroup.TYPE_GAMING, MediaGroup.TYPE_NEWS
    };

    /** The menu upstream creates on a fresh install, in order (what version 0 saved). */
    private static final int[] UPSTREAM_DEFAULT = {
            MediaGroup.TYPE_HOME, MediaGroup.TYPE_SHORTS, MediaGroup.TYPE_KIDS_HOME, MediaGroup.TYPE_SPORTS,
            MediaGroup.TYPE_LIVE, MediaGroup.TYPE_GAMING, MediaGroup.TYPE_NEWS, MediaGroup.TYPE_MUSIC,
            MediaGroup.TYPE_CHANNEL_UPLOADS, MediaGroup.TYPE_SUBSCRIPTIONS, MediaGroup.TYPE_HISTORY,
            MediaGroup.TYPE_USER_PLAYLISTS, MediaGroup.TYPE_MY_VIDEOS, MediaGroup.TYPE_SETTINGS
    };

    /** The menu this app created by default in version 1: no topics at all, Music included. */
    private static final int[] V1_DEFAULT = {
            MediaGroup.TYPE_HOME, MediaGroup.TYPE_SHORTS, MediaGroup.TYPE_CHANNEL_UPLOADS,
            MediaGroup.TYPE_SUBSCRIPTIONS, MediaGroup.TYPE_HISTORY, MediaGroup.TYPE_USER_PLAYLISTS,
            MediaGroup.TYPE_MY_VIDEOS, MediaGroup.TYPE_SETTINGS
    };

    /** The menu this app creates by default now (version 2), in order. */
    private static final int[] V2_DEFAULT = {
            MediaGroup.TYPE_HOME, MediaGroup.TYPE_SHORTS, MediaGroup.TYPE_CHANNEL_UPLOADS,
            MediaGroup.TYPE_SUBSCRIPTIONS, MediaGroup.TYPE_MUSIC, MediaGroup.TYPE_HISTORY,
            MediaGroup.TYPE_USER_PLAYLISTS, MediaGroup.TYPE_MY_VIDEOS, MediaGroup.TYPE_SETTINGS
    };

    private SidebarDefaults() {
    }

    public static boolean isTopic(int sectionId) {
        for (int topic : TOPIC_SECTIONS) {
            if (topic == sectionId) {
                return true;
            }
        }

        return false;
    }

    /** Section ids of the menu a fresh install of the hiding flavor gets, in order. */
    public static List<Integer> freshMenu() {
        return toList(V2_DEFAULT);
    }

    /**
     * The menu a saved one has to be replaced with now, or null to leave it alone. Only a menu
     * that is still exactly the default of the version it was saved with is replaced: version 0
     * (upstream's default) or version 1 (this app's previous default, without Music after
     * Subscriptions). A menu the user edited by hand keeps its choices.
     *
     * @param sectionIds section id of every item of the saved menu, in order (-1 for a pinned
     *                   channel or playlist)
     * @param hideTopics what the flavor wants (R.bool.leanview_hide_topic_sections)
     */
    public static List<Integer> migratedMenu(List<Integer> sectionIds, int savedVersion, boolean hideTopics) {
        if (!hideTopics || savedVersion >= VERSION) {
            return null;
        }

        if (savedVersion == 0 && matches(sectionIds, UPSTREAM_DEFAULT)) {
            return freshMenu();
        }

        if (savedVersion == 1 && matches(sectionIds, V1_DEFAULT)) {
            return freshMenu();
        }

        return null;
    }

    private static List<Integer> toList(int[] ids) {
        List<Integer> list = new ArrayList<>();
        for (int id : ids) {
            list.add(id);
        }
        return list;
    }

    private static boolean matches(List<Integer> sectionIds, int[] expected) {
        if (sectionIds == null || sectionIds.size() != expected.length) {
            return false;
        }

        for (int i = 0; i < expected.length; i++) {
            if (sectionIds.get(i) == null || sectionIds.get(i) != expected[i]) {
                return false;
            }
        }

        return true;
    }
}
