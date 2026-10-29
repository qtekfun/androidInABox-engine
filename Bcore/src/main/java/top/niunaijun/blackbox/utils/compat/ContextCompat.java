package top.niunaijun.blackbox.utils.compat;

import android.content.Context;
import android.content.ContextWrapper;
import android.util.*;

import black.android.app.BRContextImpl;
import black.android.app.BRContextImplKitkat;
import black.android.content.AttributionSourceStateContext;
import black.android.content.BRAttributionSource;
import black.android.content.BRAttributionSourceState;
import black.android.content.BRContentResolver;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.utils.Slog;


public class ContextCompat {
    public static final String TAG = "ContextCompat";

    public static void fixAttributionSourceState(Object obj, int uid) {
        Object mAttributionSourceState;
        if (obj != null && BRAttributionSource.get(obj)._check_mAttributionSourceState() != null) {
            mAttributionSourceState = BRAttributionSource.get(obj).mAttributionSourceState();

            AttributionSourceStateContext attributionSourceStateContext = BRAttributionSourceState.get(mAttributionSourceState);
            attributionSourceStateContext._set_packageName(BlackBoxCore.getHostPkg());
            attributionSourceStateContext._set_uid(uid);
            fixAttributionSourceState(BRAttributionSource.get(obj).getNext(), uid);
        }
    }

    /**
     * Algunos firmwares (p. ej. ColorOS/OxygenOS) filtran en ContentResolver.query las consultas a providers
     * de GMS cuando el paquete llamante no es de Google: como el invitado se presenta con el paquete del
     * host, esas consultas devuelven null sin llegar al proveedor (los ajustes de microG fallan con
     * "Cursor ... was null"). Se envuelve la extension del firmware: los hook* (insert/query/delete/batch)
     * devuelven true (operacion permitida) y el resto el valor por defecto.
     */
    public static void neutralizarFiltroGmsDelResolver(android.content.ContentResolver resolver) {
        try {
            if (resolver == null) return;
            java.lang.reflect.Field campo = android.content.ContentResolver.class.getDeclaredField("mContentResolverExt");
            campo.setAccessible(true);
            Object original = campo.get(resolver);
            if (original == null || java.lang.reflect.Proxy.isProxyClass(original.getClass())) return;
            Class<?> tipo = campo.getType();
            if (!tipo.isInterface()) return;
            Object envoltorio = java.lang.reflect.Proxy.newProxyInstance(tipo.getClassLoader(), new Class<?>[]{tipo},
                    (proxy, method, args) -> {
                        Class<?> ret = method.getReturnType();
                        if (ret == boolean.class) return method.getName().startsWith("hook");
                        if (ret == int.class) return 0;
                        if (ret == long.class) return 0L;
                        if (ret == float.class) return 0f;
                        if (ret == double.class) return 0d;
                        if (ret == short.class) return (short) 0;
                        if (ret == byte.class) return (byte) 0;
                        if (ret == char.class) return (char) 0;
                        return null;
                    });
            campo.set(resolver, envoltorio);
        } catch (NoSuchFieldException ignored) {
            // firmware sin esa extension
        } catch (Throwable e) {
            Slog.w(TAG, "No se pudo neutralizar el filtro GMS del ContentResolver: " + e);
        }
    }

    public static void fix(Context context) {
        try {
            
            if (context == null) {
                Slog.w(TAG, "Context is null, skipping ContextCompat.fix");
                return;
            }
            
            int deep = 0;
            while (context instanceof ContextWrapper) {
                context = ((ContextWrapper) context).getBaseContext();
                deep++;
                if (deep >= 10) {
                    return;
                }
            }
            
            
            if (context == null) {
                Slog.w(TAG, "Base context is null after unwrapping, skipping ContextCompat.fix");
                return;
            }
            
            BRContextImpl.get(context)._set_mPackageManager(null);
            try {
                context.getPackageManager();
            } catch (Throwable e) {
                e.printStackTrace();
            }

            BRContextImpl.get(context)._set_mBasePackageName(BlackBoxCore.getHostPkg());
            BRContextImplKitkat.get(context)._set_mOpPackageName(BlackBoxCore.getHostPkg());
            
            try {
                BRContentResolver.get(context.getContentResolver())._set_mPackageName(BlackBoxCore.getHostPkg());
            } catch (Exception e) {
                Slog.w(TAG, "Failed to fix content resolver: " + e.getMessage());
            }
            neutralizarFiltroGmsDelResolver(context.getContentResolver());

            if (BuildCompat.isS()) {
                try {
                    
                    
                    fixAttributionSourceState(BRContextImpl.get(context).getAttributionSource(), BlackBoxCore.getHostUid());
                } catch (Exception e) {
                    Slog.w(TAG, "Failed to fix attribution source state: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            Slog.e(TAG, "Error in ContextCompat.fix: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
