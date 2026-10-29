package top.niunaijun.blackbox.utils;

import android.os.Build;

import java.lang.reflect.Method;

/**
 * Volcado de depuración, bajo demanda, del estado que un invitado ve de sí mismo:
 * Build.*, propiedades del sistema y estado del perfil falso. Solo mide; no cambia nada.
 * Se activa con: setprop debug.aiab.dump 1 (antes de abrir la app invitada).
 * Se lee con: logcat -s AiabProbe
 */
public final class IntegrityProbe {
    public static final String TAG = "AiabProbe";
    private static final String TRIGGER = "debug.aiab.dump";
    private static final String[] PROPS = {
            "ro.product.model", "ro.product.brand", "ro.product.manufacturer", "ro.product.device",
            "ro.build.fingerprint", "ro.build.version.release", "ro.build.version.sdk",
            "ro.build.version.security_patch", "ro.build.type", "ro.build.tags",
            "ro.hardware", "ro.boot.verifiedbootstate", "ro.boot.flash.locked",
            "ro.boot.vbmeta.device_state", "ro.debuggable", "ro.secure",
    };

    private IntegrityProbe() {
    }

    public static void dumpIfRequested(String packageName) {
        if ("1".equals(sysprop(TRIGGER))) {
            Slog.i(TAG, dump(packageName));
        }
    }

    /** Texto con el estado visible desde el proceso actual (sin datos personales). */
    public static String dump(String packageName) {
        StringBuilder sb = new StringBuilder("paquete=").append(packageName).append('\n');
        sb.append("Build.MODEL=").append(Build.MODEL).append('\n');
        sb.append("Build.BRAND=").append(Build.BRAND).append('\n');
        sb.append("Build.MANUFACTURER=").append(Build.MANUFACTURER).append('\n');
        sb.append("Build.DEVICE=").append(Build.DEVICE).append('\n');
        sb.append("Build.FINGERPRINT=").append(Build.FINGERPRINT).append('\n');
        sb.append("Build.TAGS=").append(Build.TAGS).append('\n');
        sb.append("Build.TYPE=").append(Build.TYPE).append('\n');
        sb.append("Build.VERSION.RELEASE=").append(Build.VERSION.RELEASE).append('\n');
        sb.append("Build.VERSION.SDK_INT=").append(Build.VERSION.SDK_INT).append('\n');
        sb.append("Build.VERSION.SECURITY_PATCH=").append(Build.VERSION.SECURITY_PATCH).append('\n');
        for (String key : PROPS) {
            sb.append("prop ").append(key).append('=').append(sysprop(key)).append('\n');
        }
        return sb.toString();
    }

    private static String sysprop(String key) {
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            Method m = c.getMethod("get", String.class, String.class);
            return (String) m.invoke(null, key, "");
        } catch (Throwable t) {
            return "<n/d>";
        }
    }
}
