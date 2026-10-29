package top.niunaijun.blackbox.utils;

/**
 * Los invitados (GMS) envian broadcasts con UserHandle.ALL / CURRENT (-1, -2, -3). El sistema real los
 * rechaza para una app normal (exige INTERACT_ACROSS_USERS_FULL), asi que al sistema real se le pide
 * siempre el usuario del propio host. El reparto entre usuarios del box lo resuelve el box.
 */
public final class BroadcastUserCompat {
    private BroadcastUserCompat() {
    }

    /** Usuario con el que se llama al sistema real: el del host si el pedido es un pseudo-usuario. */
    public static int realUserId(int requestedUserId, int hostUserId) {
        return requestedUserId < 0 ? hostUserId : requestedUserId;
    }

    /** Posicion del ultimo argumento int (el userId en broadcastIntent*), o -1 si no hay. */
    public static int lastIntIndex(Object[] args) {
        for (int i = args.length - 1; i >= 0; i--) {
            if (args[i] instanceof Integer) {
                return i;
            }
        }
        return -1;
    }

    /** Reescribe in situ el userId del ultimo int de args. */
    public static void rewriteUserId(Object[] args, int hostUserId) {
        int i = lastIntIndex(args);
        if (i >= 0) {
            args[i] = realUserId((Integer) args[i], hostUserId);
        }
    }
}
