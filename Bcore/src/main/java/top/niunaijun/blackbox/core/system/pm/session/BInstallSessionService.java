package top.niunaijun.blackbox.core.system.pm.session;

import android.content.Context;
import android.content.IntentSender;
import android.content.pm.PackageInstaller;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.system.Os;
import android.system.OsConstants;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.core.env.BEnvironment;
import top.niunaijun.blackbox.core.system.ISystemService;
import top.niunaijun.blackbox.core.system.pm.BPackageManagerService;
import top.niunaijun.blackbox.core.system.pm.BPackageSettings;
import top.niunaijun.blackbox.core.system.pm.IBInstallSessionService;
import top.niunaijun.blackbox.core.system.pm.split.SplitInfo;
import top.niunaijun.blackbox.core.system.pm.split.SplitManifestReader;
import top.niunaijun.blackbox.entity.pm.InstallOption;
import top.niunaijun.blackbox.entity.pm.InstallResult;
import top.niunaijun.blackbox.utils.Slog;

/**
 * Sesiones de PackageInstaller dentro del box (T03), en {@code :black}. El invitado (Play Store)
 * escribe los APK en un staging por sesión y al confirmar se instalan con el instalador del box
 * (conjunto de splits, fusionando los ya instalados si la sesión es MODE_INHERIT_EXISTING).
 */
public class BInstallSessionService extends IBInstallSessionService.Stub implements ISystemService {
    public static final String TAG = "BInstallSessionService";
    private static final BInstallSessionService sService = new BInstallSessionService();

    private InstallSessionStore mStore;
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    public static BInstallSessionService get() {
        return sService;
    }

    private synchronized InstallSessionStore store() {
        if (mStore == null) {
            mStore = new InstallSessionStore(new File(BEnvironment.getCacheDir(), "sessions"));
            mStore.clearStale();
        }
        return mStore;
    }

    @Override
    public void systemReady() {
    }

    @Override
    public int createSession(Bundle params, String installerPackage, int userId) {
        try {
            SessionRecord rec = store().create(userId, installerPackage);
            if (params != null) {
                params.setClassLoader(PackageInstaller.SessionParams.class.getClassLoader());
                SessionParamsReflect.readParams(params.getParcelable("params"), rec);
            }
            Slog.d(TAG, "Sesión de instalación " + rec.id + " creada (" + rec.appPackageName
                    + ", instalador " + installerPackage + ", user " + userId + ", mode " + rec.mode + ")");
            return rec.id;
        } catch (IOException e) {
            throw new IllegalStateException(e.getMessage());
        }
    }

    private SessionRecord require(int sessionId) {
        SessionRecord rec = store().get(sessionId);
        if (rec == null) {
            throw new SecurityException("Sesión de instalación desconocida: " + sessionId);
        }
        return rec;
    }

    @Override
    public boolean hasSession(int sessionId, int userId) {
        return store().get(sessionId, userId) != null;
    }

    @Override
    public ParcelFileDescriptor openWrite(int sessionId, String name, long offsetBytes, long lengthBytes) {
        SessionRecord rec = require(sessionId);
        if (rec.sealed) throw new IllegalStateException("La sesión " + sessionId + " ya está confirmada");
        try {
            File f = InstallSessionStore.stagedFile(rec, name);
            ParcelFileDescriptor pfd = ParcelFileDescriptor.open(f,
                    ParcelFileDescriptor.MODE_READ_WRITE | ParcelFileDescriptor.MODE_CREATE);
            if (offsetBytes > 0) {
                Os.lseek(pfd.getFileDescriptor(), offsetBytes, OsConstants.SEEK_SET);
            }
            return pfd;
        } catch (Exception e) {
            throw new IllegalStateException("openWrite " + name + ": " + e);
        }
    }

    @Override
    public ParcelFileDescriptor openRead(int sessionId, String name) {
        SessionRecord rec = require(sessionId);
        try {
            return ParcelFileDescriptor.open(InstallSessionStore.stagedFile(rec, name),
                    ParcelFileDescriptor.MODE_READ_ONLY);
        } catch (Exception e) {
            throw new IllegalStateException("openRead " + name + ": " + e);
        }
    }

