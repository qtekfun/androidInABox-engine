package top.niunaijun.blackbox.fake.service;

import android.os.IBinder;

import black.android.os.BRServiceManager;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;

/**
 * Servicio de suscripciones de telefonía ("isub"). GMS consulta datos de SIM que el sistema reserva a apps
 * privilegiadas (READ_PRIVILEGED_PHONE_STATE). Al pasar por el proxy, la regla de GooglePrivileged
 * convierte esas SecurityException en el valor por defecto para las apps de Google del box.
 */
public class ISubProxy extends BinderInvocationStub {
    private static final String SERVICE = "isub";

    public ISubProxy() {
        super(BRServiceManager.get().getService(SERVICE));
    }

    @Override
    protected Object getWho() {
        try {
            IBinder binder = BRServiceManager.get().getService(SERVICE);
            Class<?> stub = Class.forName("com.android.internal.telephony.ISub$Stub");
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
}
