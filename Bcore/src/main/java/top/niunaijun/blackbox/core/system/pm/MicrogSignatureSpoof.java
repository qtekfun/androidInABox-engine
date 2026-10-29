package top.niunaijun.blackbox.core.system.pm;

import android.content.pm.Signature;
import android.os.Bundle;

import java.util.Base64;

/**
 * Suplantación de firma para microG dentro del box (ADR-0007).
 * Solo se aplica si el paquete declara la meta-data de aplicación "fake-signature" y el perfil
 * tiene el modo microG activo. Nunca afecta al sistema real ni a otros perfiles.
 */
public final class MicrogSignatureSpoof {
    public static final String META_FAKE_SIGNATURE = "fake-signature";
    public static final String PERM_FAKE_PACKAGE_SIGNATURE = "android.permission.FAKE_PACKAGE_SIGNATURE";

    private MicrogSignatureSpoof() {
    }

    /**
     * Decisión pura: devuelve el certificado falso (bytes) o null si no hay que suplantar.
     * El valor de la meta-data es el certificado en hexadecimal (convención de microG) o,
     * si no es hexadecimal válido, en Base64.
     */
    public static byte[] decide(Object metaValue, boolean microgMode) {
        if (!microgMode || !(metaValue instanceof String)) return null;
        String v = ((String) metaValue).trim();
        if (v.isEmpty()) return null;
        byte[] hex = parseHex(v);
        if (hex != null) return hex;
        try {
            byte[] b = Base64.getDecoder().decode(v);
            return b.length > 0 ? b : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static byte[] parseHex(String s) {
        int n = s.length();
        if (n == 0 || (n & 1) != 0) return null;
        byte[] out = new byte[n / 2];
        for (int i = 0; i < n; i += 2) {
            int hi = Character.digit(s.charAt(i), 16);
            int lo = Character.digit(s.charAt(i + 1), 16);
            if (hi < 0 || lo < 0) return null;
            out[i / 2] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    /** Firmas falsas del paquete para el perfil, o null si no se suplanta. */
    public static Signature[] fakeSignatures(Bundle appMetaData, int userId) {
        if (appMetaData == null) return null;
        Object v = appMetaData.get(META_FAKE_SIGNATURE);
        if (v == null) return null;
        byte[] cert = decide(v, BPackageManagerService.get().isMicrogProfile(userId));
        return cert == null ? null : new Signature[]{new Signature(cert)};
    }
}
