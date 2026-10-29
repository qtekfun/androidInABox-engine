package top.niunaijun.blackbox.fake.service;

import java.lang.reflect.Method;

import black.android.os.BRServiceManager;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.utils.MethodParameterUtils;
import top.niunaijun.blackbox.utils.Reflector;
import top.niunaijun.blackbox.utils.Slog;

/**
 * BiometricPrompt (GMS pide la huella al iniciar sesion) llama a IAuthService con el nombre de paquete del
 * invitado, pero el uid real es el del host: AppOps responde "com.google.android.gms from uid N not allowed
 * to perform USE_BIOMETRIC". Se presenta el paquete del host, que es el dueno real del uid.
 */
public class IAuthServiceProxy extends BinderInvocationStub {
    public static final String TAG = "IAuthServiceProxy";
    private static final String SERVICE = "auth";

    public IAuthServiceProxy() {
        super(BRServiceManager.get().getService(SERVICE));
    }

    @Override
    protected Object getWho() {
        try {
            return Reflector.on("android.hardware.biometrics.IAuthService$Stub")
                    .call("asInterface", BRServiceManager.get().getService(SERVICE));
        } catch (Throwable t) {
            Slog.w(TAG, "IAuthService no disponible: " + t);
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

    @Override
    protected void onBindMethod() {
        super.onBindMethod();
        for (String name : new String[]{"authenticate", "canAuthenticate", "hasEnrolledBiometrics",
                "registerEnabledOnKeyguardCallback", "getLastAuthenticationTime", "getAuthenticators",
                "getSensorProperties", "invalidateAuthenticatorIds", "getAuthenticatorIds"}) {
            addMethodHook(new AllPkgMethod(name));
        }
    }

    private static class AllPkgMethod extends MethodHook {
        private final String mName;

        AllPkgMethod(String name) {
            mName = name;
        }

        @Override
        protected String getMethodName() {
            return mName;
        }

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceAllAppPkg(args);
            return method.invoke(who, args);
        }
    }
}
