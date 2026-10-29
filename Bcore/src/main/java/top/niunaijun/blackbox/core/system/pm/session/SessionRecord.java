package top.niunaijun.blackbox.core.system.pm.session;

import java.io.File;

/** Estado de una sesión de instalación del box. Solo datos, sin dependencias de Android. */
public class SessionRecord {
    /** Equivale a PackageInstaller.SessionParams.MODE_INHERIT_EXISTING. */
    public static final int MODE_INHERIT_EXISTING = 2;

    public final int id;
    public final int userId;
    public final String installerPackage;
    public final File dir;

    public String appPackageName;
    public String appLabel;
    public int mode = 1;
    public int installFlags;
    public long sizeBytes = -1;
    public float progress;
    public boolean sealed;

    SessionRecord(int id, int userId, String installerPackage, File dir) {
        this.id = id;
        this.userId = userId;
        this.installerPackage = installerPackage;
        this.dir = dir;
    }

    public boolean inheritsExisting() {
        return mode == MODE_INHERIT_EXISTING;
    }
}
