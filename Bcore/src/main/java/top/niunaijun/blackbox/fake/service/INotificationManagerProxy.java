package top.niunaijun.blackbox.fake.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.annotation.RequiresApi;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import black.android.app.BRNotificationManager;
import black.android.content.pm.BRParceledListSlice;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.fake.frameworks.BNotificationManager;
import top.niunaijun.blackbox.fake.frameworks.notification.NotificationPresenter;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.utils.MethodParameterUtils;
import top.niunaijun.blackbox.utils.compat.BuildCompat;
import top.niunaijun.blackbox.utils.compat.ParceledListSliceCompat;


public class INotificationManagerProxy extends BinderInvocationStub {
    public static final String TAG = "INotificationManagerProxy";

    /** Metodos de la API de escucha de notificaciones (spec 06) que atiende el motor. */
    private static final java.util.Set<String> LISTENER_METHODS = new java.util.HashSet<>(java.util.Arrays.asList(
            "getActiveNotificationsFromListener", "cancelNotificationFromListener",
            "cancelNotificationsFromListener", "registerListener", "unregisterListener",
            "isNotificationListenerAccessGranted", "isNotificationListenerAccessGrantedForUser",
            "getEnabledNotificationListenerPackages", "getEnabledNotificationListeners",
            "requestUnbindListener", "requestRebindListener"));

    public INotificationManagerProxy() {
        super(BRNotificationManager.get().getService().asBinder());
    }

