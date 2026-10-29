package top.niunaijun.blackbox.fake.service.installer;

import android.content.IntentSender;
import android.content.pm.PackageInstaller;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;

import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.core.system.pm.IBInstallSessionService;
import top.niunaijun.blackbox.fake.frameworks.BInstallSessionManager;
import top.niunaijun.blackbox.utils.Slog;
import top.niunaijun.blackbox.utils.compat.ParceledListSliceCompat;

/**
 * Proxies de {@code IPackageInstaller} e {@code IPackageInstallerSession} para el invitado (T03).
 * Ambas interfaces son @hide y sus firmas cambian por versión de Android, así que se implementan
 * con {@link Proxy} despachando por nombre de método y argumentos por tipo, y se reenvían al
 * servicio de sesiones del box. Lo que no se soporta devuelve el valor por defecto de su tipo.
 */
public final class VirtualPackageInstaller {
    public static final String TAG = "VirtualPackageInstaller";

    private VirtualPackageInstaller() {
    }

    private static IBInstallSessionService svc() throws RemoteException {
        IBInstallSessionService s = BInstallSessionManager.get().getService();
        if (s == null) throw new RemoteException("Servicio de sesiones de instalación no disponible");
        return s;
    }

    /** Objeto que sustituye al IPackageInstaller real. */
    public static Object createInstaller() throws ClassNotFoundException {
        Class<?> itf = Class.forName("android.content.pm.IPackageInstaller");
        final IBinder token = new Binder();
        return Proxy.newProxyInstance(itf.getClassLoader(), new Class<?>[]{itf}, new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method m, Object[] args) throws Throwable {
                Object basic = objectMethod(proxy, m, args, token);
                if (basic != NOT_HANDLED) return basic;
                try {
                    return handleInstaller(m, args == null ? new Object[0] : args);
                } catch (java.lang.reflect.InvocationTargetException e) {
                    throw e.getCause();
                }
            }
        });
    }

    private static Object handleInstaller(Method m, Object[] args) throws Throwable {
        int userId = BActivityThread.getUserId();
        switch (m.getName()) {
            case "createSession": {
                Bundle b = new Bundle();
                String installer = null;
                for (Object a : args) {
                    if (a instanceof android.os.Parcelable) b.putParcelable("params", (android.os.Parcelable) a);
                    else if (a instanceof String && installer == null) installer = (String) a;
                }
                if (installer == null) installer = BActivityThread.getAppPackageName();
                return svc().createSession(b, installer, userId);
            }
            case "openSession": {
                int id = (Integer) args[0];
                if (!svc().hasSession(id, userId)) {
                    throw new SecurityException("Caller has no access to session " + id);
                }
                return createSession(id);
            }
            case "abandonSession":
                svc().abandon((Integer) args[0]);
                return null;
            case "updateSessionAppLabel":
                svc().updateAppLabel((Integer) args[0], (String) args[1]);
                return null;
            case "getSessionInfo": {
                Bundle b = svc().getSessionInfo((Integer) args[0], userId);
                b.setClassLoader(PackageInstaller.SessionInfo.class.getClassLoader());
                return b.getParcelable("info");
            }
            case "getAllSessions":
                return sessions(null);
            case "getMySessions": {
                String installer = args.length > 0 && args[0] instanceof String
                        ? (String) args[0] : BActivityThread.getAppPackageName();
                return sessions(installer);
            }
            case "uninstall":
            case "uninstallExistingPackage": {
                String pkg = packageNameOf(args);
                IntentSender receiver = null;
                for (Object a : args) if (a instanceof IntentSender) receiver = (IntentSender) a;
                Slog.d(TAG, m.getName() + " " + pkg + " (user " + userId + ")");
                svc().uninstall(pkg, receiver, userId);
                return null;
            }
            default:
                // registerCallback, unregisterCallback, setPermissionsResult, etc.: sin efecto
                return defaultFor(m.getReturnType());
        }
    }

    private static Object sessions(String installer) throws Throwable {
        Bundle b = svc().getSessions(BActivityThread.getUserId(), installer);
        b.setClassLoader(PackageInstaller.SessionInfo.class.getClassLoader());
        ArrayList<PackageInstaller.SessionInfo> list = b.getParcelableArrayList("sessions");
        return ParceledListSliceCompat.create(list == null ? new ArrayList<>() : list);
    }

    private static String packageNameOf(Object[] args) throws Exception {
        for (Object a : args) {
            if (a instanceof String) return (String) a;
            if (a != null && a.getClass().getName().equals("android.content.pm.VersionedPackage")) {
                return (String) a.getClass().getMethod("getPackageName").invoke(a);
            }
        }
        return null;
    }

    /** Objeto que sustituye al IPackageInstallerSession real. */
    private static Object createSession(final int id) throws ClassNotFoundException {
        Class<?> itf = Class.forName("android.content.pm.IPackageInstallerSession");
        final IBinder token = new Binder();
        return Proxy.newProxyInstance(itf.getClassLoader(), new Class<?>[]{itf}, new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method m, Object[] args) throws Throwable {
                Object basic = objectMethod(proxy, m, args, token);
                if (basic != NOT_HANDLED) return basic;
                return handleSession(id, m, args == null ? new Object[0] : args);
            }
        });
    }

    private static Object handleSession(int id, Method m, Object[] args) throws Throwable {
        switch (m.getName()) {
            case "setClientProgress":
                svc().setProgress(id, (Float) args[0], false);
                return null;
            case "addClientProgress":
                svc().setProgress(id, (Float) args[0], true);
                return null;
            case "getNames":
                return svc().getNames(id);
            case "openWrite":
                return svc().openWrite(id, (String) args[0], (Long) args[1], (Long) args[2]);
            case "openRead":
                return svc().openRead(id, (String) args[0]);
            case "write": {
                // write(name, offset, length, ParcelFileDescriptor origin): copia desde el fd dado
                ParcelFileDescriptor out = svc().openWrite(id, (String) args[0], (Long) args[1], (Long) args[2]);
                try (FileInputStream in = new FileInputStream(((ParcelFileDescriptor) args[3]).getFileDescriptor());
                     FileOutputStream os = new FileOutputStream(out.getFileDescriptor())) {
                    byte[] buf = new byte[64 * 1024];
                    int r;
                    while ((r = in.read(buf)) > 0) os.write(buf, 0, r);
                    os.getFD().sync();
                } finally {
                    out.close();
                }
                return null;
            }
            case "removeSplit":
                svc().removeSplit(id, (String) args[0]);
                return null;
            case "commit": {
                IntentSender receiver = null;
                for (Object a : args) if (a instanceof IntentSender) receiver = (IntentSender) a;
                svc().commit(id, receiver);
                return null;
            }
            case "abandon":
                svc().abandon(id);
                return null;
            case "getParentSessionId":
                return -1;
            case "getChildSessionIds":
                return new int[0];
            default:
                // close, transfer, requestUserPreapproval, isMultiPackage, isStaged, etc.
                return defaultFor(m.getReturnType());
        }
    }

    private static final Object NOT_HANDLED = new Object();

    /** equals/hashCode/toString/asBinder comunes a ambos proxies. */
    private static Object objectMethod(Object proxy, Method m, Object[] args, IBinder token) {
        switch (m.getName()) {
            case "asBinder":
                return token;
            case "hashCode":
                return System.identityHashCode(proxy);
            case "equals":
                return proxy == args[0];
            case "toString":
                return "VirtualPackageInstaller@" + Integer.toHexString(System.identityHashCode(proxy));
            default:
                return NOT_HANDLED;
        }
    }

    static Object defaultFor(Class<?> type) {
        if (type == void.class) return null;
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        if (type == short.class) return (short) 0;
        if (type == byte.class) return (byte) 0;
        if (type == char.class) return (char) 0;
        if (type.isArray()) return Array.newInstance(type.getComponentType(), 0);
        if (type.getName().endsWith("ParceledListSlice")) {
            return ParceledListSliceCompat.create(new ArrayList<>());
        }
        return null;
    }
}
