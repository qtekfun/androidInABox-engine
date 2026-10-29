package top.niunaijun.blackbox.fake.frameworks;

import top.niunaijun.blackbox.core.system.ServiceManager;
import top.niunaijun.blackbox.core.system.pm.IBInstallSessionService;

/** Cliente (en el proceso invitado) de las sesiones de PackageInstaller del box. */
public class BInstallSessionManager extends BlackManager<IBInstallSessionService> {
    private static final BInstallSessionManager sInstance = new BInstallSessionManager();

    public static BInstallSessionManager get() {
        return sInstance;
    }

    @Override
    protected String getServiceName() {
        return ServiceManager.INSTALL_SESSION;
    }
}
