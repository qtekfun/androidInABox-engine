package top.niunaijun.blackbox.core.system.companion;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Mapa de asociaciones de {@code CompanionDeviceManager} a la app invitada que las pidio (spec 06,
 * 3.2). El sistema real registra todas las asociaciones a nombre del host, asi que el motor
 * decide aqui cuales ve cada invitado. El sistema devuelve la asociacion de forma asincrona (por el
 * callback de {@code associate}) y ese binder no se puede interponer, por eso la atribucion es por
 * diferencia: al pedir {@code associate} se anota el conjunto de ids que ya existia, y en la
 * siguiente consulta los ids nuevos sin dueno pasan al invitado pendiente mas reciente.
 *
 * Fichero de texto releido en cada consulta; sin dependencias de Android.
 */
public final class CompanionAssociationStore {
    static final String FILE_NAME = "bb_cdm_associations";
    /** Tiempo maximo que una peticion de asociacion sigue "pendiente". */
    static final long PENDING_TTL_MS = 5 * 60 * 1000L;

    private final File mFile;

    public CompanionAssociationStore(File filesDir) {
        mFile = new File(filesDir, FILE_NAME);
    }

    private static String owner(int userId, String pkg) {
        return userId + "\t" + pkg;
    }

    /** El invitado pide asociar; {@code knownIds}: asociaciones del host ya existentes. */
    public synchronized void onAssociateRequested(int userId, String pkg, int[] knownIds, long now) {
        State s = read();
        s.pending.remove(owner(userId, pkg));
        Set<Integer> known = new LinkedHashSet<>();
        for (int id : knownIds) {
            known.add(id);
        }
        s.pending.put(owner(userId, pkg), new Pending(known, now));
        write(s);
    }

    /**
     * Pone al dia el mapa con las asociaciones reales del host y devuelve las del invitado:
     * retira las que ya no existen y asigna las nuevas al invitado pendiente mas reciente.
     */
    public synchronized int[] reconcile(int userId, String pkg, int[] systemIds, long now) {
        State s = read();
        Set<Integer> present = new LinkedHashSet<>();
        for (int id : systemIds) {
            present.add(id);
        }
        s.owners.keySet().retainAll(present);
        for (java.util.Iterator<Pending> it = s.pending.values().iterator(); it.hasNext(); ) {
            if (now - it.next().at > PENDING_TTL_MS) {
                it.remove();
            }
        }

        for (int id : present) {
            if (s.owners.containsKey(id)) {
                continue;
            }
            String newest = null;
            long newestAt = Long.MIN_VALUE;
            for (Map.Entry<String, Pending> e : s.pending.entrySet()) {
                if (!e.getValue().known.contains(id) && e.getValue().at >= newestAt) {
                    newest = e.getKey();
                    newestAt = e.getValue().at;
                }
            }
            if (newest != null) {
                s.owners.put(id, newest);
            }
        }
        // un invitado con asociacion nueva ya no esta pendiente
        for (String owner : new ArrayList<>(s.pending.keySet())) {
            for (Map.Entry<Integer, String> e : s.owners.entrySet()) {
                if (e.getValue().equals(owner) && !s.pending.get(owner).known.contains(e.getKey())) {
                    s.pending.remove(owner);
                    break;
                }
            }
        }
        write(s);

        List<Integer> mine = new ArrayList<>();
        for (Map.Entry<Integer, String> e : s.owners.entrySet()) {
            if (e.getValue().equals(owner(userId, pkg))) {
                mine.add(e.getKey());
            }
        }
        int[] out = new int[mine.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = mine.get(i);
        }
        return out;
    }

    public synchronized boolean owns(int userId, String pkg, int associationId) {
        return owner(userId, pkg).equals(read().owners.get(associationId));
    }

    public synchronized void release(int associationId) {
        State s = read();
        if (s.owners.remove(associationId) != null) {
            write(s);
        }
    }

    // ---- fichero: "A\tid\tuserId\tpkg" (dueno) y "P\tuserId\tpkg\tat\tid,id,..." (pendiente) ----

    private static final class Pending {
        final Set<Integer> known;
        final long at;

        Pending(Set<Integer> known, long at) {
            this.known = known;
            this.at = at;
        }
    }

    private static final class State {
        final Map<Integer, String> owners = new LinkedHashMap<>();
        final Map<String, Pending> pending = new LinkedHashMap<>();
    }

    private State read() {
        State s = new State();
        if (!mFile.exists()) {
            return s;
        }
        try (BufferedReader r = new BufferedReader(new FileReader(mFile))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] p = line.split("\t", -1);
                try {
                    if (p[0].equals("A") && p.length == 4) {
                        s.owners.put(Integer.parseInt(p[1]), owner(Integer.parseInt(p[2]), p[3]));
                    } else if (p[0].equals("P") && p.length == 5) {
                        Set<Integer> known = new LinkedHashSet<>();
                        for (String id : p[4].split(",")) {
                            if (!id.isEmpty()) {
                                known.add(Integer.parseInt(id));
                            }
                        }
                        s.pending.put(owner(Integer.parseInt(p[1]), p[2]), new Pending(known, Long.parseLong(p[3])));
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
        return s;
    }

    private void write(State s) {
        try (FileWriter w = new FileWriter(mFile, false)) {
            for (Map.Entry<Integer, String> e : s.owners.entrySet()) {
                w.write("A\t" + e.getKey() + "\t" + e.getValue() + "\n");
            }
            for (Map.Entry<String, Pending> e : s.pending.entrySet()) {
                StringBuilder ids = new StringBuilder();
                for (int id : e.getValue().known) {
                    if (ids.length() > 0) {
                        ids.append(',');
                    }
                    ids.append(id);
                }
                w.write("P\t" + e.getKey() + "\t" + e.getValue().at + "\t" + ids + "\n");
            }
        } catch (IOException ignored) {
        }
    }
}
