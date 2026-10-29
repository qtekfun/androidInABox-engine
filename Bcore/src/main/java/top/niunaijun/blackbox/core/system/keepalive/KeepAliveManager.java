package top.niunaijun.blackbox.core.system.keepalive;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.core.GmsCore;
import top.niunaijun.blackbox.core.env.BEnvironment;
import top.niunaijun.blackbox.core.system.BProcessManagerService;
import top.niunaijun.blackbox.core.system.ProcessRecord;
import top.niunaijun.blackbox.core.system.pm.BPackageManagerService;
import top.niunaijun.blackbox.proxy.ProxyManifest;
import top.niunaijun.blackbox.utils.FileUtils;
import top.niunaijun.blackbox.utils.Slog;

/**
 * Mantiene vivos los procesos invitados de las apps marcadas (spec 04, capa 3). Solo vive en
 * {@code :black}.
 *
 * Prioridad: {@code :black} hace bindService con BIND_AUTO_CREATE|BIND_IMPORTANT al stub
 * {@code ProxyService$Pn} del proceso, de modo que el proceso {@code :pN} hereda la importancia del
 * cliente (servicio central en primer plano) y no queda "en cache".
 * Reinicio: si el proceso muere se relanza con espera creciente ({@link KeepAlivePolicy}).
 */
public class KeepAliveManager {
    public static final String TAG = "KeepAliveManager";
    /** Accion de los bind de prioridad; {@code ProxyService.onBind} la ignora. */
    public static final String ACTION_KEEPALIVE = "bb.KEEPALIVE";

    private static final KeepAliveManager sInstance = new KeepAliveManager();

    public static KeepAliveManager get() {
        return sInstance;
    }

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Object mLock = new Object();
    private Set<String> mKeys;
    /** Binds activos por bpid. */
    private final Map<Integer, ServiceConnection> mBinds = new HashMap<>();
    /** Fallos seguidos y hora de arranque por proceso ("clave|proceso"). */
    private final Map<String, Integer> mFailures = new HashMap<>();
    private final Map<Integer, Long> mStartedAt = new HashMap<>();
    private final Map<String, Boolean> mGaveUp = new HashMap<>();

    private File confFile() {
        return new File(BEnvironment.getSystemDir(), "keepalive.conf");
    }

    private Set<String> keysL() {
        if (mKeys != null) {
            return mKeys;
        }
        File f = confFile();
        if (f.exists()) {
            try {
                mKeys = KeepAlivePolicy.parse(readAll(f));
            } catch (Throwable e) {
                Slog.w(TAG, "keepalive.conf ilegible: " + e);
                mKeys = new LinkedHashSet<>();
            }
        } else {
            // Primera vez: GMS y GSF por defecto (spec 03/04)
            mKeys = new LinkedHashSet<>();
            mKeys.add(KeepAlivePolicy.key(GmsCore.GMS_PKG, 0));
            mKeys.add(KeepAlivePolicy.key("com.google.android.gsf", 0));
            saveL();
        }
        return mKeys;
    }

