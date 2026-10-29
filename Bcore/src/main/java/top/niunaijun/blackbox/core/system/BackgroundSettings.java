package top.niunaijun.blackbox.core.system;

import java.io.File;
import java.io.IOException;

/**
 * Ajustes de segundo plano compartidos entre el proceso principal y {@code :black} (spec 04).
 * Se guardan como ficheros marcador (no SharedPreferences) porque dos procesos no comparten su
 * cache. "Iniciar con el sistema" esta activo por defecto (R04.7); la marca existe si esta desactivado.
 */
public final class BackgroundSettings {
    static final String BOOT_DISABLED = "bb_boot_disabled";

    private BackgroundSettings() {
    }

    public static boolean isStartOnBoot(File filesDir) {
        return !new File(filesDir, BOOT_DISABLED).exists();
    }

    public static void setStartOnBoot(File filesDir, boolean enabled) {
        File f = new File(filesDir, BOOT_DISABLED);
        if (enabled) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        } else {
            try {
                //noinspection ResultOfMethodCallIgnored
                f.createNewFile();
            } catch (IOException ignored) {
            }
        }
    }

    /** true si la accion es una de las que reinician el motor (arranque o actualizacion del host). */
    public static boolean isRestartAction(String action) {
        return "android.intent.action.BOOT_COMPLETED".equals(action)
                || "android.intent.action.MY_PACKAGE_REPLACED".equals(action);
    }
}
