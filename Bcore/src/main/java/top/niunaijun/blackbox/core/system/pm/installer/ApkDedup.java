package top.niunaijun.blackbox.core.system.pm.installer;

import android.system.Os;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.Map;

import top.niunaijun.blackbox.core.env.BEnvironment;
import top.niunaijun.blackbox.utils.FileUtils;
import top.niunaijun.blackbox.utils.Slog;

/**
 * Reutiliza en disco los APK idénticos de un mismo paquete entre perfiles (ADR-0008). Se usan
 * hard links: cada perfil conserva su propio fichero y borrar uno no afecta a los demás.
 */
public final class ApkDedup {
    private static final String TAG = "ApkDedup";

    private ApkDedup() {
    }

    /**
     * Carpeta de la copia de otro perfil cuyos ficheros coinciden (mismo tamaño y SHA-256) con todos
     * los de {@code wanted} (nombre en la carpeta de la app -> fichero origen), o null.
     */
    public static File findIdenticalCopy(int userId, String packageName, Map<String, File> wanted) {
        File[] users = BEnvironment.getUserAppsRoot().listFiles();
        if (users == null) return null;
        for (File userDir : users) {
            if (userDir.getName().equals(String.valueOf(userId))) continue;
            File candidate = new File(userDir, packageName);
            if (!candidate.isDirectory()) continue;
            if (matches(candidate, wanted)) return candidate;
        }
        return null;
    }

    private static boolean matches(File candidateDir, Map<String, File> wanted) {
        try {
            for (Map.Entry<String, File> e : wanted.entrySet()) {
                File other = new File(candidateDir, e.getKey());
                File mine = e.getValue();
                if (!other.isFile() || other.length() != mine.length()) return false;
                if (!sha256(other).equals(sha256(mine))) return false;
            }
            return true;
        } catch (Throwable t) {
            Slog.w(TAG, "No se pudo comparar con " + candidateDir.getName() + ": " + t);
            return false;
        }
    }

    static String sha256(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] buf = new byte[256 * 1024];
        try (FileInputStream in = new FileInputStream(f)) {
            int r;
            while ((r = in.read(buf)) > 0) md.update(buf, 0, r);
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    /** Hard link; false si no es posible (otro sistema de ficheros, permisos...). */
    public static boolean link(File existing, File target) {
        try {
            target.delete();
            Os.link(existing.getAbsolutePath(), target.getAbsolutePath());
            return true;
        } catch (Throwable t) {
            target.delete();
            return false;
        }
    }

    /** Enlaza el fichero si se puede y si no lo copia. */
    public static void linkOrCopy(File existing, File target) throws IOException {
        if (!link(existing, target)) {
            FileUtils.copyFile(existing, target);
        }
    }

    /**
     * Replica una carpeta de app: los .apk y las librerías nativas se enlazan, el resto
     * (package.conf, splits.conf) se copia porque cada copia lo reescribe.
     */
    public static void linkTree(File src, File dst) throws IOException {
        linkTree(src, dst, false);
    }

    /** Replica solo la carpeta de librerías nativas, enlazando todos sus ficheros. */
    public static void linkLibTree(File srcLib, File dstLib) throws IOException {
        linkTree(srcLib, dstLib, true);
    }

    private static void linkTree(File src, File dst, boolean linkAll) throws IOException {
        FileUtils.mkdirs(dst);
        File[] children = src.listFiles();
        if (children == null) return;
        for (File child : children) {
            File target = new File(dst, child.getName());
            if (child.isDirectory()) {
                linkTree(child, target, linkAll || child.getName().equals("lib"));
            } else if (linkAll || child.getName().endsWith(".apk")) {
                linkOrCopy(child, target);
            } else {
                FileUtils.copyFile(child, target);
            }
        }
    }
}
