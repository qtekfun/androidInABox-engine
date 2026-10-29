package top.niunaijun.blackbox.core.system.pm.split;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Conjunto validado de un base y sus splits (un único paquete, una única versión).
 */
public class SplitSet {
    public final SplitInfo base;
    /** Splits en el orden de entrada, sin el base. */
    public final List<SplitInfo> splits;

    private SplitSet(SplitInfo base, List<SplitInfo> splits) {
        this.base = base;
        this.splits = Collections.unmodifiableList(splits);
    }

    public String getPackageName() {
        return base.packageName;
    }

    public static SplitSet of(List<SplitInfo> apks) throws InvalidSplitSetException {
        if (apks == null || apks.isEmpty()) {
            throw new InvalidSplitSetException("No hay ningún APK");
        }
        SplitInfo base = null;
        List<SplitInfo> splits = new ArrayList<>();
        for (SplitInfo info : apks) {
            if (info.packageName == null || info.packageName.isEmpty()) {
                throw new InvalidSplitSetException("APK sin nombre de paquete: " + nameOf(info));
            }
            if (info.isBase()) {
                if (base != null) {
                    throw new InvalidSplitSetException("Hay más de un APK base: " + nameOf(base) + ", " + nameOf(info));
                }
                base = info;
            } else {
                splits.add(info);
            }
        }
        if (base == null) {
            throw new InvalidSplitSetException("Falta el APK base");
        }
        Set<String> names = new HashSet<>();
        for (SplitInfo s : splits) {
            if (!base.packageName.equals(s.packageName)) {
                throw new InvalidSplitSetException("Nombres de paquete distintos: " + base.packageName
                        + " y " + s.packageName);
            }
            if (s.versionCode != base.versionCode) {
                throw new InvalidSplitSetException("El split " + s.splitName + " tiene versionCode "
                        + s.versionCode + " y el base " + base.versionCode);
            }
            if (!names.add(s.splitName)) {
                throw new InvalidSplitSetException("Split duplicado: " + s.splitName);
            }
        }
        return new SplitSet(base, splits);
    }

    private static String nameOf(SplitInfo info) {
        return info.file != null ? info.file.getName() : String.valueOf(info.splitName);
    }
}
