package top.niunaijun.blackbox.core.system.notification;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Process;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.core.system.am.BActivityManagerService;
import top.niunaijun.blackbox.utils.Slog;

/**
 * Alimentacion de escucha de notificaciones para apps invitadas (spec 06, 3.3): cuando un invitado
 * declara un {@code NotificationListenerService} y el usuario lo autoriza en el box, se enlaza
 * dentro del box (como cualquier servicio invitado, via el stub del proceso) y recibe
 * {@code onNotificationPosted/Removed} de lo que publican las demas apps del box. Opcionalmente
 * recibe tambien notificaciones del sistema real que reenvia {@code HostNotificationListener}
 * (puente desactivado por defecto, ver {@link RealBridgeSettings}).
 *
 * No toca el servicio de notificaciones real: los oyentes invitados no se registran en el sistema.
 * El estado de las notificaciones activas vive en memoria; si {@code :black} muere, los oyentes se
 * vuelven a enlazar al arrancar y empiezan sin historial.
 */
public class BNotificationListenerService {
    public static final String TAG = "BNotifListener";
    /** NotificationListenerService.SERVICE_INTERFACE. */
    private static final String SERVICE_INTERFACE = "android.service.notification.NotificationListenerService";

    private static final BNotificationListenerService sService = new BNotificationListenerService();

    public static BNotificationListenerService get() {
        return sService;
    }

    private final Object mLock = new Object();
    private final Map<String, StatusBarNotification> mActive = new LinkedHashMap<>();
    private final Map<String, Connection> mConnections = new HashMap<>();
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private ListenerAccessStore mAccess;
    private RealBridgeSettings mBridge;

    public synchronized ListenerAccessStore access() {
        if (mAccess == null) {
            mAccess = new ListenerAccessStore(BlackBoxCore.getContext().getFilesDir());
        }
        return mAccess;
    }

    public synchronized RealBridgeSettings bridge() {
        if (mBridge == null) {
            mBridge = new RealBridgeSettings(BlackBoxCore.getContext().getFilesDir(), BlackBoxCore.getHostPkg());
        }
        return mBridge;
    }

