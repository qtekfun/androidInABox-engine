package top.niunaijun.blackbox.core.system.pm.session;

import android.content.Intent;

import java.util.LinkedHashMap;
import java.util.Map;

/** Extras del Intent de estado que PackageInstaller entrega al IntentSender de commit/uninstall. */
public final class InstallStatus {
    public static final int STATUS_SUCCESS = 0;
    public static final int STATUS_FAILURE = 1;
    public static final int STATUS_FAILURE_INVALID = 4;

    // Mismos valores que PackageInstaller.EXTRA_*; algunos son @hide.
    public static final String EXTRA_STATUS = "android.content.pm.extra.STATUS";
    public static final String EXTRA_STATUS_MESSAGE = "android.content.pm.extra.STATUS_MESSAGE";
    public static final String EXTRA_PACKAGE_NAME = "android.content.pm.extra.PACKAGE_NAME";
    public static final String EXTRA_SESSION_ID = "android.content.pm.extra.SESSION_ID";
    public static final String EXTRA_LEGACY_STATUS = "android.content.pm.extra.LEGACY_STATUS";

    private InstallStatus() {
    }

    /** {@code sessionId < 0} omite EXTRA_SESSION_ID (caso de uninstall). */
    public static Map<String, Object> extras(int status, String message, String packageName, int sessionId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(EXTRA_STATUS, status);
        m.put(EXTRA_STATUS_MESSAGE, messageFor(status, message));
        if (packageName != null) m.put(EXTRA_PACKAGE_NAME, packageName);
        if (sessionId >= 0) m.put(EXTRA_SESSION_ID, sessionId);
        // Estado heredado de PackageManager: 1 = INSTALL_SUCCEEDED, -110 = INSTALL_FAILED_INTERNAL_ERROR
        m.put(EXTRA_LEGACY_STATUS, status == STATUS_SUCCESS ? 1 : -110);
        return m;
    }

    static String messageFor(int status, String message) {
        if (message != null && !message.isEmpty()) return message;
        return status == STATUS_SUCCESS ? "INSTALL_SUCCEEDED" : "INSTALL_FAILED";
    }

    public static Intent toIntent(Map<String, Object> extras) {
        Intent intent = new Intent();
        for (Map.Entry<String, Object> e : extras.entrySet()) {
            Object v = e.getValue();
            if (v instanceof Integer) intent.putExtra(e.getKey(), (int) v);
            else if (v instanceof String) intent.putExtra(e.getKey(), (String) v);
        }
        return intent;
    }
}
