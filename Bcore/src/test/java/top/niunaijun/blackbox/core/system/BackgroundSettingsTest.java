package top.niunaijun.blackbox.core.system;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;

import org.junit.Test;

public class BackgroundSettingsTest {
    @Test
    public void iniciarConElSistemaActivoPorDefectoYConmutable() throws Exception {
        File dir = Files.createTempDirectory("bbset").toFile();
        assertTrue(BackgroundSettings.isStartOnBoot(dir));
        BackgroundSettings.setStartOnBoot(dir, false);
        assertFalse(BackgroundSettings.isStartOnBoot(dir));
        BackgroundSettings.setStartOnBoot(dir, true);
        assertTrue(BackgroundSettings.isStartOnBoot(dir));
    }

    @Test
    public void accionesQueReinician() {
        assertTrue(BackgroundSettings.isRestartAction("android.intent.action.BOOT_COMPLETED"));
        assertTrue(BackgroundSettings.isRestartAction("android.intent.action.MY_PACKAGE_REPLACED"));
        assertFalse(BackgroundSettings.isRestartAction("android.intent.action.USER_PRESENT"));
        assertFalse(BackgroundSettings.isRestartAction(null));
    }
}
