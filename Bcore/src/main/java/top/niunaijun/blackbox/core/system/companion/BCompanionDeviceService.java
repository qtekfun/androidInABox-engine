package top.niunaijun.blackbox.core.system.companion;

import android.os.SystemClock;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.core.system.ISystemService;

/**
 * Mapa invitado -> asociaciones de CompanionDeviceManager, en {@code :black} (spec 06, 3.2).
 * La logica esta en {@link CompanionAssociationStore}; aqui solo se expone por binder.
 */
public class BCompanionDeviceService extends IBCompanionDeviceService.Stub implements ISystemService {
    private static final BCompanionDeviceService sService = new BCompanionDeviceService();

    private CompanionAssociationStore mStore;

    public static BCompanionDeviceService get() {
        return sService;
    }

    private synchronized CompanionAssociationStore store() {
        if (mStore == null) {
            mStore = new CompanionAssociationStore(BlackBoxCore.getContext().getFilesDir());
        }
        return mStore;
    }

    @Override
    public void systemReady() {
    }

    @Override
    public void onAssociateRequested(int userId, String packageName, int[] knownIds) {
        store().onAssociateRequested(userId, packageName, knownIds, SystemClock.elapsedRealtime());
    }

    @Override
    public int[] reconcile(int userId, String packageName, int[] systemIds) {
        return store().reconcile(userId, packageName, systemIds, SystemClock.elapsedRealtime());
    }

    @Override
    public boolean owns(int userId, String packageName, int associationId) {
        return store().owns(userId, packageName, associationId);
    }

    @Override
    public void release(int associationId) {
        store().release(associationId);
    }
}
