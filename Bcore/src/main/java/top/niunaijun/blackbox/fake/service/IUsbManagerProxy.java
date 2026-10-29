package top.niunaijun.blackbox.fake.service;

import android.content.Context;
import android.util.Log;

import java.lang.reflect.Method;

import black.android.hardware.usb.BRIUsbManagerStub;
import black.android.os.BRServiceManager;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.utils.MethodParameterUtils;

/**
 * Servicio "usb" para el invitado (spec 07, T07.4). Android Auto del box llama a getAccessoryList,
 * hasAccessoryPermission, requestAccessoryPermission y openAccessory. El permiso del accesorio se
 * concede por UID y todos los invitados comparten el del host, asi que basta con que las llamadas
 * lleguen al servicio real con el nombre de paquete del host (el sistema comprueba que el paquete
 * pertenezca al UID que llama).
 */
public class IUsbManagerProxy extends BinderInvocationStub {
    public static final String TAG = "IUsbManagerProxy";

    public IUsbManagerProxy() {
        super(BRServiceManager.get().getService(Context.USB_SERVICE));
    }

    @Override
    protected Object getWho() {
        return BRIUsbManagerStub.get().asInterface(BRServiceManager.get().getService(Context.USB_SERVICE));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(Context.USB_SERVICE);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    /** Solicitud de permiso: el paquete del invitado se sustituye por el del host. */
    @ProxyMethod("requestAccessoryPermission")
    public static class RequestAccessoryPermission extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceFirstAppPkg(args);
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("requestDevicePermission")
    public static class RequestDevicePermission extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceFirstAppPkg(args);
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("hasAccessoryPermission")
    public static class HasAccessoryPermission extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Object r = method.invoke(who, args);
            Log.d(TAG, "hasAccessoryPermission -> " + r);
            return r;
        }
    }

    @ProxyMethod("openAccessory")
    public static class OpenAccessory extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            // Mismo UID que el host: el descriptor se abre con el permiso que el sistema dio al host
            Object r = method.invoke(who, args);
            Log.i(TAG, "openAccessory -> " + (r != null ? "descriptor abierto" : "null"));
            return r;
        }
    }
}
