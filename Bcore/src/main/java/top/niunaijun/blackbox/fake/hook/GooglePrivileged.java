package top.niunaijun.blackbox.fake.hook;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.core.GooglePrivappPermissions;
import top.niunaijun.blackbox.utils.Slog;

/**
 * GMS y sus apps se comportan como apps de sistema privilegiadas y llaman a API que el sistema real solo
 * concede a system_server o a apps con permisos privilegiados (usuarios, datos de SIM, administración...).
 * Dentro del box, cuando el sistema real responde con SecurityException a una app de Google, se devuelve el
 * valor por defecto del tipo, como haría un sistema que no tiene ese dato, en lugar de tumbar la app.
 */
public final class GooglePrivileged {
    private static final String TAG = "GooglePrivileged";
    private static final Set<String> LOGGED = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private GooglePrivileged() {
    }

    static boolean shouldSoften(Method method, Throwable cause) {
        String pkg = BActivityThread.getAppPackageName();
        if (!GooglePrivappPermissions.isGooglePackage(pkg)) {
            return false;
        }
        String key = method.getDeclaringClass().getSimpleName() + "." + method.getName();
        if (LOGGED.add(key)) {
            Slog.w(TAG, "SecurityException del sistema real en " + key + " para " + pkg + ": valor por defecto (" + cause.getMessage() + ")");
        }
        return true;
    }

    static Object defaultValue(Class<?> type) {
        if (type == void.class) return null;
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        if (type == short.class) return (short) 0;
        if (type == byte.class) return (byte) 0;
        if (type == char.class) return (char) 0;
        if (type.isArray()) return java.lang.reflect.Array.newInstance(type.getComponentType(), 0);
        return null;
    }
}
