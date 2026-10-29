package top.niunaijun.blackbox.utils;

import android.os.Build;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;



public class NativeUtils {
    public static final String TAG = "VirtualM";

    public static void copyNativeLib(File apk, File nativeLibDir) throws Exception {
        copyNativeLib(Collections.singletonList(apk), nativeLibDir);
    }

    /** Copia las libs de la ABI del box desde el base y los splits de configuración. */
    public static void copyNativeLib(List<File> apks, File nativeLibDir) throws Exception {
        copyNativeLib(apks, nativeLibDir, Arrays.asList(Build.CPU_ABI, "armeabi"));
    }

    /**
     * @param abis ABI por orden de preferencia; se usa la primera que tenga libs en algún APK
     */
    public static void copyNativeLib(List<File> apks, File nativeLibDir, List<String> abis) throws Exception {
        long startTime = System.currentTimeMillis();
        if (!nativeLibDir.exists()) {
            nativeLibDir.mkdirs();
        }
        try {
            String chosen = null;
            for (String abi : abis) {
                for (File apk : apks) {
                    if (hasLibs(apk, abi)) {
                        chosen = abi;
                        break;
                    }
                }
                if (chosen != null) break;
            }
            if (chosen == null) {
                return;
            }
            for (File apk : apks) {
                try (ZipFile zipfile = new ZipFile(apk.getAbsolutePath())) {
                    findAndCopyNativeLib(zipfile, chosen, nativeLibDir);
                }
            }
        } finally {
            Log.d(TAG, "Done! +" + (System.currentTimeMillis() - startTime) + "ms");
        }
    }

    private static boolean hasLibs(File apk, String abi) throws IOException {
        String prefix = "lib/" + abi + "/";
        try (ZipFile zipfile = new ZipFile(apk.getAbsolutePath())) {
            Enumeration<? extends ZipEntry> e = zipfile.entries();
            while (e.hasMoreElements()) {
                String name = e.nextElement().getName();
                if (name.startsWith(prefix) && name.endsWith(".so")) {
                    return true;
                }
            }
        }
        return false;
    }


    private static boolean findAndCopyNativeLib(ZipFile zipfile, String cpuArch, File nativeLibDir) throws Exception {
        Log.d(TAG, "Try to copy plugin's cup arch: " + cpuArch);
        boolean findLib = false;
        boolean findSo = false;
        byte buffer[] = null;
        String libPrefix = "lib/" + cpuArch + "/";
        ZipEntry entry;
        Enumeration e = zipfile.entries();

        while (e.hasMoreElements()) {
            entry = (ZipEntry) e.nextElement();
            String entryName = entry.getName();
            if (!findLib && !entryName.startsWith("lib/")) {
                continue;
            }
            findLib = true;
            if (!entryName.endsWith(".so") || !entryName.startsWith(libPrefix)) {
                continue;
            }

            if (buffer == null) {
                findSo = true;
                Log.d(TAG, "Found plugin's cup arch dir: " + cpuArch);
                buffer = new byte[8192];
            }

            String libName = entryName.substring(entryName.lastIndexOf('/') + 1);
            Log.d(TAG, "verify so " + libName);





            File libFile = new File(nativeLibDir, libName);
            if (libFile.exists() && libFile.length() == entry.getSize()) {
                Log.d(TAG, libName + " skip copy");
                continue;
            }
            FileOutputStream fos = new FileOutputStream(libFile);
            Log.d(TAG, "copy so " + entry.getName() + " of " + cpuArch);
            copySo(buffer, zipfile.getInputStream(entry), fos);
        }

        if (!findLib) {
            Log.d(TAG, "Fast skip all!");
            return true;
        }

        return findSo;
    }

    private static void copySo(byte[] buffer, InputStream input, OutputStream output) throws IOException {
        BufferedInputStream bufferedInput = new BufferedInputStream(input);
        BufferedOutputStream bufferedOutput = new BufferedOutputStream(output);
        int count;

        while ((count = bufferedInput.read(buffer)) > 0) {
            bufferedOutput.write(buffer, 0, count);
        }
        bufferedOutput.flush();
        bufferedOutput.close();
        output.close();
        bufferedInput.close();
        input.close();
    }
}
