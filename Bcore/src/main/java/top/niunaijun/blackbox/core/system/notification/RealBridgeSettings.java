package top.niunaijun.blackbox.core.system.notification;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Ajustes del puente opcional de notificaciones del sistema real hacia los escuchas del box
 * (spec 06, 3.4, R06.12). Desactivado por defecto y con lista blanca de paquetes de origen: sin
 * activar el puente Y sin el paquete en la lista no se reenvia nada. Ficheros marcador/texto
 * releidos en cada consulta, porque los leen el proceso principal (servicio de escucha del host) y
 * {@code :black}. Sin dependencias de Android.
 */
public final class RealBridgeSettings {
    static final String ENABLED_FILE = "bb_nls_real_enabled";
    static final String ALLOW_FILE = "bb_nls_real_allow";

    private final File mDir;
    private final String mHostPackage;

    public RealBridgeSettings(File filesDir, String hostPackage) {
        mDir = filesDir;
        mHostPackage = hostPackage;
    }

    public boolean isEnabled() {
        return new File(mDir, ENABLED_FILE).exists();
    }

    public void setEnabled(boolean enabled) {
        File f = new File(mDir, ENABLED_FILE);
        if (enabled) {
            try {
                //noinspection ResultOfMethodCallIgnored
                f.createNewFile();
            } catch (IOException ignored) {
            }
        } else {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }

    public synchronized List<String> getAllowlist() {
        return new ArrayList<>(readAllow());
    }

    public synchronized void setAllowed(String packageName, boolean allowed) {
        if (packageName == null || packageName.trim().isEmpty() || packageName.indexOf('\n') >= 0) {
            return;
        }
        Set<String> all = readAllow();
        boolean changed = allowed ? all.add(packageName) : all.remove(packageName);
        if (changed) {
            try (FileWriter w = new FileWriter(new File(mDir, ALLOW_FILE), false)) {
                for (String p : all) {
                    w.write(p);
                    w.write('\n');
                }
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * Decide si una notificacion real de {@code sourcePackage} se reenvia. Nunca se reenvian las
     * del propio host: incluyen las que el motor publico en el sistema desde el box, que los
     * escuchas ya reciben por la via interna (evita duplicados).
     */
    public boolean shouldForward(String sourcePackage) {
        if (sourcePackage == null || sourcePackage.equals(mHostPackage) || !isEnabled()) {
            return false;
        }
        return readAllow().contains(sourcePackage);
    }

    private Set<String> readAllow() {
        Set<String> out = new LinkedHashSet<>();
        File f = new File(mDir, ALLOW_FILE);
        if (!f.exists()) {
            return out;
        }
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (!line.isEmpty()) {
                    out.add(line);
                }
            }
        } catch (IOException ignored) {
        }
        return out;
    }
}
