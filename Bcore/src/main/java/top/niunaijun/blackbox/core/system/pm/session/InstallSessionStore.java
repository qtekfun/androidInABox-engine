package top.niunaijun.blackbox.core.system.pm.session;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Almacén en memoria de sesiones de instalación, con un directorio de staging por sesión bajo
 * {@code root}. Lógica pura (testeable en JVM); el servicio de :black solo lo expone por binder.
 */
public class InstallSessionStore {
    private final File mRoot;
    private final Map<Integer, SessionRecord> mSessions = new LinkedHashMap<>();
    private int mNextId = 1000;

    public InstallSessionStore(File root) {
        mRoot = root;
    }

    /** Borra restos de sesiones anteriores (el almacén no se persiste). */
    public synchronized void clearStale() {
        deleteRecursively(mRoot);
        mRoot.mkdirs();
    }

    public synchronized SessionRecord create(int userId, String installerPackage) throws IOException {
        int id = mNextId++;
        File dir = new File(mRoot, String.valueOf(id));
        if (!dir.mkdirs() && !dir.isDirectory()) {
            throw new IOException("No se pudo crear el staging de la sesión " + id);
        }
        SessionRecord rec = new SessionRecord(id, userId, installerPackage, dir);
        mSessions.put(id, rec);
        return rec;
    }

    public synchronized SessionRecord get(int id) {
        return mSessions.get(id);
    }

    /** Sesión {@code id} solo si pertenece al usuario del box indicado. */
    public synchronized SessionRecord get(int id, int userId) {
        SessionRecord r = mSessions.get(id);
        return r != null && r.userId == userId ? r : null;
    }

    /** Sesiones del usuario; con {@code installerPackage} no nulo, solo las de ese instalador. */
    public synchronized List<SessionRecord> list(int userId, String installerPackage) {
        List<SessionRecord> out = new ArrayList<>();
        for (SessionRecord r : mSessions.values()) {
            if (r.userId != userId) continue;
            if (installerPackage != null && !installerPackage.equals(r.installerPackage)) continue;
            out.add(r);
        }
        return out;
    }

    /** Quita la sesión y su staging. */
    public synchronized void remove(int id) {
        SessionRecord r = mSessions.remove(id);
        if (r != null) {
            deleteRecursively(r.dir);
        }
    }

    /** Quita la sesión del almacén sin borrar el staging (lo consume el instalador). */
    public synchronized void forget(int id) {
        mSessions.remove(id);
    }

    /** Un nombre de fichero de sesión válido: sin separadores ni componentes de ruta. */
    public static boolean isValidName(String name) {
        if (name == null || name.isEmpty() || name.length() > 255) return false;
        if (name.equals(".") || name.equals("..")) return false;
        return name.indexOf('/') < 0 && name.indexOf('\\') < 0 && name.indexOf('\0') < 0;
    }

    /** Fichero de staging para {@code name}; lanza IllegalArgumentException si el nombre no es válido. */
    public static File stagedFile(SessionRecord rec, String name) {
        if (!isValidName(name)) {
            throw new IllegalArgumentException("Nombre de fichero inválido: " + name);
        }
        return new File(rec.dir, name);
    }

    /** Nombres de los ficheros ya escritos en la sesión, ordenados. */
    public static String[] stagedNames(SessionRecord rec) {
        File[] files = rec.dir.listFiles(File::isFile);
        if (files == null) return new String[0];
        String[] names = new String[files.length];
        for (int i = 0; i < files.length; i++) names[i] = files[i].getName();
        java.util.Arrays.sort(names);
        return names;
    }

    static void deleteRecursively(File f) {
        File[] children = f.listFiles();
        if (children != null) {
            for (File c : children) deleteRecursively(c);
        }
        f.delete();
    }
}
