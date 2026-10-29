package top.niunaijun.blackbox.fake.frameworks;

import android.os.RemoteException;

import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.core.system.ServiceManager;
import top.niunaijun.blackbox.core.system.companion.IBCompanionDeviceService;

/** Cliente (en el proceso invitado) del mapa de asociaciones de CompanionDeviceManager. */
public class BCompanionDeviceManager extends BlackManager<IBCompanionDeviceService> {
    private static final BCompanionDeviceManager sInstance = new BCompanionDeviceManager();

    public static BCompanionDeviceManager get() {
        return sInstance;
    }

    @Override
    protected String getServiceName() {
        return ServiceManager.COMPANION_DEVICE;
    }

    public void onAssociateRequested(int[] knownIds) {
        try {
            getService().onAssociateRequested(BActivityThread.getUserId(), BActivityThread.getAppPackageName(), knownIds);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    /** Ids de asociacion del invitado, tras poner al dia el mapa con los {@code systemIds} reales. */
    public int[] reconcile(int[] systemIds) {
        try {
            return getService().reconcile(BActivityThread.getUserId(), BActivityThread.getAppPackageName(), systemIds);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return new int[0];
    }

    public boolean owns(int associationId) {
        try {
            return getService().owns(BActivityThread.getUserId(), BActivityThread.getAppPackageName(), associationId);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return false;
    }

    public void release(int associationId) {
        try {
            getService().release(associationId);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }
}
