package top.niunaijun.blackbox.core.system.pm.installer;


import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import top.niunaijun.blackbox.core.env.BEnvironment;
import top.niunaijun.blackbox.core.system.pm.BPackageSettings;
import top.niunaijun.blackbox.core.system.pm.split.SplitsConf;
import top.niunaijun.blackbox.entity.pm.InstallOption;
import top.niunaijun.blackbox.utils.FileUtils;
import top.niunaijun.blackbox.utils.NativeUtils;


public class CopyExecutor implements Executor {

    @Override
    public int exec(BPackageSettings ps, InstallOption option, int userId) {
        boolean hasSplits = ps.pkg.splitCodePaths != null && !ps.pkg.splitCodePaths.isEmpty();
        try {
            if (!option.isFlag(InstallOption.FLAG_SYSTEM)) {
                List<File> apks = new ArrayList<>();
                apks.add(new File(ps.pkg.baseCodePath));
                if (hasSplits) {
                    for (String split : ps.pkg.splitCodePaths) {
                        apks.add(new File(split));
                    }
                }
                NativeUtils.copyNativeLib(apks, BEnvironment.getAppLibDir(ps.pkg.packageName));
            }
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
        if (option.isFlag(InstallOption.FLAG_STORAGE)) {
            
            File origFile = new File(ps.pkg.baseCodePath);
            File newFile = BEnvironment.getBaseApkDir(ps.pkg.packageName);
            boolean move = option.isFlag(InstallOption.FLAG_URI_FILE) || option.isFlag(InstallOption.FLAG_SPLIT_SET);
            try {
                moveOrCopy(origFile, newFile, move);
                ps.pkg.baseCodePath = newFile.getAbsolutePath();

                if (hasSplits) {
                    File appDir = BEnvironment.getAppDir(ps.pkg.packageName);
                    List<String> files = new ArrayList<>();
                    List<String> newPaths = new ArrayList<>();
                    for (int i = 0; i < ps.pkg.splitCodePaths.size(); i++) {
                        String fileName = "split_" + sanitize(ps.pkg.splitNames.get(i)) + ".apk";
                        File target = new File(appDir, fileName);
                        moveOrCopy(new File(ps.pkg.splitCodePaths.get(i)), target, move);
                        files.add(fileName);
                        newPaths.add(target.getAbsolutePath());
                    }
                    SplitsConf.write(BEnvironment.getSplitsConf(ps.pkg.packageName), ps.pkg.splitNames, files);
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
