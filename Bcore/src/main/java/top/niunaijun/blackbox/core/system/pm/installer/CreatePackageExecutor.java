package top.niunaijun.blackbox.core.system.pm.installer;

import top.niunaijun.blackbox.core.env.BEnvironment;
import top.niunaijun.blackbox.core.system.pm.BPackageSettings;
import top.niunaijun.blackbox.entity.pm.InstallOption;
import top.niunaijun.blackbox.utils.FileUtils;


public class CreatePackageExecutor implements Executor {

    @Override
    public int exec(BPackageSettings ps, InstallOption option, int userId) {
        // Solo la copia de este perfil: las de otros perfiles no se tocan (ADR-0008)
        FileUtils.deleteDir(BEnvironment.getAppDir(ps.userId, ps.pkg.packageName));

        
        FileUtils.mkdirs(BEnvironment.getAppDir(ps.userId, ps.pkg.packageName));
        FileUtils.mkdirs(BEnvironment.getAppLibDir(ps.userId, ps.pkg.packageName));
        return 0;
    }
}
