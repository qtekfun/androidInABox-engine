package top.niunaijun.blackbox.fake.frameworks.notification;

/**
 * Limites de tamano de los bitmaps que cruzan el binder hacia el host (la transaccion admite
 * ~1 MB). Parte pura, sin dependencias de Android para poder probarla en la JVM.
 */
public final class IconBudget {
    public static final int SMALL_PX = 96;
    public static final int LARGE_PX = 256;
    /** Si lo ya reservado supera esto, se omite el icono grande. */
    public static final int MAX_BYTES = 700 * 1024;

    private int mUsed;

    /** Lado mayor reducido a {@code max} sin deformar; nunca devuelve 0. Sin tamano intrinseco usa {@code max}. */
    public static int[] fit(int w, int h, int max) {
        if (w <= 0 || h <= 0) {
            return new int[]{max, max};
        }
        if (w <= max && h <= max) {
            return new int[]{w, h};
        }
        float s = (float) max / Math.max(w, h);
        return new int[]{Math.max(1, Math.round(w * s)), Math.max(1, Math.round(h * s))};
    }

    public static int bytesOf(int w, int h) {
        return w * h * 4;
    }

    /** Reserva {@code bytes}; devuelve false (sin reservar) si no caben. */
    public synchronized boolean tryTake(int bytes) {
        if (mUsed + bytes > MAX_BYTES) {
            return false;
        }
        mUsed += bytes;
        return true;
    }

    /** Reserva siempre (iconos imprescindibles, como el pequeno). */
    public synchronized void take(int bytes) {
        mUsed += bytes;
    }

    public synchronized int used() {
        return mUsed;
    }
}
