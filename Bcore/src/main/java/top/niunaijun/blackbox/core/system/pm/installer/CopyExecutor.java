package top.niunaijun.blackbox.core.system.pm.installer;


import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import top.niunaijun.blackbox.core.env.BEnvironment;
import top.niunaijun.blackbox.core.system.pm.BPackageSettings;
import top.niunaijun.blackbox.core.system.pm.split.SplitsConf;
import top.niunaijun.blackbox.entity.pm.InstallOption;
import top.niunaijun.blackbox.utils.FileUtils;
import top.niunaijun.blackbox.utils.NativeUtils;


public class CopyExecutor implements Executor {

    @Override
    public int exec(BPackageSettings ps, InstallOption option, int userId) {
        userId = ps.userId;
        String packageName = ps.pkg.packageName;
        boolean hasSplits = ps.pkg.splitCodePaths != null && !ps.pkg.splitCodePaths.isEmpty();
        List<File> apks = new ArrayList<>();
        apks.add(new File(ps.pkg.baseCodePath));
        // Nombre final de cada APK dentro de la carpeta de la app
        Map<String, File> named = new LinkedHashMap<>();
        named.put("base.apk", apks.get(0));
        if (hasSplits) {
            for (int i = 0; i < ps.pkg.splitCodePaths.size(); i++) {
                File split = new File(ps.pkg.splitCodePaths.get(i));
                apks.add(split);
                named.put(splitFileName(ps.pkg.splitNames.get(i)), split);
            }
        }
        // Copia idéntica en otro perfil: se enlaza en vez de duplicar el APK y las libs (ADR-0008)
        File sibling = option.isFlag(InstallOption.FLAG_STORAGE)
                ? ApkDedup.findIdenticalCopy(userId, packageName, named) : null;
        try {
            if (!option.isFlag(InstallOption.FLAG_SYSTEM)) {
                File libDir = BEnvironment.getAppLibDir(userId, packageName);
                File siblingLib = sibling != null ? new File(sibling, "lib") : null;
                if (siblingLib != null && siblingLib.isDirectory()) {
                    ApkDedup.linkLibTree(siblingLib, libDir);
                } else {
                    NativeUtils.copyNativeLib(apks, libDir);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
        if (option.isFlag(InstallOption.FLAG_STORAGE)) {
            
            File origFile = new File(ps.pkg.baseCodePath);
            File newFile = BEnvironment.getBaseApkDir(userId, packageName);
            boolean move = option.isFlag(InstallOption.FLAG_URI_FILE) || option.isFlag(InstallOption.FLAG_SPLIT_SET);
            try {
                place(origFile, newFile, move, sibling);
                ps.pkg.baseCodePath = newFile.getAbsolutePath();

                if (hasSplits) {
                    File appDir = BEnvironment.getAppDir(userId, packageName);
                    List<String> files = new ArrayList<>();
                    List<String> newPaths = new ArrayList<>();
                    for (int i = 0; i < ps.pkg.splitCodePaths.size(); i++) {
                        String fileName = splitFileName(ps.pkg.splitNames.get(i));
                        File target = new File(appDir, fileName);
                        place(new File(ps.pkg.splitCodePaths.get(i)), target, move, sibling);
                        files.add(fileName);
                        newPaths.add(target.getAbsolutePath());
                    }
                    SplitsConf.write(BEnvironment.getSplitsConf(userId, packageName), ps.pkg.splitNames, files);
                    ps.pkg.splitCodePaths = newPaths;
                }
            } catch (IOException e) {
                e.printStackTrace();
                return -1;
            }
        } else if (option.isFlag(InstallOption.FLAG_SYSTEM)) {
            
        }
        return 0;
    }

    private static String splitFileName(String splitName) {
        return "split_" + sanitize(splitName) + ".apk";
    }

    /** Coloca el APK en su destino: enlace a la copia idéntica de otro perfil si la hay; si no, mover o copiar. */
    private static void place(File orig, File target, boolean move, File siblingDir) throws IOException {
        if (siblingDir != null && ApkDedup.link(new File(siblingDir, target.getName()), target)) {
            if (move) orig.delete();
            return;
        }
        moveOrCopy(orig, target, move);
    }

    private static void moveOrCopy(File orig, File target, boolean move) throws IOException {
        if (move) {
            if (!FileUtils.renameTo(orig, target)) {
                FileUtils.copyFile(orig, target);
            }
        } else {
            FileUtils.copyFile(orig, target);
        }
        target.setReadOnly();
    }

    /** Nombre de split seguro para usar como nombre de fichero. */
    private static String sanitize(String splitName) {
        return splitName.replaceAll("[^A-Za-z0-9._+-]", "_");
    }
}
