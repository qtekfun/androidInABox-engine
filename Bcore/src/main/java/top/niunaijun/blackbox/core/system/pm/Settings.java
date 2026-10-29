package top.niunaijun.blackbox.core.system.pm;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageParser;
import android.os.Parcel;
import android.os.Process;
import android.util.ArrayMap;
import android.util.AtomicFile;
import android.util.SparseArray;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.core.env.BEnvironment;
import top.niunaijun.blackbox.core.system.BProcessManagerService;
import top.niunaijun.blackbox.core.system.pm.installer.ApkDedup;
import top.niunaijun.blackbox.core.system.pm.split.SplitsConf;
import top.niunaijun.blackbox.core.system.user.BUserHandle;
import top.niunaijun.blackbox.core.GooglePrivappPermissions;
import top.niunaijun.blackbox.entity.pm.InstallOption;
import top.niunaijun.blackbox.utils.FileUtils;
import top.niunaijun.blackbox.utils.Slog;
import top.niunaijun.blackbox.utils.compat.PackageParserCompat;


 class Settings {
    public static final String TAG = "Settings";

    /** Copias instaladas por perfil (ADR-0008): userId -> nombre de paquete -> ajustes. También es el cerrojo. */
    final SparseArray<ArrayMap<String, BPackageSettings>> mPackages = new SparseArray<>();
    private final Map<String, Integer> mAppIds = new HashMap<>();
    private final Map<String, Integer> mAppIdByName = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, SharedUserSetting> mSharedUsers = SharedUserSetting.sSharedUsers;
    private int mCurrUid = 0;

    public Settings() {
        synchronized (mPackages) {
            loadUidLP();
            SharedUserSetting.loadSharedUsers();
        }
    }

    BPackageSettings getPackage(int userId, String name) {
        synchronized (mPackages) {
            ArrayMap<String, BPackageSettings> map = mPackages.get(userId);
            return map == null ? null : map.get(name);
        }
    }

    /** Copias de un perfil. */
    List<BPackageSettings> getPackages(int userId) {
        synchronized (mPackages) {
            ArrayMap<String, BPackageSettings> map = mPackages.get(userId);
            return map == null ? new ArrayList<>() : new ArrayList<>(map.values());
        }
    }

    /** Copias de todos los perfiles. */
    List<BPackageSettings> getAllPackages() {
        synchronized (mPackages) {
            List<BPackageSettings> all = new ArrayList<>();
            for (int i = 0; i < mPackages.size(); i++) {
                all.addAll(mPackages.valueAt(i).values());
            }
            return all;
        }
    }

    /** Copias de un paquete en todos los perfiles. */
    List<BPackageSettings> getPackageCopies(String name) {
        synchronized (mPackages) {
            List<BPackageSettings> copies = new ArrayList<>();
            for (int i = 0; i < mPackages.size(); i++) {
                BPackageSettings ps = mPackages.valueAt(i).get(name);
                if (ps != null) copies.add(ps);
            }
            return copies;
        }
    }

    void putPackage(BPackageSettings ps) {
        synchronized (mPackages) {
            ArrayMap<String, BPackageSettings> map = mPackages.get(ps.userId);
            if (map == null) {
                map = new ArrayMap<>();
                mPackages.put(ps.userId, map);
            }
            map.put(ps.pkg.packageName, ps);
            mAppIdByName.put(ps.pkg.packageName, ps.appId);
        }
    }

    BPackageSettings removePackage(int userId, String name) {
        synchronized (mPackages) {
            ArrayMap<String, BPackageSettings> map = mPackages.get(userId);
            BPackageSettings removed = map == null ? null : map.remove(name);
            if (getPackageCopies(name).isEmpty()) {
                mAppIdByName.remove(name);
            }
            return removed;
        }
    }

    /**
     * El appId es del paquete, no de la copia: todas las copias de un nombre comparten uid. Sin
     * cerrojo (lo consulta el gestor de procesos), a partir de la caché que mantiene putPackage.
     */
    int getAppId(String name) {
        Integer appId = mAppIdByName.get(name);
        return appId != null ? appId : -1;
    }

    BPackageSettings getPackageLPw(String name, PackageParser.Package aPackage, InstallOption installOption, int userId) {
        BPackageSettings origSettings = new BPackageSettings();
        origSettings.userId = userId;
        if (installOption != null && GooglePrivappPermissions.isGooglePackage(name)) {
            // GMS, GSF y Play Store se ven como apps de sistema privilegiadas dentro del box
            installOption.makePrivilegedVirtual();
        }
        origSettings.pkg = new BPackage(aPackage);
        origSettings.pkg.installOption = installOption;
        origSettings.installOption = installOption;
        origSettings.pkg.mExtras = origSettings;
        origSettings.pkg.applicationInfo = PackageManagerCompat.generateApplicationInfo(origSettings.pkg, 0, BPackageUserState.create(), userId);
        synchronized (mPackages) {
            BPackageSettings same = getPackage(userId, name);
            int appId = getAppId(name);
            if (same != null) {
                origSettings.userState = same.userState;
            }
            if (appId > 0) {
                origSettings.appId = appId;
            } else {
                boolean b = registerAppIdLPw(origSettings);
                if (!b) {
                    throw new RuntimeException("registerAppIdLPw err.");
                }
            }
        }
        return origSettings;
    }

    boolean registerAppIdLPw(BPackageSettings p) {
        boolean createdNew = false;
        String sharedUserId = p.pkg.mSharedUserId;
        SharedUserSetting sharedUserSetting = null;
        if (sharedUserId != null) {
            sharedUserSetting = mSharedUsers.get(sharedUserId);
            if (sharedUserSetting == null) {
                sharedUserSetting = new SharedUserSetting(sharedUserId);
                sharedUserSetting.userId = acquireAndRegisterNewAppIdLPw(p);
                mSharedUsers.put(sharedUserId, sharedUserSetting);
            }
        }
        if (sharedUserSetting != null) {
            p.appId = sharedUserSetting.userId;
            Slog.d(TAG, p.pkg.packageName + " sharedUserId = " + sharedUserId + ", setAppId = " + p.appId);
        }
        if (p.appId == 0) {
            
            p.appId = acquireAndRegisterNewAppIdLPw(p);
        }
        if (p.appId < 0) {
            createdNew = false;




        } else {
            createdNew = true;
        }
        saveUidLP();
        SharedUserSetting.saveSharedUsers();
        return createdNew;
    }

    private int acquireAndRegisterNewAppIdLPw(BPackageSettings obj) {
        
        Integer integer = mAppIds.get(obj.pkg.packageName);
        if (integer != null)
            return integer;

        if (mCurrUid >= Process.LAST_APPLICATION_UID) {
            return -1;
        }
        mCurrUid++;
        mAppIds.put(obj.pkg.packageName, mCurrUid);
        return Process.FIRST_APPLICATION_UID + mCurrUid;
    }

    private void saveUidLP() {
        Parcel parcel = Parcel.obtain();
        FileOutputStream fileOutputStream = null;
        AtomicFile atomicFile = new AtomicFile(BEnvironment.getUidConf());
        try {
            // No se podan los ids de paquetes sin copia cargada: durante el arranque y la migración
            // aún no están todas, y reinstalar un paquete debe conservar su uid.
            parcel.writeInt(mCurrUid);
            parcel.writeMap(mAppIds);

            fileOutputStream = atomicFile.startWrite();
            FileUtils.writeParcelToOutput(parcel, fileOutputStream);
            atomicFile.finishWrite(fileOutputStream);
        } catch (Exception e) {
            e.printStackTrace();
            atomicFile.failWrite(fileOutputStream);
        } finally {
            parcel.recycle();
        }
    }

    private void loadUidLP() {
        Parcel parcel = Parcel.obtain();
        try {
            byte[] uidBytes = FileUtils.toByteArray(BEnvironment.getUidConf());
            if (uidBytes == null || uidBytes.length == 0) {
                
                return;
            }
            
            parcel.unmarshall(uidBytes, 0, uidBytes.length);
            parcel.setDataPosition(0);

            mCurrUid = parcel.readInt();
            HashMap hashMap = parcel.readHashMap(HashMap.class.getClassLoader());
            synchronized (mAppIds) {
                mAppIds.clear();
                mAppIds.putAll(hashMap);
            }
        } catch (Exception e) {
            
            try {
                
                BEnvironment.getUidConf().delete();
            } catch (Exception deleteException) {
                
            }
            
            
            mCurrUid = 0;
            synchronized (mAppIds) {
                mAppIds.clear();
            }
        } finally {
            parcel.recycle();
        }
    }

    public void scanPackage() {
        synchronized (mPackages) {
            migrateLegacyLP();
            File root = BEnvironment.getUserAppsRoot();
            FileUtils.mkdirs(root);
            File[] users = root.listFiles();
            if (users == null) return;
            for (File userDir : users) {
                int userId;
                try {
                    userId = Integer.parseInt(userDir.getName());
                } catch (NumberFormatException e) {
                    continue;
                }
                File[] apps = userDir.listFiles();
                if (apps == null) continue;
                for (File app : apps) {
                    if (app.isDirectory()) {
                        updatePackageLP(userId, app, false);
                    }
                }
            }
        }
    }

    public void scanPackage(int userId, String packageName) {
        synchronized (mPackages) {
            updatePackageLP(userId, BEnvironment.getAppDir(userId, packageName), false);
        }
    }

    /**
     * Crea la copia de un paquete para otro perfil a partir de la copia de origen: los APK y las libs
     * se enlazan (no se copian) y la copia nueva se reconstruye con rutas propias.
     */
    BPackageSettings cloneForUserLP(BPackageSettings source, int userId) throws Exception {
        String packageName = source.pkg.packageName;
        File srcDir = BEnvironment.getAppDir(source.userId, packageName);
        File dstDir = BEnvironment.getAppDir(userId, packageName);
        FileUtils.deleteDir(dstDir);
        FileUtils.mkdirs(dstDir);
        ApkDedup.linkTree(srcDir, dstDir);
        updatePackageLP(userId, dstDir, true);
        return getPackage(userId, packageName);
    }

    /**
     * Migra el formato anterior (una copia compartida en data/app/<pkg>) a una copia por perfil
     * instalado. Los ficheros se enlazan (hard link) en vez de copiarse y los datos de las apps no
     * se tocan. La carpeta antigua solo se borra cuando TODAS las copias nuevas se han cargado; si
     * algo falla se conserva y se reintenta en el siguiente arranque.
     */
    private void migrateLegacyLP() {
        File[] apps = BEnvironment.getLegacyAppRootDir().listFiles();
        if (apps == null) return;
        for (File app : apps) {
            if (!app.isDirectory()) continue;
            String packageName = app.getName();
            try {
                List<Integer> users = legacyInstalledUsers(app);
                boolean ok = true;
                for (int userId : users) {
                    File dst = BEnvironment.getAppDir(userId, packageName);
                    if (BEnvironment.getPackageConf(userId, packageName).isFile()) {
                        continue;
                    }
                    try {
                        FileUtils.mkdirs(dst);
                        ApkDedup.linkTree(app, dst);
                        updatePackageLP(userId, dst, true);
                        if (getPackage(userId, packageName) == null) {
                            ok = false;
                        }
                    } catch (Throwable t) {
                        ok = false;
                        Slog.e(TAG, "PERSIST_MIGRATE_FAIL " + packageName + " perfil " + userId, t);
                    }
                }
                if (ok) {
                    FileUtils.deleteDir(app);
                    Slog.w(TAG, "PERSIST_MIGRATED " + packageName + ": copias por perfil " + users);
                } else {
                    Slog.e(TAG, "PERSIST_MIGRATE_KEPT " + packageName + ": se conserva la copia antigua");
                }
            } catch (Throwable t) {
                Slog.e(TAG, "PERSIST_MIGRATE_FAIL " + packageName + ": se conserva la copia antigua", t);
            }
        }
    }

    /** Perfiles con el paquete instalado según el package.conf antiguo; si no se lee, los que tienen datos. */
    private List<Integer> legacyInstalledUsers(File app) {
        String packageName = app.getName();
        List<Integer> users = new ArrayList<>();
        Parcel in = Parcel.obtain();
        try {
            byte[] bytes = FileUtils.toByteArray(new File(app, "package.conf"));
            in.unmarshall(bytes, 0, bytes.length);
            in.setDataPosition(0);
            BPackageSettings partial = BPackageSettings.readLenient(in);
            for (Map.Entry<Integer, BPackageUserState> e : partial.userState.entrySet()) {
                if (e.getValue() != null && e.getValue().installed) {
                    users.add(e.getKey());
                }
            }
        } catch (Throwable t) {
            Slog.w(TAG, "package.conf antiguo ilegible: " + packageName + ", se infieren los perfiles", t);
        } finally {
            in.recycle();
        }
        if (users.isEmpty()) {
            users = PersistFormat.assumeInstalledUsers(candidateUserIds(),
                    id -> BEnvironment.getDataDir(packageName, id).exists());
        }
        return users;
    }

    /**
     * Carga una copia persistida. Si los metadatos no se pueden leer (p. ej. cambió el formato
     * del parcel) NUNCA se borra nada: se reconstruyen desde el APK instalado. Solo se descarta
     * la copia si su APK realmente no existe. Con forceRecover (migración) los metadatos antiguos
     * apuntan a rutas de la carpeta antigua, así que se reconstruye siempre desde el APK.
     */
    private void updatePackageLP(int userId, File app, boolean forceRecover) {
        String packageName = app.getName();
        BPackageSettings partial = null;
        Throwable error = null;
        Parcel in = Parcel.obtain();
        try {
            byte[] bytes = FileUtils.toByteArray(BEnvironment.getPackageConf(userId, packageName));
            in.unmarshall(bytes, 0, bytes.length);
            in.setDataPosition(0);
            partial = BPackageSettings.readLenient(in);
            if (partial.pkg == null) {
                error = partial.pkgError != null ? partial.pkgError : new IllegalStateException("BPackage nulo");
            } else if (partial.pkg.packageName == null || partial.pkg.baseCodePath == null) {
                error = new IllegalStateException("BPackage incompleto");
                partial.pkg = null;
            }
        } catch (Throwable e) {
            error = e;
        } finally {
            in.recycle();
        }

        if (error == null && !forceRecover) {
            try {
                finishLoadPackageLP(userId, app, partial);
                return;
            } catch (Throwable e) {
                error = e;
            }
        }

        if (!forceRecover) {
            Slog.e(TAG, "PERSIST_READ_FAIL " + packageName + " perfil " + userId + ": metadatos ilegibles, se conservan los ficheros e intento recuperar desde el APK", error);
        }
        try {
            BPackageSettings recovered = recoverPackageLP(userId, app, partial);
            finishLoadPackageLP(userId, app, recovered);
            Slog.w(TAG, "PERSIST_RECOVERED " + packageName + " perfil " + userId + ": reconstruido desde el APK instalado");
            return;
        } catch (Throwable e) {
            Slog.e(TAG, "PERSIST_RECOVER_FAIL " + packageName + " perfil " + userId, e);
        }

        if (!hasInstalledApk(userId, app, partial)) {
            Slog.e(TAG, "PERSIST_APK_MISSING " + packageName + " perfil " + userId + ": el APK no existe, se descarta la copia");
            FileUtils.deleteDir(app);
            removePackage(userId, packageName);
            BProcessManagerService.get().killPackageAsUser(packageName, userId);
            BPackageManagerService.get().onPackageUninstalled(packageName, true, userId);
        } else {
            // El APK existe pero no se pudo cargar: no se toca nada; se reintentará en el próximo arranque.
            Slog.e(TAG, "PERSIST_KEPT " + packageName + " perfil " + userId + ": no cargado, ficheros y datos intactos");
        }
    }

    private void finishLoadPackageLP(int userId, File app, BPackageSettings bPackageSettings) throws Exception {
        String packageName = app.getName();
        bPackageSettings.userId = userId;
        // La copia solo conoce a su perfil; un estado multiperfil (formato antiguo) se reduce al propio
        BPackageUserState own = bPackageSettings.userState != null ? bPackageSettings.userState.get(userId) : null;
        bPackageSettings.userState = new HashMap<>();
        bPackageSettings.userState.put(userId, own != null ? own : BPackageUserState.create());
        bPackageSettings.pkg.mExtras = bPackageSettings;
        SplitsConf splits = SplitsConf.read(BEnvironment.getSplitsConf(userId, packageName), app);
        if (!splits.names.isEmpty()) {
            bPackageSettings.pkg.splitNames = splits.names;
            bPackageSettings.pkg.splitCodePaths = splits.codePaths;
        }
        if (bPackageSettings.installOption != null && bPackageSettings.installOption.isFlag(InstallOption.FLAG_SYSTEM)) {
            PackageInfo packageInfo = BlackBoxCore.getPackageManager().getPackageInfo(packageName, PackageManager.GET_META_DATA);
            String currPackageSourcePath = packageInfo.applicationInfo.sourceDir;
            if (!currPackageSourcePath.equals(bPackageSettings.pkg.baseCodePath)
                    || PackageManagerCompat.hostSplitsChanged(packageInfo.applicationInfo, bPackageSettings.pkg.applicationInfo)) {

                BProcessManagerService.get().killPackageAsUser(bPackageSettings.pkg.packageName, userId);
                BPackageSettings newPkg = reInstallBySystem(packageInfo, bPackageSettings.installOption, userId);
                bPackageSettings.pkg = newPkg.pkg;
                bPackageSettings.pkg.mExtras = bPackageSettings;
            } else {
                PackageManagerCompat.adoptHostSplits(bPackageSettings.pkg, packageInfo.applicationInfo);
            }
        } else {
            bPackageSettings.pkg.applicationInfo = PackageManagerCompat.generateApplicationInfo(bPackageSettings.pkg, 0, BPackageUserState.create(), userId);
        }
        bPackageSettings.save();
        putPackage(bPackageSettings);
        Slog.d(TAG, "loaded Package: " + packageName + " perfil " + userId);
    }

    private boolean isSystemOption(BPackageSettings partial) {
        return partial != null && partial.installOption != null
                && partial.installOption.isFlag(InstallOption.FLAG_SYSTEM);
    }

    private PackageInfo hostPackageInfo(String packageName) {
        try {
            return BlackBoxCore.getPackageManager().getPackageInfo(packageName, 0);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Ruta del APK base: el del box (base.apk) o, si es un paquete del host, el del host. */
    private String findBaseApk(int userId, File app, BPackageSettings partial) {
        File base = BEnvironment.getBaseApkDir(userId, app.getName());
        if (!isSystemOption(partial) && base.isFile()) {
            return base.getAbsolutePath();
        }
        PackageInfo host = hostPackageInfo(app.getName());
        if (host != null && host.applicationInfo != null && host.applicationInfo.sourceDir != null
                && new File(host.applicationInfo.sourceDir).isFile()
                && (isSystemOption(partial) || !base.isFile())) {
            return host.applicationInfo.sourceDir;
        }
        return base.isFile() ? base.getAbsolutePath() : null;
    }

    private boolean hasInstalledApk(int userId, File app, BPackageSettings partial) {
        return findBaseApk(userId, app, partial) != null;
    }

    /**
     * Reconstruye el BPackage reparseando base.apk (y los splits de splits.conf), conservando
     * las banderas de instalación y el estado por usuario si eran legibles; si no, se infieren.
     */
    private BPackageSettings recoverPackageLP(int userId, File app, BPackageSettings partial) throws Exception {
        String packageName = app.getName();
        String apkPath = findBaseApk(userId, app, partial);
        if (apkPath == null) {
            throw new java.io.FileNotFoundException("APK no encontrado para " + packageName);
        }
        boolean inAppDir = apkPath.equals(BEnvironment.getBaseApkDir(userId, packageName).getAbsolutePath());
        PackageInfo host = inAppDir ? null : hostPackageInfo(packageName);

        InstallOption option = partial != null ? partial.installOption : null;
        if (option == null) {
            option = new InstallOption();
            option.flags = PersistFormat.recoverInstallFlags(inAppDir, host != null);
        }

        PackageParser.Package aPackage = parserApk(apkPath);
        if (aPackage == null) {
            throw new RuntimeException("parser apk error: " + packageName);
        }
        if (host != null) {
            aPackage.applicationInfo = host.applicationInfo;
        }
        BPackageSettings ps = getPackageLPw(packageName, aPackage, option, userId);
        if (host != null) {
            PackageManagerCompat.adoptHostSplits(ps.pkg, aPackage.applicationInfo);
        }
        if (partial != null && partial.appId != 0 && partial.appId != ps.appId) {
            Slog.w(TAG, "recover " + packageName + ": appId " + ps.appId + " -> " + partial.appId);
            ps.appId = partial.appId;
        }
        if (partial != null && partial.userState != null && !partial.userState.isEmpty()) {
            ps.userState = partial.userState;
        } else {
            ps.userState = new HashMap<>();
            ps.setInstalled(true, userId);
        }
        return ps;
    }

    private java.util.List<Integer> candidateUserIds() {
        java.util.List<Integer> ids = new java.util.ArrayList<>();
        File[] dirs = BEnvironment.getUserDir(0).getParentFile().listFiles();
        if (dirs != null) {
            for (File d : dirs) {
                try {
                    ids.add(Integer.parseInt(d.getName()));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return ids;
    }

    private BPackageSettings reInstallBySystem(PackageInfo systemPackageInfo, InstallOption option, int userId) throws Exception {
        Slog.d(TAG, "reInstallBySystem: " + systemPackageInfo.packageName);
        PackageParser.Package aPackage = parserApk(systemPackageInfo.applicationInfo.sourceDir);
        if (aPackage == null) {
            throw new RuntimeException("parser apk error.");
        }
        aPackage.applicationInfo = BlackBoxCore.getPackageManager().getPackageInfo(aPackage.packageName, 0).applicationInfo;
        BPackageSettings ps = getPackageLPw(aPackage.packageName, aPackage, option, userId);
        PackageManagerCompat.adoptHostSplits(ps.pkg, aPackage.applicationInfo);
        return ps;
    }

    private PackageParser.Package parserApk(String file) {
        try {
            PackageParser parser = PackageParserCompat.createParser(new File(file));
            PackageParser.Package aPackage = PackageParserCompat.parsePackage(parser, new File(file), 0);
            PackageParserCompat.collectCertificates(parser, aPackage, 0);
            return aPackage;
        } catch (Throwable t) {
            t.printStackTrace();
        }
        return null;
    }
}
