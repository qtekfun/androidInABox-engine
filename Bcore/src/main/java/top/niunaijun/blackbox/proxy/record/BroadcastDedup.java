package top.niunaijun.blackbox.proxy.record;

import java.util.Iterator;
import java.util.LinkedHashSet;

/**
 * Un mismo broadcast del invitado llega al proceso del box una vez por cada receptor del host
 * registrado con un filtro que coincide (uno por app con ese filtro). Cada llamada reparte a
 * todos los receptores del box, asi que solo la primera debe procesarse: si no, un mensaje
 * c2dm se entregaria N veces.
 */
public final class BroadcastDedup {
    private final int capacity;
    private final LinkedHashSet<Long> seen = new LinkedHashSet<>();

    public BroadcastDedup(int capacity) {
        this.capacity = capacity;
    }

    /** {@code true} la primera vez que se ve el identificador. */
    public synchronized boolean firstTime(long id) {
        if (!seen.add(id)) {
            return false;
        }
        if (seen.size() > capacity) {
            Iterator<Long> it = seen.iterator();
            it.next();
            it.remove();
        }
        return true;
    }
}
