package top.niunaijun.blackbox.core.system.notification;

/**
 * Convencion de ids reales (los que ve el NotificationManager del host) para lo que publica
 * una app invitada: {@code bb:<userId>:<paquete>[:<id del invitado>]}. El paquete no contiene
 * ':' y el userId es un entero, de modo que el resto es siempre el id del invitado, con o sin
 * ':' o '@'. Sin dependencias de Android para poder probarla en la JVM.
 */
public final class NotificationIds {
    public static final String PREFIX = "bb:";

    private NotificationIds() {
    }

    /** {@code bb:<userId>:<paquete>:}, prefijo de todo lo de (paquete, perfil). */
    public static String prefix(int userId, String packageName) {
        return PREFIX + userId + ":" + packageName + ":";
    }

    /** Id real de tag, canal o grupo de notificaciones; un tag nulo se trata como vacio. */
    public static String scoped(int userId, String packageName, String guestId) {
        return prefix(userId, packageName) + (guestId == null ? "" : guestId);
    }

    /** Id real del grupo de canales por defecto de (paquete, perfil), sin ':' final. */
    public static String packageGroup(int userId, String packageName) {
        return PREFIX + userId + ":" + packageName;
    }

    public static boolean belongsTo(String realId, int userId, String packageName) {
        return realId != null && realId.startsWith(prefix(userId, packageName));
    }

    /**
     * True si el id real (tag, canal, grupo de canales, incluido el de paquete sin ':' final) es
     * de ese paquete y perfil; {@code userId == -1} (USER_ALL) acepta cualquier perfil.
     */
    public static boolean matches(String realId, int userId, String packageName) {
        if (realId == null || !realId.startsWith(PREFIX)) {
            return false;
        }
        String[] p = realId.split(":", 4);
        if (p.length < 3 || !p[2].equals(packageName)) {
            return false;
        }
        if (userId == -1) {
            return true;
        }
        return p[1].equals(Integer.toString(userId));
    }

    /** Devuelve el id del invitado, o null si {@code realId} no es de este espacio. */
    public static String guestId(String realId) {
        String[] p = split(realId);
        return p == null ? null : p[2];
    }

    /** {userId, paquete, idInvitado} o null si no tiene la forma {@code bb:<n>:<paquete>:<resto>}. */
    public static String[] split(String realId) {
        if (realId == null || !realId.startsWith(PREFIX)) {
            return null;
        }
        String[] p = realId.split(":", 4);
        if (p.length != 4 || p[2].isEmpty()) {
            return null;
        }
        try {
            Integer.parseInt(p[1]);
        } catch (NumberFormatException e) {
            return null;
        }
        return new String[]{p[1], p[2], p[3]};
    }
}
