package top.niunaijun.blackbox.core.system.pm;

import android.content.IntentSender;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;

/** Sesiones de PackageInstaller del box (T03). Los SessionParams/SessionInfo viajan dentro de un Bundle. */
interface IBInstallSessionService {
    int createSession(in Bundle params, String installerPackage, int userId);
    boolean hasSession(int sessionId, int userId);
    ParcelFileDescriptor openWrite(int sessionId, String name, long offsetBytes, long lengthBytes);
    ParcelFileDescriptor openRead(int sessionId, String name);
    String[] getNames(int sessionId);
    void removeSplit(int sessionId, String name);
    void setProgress(int sessionId, float progress, boolean add);
    void updateAppLabel(int sessionId, String label);
    void abandon(int sessionId);
    void commit(int sessionId, in IntentSender statusReceiver);
    /** Bundle con "sessions": ArrayList de SessionInfo. installerPackage null = todas. */
    Bundle getSessions(int userId, String installerPackage);
    Bundle getSessionInfo(int sessionId, int userId);
    void uninstall(String packageName, in IntentSender statusReceiver, int userId);
}
