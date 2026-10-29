package top.niunaijun.blackbox.core.system.pm;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;

import org.junit.Test;

public class AppFlagsStoreTest {
    @Test
    public void persisteYAislaPorUsuarioYPaquete() throws Exception {
        File f = new File(Files.createTempDirectory("aiab").toFile(), "app-flags.conf");
        AppFlagsStore s = new AppFlagsStore(f);
        assertFalse(s.get("a.b", 0, AppFlagsStore.FLAG_PROPERTY_SPOOF));
        s.set("a.b", 0, AppFlagsStore.FLAG_PROPERTY_SPOOF, true);
        assertTrue(s.get("a.b", 0, AppFlagsStore.FLAG_PROPERTY_SPOOF));
        assertFalse(s.get("a.b", 1, AppFlagsStore.FLAG_PROPERTY_SPOOF));
        assertFalse(s.get("c.d", 0, AppFlagsStore.FLAG_PROPERTY_SPOOF));
        AppFlagsStore otra = new AppFlagsStore(f);
        assertTrue(otra.get("a.b", 0, AppFlagsStore.FLAG_PROPERTY_SPOOF));
        otra.set("a.b", 0, AppFlagsStore.FLAG_PROPERTY_SPOOF, false);
        assertFalse(new AppFlagsStore(f).get("a.b", 0, AppFlagsStore.FLAG_PROPERTY_SPOOF));
    }
}
