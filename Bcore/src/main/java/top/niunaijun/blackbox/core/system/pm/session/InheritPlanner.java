package top.niunaijun.blackbox.core.system.pm.session;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Para sesiones MODE_INHERIT_EXISTING: decide qué APK ya instalados (base y splits) se copian al
 * staging para fusionarlos con los de la sesión. Un split de la sesión sustituye al instalado con
 * el mismo nombre; los instalados con distinto versionCode que el conjunto nuevo se descartan
 * porque el instalador exige un único versionCode.
 */
public final class InheritPlanner {
    public static class Candidate {
        /** null si es el APK base. */
        public final String splitName;
        public final String path;
        public final long versionCode;

        public Candidate(String splitName, String path, long versionCode) {
            this.splitName = splitName;
            this.path = path;
            this.versionCode = versionCode;
        }
    }

    private InheritPlanner() {
    }

    /**
     * @param stagedSplitNames nombres de split presentes en la sesión
     * @param stagedHasBase    la sesión trae un base
     * @param stagedVersion    versionCode de la sesión (-1 si no hay ninguno que comparar)
     */
    public static List<Candidate> plan(Set<String> stagedSplitNames, boolean stagedHasBase,
                                       long stagedVersion, List<Candidate> installed) {
        List<Candidate> out = new ArrayList<>();
        for (Candidate c : installed) {
            if (c.splitName == null) {
                if (stagedHasBase) continue;
            } else if (stagedSplitNames.contains(c.splitName)) {
                continue;
            }
            if (stagedVersion >= 0 && c.versionCode != stagedVersion) continue;
            out.add(c);
        }
        return out;
    }
}
