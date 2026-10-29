package top.niunaijun.blackbox.core.system.pm.split;

import java.io.File;

/**
 * Atributos del manifiesto de un APK relevantes para decidir si es base o split.
 */
public class SplitInfo {
    public File file;
    public String packageName;
    /** Atributo {@code split}; null si el APK es el base. */
    public String splitName;
    /** Atributo {@code configForSplit}; null si no es un split de configuración. */
    public String configForSplit;
    public boolean isFeatureSplit;
    public boolean isSplitRequired;
    public boolean isolatedSplits;
    public long versionCode;

    public boolean isBase() {
        return splitName == null;
    }

    public boolean isConfig() {
        return splitName != null && configForSplit != null;
    }

    @Override
    public String toString() {
        return "SplitInfo{" + packageName + ", split=" + splitName + ", configForSplit=" + configForSplit
                + ", v=" + versionCode + ", file=" + (file == null ? null : file.getName()) + "}";
    }
}
