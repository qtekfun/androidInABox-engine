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
 * Componentes {@code NotificationListenerService} de apps invitadas que el usuario ha autorizado
 * dentro del box (spec 06, R06.10), por usuario virtual. Se guarda en un fichero de texto
 * ({@code <userId>\t<paquete>/<clase>} por linea) que se relee en cada consulta: no hay cache que
 * invalidar entre procesos. Sin dependencias de Android para poder probarla en la JVM.
 */
public final class ListenerAccessStore {
    static final String FILE_NAME = "bb_nls_access";

    private final File mFile;

    public ListenerAccessStore(File filesDir) {
        mFile = new File(filesDir, FILE_NAME);
    }

    /** Componente en forma {@code paquete/clase} (la de {@code ComponentName.flattenToString}). */
    public synchronized boolean isGranted(int userId, String component) {
        return component != null && read().contains(userId + "\t" + component);
    }

    /** true si alguna clase del paquete esta autorizada para el usuario. */
    public synchronized boolean isPackageGranted(int userId, String packageName) {
        if (packageName == null) {
            return false;
        }
        String prefix = userId + "\t" + packageName + "/";
        for (String line : read()) {
            if (line.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    public synchronized void setGranted(int userId, String component, boolean granted) {
        if (component == null || component.indexOf('\n') >= 0 || component.indexOf('\t') >= 0) {
            return;
        }
        Set<String> all = read();
        String line = userId + "\t" + component;
        boolean changed = granted ? all.add(line) : all.remove(line);
        if (changed) {
            write(all);
        }
    }

    /** Componentes autorizados del usuario. */
    public synchronized List<String> list(int userId) {
        List<String> out = new ArrayList<>();
        String prefix = userId + "\t";
        for (String line : read()) {
            if (line.startsWith(prefix)) {
                out.add(line.substring(prefix.length()));
            }
        }
        return out;
    }

    /** Todos los pares {@code userId\tcomponente}, para reconectar al arrancar. */
    public synchronized List<String> listAll() {
        return new ArrayList<>(read());
    }

    private Set<String> read() {
        Set<String> out = new LinkedHashSet<>();
        if (!mFile.exists()) {
            return out;
        }
        try (BufferedReader r = new BufferedReader(new FileReader(mFile))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.indexOf('\t') > 0) {
                    out.add(line);
                }
            }
        } catch (IOException ignored) {
        }
        return out;
    }

    private void write(Set<String> all) {
        try (FileWriter w = new FileWriter(mFile, false)) {
            for (String line : all) {
                w.write(line);
                w.write('\n');
            }
        } catch (IOException ignored) {
        }
    }
}
