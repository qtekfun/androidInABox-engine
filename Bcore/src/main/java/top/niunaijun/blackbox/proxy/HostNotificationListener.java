package top.niunaijun.blackbox.proxy;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import top.niunaijun.blackbox.core.system.notification.RealBridgeSettings;
import top.niunaijun.blackbox.fake.frameworks.BNotificationManager;
import top.niunaijun.blackbox.utils.Slog;

/**
 * Servicio de escucha REAL del host (spec 06, 3.4): lleva las notificaciones del sistema que el
 * usuario eligio (puente activado y paquete en la lista blanca, ver {@link RealBridgeSettings}) a
 * los escuchas invitados del box. Solo funciona si el usuario concede "Acceso a notificaciones" al
 * host en los ajustes del sistema. Sin puente activado no reenvia nada y no guarda ni registra el
 * contenido de ninguna notificacion.
 */
public class HostNotificationListener extends NotificationListenerService {
    private static final String TAG = "HostNotifListener";

    private RealBridgeSettings settings() {
        return new RealBridgeSettings(getFilesDir(), getPackageName());
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || !settings().shouldForward(sbn.getPackageName())) {
            return;
        }
        try {
            BNotificationManager.get().onRealNotificationPosted(sbn);
        } catch (Throwable t) {
            Slog.w(TAG, "No se pudo reenviar al box: " + t);
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn == null || !settings().shouldForward(sbn.getPackageName())) {
            return;
        }
        try {
            BNotificationManager.get().onRealNotificationRemoved(sbn);
        } catch (Throwable t) {
            Slog.w(TAG, "No se pudo reenviar al box: " + t);
        }
    }
}
