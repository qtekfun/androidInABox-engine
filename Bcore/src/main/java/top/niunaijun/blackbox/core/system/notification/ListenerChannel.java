package top.niunaijun.blackbox.core.system.notification;

import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.service.notification.StatusBarNotification;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import top.niunaijun.blackbox.utils.Slog;

/**
 * Extremo servidor de un {@code NotificationListenerService} invitado conectado en el box: habla
 * con su {@code INotificationListener} (interfaz oculta) por reflexion, porque las firmas de
 * {@code onNotificationPosted/Removed} cambian entre versiones de Android. Los argumentos se
 * construyen por tipo: el holder del {@code StatusBarNotification} es un binder propio, el
 * {@code NotificationRankingUpdate} es uno vacio (sin el, el servicio invitado falla al aplicarlo).
 * Cualquier fallo se registra y se ignora: un oyente roto no debe afectar al resto.
 */
final class ListenerChannel {
    private static final String TAG = "ListenerChannel";
    private static final String LISTENER = "android.service.notification.INotificationListener";
    private static final String HOLDER = "android.service.notification.IStatusBarNotificationHolder";
    private static final String RANKING_UPDATE = "android.service.notification.NotificationRankingUpdate";
    /** NotificationListenerService.REASON_APP_CANCEL. */
    private static final int REASON_APP_CANCEL = 8;

    private final Object mListener;

    private ListenerChannel(Object listener) {
        mListener = listener;
    }

    /** null si el binder no es un INotificationListener (el servicio invitado no lo devolvio). */
    static ListenerChannel from(IBinder binder) {
        if (binder == null) {
            return null;
        }
        try {
            Class<?> stub = Class.forName(LISTENER + "$Stub");
            Object iface = stub.getMethod("asInterface", IBinder.class).invoke(null, binder);
            return iface == null ? null : new ListenerChannel(iface);
        } catch (Throwable t) {
            Slog.w(TAG, "No se pudo obtener INotificationListener: " + t);
            return null;
        }
    }

    void onConnected() {
        call("onListenerConnected", null);
    }

    void onPosted(StatusBarNotification sbn) {
        call("onNotificationPosted", sbn);
    }

    void onRemoved(StatusBarNotification sbn) {
        call("onNotificationRemoved", sbn);
    }

    private void call(String name, StatusBarNotification sbn) {
        try {
            Method target = null;
            for (Method m : mListener.getClass().getMethods()) {
                if (m.getName().equals(name)) {
                    target = m;
                    break;
                }
            }
            if (target == null) {
                Slog.w(TAG, "INotificationListener sin " + name);
                return;
            }
            target.invoke(mListener, buildArgs(target.getParameterTypes(), sbn));
        } catch (Throwable t) {
            Slog.w(TAG, name + " fallo: " + t);
        }
    }

    private static Object[] buildArgs(Class<?>[] types, StatusBarNotification sbn) throws Throwable {
        Object[] args = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            Class<?> t = types[i];
            if (t.getName().equals(HOLDER)) {
                args[i] = newHolder(t, sbn);
            } else if (t.getName().equals(RANKING_UPDATE)) {
                args[i] = emptyRankingUpdate(t);
            } else if (t == int.class) {
                args[i] = REASON_APP_CANCEL;
            } else if (t == long.class) {
                args[i] = 0L;
            } else if (t == boolean.class) {
                args[i] = false;
            }
        }
        return args;
    }

    /** {@code NotificationRankingUpdate(Ranking[])}, el constructor de Android 11 en adelante. */
    private static Object emptyRankingUpdate(Class<?> type) {
        try {
            Class<?> ranking = Class.forName("android.service.notification.NotificationListenerService$Ranking");
            java.lang.reflect.Constructor<?> c = type.getDeclaredConstructor(
                    java.lang.reflect.Array.newInstance(ranking, 0).getClass());
            c.setAccessible(true);
            return c.newInstance(java.lang.reflect.Array.newInstance(ranking, 0));
        } catch (Throwable t) {
            Slog.w(TAG, "Sin NotificationRankingUpdate vacio: " + t);
            return null;
        }
    }

    /**
     * {@code IStatusBarNotificationHolder}: un proxy de la interfaz cuyo {@code asBinder} es un
     * Binder que responde a {@code get()} (transaccion 1) con la notificacion. Es lo que el
     * cliente AIDL del invitado llama al hacer {@code sbnHolder.get()}.
     */
    private static Object newHolder(Class<?> holderType, final StatusBarNotification sbn) {
        final Binder binder = new Binder() {
            @Override
            protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
                if (code == IBinder.FIRST_CALL_TRANSACTION && reply != null) {
                    reply.writeNoException();
                    reply.writeInt(1);
                    sbn.writeToParcel(reply, 0);
                    return true;
                }
                return false;
            }
        };
        return Proxy.newProxyInstance(holderType.getClassLoader(), new Class<?>[]{holderType}, new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] a) {
                switch (method.getName()) {
                    case "asBinder":
                        return binder;
                    case "get":
                        return sbn;
                    case "hashCode":
                        return System.identityHashCode(proxy);
                    case "equals":
                        return proxy == a[0];
                    case "toString":
                        return "StatusBarNotificationHolder";
                    default:
                        return null;
                }
            }
        });
    }
}
