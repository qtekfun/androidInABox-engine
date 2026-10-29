package top.niunaijun.blackbox.core.system.notification;

import android.app.Notification;
import android.os.UserHandle;
import android.service.notification.StatusBarNotification;

import java.lang.reflect.Constructor;

import top.niunaijun.blackbox.utils.Slog;

/**
 * Construye el {@code StatusBarNotification} que ve un escucha invitado: paquete, id y tag del
 * invitado (no los reales con prefijo {@code bb:}) y la notificacion tal como la publico la app.
 * El constructor es oculto y cambia entre versiones, por eso se resuelve por reflexion.
 */
final class GuestStatusBarNotification {
    private static final String TAG = "GuestSBN";

    private GuestStatusBarNotification() {
    }

    static StatusBarNotification create(String pkg, int id, String tag, int uid,
                                        Notification notification, UserHandle user, long postTime) {
        try {
            // Android 10 en adelante: (pkg, opPkg, id, tag, uid, initialPid, notification, user, overrideGroupKey, postTime)
            Constructor<StatusBarNotification> c = StatusBarNotification.class.getDeclaredConstructor(
                    String.class, String.class, int.class, String.class, int.class, int.class,
                    Notification.class, UserHandle.class, String.class, long.class);
            c.setAccessible(true);
            return c.newInstance(pkg, pkg, id, tag, uid, 0, notification, user, null, postTime);
        } catch (NoSuchMethodException ignored) {
            // continua con la firma antigua
        } catch (Throwable t) {
            Slog.w(TAG, "SBN (firma nueva) fallo: " + t);
            return null;
        }
        try {
            // Android 8 y 9: lleva ademas un int score entre initialPid y notification
            Constructor<StatusBarNotification> c = StatusBarNotification.class.getDeclaredConstructor(
                    String.class, String.class, int.class, String.class, int.class, int.class, int.class,
                    Notification.class, UserHandle.class, long.class);
            c.setAccessible(true);
            return c.newInstance(pkg, pkg, id, tag, uid, 0, 0, notification, user, postTime);
        } catch (Throwable t) {
            Slog.w(TAG, "SBN (firma antigua) fallo: " + t);
            return null;
        }
    }
}
