package top.niunaijun.blackbox.core.system.user;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.system.Os;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.core.env.BEnvironment;
import top.niunaijun.blackbox.utils.Slog;

/**
 * Identidad de dispositivo de cada perfil (ADR-0008): android_id, android_id por app y número de serie
 * virtual. Cada perfil guarda UNA semilla aleatoria de 256 bits; todo lo demás se deriva de ella con HMAC,
 * así hay un único dato persistido por perfil.
 *
 * <p>Persistencia: {@code system/identity/<usuario>.conf}, texto con cabecera de versión
 * ({@code aiab-identity <n>}) y la línea {@code seed=<64 hex>}. Campos nuevos: solo líneas nuevas
 * al final, las líneas desconocidas se ignoran. Se crea una sola vez con enlace duro (atómico y sin
 * sobrescribir), por lo que varios procesos del box convergen en la misma semilla. Si el fichero existe
 * pero no se puede leer, NUNCA se reescribe: la semilla se deriva de su contenido, estable mientras
 * el fichero no cambie.
 *
 * <p>No se fabrican identificadores de hardware (IMEI, MAC): android_id y serie son valores que
 * Android también aleatoriza.
 */
public final class ProfileIdentity {
    private static final String TAG = "ProfileIdentity";
    private static final String HEADER = "aiab-identity";
    private static final int VERSION = 1;

    private static final Map<Integer, byte[]> sSeeds = new ConcurrentHashMap<>();
    private static final Map<String, String> sCertHashes = new ConcurrentHashMap<>();

    private ProfileIdentity() {
    }

    /** android_id del perfil (sin contexto de app): 16 hex en minúsculas. */
    public static String androidId(int userId) {
        return hex(derive(userId, "ssaid:"), 8);
    }

    /**
     * android_id para una app: como en Android 8+, depende del perfil y del certificado de firma de la app.
     * Si no se conoce el certificado se usa el del perfil.
     */
    public static String androidId(int userId, String packageName) {
        String cert = certHash(userId, packageName);
        return hex(derive(userId, "ssaid:" + (cert == null ? "" : cert)), 8);
    }

    /** Número de serie virtual del perfil (12 caracteres alfanuméricos en mayúsculas). */
    public static String serial(int userId) {
        return hex(derive(userId, "serial"), 6).toUpperCase();
    }

    private static String certHash(int userId, String packageName) {
        if (packageName == null) return null;
        String key = userId + "/" + packageName;
        String cached = sCertHashes.get(key);
        if (cached != null) return cached.isEmpty() ? null : cached;
        String value = "";
        try {
            PackageInfo pi = BlackBoxCore.getBPackageManager().getPackageInfo(packageName, PackageManager.GET_SIGNATURES, userId);
            if (pi != null && pi.signatures != null && pi.signatures.length > 0) {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                for (Signature s : pi.signatures) {
                    md.update(s.toByteArray());
                }
                value = hex(md.digest(), 32);
            }
        } catch (Throwable t) {
            Slog.w(TAG, "sin certificado de " + packageName + ": " + t);
        }
        if (!value.isEmpty()) {
            sCertHashes.put(key, value);
        }
        return value.isEmpty() ? null : value;
    }

    private static byte[] derive(int userId, String context) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(seed(userId), "HmacSHA256"));
            return mac.doFinal(context.getBytes(StandardCharsets.UTF_8));
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    private static byte[] seed(int userId) {
        byte[] cached = sSeeds.get(userId);
        if (cached != null) return cached;
        synchronized (ProfileIdentity.class) {
            cached = sSeeds.get(userId);
            if (cached == null) {
                cached = loadOrCreate(userId);
                sSeeds.put(userId, cached);
            }
            return cached;
        }
    }

    private static File file(int userId) {
        return new File(new File(BEnvironment.getSystemDir(), "identity"), userId + ".conf");
    }

    private static byte[] loadOrCreate(int userId) {
        File f = file(userId);
        byte[] existing = read(f);
        if (existing != null) return existing;
        if (f.exists()) {
            return fallbackFromContent(f);
        }
        byte[] fresh = new byte[32];
        new SecureRandom().nextBytes(fresh);
        File tmp = new File(f.getPath() + "." + android.os.Process.myPid() + ".tmp");
        try {
            File dir = f.getParentFile();
            if (dir != null) dir.mkdirs();
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write((HEADER + " " + VERSION + "\nseed=" + hex(fresh, 32) + "\n").getBytes(StandardCharsets.UTF_8));
                out.getFD().sync();
            }
            try {
                Os.link(tmp.getPath(), f.getPath());
            } catch (Throwable alreadyThere) {
                // otro proceso la creó antes: se usa la suya
            }
        } catch (Throwable t) {
            Slog.w(TAG, "no se pudo guardar la identidad del perfil " + userId + ": " + t);
        } finally {
            tmp.delete();
        }
        byte[] stored = read(f);
        return stored != null ? stored : fresh;
    }

    /** Lee la semilla; null si el fichero no existe o no tiene una línea seed= válida. */
    private static byte[] read(File f) {
        try {
            if (!f.isFile()) return null;
            byte[] raw = java.nio.file.Files.readAllBytes(f.toPath());
            for (String line : new String(raw, StandardCharsets.UTF_8).split("\n")) {
                line = line.trim();
                if (line.startsWith("seed=") && line.length() == 5 + 64) {
                    byte[] seed = unhex(line.substring(5));
                    if (seed != null) return seed;
                }
            }
        } catch (Throwable t) {
            Slog.w(TAG, "no se pudo leer " + f.getName() + ": " + t);
        }
        return null;
    }

    private static byte[] fallbackFromContent(File f) {
        try {
            Slog.w(TAG, "identidad ilegible en " + f.getName() + "; se conserva y se deriva de su contenido");
            return MessageDigest.getInstance("SHA-256").digest(java.nio.file.Files.readAllBytes(f.toPath()));
        } catch (Throwable t) {
            // ni se puede leer: semilla de proceso, sin tocar el fichero
            byte[] tmp = new byte[32];
            new SecureRandom().nextBytes(tmp);
            return tmp;
        }
    }

    private static String hex(byte[] b, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n && i < b.length; i++) {
            sb.append(Character.forDigit((b[i] >> 4) & 0xF, 16)).append(Character.forDigit(b[i] & 0xF, 16));
        }
        return sb.toString();
    }

    private static byte[] unhex(String s) {
        if (s.length() % 2 != 0) return null;
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(s.charAt(2 * i), 16);
            int lo = Character.digit(s.charAt(2 * i + 1), 16);
            if (hi < 0 || lo < 0) return null;
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }
}
