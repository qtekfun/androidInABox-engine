package top.niunaijun.blackbox.fake.service;

import android.content.ComponentName;
import android.content.Context;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import black.android.companion.BRICompanionDeviceManagerStub;
import black.android.os.BRServiceManager;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.fake.frameworks.BCompanionDeviceManager;
import top.niunaijun.blackbox.fake.frameworks.BNotificationManager;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.utils.MethodParameterUtils;
import top.niunaijun.blackbox.utils.Slog;

/**
 * Servicio {@code companiondevice} (spec 06, 3.2): las llamadas del invitado se ejecutan a nombre
 * del host (el sistema muestra el dialogo de asociacion del host) y el motor lleva el mapa de que
 * asociaciones son de que invitado ({@link BCompanionDeviceManager}). Los nombres de metodo son
 * los de Android 12 en adelante; las firmas cambian por version, por eso los ganchos buscan
 * argumentos por tipo y no por posicion.
 */
public class ICompanionDeviceManagerProxy extends BinderInvocationStub {
    public static final String TAG = "ICompanionDeviceManagerProxy";

    public ICompanionDeviceManagerProxy() {
        super(BRServiceManager.get().getService(Context.COMPANION_DEVICE_SERVICE));
    }

    @Override
    protected Object getWho() {
        return BRICompanionDeviceManagerStub.get().asInterface(
                BRServiceManager.get().getService(Context.COMPANION_DEVICE_SERVICE));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(Context.COMPANION_DEVICE_SERVICE);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // El paquete llamante pasa a ser el del host en todas las llamadas
        MethodParameterUtils.replaceAllAppPkg(args);
        return super.invoke(proxy, method, args);
    }

    /** Ids de asociacion que el host tiene en el sistema real (vacio si no se pueden leer). */
    private static int[] systemAssociationIds(Object who) {
        try {
            Method get = null;
            for (Method m : who.getClass().getMethods()) {
                if (m.getName().equals("getAssociations")) {
                    get = m;
                    break;
                }
            }
            if (get == null) {
                return new int[0];
            }
            Class<?>[] types = get.getParameterTypes();
            Object[] a = new Object[types.length];
            for (int i = 0; i < types.length; i++) {
                if (types[i] == String.class) {
                    a[i] = BlackBoxCore.getHostPkg();
                } else if (types[i] == int.class) {
                    a[i] = android.os.Process.myUid() / 100000;
                }
            }
            return idsOf((List<?>) get.invoke(who, a));
        } catch (Throwable t) {
            Slog.w(TAG, "No se pudieron leer las asociaciones reales: " + t);
            return new int[0];
        }
    }

    private static int[] idsOf(List<?> list) {
        List<Integer> ids = new ArrayList<>();
        if (list != null) {
            for (Object o : list) {
                Integer id = idOf(o);
                if (id != null) {
                    ids.add(id);
                }
            }
        }
        int[] out = new int[ids.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = ids.get(i);
        }
        return out;
    }

    /** {@code AssociationInfo.getId()} (Android 12+); null si el elemento no es una AssociationInfo. */
    private static Integer idOf(Object info) {
        try {
            return (Integer) info.getClass().getMethod("getId").invoke(info);
        } catch (Throwable t) {
            return null;
        }
    }

    @ProxyMethod("associate")
    public static class Associate extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            BCompanionDeviceManager.get().onAssociateRequested(systemAssociationIds(who));
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("getAssociations")
    public static class GetAssociations extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Object result = method.invoke(who, args);
            if (!(result instanceof List)) {
                return result;
            }
            List<?> all = (List<?>) result;
            int[] mine = BCompanionDeviceManager.get().reconcile(idsOf(all));
            List<Object> out = new ArrayList<>();
            for (Object o : all) {
                Integer id = idOf(o);
                if (id == null) {
                    return result; // formato antiguo (direcciones MAC): sin filtrar
                }
                for (int m : mine) {
                    if (m == id) {
                        out.add(o);
                        break;
                    }
                }
            }
            return out;
        }
    }

    /** Un invitado solo puede deshacer sus propias asociaciones. */
    @ProxyMethod("disassociate")
    public static class Disassociate extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Integer id = args != null && args.length > 0 && args[0] instanceof Integer ? (Integer) args[0] : null;
            if (id != null) {
                if (!BCompanionDeviceManager.get().owns(id)) {
                    Slog.w(TAG, "disassociate(" + id + ") de otro invitado: ignorado");
                    return null;
                }
                BCompanionDeviceManager.get().release(id);
            }
            return method.invoke(who, args);
        }
    }

    /** El acceso a notificaciones lo gestiona el box (escucha de notificaciones del box). */
    @ProxyMethod("hasNotificationAccess")
    public static class HasNotificationAccess extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ComponentName cn = MethodParameterUtils.getFirstParam(args, ComponentName.class);
            return cn != null && BNotificationManager.get().isListenerAccessGranted(cn.flattenToString());
        }
    }
}
