package top.niunaijun.blackbox.core.system.pm.session;

import android.content.pm.PackageInstaller;

import java.lang.reflect.Field;

/** Lectura/escritura por reflexión de los campos @hide de SessionParams y SessionInfo. */
final class SessionParamsReflect {
    private SessionParamsReflect() {
    }

    static void readParams(Object params, SessionRecord rec) {
        if (params == null) return;
        Object pkg = get(params, "appPackageName");
        if (pkg instanceof String) rec.appPackageName = (String) pkg;
        Object label = get(params, "appLabel");
        if (label != null) rec.appLabel = label.toString();
        Object mode = get(params, "mode");
        if (mode instanceof Integer) rec.mode = (Integer) mode;
        Object flags = get(params, "installFlags");
        if (flags instanceof Integer) rec.installFlags = (Integer) flags;
        Object size = get(params, "sizeBytes");
        if (size instanceof Long) rec.sizeBytes = (Long) size;
    }

    static PackageInstaller.SessionInfo toInfo(SessionRecord rec, int hostUserId) {
        PackageInstaller.SessionInfo info = newInfo();
        if (info == null) return null;
        set(info, "sessionId", rec.id);
        set(info, "userId", hostUserId);
        set(info, "installerPackageName", rec.installerPackage);
        set(info, "appPackageName", rec.appPackageName);
        set(info, "appLabel", rec.appLabel);
        set(info, "mode", rec.mode);
        set(info, "progress", rec.progress);
        set(info, "sealed", rec.sealed);
        set(info, "active", true);
        set(info, "sizeBytes", rec.sizeBytes);
        return info;
    }

    private static PackageInstaller.SessionInfo newInfo() {
        try {
            java.lang.reflect.Constructor<PackageInstaller.SessionInfo> c =
                    PackageInstaller.SessionInfo.class.getDeclaredConstructor();
            c.setAccessible(true);
            return c.newInstance();
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object get(Object o, String name) {
        try {
            Field f = o.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(o);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void set(Object o, String name, Object value) {
        try {
            Field f = o.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(o, value);
        } catch (Throwable ignored) {
            // campo ausente en esta versión de Android
        }
    }
}
