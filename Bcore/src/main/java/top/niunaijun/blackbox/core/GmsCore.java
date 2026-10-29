package top.niunaijun.blackbox.core;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import java.io.File;

import java.util.HashSet;
import java.util.Set;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.entity.pm.InstallOption;
import top.niunaijun.blackbox.entity.pm.InstallResult;
import top.niunaijun.blackbox.fake.frameworks.BPackageManager;
import top.niunaijun.blackbox.core.system.user.BUserInfo;


public class GmsCore {
    private static final String TAG = "GmsCore";

    private static final HashSet<String> GOOGLE_APP = new HashSet<>();
    private static final HashSet<String> GOOGLE_SERVICE = new HashSet<>();
    public static final String GMS_PKG = "com.google.android.gms";
    public static final String GSF_PKG = "com.google.android.gsf";
    public static final String VENDING_PKG = "com.android.vending";

    static {
        GOOGLE_APP.add(VENDING_PKG);
        GOOGLE_APP.add("com.google.android.play.games");
        GOOGLE_APP.add("com.google.android.wearable.app");
        GOOGLE_APP.add("com.google.android.wearable.app.cn");

        
        GOOGLE_SERVICE.add(GMS_PKG);
        GOOGLE_SERVICE.add(GSF_PKG);
        GOOGLE_SERVICE.add("com.google.android.gsf.login");
        GOOGLE_SERVICE.add("com.google.android.backuptransport");
        GOOGLE_SERVICE.add("com.google.android.backup");
        GOOGLE_SERVICE.add("com.google.android.configupdater");
        GOOGLE_SERVICE.add("com.google.android.syncadapters.contacts");
        GOOGLE_SERVICE.add("com.google.android.feedback");
        GOOGLE_SERVICE.add("com.google.android.onetimeinitializer");
        GOOGLE_SERVICE.add("com.google.android.partnersetup");
        GOOGLE_SERVICE.add("com.google.android.setupwizard");
        GOOGLE_SERVICE.add("com.google.android.syncadapters.calendar");
    }

    public static boolean isGoogleService(String packageName) {
        return GOOGLE_SERVICE.contains(packageName);
    }

    public static boolean isGoogleAppOrService(String str) {
        return GOOGLE_APP.contains(str) || GOOGLE_SERVICE.contains(str);
    }

    private static InstallResult installPackages(Set<String> list, int userId) {
        BlackBoxCore blackBoxCore = BlackBoxCore.get();
        for (String packageName : list) {
            if (blackBoxCore.isInstalled(packageName, userId)) {
                continue;
            }
            // Si otro perfil del box ya tiene el paquete (p. ej. GMS instalado desde archivos), se reutiliza
            // esa copia: en ROM sin GMS el sistema no tiene nada que clonar.
            File boxApk = findBoxApk(packageName, true);
            if (boxApk != null) {
                InstallResult fromBox = BlackBoxCore.getBPackageManager().installPackageAsUser(
                        packageName, InstallOption.installExistingForUser(), userId);
                if (!fromBox.success) {
                    return fromBox;
                }
                continue;
            }
            try {
                BlackBoxCore.getContext().getPackageManager().getApplicationInfo(packageName, 0);
            } catch (PackageManager.NameNotFoundException e) {
                
                continue;
            }
            InstallResult installResult = blackBoxCore.installPackageAsUser(packageName, userId);
            if (!installResult.success) {
                return installResult;
            }
        }
        return new InstallResult();
    }

    private static void uninstallPackages(Set<String> list, int userId) {
        BlackBoxCore blackBoxCore = BlackBoxCore.get();
        for (String packageName : list) {
            blackBoxCore.uninstallPackageAsUser(packageName, userId);
        }
    }

    public static InstallResult installGApps(int userId) {
        Set<String> googleApps = new HashSet<>();

        googleApps.addAll(GOOGLE_SERVICE);
        googleApps.addAll(GOOGLE_APP);

        // En ROM chinas el GMS del sistema suele estar incompleto (p. ej. GSF sin GmsCore). Solo se clona
        // del sistema si tiene el trío completo; si no, el GMS del box debe instalarse desde archivos.
        if (findBoxApk(GMS_PKG, true) == null && !hostHasCompleteGms()) {
            return new InstallResult().installError(
                    "El sistema no tiene un GMS completo (GmsCore, GSF y Play Store). Instala GMS desde archivos.");
        }

        InstallResult installResult = installPackages(googleApps, userId);
        if (!installResult.success) {
            uninstallGApps(userId);
            return installResult;
        }
        return installResult;
    }

    public static void uninstallGApps(int userId) {
        uninstallPackages(GOOGLE_SERVICE, userId);
        uninstallPackages(GOOGLE_APP, userId);
    }

    public static void remove(String packageName) {
        GOOGLE_SERVICE.remove(packageName);
        GOOGLE_APP.remove(packageName);
    }


    public static boolean isSupportGms() {
        try {
            BlackBoxCore.getPackageManager().getPackageInfo(GMS_PKG, 0);
            return true;
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        // Sin GMS en el sistema (ROM china) basta con que algún perfil del box lo tenga instalado.
        return findBoxApk(GMS_PKG) != null;
    }

    /** El sistema real tiene GmsCore, GSF y Play Store instalados. */
    private static boolean hostHasCompleteGms() {
        for (String pkg : new String[]{GMS_PKG, GSF_PKG, VENDING_PKG}) {
            try {
                BlackBoxCore.getContext().getPackageManager().getApplicationInfo(pkg, 0);
            } catch (PackageManager.NameNotFoundException e) {
                return false;
            }
        }
        return true;
    }

    /** APK de un paquete ya instalado en algún perfil del box, o null. */
    private static File findBoxApk(String packageName) {
        return findBoxApk(packageName, false);
    }

    /** Con skipMicrog se ignoran los perfiles en modo microG: su GmsCore no es el GMS real (ADR-0007). */
    private static File findBoxApk(String packageName, boolean skipMicrog) {
        try {
            for (BUserInfo user : BlackBoxCore.get().getUsers()) {
                if (skipMicrog && BlackBoxCore.get().isMicrogMode(user.id)) continue;
                ApplicationInfo info = BPackageManager.get().getApplicationInfo(packageName, 0, user.id);
                if (info != null && info.sourceDir != null && new File(info.sourceDir).exists()) {
                    return new File(info.sourceDir);
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static boolean isInstalledGoogleService(int userId) {
        return BlackBoxCore.get().isInstalled(GMS_PKG, userId);
    }

    /** Authority que pertenece a servicios de Google (GMS/GSF/Play Store). */
    public static boolean isGoogleAuthority(String authority) {
        return authority != null
                && (authority.contains(GMS_PKG)
                || authority.contains(VENDING_PKG)
                || authority.contains(GSF_PKG));
    }

    /**
     * Decide si un provider se pasa al sistema real. Los de Google solo se pasan
     * cuando el paquete NO esta instalado en el box para ese usuario.
     */
    public static boolean shouldPassthroughProvider(String authority, boolean resolvedInBox) {
        if (authority == null) return false;
        if (isGoogleAuthority(authority)) return !resolvedInBox;
        return authority.equals("settings")
                || authority.equals("media")
                || authority.equals("telephony")
                || authority.equals("com.huawei.android.launcher.settings")
                || authority.equals("com.hihonor.android.launcher.settings");
    }
}