    @Override
    protected Object getWho() {
        return BRNotificationManager.get().getService();
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        BRNotificationManager.get()._set_sService(getProxyInvocation());
        replaceSystemService(Context.NOTIFICATION_SERVICE);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

        // Estas llamadas llevan el paquete de OTRA app (el de la notificacion); no se reescribe
        if (!LISTENER_METHODS.contains(method.getName())) {
            MethodParameterUtils.replaceAllAppPkg(args);
        }
        return super.invoke(proxy, method, args);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ProxyMethod("getNotificationChannel")
    public static class GetNotificationChannel extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            NotificationChannel notificationChannel = BNotificationManager.get().getNotificationChannel((String) args[args.length - 1]);
            return notificationChannel;
        }
    }

    @ProxyMethod("getNotificationChannels")
    public static class GetNotificationChannels extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            List<NotificationChannel> notificationChannels = BNotificationManager.get().getNotificationChannels(BActivityThread.getAppPackageName());
            return ParceledListSliceCompat.create(notificationChannels);
        }
    }

    @ProxyMethod("cancelNotificationWithTag")
    public static class CancelNotificationWithTag extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            String tag = (String) args[getTagIndex()];
            int id = (int) args[getIdIndex()];
            BNotificationManager.get().cancelNotificationWithTag(id, tag);
            return 0;
        }

        public int getTagIndex() {
            if (BuildCompat.isR()) {
                return 2;
            }
            return 1;
        }

        public int getIdIndex() {
            return getTagIndex() + 1;
        }
    }


    /**
     * Sin este hook {@code replaceAllAppPkg} cambiaria el paquete por el del host y el invitado
     * cancelaria todas las notificaciones del host, incluida la del servicio del motor.
     */
    @ProxyMethod("cancelAllNotifications")
    public static class CancelAllNotifications extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            BNotificationManager.get().cancelAllNotifications();
            return 0;
        }
    }

    /** El invitado ve el estado real del host (permiso POST_NOTIFICATIONS y ajuste de la app). */
    @ProxyMethod("areNotificationsEnabled")
    public static class AreNotificationsEnabled extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            // Preguntar al servicio real por el host directamente: NotificationManager volvería a pasar por este proxy
            if (args != null) {
                for (int i = 0; i < args.length; i++) {
                    if (args[i] instanceof String) {
                        args[i] = BlackBoxCore.getHostPkg();
                    } else if (args[i] instanceof Integer) {
                        args[i] = android.os.Process.myUid();
                    }
                }
            }
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("areNotificationsEnabledForPackage")
    public static class AreNotificationsEnabledForPackage extends AreNotificationsEnabled {
    }

    /** El invitado no puede cambiar el ajuste de notificaciones del host. */
    @ProxyMethod("setNotificationsEnabledForPackage")
    public static class SetNotificationsEnabledForPackage extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return 0;
        }
    }

    /** Fuera de alcance: el invitado no lista notificaciones (evita que vea las del host). */
    @ProxyMethod("getActiveNotifications")
    public static class GetActiveNotifications extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Class<?> type = method.getReturnType();
            if (type.isArray()) {
                return Array.newInstance(type.getComponentType(), 0);
            }
            return ParceledListSliceCompat.create(new ArrayList<>());
        }
    }

    @ProxyMethod("getAppActiveNotifications")
    public static class GetAppActiveNotifications extends GetActiveNotifications {
    }

    @ProxyMethod("enqueueNotificationWithTag")
    public static class EnqueueNotificationWithTag extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            String tag = (String) args[getTagIndex()];
            int id = (int) args[getIdIndex()];
            Notification notification = MethodParameterUtils.getFirstParam(args, Notification.class);
            notification = NotificationPresenter.present(notification);
            BNotificationManager.get().enqueueNotificationWithTag(id, tag, notification);
            return 0;
        }

        public int getTagIndex() {
            return 2;
        }

        public int getIdIndex() {
            return getTagIndex() + 1;
        }
    }

    @ProxyMethod("createNotificationChannels")
    @RequiresApi(api = Build.VERSION_CODES.O)
    public static class CreateNotificationChannels extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            List<?> list = BRParceledListSlice.get(args[1]).getList();
            if (list == null)
                return 0;
            for (Object o : list) {
                BNotificationManager.get().createNotificationChannel((NotificationChannel) o);
            }
            return 0;
        }
    }

    @ProxyMethod("deleteNotificationChannel")
    public static class DeleteNotificationChannel extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            BNotificationManager.get().deleteNotificationChannel((String) args[1]);
            return 0;
        }
    }

    @ProxyMethod("createNotificationChannelGroups")
    @RequiresApi(api = Build.VERSION_CODES.O)
    public static class CreateNotificationChannelGroups extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            List<?> list = BRParceledListSlice.get(args[1]).getList();
            for (Object o : list) {
                BNotificationManager.get().createNotificationChannelGroup((NotificationChannelGroup) o);
            }
            return 0;
        }
    }

    @ProxyMethod("deleteNotificationChannelGroup")
    public static class DeleteNotificationChannelGroup extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            BNotificationManager.get().deleteNotificationChannelGroup((String) args[1]);
            return 0;
        }
    }

    @ProxyMethod("getNotificationChannelGroups")
    public static class GetNotificationChannelGroups extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            List<NotificationChannelGroup> notificationChannelGroups = BNotificationManager.get().getNotificationChannelGroups(BActivityThread.getAppPackageName());
            return ParceledListSliceCompat.create(notificationChannelGroups);
        }
    }

    // ---- escucha de notificaciones: el motor atiende al invitado, el sistema real no lo ve ----

    /** El oyente invitado pide las activas: las del box (y las reales reenviadas, si el puente esta activo). */
    @ProxyMethod("getActiveNotificationsFromListener")
    public static class GetActiveNotificationsFromListener extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            List<android.service.notification.StatusBarNotification> list =
                    BNotificationManager.get().getActiveNotificationsForListener();
            return ParceledListSliceCompat.create(list == null ? new ArrayList<>() : list);
        }
    }

    @ProxyMethod("cancelNotificationFromListener")
    public static class CancelNotificationFromListener extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            // (INotificationListener token, String pkg, String tag, int id)
            String pkg = null;
            String tag = null;
            int id = 0;
            int strings = 0;
            for (Object a : args) {
                if (a instanceof String) {
                    if (strings++ == 0) {
                        pkg = (String) a;
                    } else {
                        tag = (String) a;
                    }
                } else if (a instanceof Integer) {
                    id = (Integer) a;
                }
            }
            if (pkg != null) {
                BNotificationManager.get().cancelNotificationFromListener(pkg, tag, id);
            }
            return null;
        }
    }

    /** El registro de oyentes en el sistema real no aplica: los entrega el motor. */
    @ProxyMethod("registerListener")
    public static class RegisterListener extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return null;
        }
    }

    @ProxyMethod("unregisterListener")
    public static class UnregisterListener extends RegisterListener {
    }

    @ProxyMethod("isNotificationListenerAccessGranted")
    public static class IsNotificationListenerAccessGranted extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            android.content.ComponentName cn = MethodParameterUtils.getFirstParam(args, android.content.ComponentName.class);
            return cn != null && BNotificationManager.get().isListenerAccessGranted(cn.flattenToString());
        }
    }

    @ProxyMethod("isNotificationListenerAccessGrantedForUser")
    public static class IsNotificationListenerAccessGrantedForUser extends IsNotificationListenerAccessGranted {
    }

    @ProxyMethod("getEnabledNotificationListenerPackages")
    public static class GetEnabledNotificationListenerPackages extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            List<String> out = new ArrayList<>();
            for (String c : BNotificationManager.get().getEnabledListeners()) {
                android.content.ComponentName cn = android.content.ComponentName.unflattenFromString(c);
                if (cn != null && !out.contains(cn.getPackageName())) {
                    out.add(cn.getPackageName());
                }
            }
            return out;
        }
    }

    @ProxyMethod("getEnabledNotificationListeners")
    public static class GetEnabledNotificationListeners extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            List<android.content.ComponentName> out = new ArrayList<>();
            for (String c : BNotificationManager.get().getEnabledListeners()) {
                android.content.ComponentName cn = android.content.ComponentName.unflattenFromString(c);
                if (cn != null) {
                    out.add(cn);
                }
            }
            return out;
        }
    }
}
