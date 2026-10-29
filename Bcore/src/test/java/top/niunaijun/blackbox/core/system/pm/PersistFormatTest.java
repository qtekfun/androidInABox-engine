package top.niunaijun.blackbox.core.system.pm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

import top.niunaijun.blackbox.entity.pm.InstallOption;

public class PersistFormatTest {
    @Test
    public void sinMarcaEsLegacy() {
        assertEquals(PersistFormat.VERSION_LEGACY, PersistFormat.resolveVersion(43, 7));
        assertEquals(PersistFormat.VERSION_LEGACY, PersistFormat.resolveVersion(-1, 0));
        assertFalse(PersistFormat.hasHeader(0));
    }

    @Test
    public void conMarcaLeeLaVersion() {
        assertEquals(1, PersistFormat.resolveVersion(PersistFormat.MAGIC, 1));
        assertEquals(PersistFormat.VERSION_ACTUAL, PersistFormat.resolveVersion(PersistFormat.MAGIC, PersistFormat.VERSION_ACTUAL));
        assertTrue(PersistFormat.hasHeader(PersistFormat.MAGIC));
    }

    @Test
    public void versionesFuturasSeAceptan() {
        assertEquals(99, PersistFormat.resolveVersion(PersistFormat.MAGIC, 99));
    }

    @Test(expected = IllegalStateException.class)
    public void versionInvalidaFalla() {
        PersistFormat.resolveVersion(PersistFormat.MAGIC, 0);
    }

    @Test
    public void banderasRecuperadas() {
        assertEquals(InstallOption.FLAG_STORAGE, PersistFormat.recoverInstallFlags(true, true));
        assertEquals(InstallOption.FLAG_SYSTEM, PersistFormat.recoverInstallFlags(false, true));
        assertEquals(0, PersistFormat.recoverInstallFlags(false, false));
    }

    @Test
    public void usuariosConDatosSeAsumenInstalados() {
        assertEquals(Arrays.asList(0, 2),
                PersistFormat.assumeInstalledUsers(Arrays.asList(0, 1, 2), id -> id != 1));
    }

    @Test
    public void sinDatosSeAsumeUsuarioCero() {
        assertEquals(Collections.singletonList(0),
                PersistFormat.assumeInstalledUsers(Arrays.asList(3, 4), id -> false));
    }
}