    private static String readAll(File f) throws IOException {
        java.io.FileInputStream in = new java.io.FileInputStream(f);
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            in.close();
        }
    }

    private void saveL() {
        try {
            FileUtils.writeToFile(KeepAlivePolicy.serialize(mKeys).getBytes(StandardCharsets.UTF_8), confFile());
        } catch (IOException e) {
            Slog.w(TAG, "No se pudo guardar keepalive.conf: " + e);
        }
    }

    public boolean isKeepAlive(String pkg, int userId) {
        synchronized (mLock) {
            return keysL().contains(KeepAlivePolicy.key(pkg, userId));
        }
    }

    public List<String> getKeepAlive() {
        synchronized (mLock) {
            return new ArrayList<>(keysL());
        }
    }

    public void setKeepAlive(String pkg, int userId, boolean enabled) {
        String key = KeepAlivePolicy.key(pkg, userId);
        synchronized (mLock) {
            boolean changed = enabled ? keysL().add(key) : keysL().remove(key);
            if (changed) {
                saveL();
            }
            mGaveUp.remove(key);
        }
        Slog.i(TAG, "keepalive " + key + " -> " + enabled);
        if (enabled) {
            restoreMarked();
        } else {
            for (ProcessRecord r : BProcessManagerService.get().getPackageProcessAsUser(pkg, userId)) {
                unbind(r.bpid);
            }
        }
    }

    /** Relanza los procesos de las apps marcadas (arranque y reinicio de :black, R04.6 y R04.8). */
    public void restoreMarked() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                for (String key : getKeepAlive()) {
                    String pkg = KeepAlivePolicy.packageOf(key);
                    int user = KeepAlivePolicy.userOf(key);
                    try {
                        if (pkg == null || !BPackageManagerService.get().isInstalled(pkg, user)) {
                            continue;
                        }
                        if (BProcessManagerService.get().getPackageProcessAsUser(pkg, user).isEmpty()) {
                            Slog.i(TAG, "restaurando proceso de " + key);
                            start(pkg, user);
                        }
                    } catch (Throwable e) {
                        Slog.w(TAG, "restoreMarked " + key + ": " + e);
                    }
                }
            }
        });
    }

    private void start(String pkg, int user) {
        String process = null;
        try {
            process = BPackageManagerService.get().getApplicationInfo(pkg, 0, user).processName;
        } catch (Throwable ignored) {
        }
        if (process == null) {
            process = pkg;
        }
        ProcessRecord r = BProcessManagerService.get().startProcessLocked(pkg, process, user, -1, Process.myPid());
        if (r != null) {
            onProcessStarted(r);
        }
    }

    /** Llamado tras arrancar un proceso invitado: si esta marcado, se ata su stub para subir la prioridad. */
    public void onProcessStarted(ProcessRecord r) {
        if (!isKeepAlive(r.getPackageName(), r.userId)) {
            return;
        }
        synchronized (mLock) {
            mStartedAt.put(r.bpid, SystemClock.elapsedRealtime());
        }
        bind(r);
    }

    private void bind(final ProcessRecord r) {
        final int bpid = r.bpid;
        synchronized (mLock) {
            if (mBinds.containsKey(bpid)) {
                return;
            }
        }
        Intent intent = new Intent(ACTION_KEEPALIVE);
        intent.setComponent(new ComponentName(BlackBoxCore.getHostPkg(), ProxyManifest.getProxyService(bpid)));
        ServiceConnection conn = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
            }
        };
        boolean ok = false;
        try {
            ok = BlackBoxCore.getContext().bindService(intent, conn, Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT);
        } catch (Throwable e) {
            Slog.w(TAG, "bindService keepalive fallo para " + r.processName + ": " + e);
        }
        Slog.i(TAG, "keepalive bind " + r.processName + " (p" + bpid + ") ok=" + ok);
        if (ok) {
            synchronized (mLock) {
                mBinds.put(bpid, conn);
            }
        }
    }

    private void unbind(int bpid) {
        ServiceConnection conn;
        synchronized (mLock) {
            conn = mBinds.remove(bpid);
        }
        if (conn != null) {
            try {
                BlackBoxCore.getContext().unbindService(conn);
            } catch (Throwable ignored) {
            }
        }
    }

    /** Llamado desde onProcessDie. {@code byUser}: matado a proposito, no se reinicia. */
    public void onProcessDied(final ProcessRecord r, boolean byUser) {
        unbind(r.bpid);
        final String pkg = r.getPackageName();
        final int user = r.userId;
        final String key = KeepAlivePolicy.key(pkg, user);
        if (byUser || !isKeepAlive(pkg, user)) {
            return;
        }
        int failures;
        synchronized (mLock) {
            Long started = mStartedAt.remove(r.bpid);
            long alive = started == null ? 0 : SystemClock.elapsedRealtime() - started;
            Integer prev = mFailures.get(key);
            failures = KeepAlivePolicy.nextFailures(prev == null ? 0 : prev, alive);
            mFailures.put(key, failures);
            if (KeepAlivePolicy.shouldGiveUp(failures)) {
                mGaveUp.put(key, true);
            }
        }
        if (KeepAlivePolicy.shouldGiveUp(failures)) {
            Slog.e(TAG, "keepalive: " + key + " murio " + failures + " veces seguidas; se deja de reintentar");
            return;
        }
        long delay = KeepAlivePolicy.delayMs(failures);
        Slog.i(TAG, "keepalive: murio " + r.processName + " (fallo " + failures + "), reinicio en " + delay + " ms");
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    if (isKeepAlive(pkg, user)
                            && BProcessManagerService.get().getPackageProcessAsUser(pkg, user).isEmpty()) {
                        start(pkg, user);
                    }
                } catch (Throwable e) {
                    Slog.w(TAG, "reinicio keepalive fallo: " + e);
                }
            }
        }, delay);
    }

    /** true si se dejo de reintentar tras demasiados fallos (la UI puede avisar). */
    public boolean hasGivenUp(String pkg, int userId) {
        synchronized (mLock) {
            return mGaveUp.containsKey(KeepAlivePolicy.key(pkg, userId));
        }
    }
}