    @Override
    public String[] getNames(int sessionId) {
        return InstallSessionStore.stagedNames(require(sessionId));
    }

    @Override
    public void removeSplit(int sessionId, String name) {
        SessionRecord rec = require(sessionId);
        File f = InstallSessionStore.stagedFile(rec, name);
        if (!f.delete() && f.exists()) {
            Slog.w(TAG, "No se pudo borrar " + name + " de la sesión " + sessionId);
        }
    }

    @Override
    public void setProgress(int sessionId, float progress, boolean add) {
        SessionRecord rec = store().get(sessionId);
        if (rec == null) return;
        float v = add ? rec.progress + progress : progress;
        rec.progress = Math.max(0f, Math.min(1f, v));
    }

    @Override
    public void updateAppLabel(int sessionId, String label) {
        SessionRecord rec = store().get(sessionId);
        if (rec != null) rec.appLabel = label;
    }

    @Override
    public void abandon(int sessionId) {
        Slog.d(TAG, "Sesión de instalación " + sessionId + " abandonada");
        store().remove(sessionId);
    }

    @Override
    public void commit(int sessionId, IntentSender statusReceiver) {
        final SessionRecord rec = require(sessionId);
        if (rec.sealed) throw new IllegalStateException("La sesión " + sessionId + " ya está confirmada");
        rec.sealed = true;
        mExecutor.execute(() -> runCommit(rec, statusReceiver));
    }

    private void runCommit(SessionRecord rec, IntentSender receiver) {
        String pkg = rec.appPackageName;
        int status = InstallStatus.STATUS_FAILURE;
        String msg = null;
        try {
            String[] names = InstallSessionStore.stagedNames(rec);
            if (names.length == 0) {
                status = InstallStatus.STATUS_FAILURE_INVALID;
                msg = "La sesión no tiene ficheros";
            } else {
                String detected = inheritExisting(rec);
                if (detected != null) pkg = detected;
                InstallResult result = BPackageManagerService.get().installPackageAsUser(
                        rec.dir.getAbsolutePath(),
                        InstallOption.installByStorage().makeSplitSet(), rec.userId);
                if (result != null && result.packageName != null) pkg = result.packageName;
                if (result != null && result.success) {
                    status = InstallStatus.STATUS_SUCCESS;
                    Slog.d(TAG, "Sesión de instalación " + rec.id + " confirmada: " + pkg
                            + " (user " + rec.userId + ")");
                } else {
                    msg = result == null ? "Resultado de instalación vacío" : result.msg;
                    Slog.w(TAG, "Sesión de instalación " + rec.id + " fallida: " + pkg
                            + " (user " + rec.userId + "): " + msg);
                }
            }
        } catch (Throwable t) {
            msg = String.valueOf(t);
            Slog.w(TAG, "Sesión de instalación " + rec.id + " con error", t);
        } finally {
            // El instalador borra el staging si llegó a usarlo; si no, lo borra el almacén.
            store().remove(rec.id);
        }
        send(receiver, InstallStatus.extras(status, msg, pkg, rec.id));
    }

