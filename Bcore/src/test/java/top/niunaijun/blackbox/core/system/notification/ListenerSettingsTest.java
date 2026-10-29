package top.niunaijun.blackbox.core.system.notification;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;

import org.junit.Test;

public class ListenerSettingsTest {
    @Test
    public void accesoPorUsuarioYComponente() throws Exception {
        File dir = Files.createTempDirectory("nls").toFile();
        ListenerAccessStore s = new ListenerAccessStore(dir);
        assertFalse(s.isGranted(0, "com.a/com.a.L"));
        s.setGranted(0, "com.a/com.a.L", true);
        assertTrue(s.isGranted(0, "com.a/com.a.L"));
        assertFalse(s.isGranted(1, "com.a/com.a.L"));
        assertTrue(s.isPackageGranted(0, "com.a"));
        assertFalse(s.isPackageGranted(0, "com.ab"));
        // otra instancia (otro proceso) ve lo mismo
        assertEquals(1, new ListenerAccessStore(dir).list(0).size());
        s.setGranted(0, "com.a/com.a.L", false);
        assertFalse(new ListenerAccessStore(dir).isGranted(0, "com.a/com.a.L"));
    }

    @Test
    public void puenteRealApagadoPorDefectoYConListaBlanca() throws Exception {
        File dir = Files.createTempDirectory("nls").toFile();
        RealBridgeSettings r = new RealBridgeSettings(dir, "host.pkg");
        r.setAllowed("com.tencent.mm", true);
        assertFalse("apagado por defecto", r.shouldForward("com.tencent.mm"));
        r.setEnabled(true);
        assertTrue(r.shouldForward("com.tencent.mm"));
        assertFalse("fuera de la lista", r.shouldForward("com.otra"));
        r.setAllowed("host.pkg", true);
        assertFalse("nunca las del host", r.shouldForward("host.pkg"));
        r.setAllowed("com.tencent.mm", false);
        assertFalse(r.shouldForward("com.tencent.mm"));
        r.setEnabled(false);
        assertFalse(r.isEnabled());
    }
}