    /** Reenlaza al arrancar los oyentes que el usuario autorizo antes. */
    public void systemReady() {
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                for (String line : access().listAll()) {
                    int tab = line.indexOf('\t');
                    try {
                        connect(Integer.parseInt(line.substring(0, tab)), line.substring(tab + 1));
                    } catch (Throwable t) {
                        Slog.w(TAG, "No se pudo reenlazar " + line + ": " + t);
                    }
                }
            }
        });
    }

    // ---- acceso ----

    public boolean isGranted(int userId, String component) {
        return access().isGranted(userId, component);
    }

    public boolean isPackageGranted(int userId, String packageName) {
        return access().isPackageGranted(userId, packageName);
    }

    public List<String> getEnabled(int userId) {
        return access().list(userId);
    }

    /** Concede o retira el acceso y enlaza o desenlaza el servicio invitado. */
    public void setGranted(final int userId, final String component, boolean granted) {
        access().setGranted(userId, component, granted);
        if (granted) {
            mExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    connect(userId, component);
                }
            });
        } else {
            disconnect(userId, component);
        }
    }

    // ---- fuente de datos ----

    /** Llamado por {@link BNotificationManagerService} con la notificacion tal como la publico el invitado. */
    public void onGuestPosted(int userId, String pkg, int id, String tag, Notification notification) {
        StatusBarNotification sbn = GuestStatusBarNotification.create(pkg, id, tag, Process.myUid(),
                notification, Process.myUserHandle(), System.currentTimeMillis());
        if (sbn == null) {
            return;
        }
        synchronized (mLock) {
            mActive.put(guestKey(userId, pkg, tag, id), sbn);
        }
        dispatch(userId, sbn, true);
    }

    public void onGuestRemoved(int userId, String pkg, int id, String tag) {
        StatusBarNotification sbn;
        synchronized (mLock) {
            sbn = mActive.remove(guestKey(userId, pkg, tag, id));
        }
        if (sbn != null) {
            dispatch(userId, sbn, false);
        }
    }

    public void onGuestRemovedAll(int userId, String pkg) {
        List<StatusBarNotification> removed = new ArrayList<>();
        synchronized (mLock) {
            String prefix = userId + "|" + pkg + "|";
            for (String key : new ArrayList<>(mActive.keySet())) {
                if (key.startsWith(prefix)) {
                    removed.add(mActive.remove(key));
                }
            }
        }
        for (StatusBarNotification sbn : removed) {
            dispatch(userId, sbn, false);
        }
    }

    /** Notificacion del sistema real (puente opcional). Se vuelve a comprobar el filtro aqui. */
    public void onRealPosted(StatusBarNotification sbn) {
        if (sbn == null || !bridge().shouldForward(sbn.getPackageName())) {
            return;
        }
        synchronized (mLock) {
            mActive.put("real|" + sbn.getKey(), sbn);
        }
        dispatch(-1, sbn, true);
    }

    public void onRealRemoved(StatusBarNotification sbn) {
        if (sbn == null || !bridge().shouldForward(sbn.getPackageName())) {
            return;
        }
        synchronized (mLock) {
            mActive.remove("real|" + sbn.getKey());
        }
        dispatch(-1, sbn, false);
    }

    /** Activas visibles para un oyente del usuario: las del box de ese usuario y las reales reenviadas. */
    public List<StatusBarNotification> getActive(int userId) {
        List<StatusBarNotification> out = new ArrayList<>();
        synchronized (mLock) {
            String prefix = userId + "|";
            for (Map.Entry<String, StatusBarNotification> e : mActive.entrySet()) {
                if (e.getKey().startsWith(prefix) || e.getKey().startsWith("real|")) {
                    out.add(e.getValue());
                }
            }
        }
        return out;
    }

    /** true si es una notificacion del box (la cancelacion pedida por un oyente solo aplica a esas). */
    public boolean isGuestNotification(int userId, String pkg, String tag, int id) {
        synchronized (mLock) {
            return mActive.containsKey(guestKey(userId, pkg, tag, id));
        }
    }

    private static String guestKey(int userId, String pkg, String tag, int id) {
        return userId + "|" + pkg + "|" + (tag == null ? "" : tag) + "|" + id;
    }

    // ---- enlace con los servicios invitados ----

    private static String connKey(int userId, String component) {
        return userId + "\t" + component;
    }

    private void connect(int userId, String component) {
        final String key = connKey(userId, component);
        synchronized (mLock) {
            if (mConnections.containsKey(key)) {
                return;
            }
        }
        ComponentName cn = ComponentName.unflattenFromString(component);
        if (cn == null) {
            return;
        }
        Intent target = new Intent(SERVICE_INTERFACE).setComponent(cn);
        Intent stub;
        try {
            stub = BActivityManagerService.get().bindService(target, null, null, userId);
        } catch (Throwable t) {
            Slog.w(TAG, "No se pudo resolver el oyente " + component + ": " + t);
            return;
        }
        if (stub == null || stub.getComponent() == null
                || !BlackBoxCore.getHostPkg().equals(stub.getComponent().getPackageName())) {
            Slog.w(TAG, "Oyente no resuelto en el box: " + component);
            return;
        }
        Connection conn = new Connection(userId, component);
        boolean ok = false;
        try {
            ok = BlackBoxCore.getContext().bindService(stub, conn, Context.BIND_AUTO_CREATE);
        } catch (Throwable t) {
            Slog.w(TAG, "bindService del oyente fallo: " + t);
        }
        if (ok) {
            synchronized (mLock) {
                mConnections.put(key, conn);
            }
        }
        Slog.i(TAG, "oyente " + component + " (usuario " + userId + ") enlazado=" + ok);
    }

    private void disconnect(int userId, String component) {
        Connection conn;
        synchronized (mLock) {
            conn = mConnections.remove(connKey(userId, component));
        }
        if (conn != null) {
            try {
                BlackBoxCore.getContext().unbindService(conn);
            } catch (Throwable ignored) {
            }
        }
    }

    private void dispatch(final int userId, final StatusBarNotification sbn, final boolean posted) {
        final List<Connection> targets = new ArrayList<>();
        synchronized (mLock) {
            for (Connection c : mConnections.values()) {
                if (c.channel != null && (userId < 0 || c.userId == userId)) {
                    targets.add(c);
                }
            }
        }
        if (targets.isEmpty()) {
            return;
        }
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                for (Connection c : targets) {
                    ListenerChannel ch = c.channel;
                    if (ch == null) {
                        continue;
                    }
                    if (posted) {
                        ch.onPosted(sbn);
                    } else {
                        ch.onRemoved(sbn);
                    }
                }
            }
        });
    }

    private static final class Connection implements ServiceConnection {
        final int userId;
        final String component;
        volatile ListenerChannel channel;

        Connection(int userId, String component) {
            this.userId = userId;
            this.component = component;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            final ListenerChannel ch = ListenerChannel.from(service);
            channel = ch;
            if (ch == null) {
                Slog.w(TAG, component + " no devolvio INotificationListener (no es un NotificationListenerService?)");
                return;
            }
            get().mExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    ch.onConnected();
                }
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            channel = null;
        }
    }
}
