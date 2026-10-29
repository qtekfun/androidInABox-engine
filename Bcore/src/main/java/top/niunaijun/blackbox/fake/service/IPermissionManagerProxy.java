package top.niunaijun.blackbox.fake.service;

import android.content.pm.PackageManager;

import java.lang.reflect.Method;

import black.android.app.BRActivityThread;
import black.android.app.BRContextImpl;
import black.android.os.BRServiceManager;
import black.android.permission.BRIPermissionManagerStub;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.core.GooglePrivappPermissions;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.fake.service.base.PkgMethodProxy;
import top.niunaijun.blackbox.fake.service.base.ValueMethodProxy;
import top.niunaijun.blackbox.utils.Reflector;
import top.niunaijun.blackbox.utils.compat.BuildCompat;


public class IPermissionManagerProxy extends BinderInvocationStub {
    public static final String TAG = "IPermissionManagerProxy";

    private static final String P = "permissionmgr";

    public IPermissionManagerProxy() {
        super(BRServiceManager.get().getService(P));
    }

    @Override
    protected Object getWho() {
        return BRIPermissionManagerStub.get().asInterface(BRServiceManager.get().getService(P));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService("permissionmgr");
        BRActivityThread.getWithException()._set_sPermissionManager(proxyInvocation);
        
    }

    @Override
    protected void onBindMethod() {
        super.onBindMethod();
        addMethodHook(new ValueMethodProxy("addPermissionAsync", true));
        addMethodHook(new ValueMethodProxy("addPermission", true));
        addMethodHook(new ValueMethodProxy("performDexOpt", true));
        addMethodHook(new ValueMethodProxy("performDexOptIfNeeded", false));
        addMethodHook(new ValueMethodProxy("performDexOptSecondary", true));
        addMethodHook(new ValueMethodProxy("addOnPermissionsChangeListener", 0));
        addMethodHook(new ValueMethodProxy("removeOnPermissionsChangeListener", 0));
        addMethodHook(new ValueMethodProxy("checkDeviceIdentifierAccess", false));
        addMethodHook(new PkgMethodProxy("shouldShowRequestPermissionRationale"));
        addMethodHook(new GoogleCheckPermission("checkPermission", false));
        addMethodHook(new GoogleCheckPermission("checkUidPermission", true));
        if (BuildCompat.isOreo()) {
            addMethodHook(new ValueMethodProxy("notifyDexLoad", 0));
            addMethodHook(new ValueMethodProxy("notifyPackageUse", 0));
            addMethodHook(new ValueMethodProxy("setInstantAppCookie", false));
            addMethodHook(new ValueMethodProxy("isInstantApp", false));
        }
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    /**
     * checkPermission(pkg, permiso, ...) / checkUidPermission(uid, permiso, ...): los paquetes de
     * Google del box obtienen los permisos privilegiados de la tabla; el resto va al sistema.
     */
    private static class GoogleCheckPermission extends MethodHook {
        private final String name;
        private final boolean byUid;

        GoogleCheckPermission(String name, boolean byUid) {
            this.name = name;
            this.byUid = byUid;
        }

        @Override
        protected String getMethodName() {
            return name;
        }

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                String permission = null;
                String pkg = null;
                for (Object a : args) {
                    if (a instanceof String) {
                        if (byUid || pkg != null) {
                            if (permission == null) permission = (String) a;
                        } else {
                            pkg = (String) a;
                        }
                    }
                }
                if (GooglePrivappPermissions.isGrantedPermission(permission)) {
                    if (byUid) {
                        pkg = BActivityThread.getAppPackageName();
                    }
                    if (GooglePrivappPermissions.isGooglePackage(pkg)) {
                        return PackageManager.PERMISSION_GRANTED;
                    }
                }
            } catch (Throwable ignored) {
            }
            return method.invoke(who, args);
        }
    }
}
