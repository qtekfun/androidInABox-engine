package top.niunaijun.blackbox.core.system.notification;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NotificationIdsTest {
    @Test
    public void roundTripWithSeparatorsInGuestId() {
        for (String g : new String[]{"default", "a:b", "x@black-0", "", "::", "bb:1:z:w"}) {
            String real = NotificationIds.scoped(2, "com.example.app", g);
            assertArrayEquals(new String[]{"2", "com.example.app", g}, NotificationIds.split(real));
            assertEquals(g, NotificationIds.guestId(real));
        }
    }

    @Test
    public void nullTagIsEmpty() {
        assertEquals("bb:0:com.a:", NotificationIds.scoped(0, "com.a", null));
        assertEquals("", NotificationIds.guestId("bb:0:com.a:"));
    }

    @Test
    public void prefixDoesNotMatchSimilarPackagesOrUsers() {
        String real = NotificationIds.scoped(1, "com.a.b", "t");
        assertTrue(NotificationIds.belongsTo(real, 1, "com.a.b"));
        assertFalse(NotificationIds.belongsTo(real, 1, "com.a"));
        assertFalse(NotificationIds.belongsTo(real, 11, "com.a.b"));
        assertFalse(NotificationIds.belongsTo(real, 0, "com.a.b"));
        assertFalse(NotificationIds.belongsTo(null, 0, "com.a.b"));
    }

    @Test
    public void foreignIdsAreRejected() {
        assertNull(NotificationIds.split("default"));
        assertNull(NotificationIds.split("default@black-0"));
        assertNull(NotificationIds.split("bb:x:com.a:t"));
        assertNull(NotificationIds.split("bb:1:com.a"));
        assertNull(NotificationIds.split(null));
    }

    @Test
    public void packageGroupHasNoTrailingSeparator() {
        assertEquals("bb:3:com.a", NotificationIds.packageGroup(3, "com.a"));
        assertNull(NotificationIds.split(NotificationIds.packageGroup(3, "com.a")));
    }

    @Test
    public void matchesCoversGroupsAndAllUsers() {
        assertTrue(NotificationIds.matches("bb:1:com.a", 1, "com.a"));
        assertTrue(NotificationIds.matches("bb:1:com.a:x:y", 1, "com.a"));
        assertTrue(NotificationIds.matches("bb:7:com.a:x", -1, "com.a"));
        assertFalse(NotificationIds.matches("bb:7:com.a:x", 1, "com.a"));
        assertFalse(NotificationIds.matches("bb:1:com.ab:x", 1, "com.a"));
        assertFalse(NotificationIds.matches("default", -1, "com.a"));
    }
}
