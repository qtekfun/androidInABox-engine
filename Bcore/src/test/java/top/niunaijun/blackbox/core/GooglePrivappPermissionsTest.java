package top.niunaijun.blackbox.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GooglePrivappPermissionsTest {
    @Test
    public void gmsRecibePermisosDeGoogle() {
        assertTrue(GooglePrivappPermissions.isGranted("com.google.android.gms",
                "com.google.android.gms.permission.INTERNAL_BROADCAST"));
        assertTrue(GooglePrivappPermissions.isGranted("com.google.android.gsf",
                "com.google.android.providers.settings.permission.WRITE_GSERVICES"));
        assertTrue(GooglePrivappPermissions.isGranted("com.android.vending",
                "android.permission.INSTALL_PACKAGES"));
        assertTrue(GooglePrivappPermissions.isGranted("com.google.android.gms",
                "com.google.android.c2dm.permission.SEND"));
    }

    @Test
    public void otrosPaquetesNoRecibenNada() {
        assertFalse(GooglePrivappPermissions.isGranted("com.example.app",
                "com.google.android.gms.permission.INTERNAL_BROADCAST"));
        assertFalse(GooglePrivappPermissions.isGranted(null, "android.permission.GET_ACCOUNTS"));
    }

    @Test
    public void permisoDesconocidoNoSeConcede() {
        assertFalse(GooglePrivappPermissions.isGranted("com.google.android.gms",
                "android.permission.CAMERA_UNKNOWN_X"));
        assertFalse(GooglePrivappPermissions.isGranted("com.google.android.gms", null));
    }

    @Test
    public void flagsDeSistemaYPrivilegiado() {
        assertTrue((GooglePrivappPermissions.withSystemFlags(0) & 1) != 0);
        assertTrue((GooglePrivappPermissions.withPrivilegedFlags(0x10) & 8) != 0);
        assertTrue((GooglePrivappPermissions.withPrivilegedFlags(0x10) & 0x10) != 0);
    }
}
