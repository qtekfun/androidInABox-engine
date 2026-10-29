package top.niunaijun.blackbox.utils;

/**
 * Permisos de "dispositivos cercanos" (Bluetooth y Wi-Fi cercano) que un invitado puede consultar
 * (spec 06, R06.4 y R06.5). Todo el box comparte la identidad del host, asi que el invitado debe ver
 * la concesion real del host, no la de su propio paquete (que el sistema no conoce). Sin
 * dependencias de Android para poder probarlo en la JVM.
 */
public final class NearbyPermissions {
    public static final String BLUETOOTH_SCAN = "android.permission.BLUETOOTH_SCAN";
    public static final String BLUETOOTH_CONNECT = "android.permission.BLUETOOTH_CONNECT";
    public static final String BLUETOOTH_ADVERTISE = "android.permission.BLUETOOTH_ADVERTISE";
    public static final String NEARBY_WIFI_DEVICES = "android.permission.NEARBY_WIFI_DEVICES";

    private NearbyPermissions() {
    }

    public static boolean isNearbyDevices(String permission) {
        return BLUETOOTH_SCAN.equals(permission)
                || BLUETOOTH_CONNECT.equals(permission)
                || BLUETOOTH_ADVERTISE.equals(permission)
                || NEARBY_WIFI_DEVICES.equals(permission);
    }

    /** Version de Android a partir de la cual el permiso existe; antes no hay nada que conceder. */
    public static int sinceSdk(String permission) {
        return NEARBY_WIFI_DEVICES.equals(permission) ? 33 : 31;
    }
}
