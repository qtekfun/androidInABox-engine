package top.niunaijun.blackbox.fake.service;

import android.app.PendingIntent;
import android.content.Context;
import android.os.WorkSource;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import black.android.app.BRIAlarmManagerStub;
import black.android.os.BRServiceManager;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.utils.MethodParameterUtils;
import top.niunaijun.blackbox.utils.Slog;


public class IAlarmManagerProxy extends BinderInvocationStub {
    private static final String TAG = "IAlarmManagerProxy";

    public IAlarmManagerProxy() {
        super(BRServiceManager.get().getService(Context.ALARM_SERVICE));
    }

    @Override
    protected Object getWho() {
        return BRIAlarmManagerStub.get().asInterface(BRServiceManager.get().getService(Context.ALARM_SERVICE));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(Context.ALARM_SERVICE);
    }

    /**
     * Alarmas del invitado. El PendingIntent de broadcast o servicio ya llega reescrito al
     * ProxyPendingReceiver del host (ver {@code IActivityManagerProxy.GetIntentSender}); aqui solo
     * se sustituye el paquete llamante por el del host y se descarta el WorkSource (lleva el uid
     * del invitado) antes de pasar la llamada al AlarmManager real. Cubre set, setExact, setWindow,
     * setRepeating, setAndAllowWhileIdle, setExactAndAllowWhileIdle, setAlarmClock y los
     * OnAlarmListener (el IAlarmListener es un binder del propio proceso invitado).
     * El host tiene targetSdk 28: SCHEDULE_EXACT_ALARM solo se exige con target >= 31, asi que
     * las alarmas exactas no necesitan permiso. El sistema limita a 500 alarmas por UID (todo el
     * box suma contra ese limite).
     */
    @ProxyMethod("set")
    public static class Set extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            String pkg = MethodParameterUtils.replaceFirstAppPkg(args);
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof WorkSource) {
                    args[i] = null;
                }
            }
            Slog.d(TAG, "set alarma de " + pkg + " args=" + describe(args));
            try {
                return method.invoke(who, args);
            } catch (InvocationTargetException e) {
                Slog.w(TAG, "set alarma rechazada para " + pkg + ": " + e.getCause());
                throw e.getCause();
            }
        }
    }

    /** Cancela una alarma: el PendingIntent reescrito es igual (filterEquals) al usado al programarla. */
    @ProxyMethod("remove")
    public static class Remove extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Slog.d(TAG, "remove alarma " + describe(args));
            return method.invoke(who, args);
        }
    }

    /** removeAll(pkg) con el paquete del host borraria las alarmas de todo el box: se ignora. */
    @ProxyMethod("removeAll")
    public static class RemoveAll extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return 0;
        }
    }

    @ProxyMethod("getNextAlarmClock")
    public static class GetNextAlarmClock extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceFirstAppPkg(args);
            return method.invoke(who, args);
        }
    }

    static String describe(Object[] args) {
        StringBuilder sb = new StringBuilder();
        for (Object a : args) {
            if (a instanceof PendingIntent || a instanceof Number || a instanceof String) {
                sb.append(a).append(' ');
            }
        }
        return sb.toString().trim();
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }
}
