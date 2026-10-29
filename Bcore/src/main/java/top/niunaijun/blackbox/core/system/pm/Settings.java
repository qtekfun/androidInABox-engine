package top.niunaijun.blackbox.core.system.pm;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageParser;
import android.os.Parcel;
import android.os.Process;
import android.util.ArrayMap;
import android.util.AtomicFile;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.core.env.BEnvironment;
import top.niunaijun.blackbox.core.system.BProcessManagerService;
import top.niunaijun.blackbox.core.system.pm.split.SplitsConf;
import top.niunaijun.blackbox.core.system.user.BUserHandle;
import top.niunaijun.blackbox.core.GooglePrivappPermissions;
import top.niunaijun.blackbox.entity.pm.InstallOption;
import top.niunaijun.blackbox.utils.FileUtils;
import top.niunaijun.blackbox.utils.Slog;
import top.niunaijun.blackbox.utils.compat.PackageParserCompat;


 class Settings {
    public static final String TAG = "Settings";

    final ArrayMap<String, BPackageSettings> mPackages = new ArrayMap<>();
    private final Map<String, Integer> mAppIds = new HashMap<>();
    private final Map<String, SharedUserSetting> mSharedUsers = SharedUserSetting.sSharedUsers;
    private int mCurrUid = 0;

    public Settings() {
        synchronized (mPackages) {
            loadUidLP();
            SharedUserSetting.loadSharedUsers();
        }
    }

    BPackageSettings getPackageLPw(String name, PackageParser.Package aPackage, InstallOption installOption) {
        BPackageSettings pkgSettings;
        BPackageSettings origSettings = new BPackageSettings();
        if (installOption != null && GooglePrivappPermissions.isGooglePackage(name)) {
            // GMS, GSF y Play Store se ven como apps de sistema privilegiadas dentro del box
            installOption.makePrivilegedVirtual();
        }
        origSettings.pkg = new BPackage(aPackage);
        origSettings.pkg.installOption = installOption;
        origSettings.installOption = installOption;
        origSettings.pkg.mExtras = origSettings;
        origSettings.pkg.applicationInfo = PackageManagerCompat.generateApplicationInfo(origSettings.pkg, 0, BPackageUserState.create(), 0);
        synchronized (mPackages) {
            pkgSettings = mPackages.get(name);
            if (pkgSettings != null) {
                origSettings.appId = pkgSettings.appId;
                origSettings.userState = pkgSettings.userState;
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
            Set<String> pkgName = mPackages.keySet();
            for (String s : new HashSet<>(mAppIds.keySet())) {
                if (!pkgName.contains(s)) {
                    mAppIds.remove(s);
                }
            }
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
            File appRootDir = BEnvironment.getAppRootDir();
            FileUtils.mkdirs(appRootDir);
            File[] apps = appRootDir.listFiles();
            for (File app : apps) {
                if (!app.isDirectory()) {
                    continue;
                }
                scanPackage(app.getName());
            }
        }
    }

    public void scanPackage(String packageName) {
        synchronized (mPackages) {
            updatePackageLP(BEnvironment.getAppDir(packageName));
        }
    }

    /**
     * Carga un paquete persistido. Si los metadatos no se pueden leer (p. ej. cambió el formato
     * del parcel) NUNCA se borra nada: se reconstruyen desde el APK instalado. Solo se descarta
     * el paquete si su APK realmente no existe.
     */
    private void updatePackageLP(File app) {
        String packageName = app.getName();
        BPackageSettings partial = null;
        Throwable error = null;
        Parcel in = Parcel.obtain();
        try {
            byte[] bytes = FileUtils.toByteArray(BEnvironment.getPackageConf(packageName));
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

        if (error == null) {
            try {
                finishLoadPackageLP(app, partial);
                return;
            } catch (Throwable e) {
                error = e;
            }
        }

        Slog.e(TAG, "PERSIST_READ_FAIL " + packageName + ": metadatos ilegibles, se conservan los ficheros e intento recuperar desde el APK", error);
        try {
            BPackageSettings recovered = recoverPackageLP(app, partial);
            finishLoadPackageLP(app, recovered);
            Slog.w(TAG, "PERSIST_RECOVERED " + packageName + ": reconstruido desde el APK instalado");
            return;
        } catch (Throwable e) {
            Slog.e(TAG, "PERSIST_RECOVER_FAIL " + packageName, e);
        }

        if (!hasInstalledApk(app, partial)) {
            Slog.e(TAG, "PERSIST_APK_MISSING " + packageName + ": el APK no existe, se descarta el paquete");
            FileUtils.deleteDir(app);
            removePackage(packageName);
            BProcessManagerService.get().killAllByPackageName(packageName);
            BPackageManagerService.get().onPackageUninstalled(packageName, true, BUserHandle.USER_ALL);
        } else {
            // El APK existe pero no se pudo cargar: no se toca nada; se reintentará en el próximo arranque.
            Slog.e(TAG, "PERSIST_KEPT " + packageName + ": no cargado, ficheros y datos intactos");
        }
    }

    private void finishLoadPackageLP(File app, BPackageSettings bPackageSettings) throws Exception {
        String packageName = app.getName();
        bPackageSettings.pkg.mExtras = bPackageSettings;
        SplitsConf splits = SplitsConf.read(BEnvironment.getSplitsConf(packageName), app);
        if (!splits.names.isEmpty()) {
            bPackageSettings.pkg.splitNames = splits.names;
            bPackageSettings.pkg.splitCodePaths = splits.codePaths;
        }
        if (bPackageSettings.installOption != null && bPackageSettings.installOption.isFlag(InstallOption.FLAG_SYSTEM)) {
            PackageInfo packageInfo = BlackBoxCore.getPackageManager().getPackageInfo(packageName, PackageManager.GET_META_DATA);
            String currPackageSourcePath = packageInfo.applicationInfo.sourceDir;
            if (!currPackageSourcePath.equals(bPackageSettings.pkg.baseCodePath)
                    || PackageManagerCompat.hostSplitsChanged(packageInfo.applicationInfo, bPackageSettings.pkg.applicationInfo)) {

                BProcessManagerService.get().killAllByPackageName(bPackageSettings.pkg.packageName);
                BPackageSettings newPkg = reInstallBySystem(packageInfo, bPackageSettings.installOption);
                bPackageSettings.pkg = newPkg.pkg;
            } else {
                PackageManagerCompat.adoptHostSplits(bPackageSettings.pkg, packageInfo.applicationInfo);
            }
        } else {
            bPackageSettings.pkg.applicationInfo = PackageManagerCompat.generateApplicationInfo(bPackageSettings.pkg, 0, BPackageUserState.create(), 0);
        }
        bPackageSettings.save();
        mPackages.put(bPackageSettings.pkg.packageName, bPackageSettings);
        Slog.d(TAG, "loaded Package: " + packageName);
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
    private String findBaseApk(File app, BPackageSettings partial) {
        File base = BEnvironment.getBaseApkDir(app.getName());
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

    private boolean hasInstalledApk(File app, BPackageSettings partial) {
        return findBaseApk(app, partial) != null;
    }

    /**
     * Reconstruye el BPackage reparseando base.apk (y los splits de splits.conf), conservando
     * las banderas de instalación y el estado por usuario si eran legibles; si no, se infieren.
     */
    private BPackageSettings recoverPackageLP(File app, BPackageSettings partial) throws Exception {
        String packageName = app.getName();
        String apkPath = findBaseApk(app, partial);
        if (apkPath == null) {
            throw new java.io.FileNotFoundException("APK no encontrado para " + packageName);
        }
        boolean inAppDir = apkPath.equals(BEnvironment.getBaseApkDir(packageName).getAbsolutePath());
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
        BPackageSettings ps = getPackageLPw(packageName, aPackage, option);
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
            for (int userId : PersistFormat.assumeInstalledUsers(candidateUserIds(),
                    id -> BEnvironment.getDataDir(packageName, id).exists())) {
                ps.setInstalled(true, userId);
            }
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

    private BPackageSettings reInstallBySystem(PackageInfo systemPackageInfo, InstallOption option) throws Exception {
        Slog.d(TAG, "reInstallBySystem: " + systemPackageInfo.packageName);
        PackageParser.Package aPackage = parserApk(systemPackageInfo.applicationInfo.sourceDir);
        if (aPackage == null) {
            throw new RuntimeException("parser apk error.");
        }
        aPackage.applicationInfo = BlackBoxCore.getPackageManager().getPackageInfo(aPackage.packageName, 0).applicationInfo;
        BPackageSettings ps = getPackageLPw(aPackage.packageName, aPackage, option);
        PackageManagerCompat.adoptHostSplits(ps.pkg, aPackage.applicationInfo);
        return ps;
    }

    public void removePackage(String packageName) {
        mPackages.remove(packageName);
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
