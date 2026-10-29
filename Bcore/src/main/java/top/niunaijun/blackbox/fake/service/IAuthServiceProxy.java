package top.niunaijun.blackbox.fake.service;

import java.lang.reflect.Method;

import black.android.os.BRServiceManager;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
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
            android.os.IBinder binder = BRServiceManager.get().getService(SERVICE);
            Class<?> stub = Class.forName("android.hardware.biometrics.IAuthService$Stub");
            return stub.getMethod("asInterface", android.os.IBinder.class).invoke(null, binder);
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

    /**
     * BiometricPrompt obtiene IAuthService directamente al crearse y no pasa por este proxy, así que el
     * sistema real vería al invitado pidiendo biometría con un paquete que no es el del UID. Se informa de
     * que no hay hardware biométrico: las apps (p. ej. GMS al iniciar sesión) no lanzan el diálogo.
     */
    @ProxyMethod("canAuthenticate")
    public static class CanAuthenticate extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return 12; // BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE
        }
    }

    @ProxyMethod("hasEnrolledBiometrics")
    public static class HasEnrolledBiometrics extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return false;
        }
    }

    /**
     * El sistema real rechaza authenticate() del invitado (paquete distinto del UID). Si solo se ignora,
     * la app espera para siempre al callback (GMS se queda en "Comprobando información"). Se responde por
     * el IBiometricServiceReceiver con "no hay biometría ni credencial", y la app sigue su flujo alternativo.
     */
    @ProxyMethod("authenticate")
    public static class Authenticate extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (args != null) {
                for (Object arg : args) {
                    if (arg == null) continue;
                    for (Class<?> itf : arg.getClass().getInterfaces()) {
                        if (itf.getName().endsWith("IBiometricServiceReceiver")) {
                            try {
                                // onError(modality, error, vendorCode): 14 = BIOMETRIC_ERROR_NO_DEVICE_CREDENTIAL
                                arg.getClass().getMethod("onError", int.class, int.class, int.class).invoke(arg, 0, 14, 0);
                            } catch (Throwable ignored) {
                            }
                        }
                    }
                }
            }
            Class<?> rt = method.getReturnType();
            return rt == long.class ? 0L : (rt == int.class ? 0 : null);
        }
    }
}
