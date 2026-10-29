package top.niunaijun.blackbox.utils.compat;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.utils.Slog;

/**
 * Soporte de &lt;activity-alias&gt;. Semántica de AOSP: el Intent conserva el
 * ComponentName del alias, ActivityInfo.name es el alias y ActivityInfo.targetActivity
 * es la clase real; ActivityThread instancia targetActivity cuando no es null.
 * Aquí nos aseguramos de que el ActivityInfo entregado al ActivityThread la lleve.
 */
public final class ActivityAliasCompat {
    private static final String TAG = "ActivityAliasCompat";

    private ActivityAliasCompat() {
    }

    /** Clase que hay que instanciar: el destino del alias si existe, si no el propio nombre. */
    public static String effectiveClassName(String name, String targetActivity) {
        if (targetActivity != null && !targetActivity.isEmpty()) {
            return targetActivity;
        }
        return name;
    }

    /** Rellena info.targetActivity desde el gestor de paquetes virtual si falta. */
    public static void ensureTarget(ActivityInfo info) {
        if (info == null || info.targetActivity != null || info.packageName == null || info.name == null) {
            return;
        }
        String target = lookupTarget(info.packageName, info.name);
        if (target != null) {
            Slog.d(TAG, "alias " + info.name + " -> " + target);
            info.targetActivity = target;
        }
    }

    /** Destino del alias {@code pkg/name} o null si no es un alias (o no se puede resolver). */
    public static String lookupTarget(String pkg, String name) {
        try {
            ActivityInfo ai = BlackBoxCore.getBPackageManager()
                    .getActivityInfo(new ComponentName(pkg, name), 0, BActivityThread.getUserId());
            return ai == null ? null : ai.targetActivity;
        } catch (Throwable e) {
            Slog.w(TAG, "lookupTarget failed: " + e);
            return null;
        }
    }
}
