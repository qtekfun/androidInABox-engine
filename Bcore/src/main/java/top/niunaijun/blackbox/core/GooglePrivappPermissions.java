package top.niunaijun.blackbox.core;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Permisos privilegiados / de firma que los paquetes de Google instalados en el box esperan
 * tener (equivalente a privapp-permissions-google y default-permissions-google de una ROM con
 * GMS). Dentro del box no hay PackageManager real que los conceda, asi que se responden aqui.
 * Solo contiene logica pura para poder probarla en JVM.
 */
public final class GooglePrivappPermissions {
    private GooglePrivappPermissions() {
    }

    /** Prefijos de permisos propios de Google: se conceden por completo a sus paquetes. */
    private static final String[] GOOGLE_PREFIXES = {
            "com.google.android.gms.permission.",
            "com.google.android.c2dm.permission.",
            "com.google.android.providers.gsf.permission.",
            "com.google.android.finsky.permission.",
            "com.google.android.gtalkservice.permission.",
            "com.google.android.googleapps.permission.",
            "com.google.android.gsf.permission.",
            "com.android.vending.",
    };

    /** Permisos exactos (incluidos los de firma/privilegiados de la plataforma). */
    private static final Set<String> EXACT = new HashSet<>(Arrays.asList(
            "com.google.android.providers.settings.permission.READ_GSERVICES",
            "com.google.android.providers.settings.permission.WRITE_GSERVICES",
            "com.google.android.googleapps.permission.GOOGLE_AUTH",
            "com.google.android.googleapps.permission.ACCESS_GOOGLE_PASSWORD",
            "android.permission.GET_ACCOUNTS",
            "android.permission.GET_ACCOUNTS_PRIVILEGED",
            "android.permission.ACCOUNT_MANAGER",
            "android.permission.AUTHENTICATE_ACCOUNTS",
            "android.permission.MANAGE_ACCOUNTS",
            "android.permission.USE_CREDENTIALS",
            "android.permission.READ_PRIVILEGED_PHONE_STATE",
            "android.permission.READ_PHONE_STATE",
            "android.permission.READ_CALL_LOG",
            "android.permission.WRITE_SECURE_SETTINGS",
            "android.permission.WRITE_SETTINGS",
            "android.permission.INTERACT_ACROSS_USERS",
            "android.permission.INTERACT_ACROSS_USERS_FULL",
            "android.permission.INSTALL_PACKAGES",
            "android.permission.DELETE_PACKAGES",
            "android.permission.REQUEST_INSTALL_PACKAGES",
            "android.permission.REQUEST_DELETE_PACKAGES",
            "android.permission.INSTALL_LOCATION_PROVIDER",
            "android.permission.CHANGE_DEVICE_IDLE_TEMP_WHITELIST",
            "android.permission.MANAGE_DEVICE_ADMINS",
            "android.permission.MANAGE_USERS",
            "android.permission.GET_APP_OPS_STATS",
            "android.permission.READ_LOGS",
            "android.permission.DUMP",
            "android.permission.PACKAGE_USAGE_STATS",
            "android.permission.PACKAGE_VERIFICATION_AGENT",
            "android.permission.UPDATE_APP_OPS_STATS",
            "android.permission.MODIFY_PHONE_STATE",
            "android.permission.SET_PREFERRED_APPLICATIONS",
            "android.permission.OVERRIDE_WIFI_CONFIG",
            "android.permission.CONNECTIVITY_INTERNAL",
            "android.permission.LOCAL_MAC_ADDRESS",
            "android.permission.MANAGE_APP_OPS_MODES",
            "android.permission.MASTER_CLEAR",
            "android.permission.READ_DEVICE_CONFIG",
            "android.permission.WRITE_DEVICE_CONFIG",
            "android.permission.MANAGE_ACTIVITY_TASKS",
            "android.permission.START_ACTIVITIES_FROM_BACKGROUND",
            "android.permission.RECEIVE_BOOT_COMPLETED",
            "android.permission.WAKE_LOCK",
            "android.permission.FOREGROUND_SERVICE",
            "android.permission.USE_EXACT_ALARM",
            "android.permission.SCHEDULE_EXACT_ALARM",
            "android.permission.REQUEST_COMPANION_RUN_IN_BACKGROUND",
            "android.permission.REQUEST_COMPANION_USE_DATA_IN_BACKGROUND",
            "android.permission.QUERY_ALL_PACKAGES",
            "android.permission.CHANGE_COMPONENT_ENABLED_STATE",
            "android.permission.GET_PACKAGE_SIZE",
            "android.permission.CLEAR_APP_CACHE"
    ));

    /** Paquetes de Google que reciben estos permisos dentro del box. */
    private static final Set<String> PACKAGES = new HashSet<>(Arrays.asList(
            GmsCore.GMS_PKG,
            GmsCore.GSF_PKG,
            GmsCore.VENDING_PKG,
            "com.google.android.gsf.login",
            "com.google.android.partnersetup",
            "com.google.android.configupdater",
            "com.google.android.setupwizard",
            "com.google.android.backuptransport",
            "com.google.android.syncadapters.contacts",
            "com.google.android.syncadapters.calendar"
    ));

    /** El paquete es uno de los de Google que se tratan como privilegiados. */
    public static boolean isGooglePackage(String packageName) {
        return packageName != null && PACKAGES.contains(packageName);
    }

    /** El permiso esta en la tabla de concesiones para paquetes de Google. */
    public static boolean isGrantedPermission(String permission) {
        if (permission == null) return false;
        if (EXACT.contains(permission)) return true;
        for (String prefix : GOOGLE_PREFIXES) {
            if (permission.startsWith(prefix)) return true;
        }
        return false;
    }

    /** {@code true} si {@code packageName} es un paquete de Google y {@code permission} esta concedido. */
    public static boolean isGranted(String packageName, String permission) {
        return isGooglePackage(packageName) && isGrantedPermission(permission);
    }

    /** ApplicationInfo.FLAG_SYSTEM. */
    static final int FLAG_SYSTEM = 1;
    /** ApplicationInfo.FLAG_UPDATED_SYSTEM_APP. */
    static final int FLAG_UPDATED_SYSTEM_APP = 1 << 7;
    /** ApplicationInfo.PRIVATE_FLAG_PRIVILEGED (oculto). */
    static final int PRIVATE_FLAG_PRIVILEGED = 1 << 3;

    /** Flags publicos de un paquete de Google tratado como app de sistema. */
    public static int withSystemFlags(int flags) {
        return flags | FLAG_SYSTEM | FLAG_UPDATED_SYSTEM_APP;
    }

    /** privateFlags de un paquete de Google tratado como privilegiado. */
    public static int withPrivilegedFlags(int privateFlags) {
        return privateFlags | PRIVATE_FLAG_PRIVILEGED;
    }
}
