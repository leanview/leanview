package com.liskovsoft.smartyoutubetv2.common.leanview;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

// 2026-11-08: Alice watches as "Carol", a channel (brand account) with no email, and the server
// profiles were keyed by email only: connecting from "Carol" saved a connection with no owner, which
// never syncs. A channel is told apart by its page id instead.
public class ProfileKeyTest {
    @Test
    public void aPersonalAccountIsItsEmail() {
        assertEquals("Alice@example.com", ServerSync.profileKey("Alice@example.com", "page123"));
    }

    @Test
    public void aChannelWithNoEmailIsItsPageId() {
        String key = ServerSync.profileKey(null, "page123");
        assertEquals("page:page123", key);
        assertTrue(ServerSync.isChannelKey(key));
    }

    @Test
    public void twoChannelsAreTwoProfiles() {
        assertNotEquals(ServerSync.profileKey(null, "pageA"), ServerSync.profileKey(null, "pageB"));
    }

    @Test
    public void noEmailAndNoPageIdIsNobody() {
        assertNull(ServerSync.profileKey(null, null));
        assertNull(ServerSync.profileKey(null, ""));
    }
}
