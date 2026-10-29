package top.niunaijun.blackbox.utils.compat;

import android.os.Build;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

import top.niunaijun.blackbox.utils.Slog;

/**
 * Desbloquea las APIs ocultas con HiddenApiBypass (exención "L").
 */
public final class HiddenApiGate {
    private static final String TAG = "HiddenApiGate";

    private HiddenApiGate() {
    }

    public static boolean unlock() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return true;
        }
        try {
            return HiddenApiBypass.setHiddenApiExemptions("L");
        } catch (Throwable t) {
            Slog.w(TAG, "No se pudo desbloquear las APIs ocultas: " + t);
            return false;
        }
    }
}
