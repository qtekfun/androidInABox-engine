package top.niunaijun.blackbox.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NearbyPermissionsTest {
    @Test
    public void reconoceLosPermisosDeDispositivosCercanos() {
        assertTrue(NearbyPermissions.isNearbyDevices("android.permission.BLUETOOTH_SCAN"));
        assertTrue(NearbyPermissions.isNearbyDevices("android.permission.BLUETOOTH_CONNECT"));
        assertTrue(NearbyPermissions.isNearbyDevices("android.permission.BLUETOOTH_ADVERTISE"));
        assertTrue(NearbyPermissions.isNearbyDevices("android.permission.NEARBY_WIFI_DEVICES"));
        assertFalse(NearbyPermissions.isNearbyDevices("android.permission.BLUETOOTH"));
        assertFalse(NearbyPermissions.isNearbyDevices(null));
    }

    @Test
    public void versionDeAparicion() {
        assertEquals(31, NearbyPermissions.sinceSdk("android.permission.BLUETOOTH_SCAN"));
        assertEquals(33, NearbyPermissions.sinceSdk("android.permission.NEARBY_WIFI_DEVICES"));
    }
}