    /** Copia al staging los APK instalados que la sesión hereda; devuelve el paquete detectado. */
    private String inheritExisting(SessionRecord rec) {
        Set<String> stagedSplits = new HashSet<>();
        boolean stagedHasBase = false;
        long stagedVersion = -1;
        String pkg = null;
        for (String name : InstallSessionStore.stagedNames(rec)) {
            try {
                SplitInfo info = SplitManifestReader.read(new File(rec.dir, name));
                if (pkg == null) pkg = info.packageName;
                stagedVersion = info.versionCode;
                if (info.isBase()) stagedHasBase = true; else stagedSplits.add(info.splitName);
            } catch (IOException | RuntimeException ignored) {
                // se informará al instalar
            }
        }
        if (!rec.inheritsExisting() || pkg == null) return pkg;
        BPackageSettings ps = BPackageManagerService.get().getBPackageSetting(rec.userId, pkg);
        if (ps == null || ps.pkg == null || ps.pkg.baseCodePath == null) return pkg;

        List<InheritPlanner.Candidate> installed = new ArrayList<>();
        addCandidate(installed, null, ps.pkg.baseCodePath);
        if (ps.pkg.splitNames != null && ps.pkg.splitCodePaths != null) {
            for (int i = 0; i < ps.pkg.splitNames.size() && i < ps.pkg.splitCodePaths.size(); i++) {
                addCandidate(installed, ps.pkg.splitNames.get(i), ps.pkg.splitCodePaths.get(i));
            }
        }
        int n = 0;
        for (InheritPlanner.Candidate c : InheritPlanner.plan(stagedSplits, stagedHasBase, stagedVersion, installed)) {
            File dst = new File(rec.dir, "inherit-" + (n++) + ".apk");
            try (InputStream in = new java.io.FileInputStream(c.path);
                 FileOutputStream out = new FileOutputStream(dst)) {
                byte[] buf = new byte[64 * 1024];
                int r;
                while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
            } catch (IOException e) {
                Slog.w(TAG, "No se pudo heredar " + c.path + ": " + e);
                dst.delete();
            }
        }
        Slog.d(TAG, "Sesión " + rec.id + " hereda " + n + " APK ya instalados de " + pkg);
        return pkg;
    }

    private static void addCandidate(List<InheritPlanner.Candidate> out, String splitName, String path) {
        if (path == null) return;
        try {
            SplitInfo info = SplitManifestReader.read(new File(path));
            out.add(new InheritPlanner.Candidate(splitName, path, info.versionCode));
        } catch (IOException | RuntimeException e) {
            Slog.w(TAG, "APK instalado ilegible: " + path + ": " + e);
        }
    }

    @Override
    public Bundle getSessions(int userId, String installerPackage) {
        ArrayList<PackageInstaller.SessionInfo> infos = new ArrayList<>();
        for (SessionRecord r : store().list(userId, installerPackage)) {
            PackageInstaller.SessionInfo info = SessionParamsReflect.toInfo(r, 0);
            if (info != null) infos.add(info);
        }
        Bundle b = new Bundle();
        b.putParcelableArrayList("sessions", infos);
        return b;
    }

    @Override
    public Bundle getSessionInfo(int sessionId, int userId) {
        Bundle b = new Bundle();
        SessionRecord r = store().get(sessionId, userId);
        PackageInstaller.SessionInfo info = r == null ? null : SessionParamsReflect.toInfo(r, 0);
        if (info != null) b.putParcelable("info", info);
        return b;
    }

    @Override
    public void uninstall(String packageName, IntentSender statusReceiver, int userId) {
        mExecutor.execute(() -> {
            int status = InstallStatus.STATUS_FAILURE;
            String msg = null;
            try {
                if (!BPackageManagerService.get().isInstalled(packageName, userId)) {
                    msg = "Paquete no instalado: " + packageName;
                } else {
                    BPackageManagerService.get().uninstallPackageAsUser(packageName, userId);
                    status = InstallStatus.STATUS_SUCCESS;
                    Slog.d(TAG, "Desinstalación confirmada: " + packageName + " (user " + userId + ")");
                }
            } catch (Throwable t) {
                msg = String.valueOf(t);
                Slog.w(TAG, "Desinstalación fallida: " + packageName, t);
            }
            send(statusReceiver, InstallStatus.extras(status, msg, packageName, -1));
        });
    }

    private static void send(IntentSender receiver, java.util.Map<String, Object> extras) {
        if (receiver == null) return;
        try {
            Context ctx = BlackBoxCore.getContext();
            receiver.sendIntent(ctx, 0, InstallStatus.toIntent(extras), null, null);
        } catch (IntentSender.SendIntentException e) {
            Slog.w(TAG, "No se pudo enviar el estado de instalación: " + e);
        }
    }
}
