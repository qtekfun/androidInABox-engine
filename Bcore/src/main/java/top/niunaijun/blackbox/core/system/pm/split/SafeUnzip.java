package top.niunaijun.blackbox.core.system.pm.split;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Extrae solo las entradas {@code *.apk} de un bundle con límites de entradas y de tamaño
 * descomprimido y protección contra zip-slip. El tamaño se cuenta con los bytes realmente
 * escritos (la cabecera del zip puede mentir). Las entradas se escriben siempre como ficheros
 * regulares, de modo que un enlace simbólico del zip no llega a crearse.
 */
public class SafeUnzip {
    public static final int MAX_ENTRIES = 512;
    public static final long MAX_TOTAL_BYTES = 4L * 1024 * 1024 * 1024;

    private final int maxEntries;
    private final long maxTotalBytes;

    public SafeUnzip() {
        this(MAX_ENTRIES, MAX_TOTAL_BYTES);
    }

    public SafeUnzip(int maxEntries, long maxTotalBytes) {
        this.maxEntries = maxEntries;
        this.maxTotalBytes = maxTotalBytes;
    }

    public static boolean isApkName(String name) {
        return name.toLowerCase(Locale.ROOT).endsWith(".apk");
    }

    /** Suma de tamaños declarados de las entradas {@code *.apk}, para comprobar el espacio libre. */
    public static long declaredApkSize(File zipFile) throws IOException {
        long total = 0;
        try (ZipFile zip = new ZipFile(zipFile)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                if (!e.isDirectory() && isApkName(e.getName()) && e.getSize() > 0) {
                    total += e.getSize();
                }
            }
        }
        return total;
    }

    /**
     * @return los APK extraídos dentro de {@code destDir}
     * @throws IOException si el zip incumple los límites o contiene rutas peligrosas
     */
    public List<File> extractApks(File zipFile, File destDir) throws IOException {
        if (!destDir.isDirectory() && !destDir.mkdirs()) {
            throw new IOException("No se pudo crear el directorio de extracción");
        }
        String destRoot = destDir.getCanonicalPath() + File.separator;
        List<File> out = new ArrayList<>();
        long total = 0;
        int count = 0;
        try (ZipFile zip = new ZipFile(zipFile)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (++count > maxEntries) {
                    throw new IOException("El bundle tiene demasiadas entradas (máximo " + maxEntries + ")");
                }
                String name = entry.getName();
                checkName(name);
                if (entry.isDirectory() || !isApkName(name)) {
                    continue;
                }
                File target = new File(destDir, name);
                String canonical = target.getCanonicalPath();
                if (!canonical.startsWith(destRoot)) {
                    throw new IOException("Ruta fuera del directorio de destino: " + name);
                }
                File parent = target.getParentFile();
                if (!parent.isDirectory() && !parent.mkdirs()) {
                    throw new IOException("No se pudo crear " + parent.getName());
                }
                total = copyLimited(zip.getInputStream(entry), target, total);
                out.add(target);
            }
        }
        return out;
    }

    private static void checkName(String name) throws IOException {
        if (name.isEmpty() || name.startsWith("/") || name.startsWith("\\") || name.indexOf('\0') >= 0
                || name.indexOf('\\') >= 0 || (name.length() > 1 && name.charAt(1) == ':')) {
            throw new IOException("Ruta de entrada no permitida: " + name);
        }
        for (String part : name.split("/")) {
            if (part.equals("..")) {
                throw new IOException("Ruta de entrada no permitida: " + name);
            }
        }
    }

    private long copyLimited(InputStream in, File target, long total) throws IOException {
        try (InputStream is = in; OutputStream os = new FileOutputStream(target)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = is.read(buf)) > 0) {
                total += n;
                if (total > maxTotalBytes) {
                    throw new IOException("El bundle supera el tamaño máximo descomprimido");
                }
                os.write(buf, 0, n);
            }
        }
        return total;
    }
}
