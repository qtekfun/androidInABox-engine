package top.niunaijun.blackbox.utils.compat;

import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.util.ArraySet;

import java.lang.reflect.Constructor;
import java.security.PublicKey;

import top.niunaijun.blackbox.utils.Slog;

/**
 * Firmas de un paquete del box con linaje de rotacion (APK Signature Scheme v3).
 * Los metodos de arrays son puros; {@link #buildSigningInfo} usa reflexion sobre la clase oculta.
 */
public final class SigningCompat {
    private static final String TAG = "SigningCompat";
    /** Todas las capacidades del linaje: instalacion, permisos, sharedUserId y auth. */
    private static final int PAST_CERT_FLAGS = 1 | 2 | 4 | 8;
    private static final int SCHEME_V3 = 3;

    private SigningCompat() {
    }

    public static boolean hasLineage(Object[] past) {
        return past != null && past.length > 0;
    }

    /**
     * Valor de {@code PackageInfo.signatures}: con rotacion el sistema devuelve solo el
     * certificado original (el mas antiguo); sin rotacion, los firmantes actuales.
     */
    public static <T> T[] legacySignatures(T[] current, T[] past) {
        if (hasLineage(past)) {
            return java.util.Arrays.copyOf(past, 1);
        }
        return current;
    }

    /** Historial completo (del mas antiguo al actual); sin rotacion, los firmantes actuales. */
    public static <T> T[] history(T[] current, T[] past) {
        return hasLineage(past) ? past : current;
    }

    /** Crea un {@link SigningInfo} con firmantes actuales e historial; {@code null} si no se puede. */
    public static SigningInfo buildSigningInfo(Signature[] current, Signature[] past) {
        if (current == null && !hasLineage(past)) {
            return null;
        }
        if (current == null) {
            current = new Signature[]{past[past.length - 1]};
        }
        try {
            for (Constructor<?> infoCtor : SigningInfo.class.getDeclaredConstructors()) {
                Class<?>[] p = infoCtor.getParameterTypes();
                if (p.length != 1 || p[0] == SigningInfo.class) continue;
                Object details = newDetails(p[0], current, hasLineage(past) ? past : null);
                if (details == null) continue;
                infoCtor.setAccessible(true);
                return (SigningInfo) infoCtor.newInstance(details);
            }
        } catch (Throwable t) {
            Slog.w(TAG, "No se pudo crear SigningInfo con linaje", t);
        }
        return null;
    }

    private static Object newDetails(Class<?> cls, Signature[] current, Signature[] past) throws Exception {
        for (Constructor<?> c : cls.getDeclaredConstructors()) {
            Class<?>[] t = c.getParameterTypes();
            Object[] args = null;
            if (t.length == 3 && t[0] == Signature[].class && t[1] == int.class && t[2] == Signature[].class) {
                args = new Object[]{current, SCHEME_V3, past};
            } else if (t.length == 5 && t[0] == Signature[].class && t[1] == int.class
                    && t[2] == ArraySet.class && t[3] == Signature[].class && t[4] == int[].class) {
                int[] flags = null;
                if (past != null) {
                    flags = new int[past.length];
                    java.util.Arrays.fill(flags, PAST_CERT_FLAGS);
                }
                args = new Object[]{current, SCHEME_V3, new ArraySet<PublicKey>(), past, flags};
            }
            if (args != null) {
                c.setAccessible(true);
                return c.newInstance(args);
            }
        }
        return null;
    }
}
