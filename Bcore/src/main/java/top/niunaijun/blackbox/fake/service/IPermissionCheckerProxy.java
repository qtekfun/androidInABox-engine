package top.niunaijun.blackbox.fake.service;

import android.content.pm.PackageManager;
import android.os.IBinder;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import black.android.os.BRServiceManager;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;

/**
 * PermissionEnforcer (stubs AIDL de Android 14+) consulta "permission_checker". Dentro del box el servidor
 * del motor hace el papel de system_server (p. ej. el AccountManager virtual llama a los autenticadores), y
 * el sistema real no le concede permisos de sistema como ACCOUNT_MANAGER: se conceden aquí.
 */
public class IPermissionCheckerProxy extends BinderInvocationStub {
    private static final String SERVICE = "permission_checker";

    /** Permisos que solo tiene system_server y que en el box ejerce el servidor del motor. */
    static final Set<String> BOX_SYSTEM_PERMISSIONS = new HashSet<>(Arrays.asList(
            "android.permission.ACCOUNT_MANAGER"
    ));

    public IPermissionCheckerProxy() {
        super(BRServiceManager.get().getService(SERVICE));
    }

    @Override
    protected Object getWho() {
        try {
            IBinder binder = BRServiceManager.get().getService(SERVICE);
            Class<?> stub = Class.forName("android.permission.IPermissionChecker$Stub");
            return stub.getMethod("asInterface", IBinder.class).invoke(null, binder);
        } catch (Throwable e) {
            return null;
        }
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(SERVICE);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ProxyMethod("checkPermission")
    public static class CheckPermission extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (args != null && args.length > 0 && args[0] instanceof String
                    && BOX_SYSTEM_PERMISSIONS.contains(args[0])) {
                return PackageManager.PERMISSION_GRANTED;
            }
            return method.invoke(who, args);
        }
    }
}
