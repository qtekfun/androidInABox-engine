package top.niunaijun.blackbox.core.system.pm.split;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class ZipTestUtil {
    private ZipTestUtil() {
    }

    static File tempDir() throws IOException {
        return Files.createTempDirectory("splittest").toFile();
    }

    /** Pares nombre, contenido. */
    static File zip(File dir, String zipName, Object... nameAndData) throws IOException {
        File f = new File(dir, zipName);
        try (ZipOutputStream z = new ZipOutputStream(new FileOutputStream(f))) {
            for (int i = 0; i < nameAndData.length; i += 2) {
                z.putNextEntry(new ZipEntry((String) nameAndData[i]));
                Object d = nameAndData[i + 1];
                z.write(d instanceof byte[] ? (byte[]) d : ((String) d).getBytes("UTF-8"));
                z.closeEntry();
            }
        }
        return f;
    }

    static void deleteAll(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteAll(k);
        f.delete();
    }
}
