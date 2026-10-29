package top.niunaijun.blackbox.core.system.keepalive;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Logica pura de la politica "mantener vivas" (spec 04): espera creciente entre reinicios y
 * (de)serializacion de la lista de apps marcadas. Sin dependencias de Android para poder probarla.
 */
public final class KeepAlivePolicy {
    /** Esperas entre reinicios tras fallos seguidos: 30 s, 1 min, 5 min (y 5 min a partir de ahi). */
    private static final long[] BACKOFF_MS = {30_000L, 60_000L, 300_000L};
    /** Un proceso que vive mas de esto reinicia el contador de fallos. */
    public static final long STABLE_AFTER_MS = 10 * 60_000L;
    /** Fallos seguidos tras los que se avisa al usuario y se deja de reintentar. */
    public static final int MAX_FAILURES = 5;

    private KeepAlivePolicy() {
    }

    /** Espera antes del reinicio numero {@code failures} (1 = primera muerte seguida). */
    public static long delayMs(int failures) {
        if (failures < 1) {
            return BACKOFF_MS[0];
        }
        return BACKOFF_MS[Math.min(failures, BACKOFF_MS.length) - 1];
    }

    /** Nuevo contador de fallos al morir un proceso que llevaba {@code aliveMs} vivo. */
    public static int nextFailures(int previousFailures, long aliveMs) {
        if (aliveMs >= STABLE_AFTER_MS) {
            return 1;
        }
        return previousFailures + 1;
    }

    public static boolean shouldGiveUp(int failures) {
        return failures >= MAX_FAILURES;
    }

    /** Clave estable de una entrada de la lista: "userId:paquete". */
    public static String key(String packageName, int userId) {
        return userId + ":" + packageName;
    }

    /** Lee una lista con una entrada por linea; ignora lineas vacias, comentarios (#) y mal formadas. */
    public static Set<String> parse(String text) {
        Set<String> out = new LinkedHashSet<>();
        if (text == null) {
            return out;
        }
        for (String line : text.split("\n")) {
            String l = line.trim();
            if (l.isEmpty() || l.startsWith("#")) {
                continue;
            }
            int sep = l.indexOf(':');
            if (sep <= 0 || sep == l.length() - 1) {
                continue;
            }
            try {
                Integer.parseInt(l.substring(0, sep));
            } catch (NumberFormatException e) {
                continue;
            }
            out.add(l);
        }
        return out;
    }

    public static String serialize(Set<String> keys) {
        StringBuilder sb = new StringBuilder();
        for (String k : keys) {
            sb.append(k).append('\n');
        }
        return sb.toString();
    }

    /** Paquete de una clave, o null si es invalida. */
    public static String packageOf(String key) {
        int sep = key.indexOf(':');
        return sep > 0 ? key.substring(sep + 1) : null;
    }

    public static int userOf(String key) {
        int sep = key.indexOf(':');
        try {
            return sep > 0 ? Integer.parseInt(key.substring(0, sep)) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
