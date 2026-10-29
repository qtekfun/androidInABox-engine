package top.niunaijun.blackbox.core.system.pm.split;

import java.io.File;

public class SplitPaths {
    /** Ruta de código de un classloader: base + splits, separados por {@link File#pathSeparator}. */
    public static String dexPath(String sourceDir, String[] splitSourceDirs) {
        StringBuilder sb = new StringBuilder(sourceDir);
        if (splitSourceDirs != null) {
            for (String split : splitSourceDirs) {
                if (split != null && !split.isEmpty()) {
                    sb.append(File.pathSeparatorChar).append(split);
                }
            }
        }
        return sb.toString();
    }
}
