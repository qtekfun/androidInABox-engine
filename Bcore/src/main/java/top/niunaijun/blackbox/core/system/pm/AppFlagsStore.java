package top.niunaijun.blackbox.core.system.pm;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Opciones por app y por usuario virtual, como máscara de bits.
 * Persistencia en texto ("usuario:paquete=bits", una por línea) con escritura atómica
 * (fichero temporal + renombrado). No toca el parcel de BPackageSettings.
 */
public class AppFlagsStore {
    /** Simular el perfil de propiedades de un Pixel 6 (apagado por defecto). */
    public static final int FLAG_PROPERTY_SPOOF = 1;

    private final File mFile;
    private final Map<String, Integer> mFlags = new HashMap<>();

    public AppFlagsStore(File file) {
        mFile = file;
        load();
    }

    private static String key(String packageName, int userId) {
        return userId + ":" + packageName;
    }

    public synchronized boolean get(String packageName, int userId, int flag) {
        Integer bits = mFlags.get(key(packageName, userId));
        return bits != null && (bits & flag) != 0;
    }

    public synchronized void set(String packageName, int userId, int flag, boolean on) {
        String k = key(packageName, userId);
        int bits = mFlags.containsKey(k) ? mFlags.get(k) : 0;
        bits = on ? (bits | flag) : (bits & ~flag);
        if (bits == 0) {
            mFlags.remove(k);
        } else {
            mFlags.put(k, bits);
        }
        save();
    }

    private void load() {
        if (mFile == null || !mFile.isFile()) return;
        try (BufferedReader r = new BufferedReader(new FileReader(mFile))) {
            String line;
            while ((line = r.readLine()) != null) {
                int eq = line.lastIndexOf('=');
                if (eq <= 0) continue;
                try {
                    mFlags.put(line.substring(0, eq), Integer.parseInt(line.substring(eq + 1).trim()));
                } catch (NumberFormatException ignored) {
                    // línea corrupta: se ignora
                }
            }
        } catch (IOException ignored) {
            // sin opciones previas
        }
    }

    private void save() {
        if (mFile == null) return;
        File tmp = new File(mFile.getPath() + ".tmp");
        try {
            File parent = mFile.getParentFile();
            if (parent != null) parent.mkdirs();
            try (FileWriter w = new FileWriter(tmp)) {
                for (Map.Entry<String, Integer> e : new TreeMap<>(mFlags).entrySet()) {
                    w.write(e.getKey() + "=" + e.getValue() + "\n");
                }
            }
            if (!tmp.renameTo(mFile)) {
                throw new IOException("rename");
            }
        } catch (IOException ignored) {
            tmp.delete();
        }
    }
}
