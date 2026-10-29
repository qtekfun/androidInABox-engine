package top.niunaijun.blackbox.core.system.companion;

interface IBCompanionDeviceService {
    void onAssociateRequested(int userId, String packageName, in int[] knownIds);

    int[] reconcile(int userId, String packageName, in int[] systemIds);

    boolean owns(int userId, String packageName, int associationId);

    void release(int associationId);
}
