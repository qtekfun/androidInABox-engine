package top.niunaijun.blackbox.core.system.pm.split;

import java.io.File;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Decide por el contenido (no por la extensión) si un fichero es un APK, un bundle
 * ({@code .apks}, {@code .apkm}, {@code .xapk}, {@code .zip}) o algo no soportado.
 */
public class BundleDetector {
    public enum Kind {
        /** Zip con AndroidManifest.xml en la raíz. */
        APK,
        /** Zip sin manifiesto en la raíz que contiene al menos un {@code *.apk}. */
        BUNDLE,
        /** No es un zip o no contiene APK. */
        UNKNOWN
    }

    public static Kind detect(File file) {
        if (file == null || !file.isFile()) return Kind.UNKNOWN;
        try (ZipFile zip = new ZipFile(file)) {
            if (zip.getEntry("AndroidManifest.xml") != null) {
                return Kind.APK;
            }
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                if (!e.isDirectory() && SafeUnzip.isApkName(e.getName())) {
                    return Kind.BUNDLE;
                }
            }
        } catch (Exception ignored) {
        }
        return Kind.UNKNOWN;
    }
}
