package top.niunaijun.blackbox.core.system.pm.split;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Elige los splits que se instalan. Los splits no de configuración se instalan todos; los de
 * configuración se filtran por ABI, densidad e idioma, por separado para cada split al que
 * configuran (el base o un módulo de funcionalidad). Los de otra clase se instalan.
 */
public class SplitSelector {
    private static final Map<String, Integer> DENSITIES = new LinkedHashMap<>();

    static {
        DENSITIES.put("ldpi", 120);
        DENSITIES.put("mdpi", 160);
        DENSITIES.put("tvdpi", 213);
        DENSITIES.put("hdpi", 240);
        DENSITIES.put("xhdpi", 320);
        DENSITIES.put("xxhdpi", 480);
        DENSITIES.put("xxxhdpi", 640);
    }

    private static final List<String> ABIS = Arrays.asList(
            "arm64_v8a", "armeabi_v7a", "armeabi", "x86_64", "x86", "mips64", "mips", "riscv64");
    private static final Pattern LOCALE = Pattern.compile("^[a-z]{2,3}(_r?[A-Za-z]{2})?$|^b\\+.+$");

    public static class Result {
        public final List<SplitInfo> selected = new ArrayList<>();
        public final List<SplitInfo> discarded = new ArrayList<>();
        /** Había splits de ABI para algún destino y ninguno es compatible con el box. */
        public boolean abiMismatch;
    }

    /**
     * @param abis       ABI del box por orden de preferencia, p. ej. {@code arm64-v8a}
     * @param densityDpi densidad de pantalla del dispositivo
     * @param locales    idiomas del sistema, p. ej. {@code es-ES}, {@code en}
     */
    public static Result select(SplitSet set, List<String> abis, int densityDpi, List<String> locales) {
        Result r = new Result();
        List<String> wantedAbis = new ArrayList<>();
        for (String a : abis) wantedAbis.add(norm(a));
        List<String> wantedLangs = new ArrayList<>();
        wantedLangs.add("en");
        for (String l : locales) wantedLangs.add(l.replace('-', '_'));

        // destino (configForSplit; "" = base) -> candidatos por clase
        Map<String, List<SplitInfo>> abiByTarget = new LinkedHashMap<>();
        Map<String, List<SplitInfo>> densByTarget = new LinkedHashMap<>();

        for (SplitInfo s : set.splits) {
            if (s.configForSplit == null) {
                r.selected.add(s);
                continue;
            }
            String suffix = configSuffix(s.splitName);
            String target = s.configForSplit;
            if (ABIS.contains(suffix)) {
                abiByTarget.computeIfAbsent(target, k -> new ArrayList<>()).add(s);
            } else if (DENSITIES.containsKey(suffix)) {
                densByTarget.computeIfAbsent(target, k -> new ArrayList<>()).add(s);
            } else if (LOCALE.matcher(suffix).matches()) {
                if (matchesLocale(suffix, wantedLangs)) r.selected.add(s);
                else r.discarded.add(s);
            } else {
                r.selected.add(s);
            }
        }

        for (List<SplitInfo> group : abiByTarget.values()) {
            SplitInfo chosen = null;
            for (String abi : wantedAbis) {
                for (SplitInfo s : group) {
                    if (configSuffix(s.splitName).equals(abi)) {
                        chosen = s;
                        break;
                    }
                }
                if (chosen != null) break;
            }
            for (SplitInfo s : group) {
                if (s == chosen) r.selected.add(s);
                else r.discarded.add(s);
            }
            if (chosen == null) r.abiMismatch = true;
        }

        for (List<SplitInfo> group : densByTarget.values()) {
            SplitInfo chosen = null;
            int bestDiff = Integer.MAX_VALUE;
            for (SplitInfo s : group) {
                int dpi = DENSITIES.get(configSuffix(s.splitName));
                int diff = Math.abs(dpi - densityDpi);
                if (diff < bestDiff || (diff == bestDiff && dpi > DENSITIES.get(configSuffix(chosen.splitName)))) {
                    bestDiff = diff;
                    chosen = s;
                }
            }
            for (SplitInfo s : group) {
                if (s == chosen) r.selected.add(s);
                else r.discarded.add(s);
            }
        }

        // Mantener el orden de entrada
        List<SplitInfo> ordered = new ArrayList<>();
        for (SplitInfo s : set.splits) {
            if (r.selected.contains(s)) ordered.add(s);
        }
        r.selected.clear();
        r.selected.addAll(ordered);
        return r;
    }

    /** {@code config.arm64_v8a} -> {@code arm64_v8a}; {@code feature_x.config.es} -> {@code es}. */
    static String configSuffix(String splitName) {
        int i = splitName.lastIndexOf("config.");
        if (i >= 0 && (i == 0 || splitName.charAt(i - 1) == '.')) {
            return splitName.substring(i + "config.".length());
        }
        int dot = splitName.lastIndexOf('.');
        return dot >= 0 ? splitName.substring(dot + 1) : splitName;
    }

    private static String norm(String abi) {
        return abi.replace('-', '_').toLowerCase(Locale.ROOT);
    }

    private static boolean matchesLocale(String suffix, List<String> wanted) {
        if (suffix.startsWith("b+")) {
            // etiqueta BCP-47 de bundletool: b+sr+Latn -> sr
            String[] parts = suffix.split("\\+");
            suffix = parts.length > 1 ? parts[1] : suffix;
        }
        String lang = suffix;
        String region = null;
        int us = suffix.indexOf('_');
        if (us > 0) {
            lang = suffix.substring(0, us);
            region = suffix.substring(us + 1);
            if (region.startsWith("r")) region = region.substring(1);
        }
        for (String w : wanted) {
            String wl = w;
            String wr = null;
            int u = w.indexOf('_');
            if (u > 0) {
                wl = w.substring(0, u);
                wr = w.substring(u + 1);
            }
            if (!wl.equalsIgnoreCase(lang)) continue;
            if (region == null || wr == null || region.equalsIgnoreCase(wr)) return true;
        }
        return false;
    }
}
