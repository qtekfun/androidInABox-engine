package top.niunaijun.blackbox.core.system.pm.session;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashSet;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class InstallSessionStoreTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();
    private InstallSessionStore store;

    @Before
    public void setUp() throws Exception {
        store = new InstallSessionStore(tmp.newFolder("sessions"));
    }

    @Test
    public void createAsignaIdsDistintosYStaging() throws Exception {
        SessionRecord a = store.create(0, "com.android.vending");
        SessionRecord b = store.create(0, "com.android.vending");
        assertTrue(a.id != b.id);
        assertTrue(a.dir.isDirectory());
        assertSame(a, store.get(a.id));
    }

    private static void assertSame(Object a, Object b) {
        org.junit.Assert.assertSame(a, b);
    }

    @Test
    public void listaFiltraPorUsuarioEInstalador() throws Exception {
        SessionRecord a = store.create(0, "play");
        store.create(0, "otro");
        store.create(1, "play");
        assertEquals(2, store.list(0, null).size());
        List<SessionRecord> mine = store.list(0, "play");
        assertEquals(1, mine.size());
        assertEquals(a.id, mine.get(0).id);
        assertNull(store.get(a.id, 1));
        assertNotNull(store.get(a.id, 0));
    }

    @Test
    public void removeBorraStaging() throws Exception {
        SessionRecord a = store.create(0, "play");
        File f = InstallSessionStore.stagedFile(a, "base.apk");
        try (FileOutputStream o = new FileOutputStream(f)) {
            o.write(1);
        }
        store.remove(a.id);
        assertFalse(a.dir.exists());
        assertNull(store.get(a.id));
    }

    @Test
    public void forgetConservaStaging() throws Exception {
        SessionRecord a = store.create(0, "play");
        store.forget(a.id);
        assertTrue(a.dir.exists());
        assertNull(store.get(a.id));
    }

    @Test
    public void nombresInvalidosSeRechazan() {
        assertFalse(InstallSessionStore.isValidName(null));
        assertFalse(InstallSessionStore.isValidName(""));
        assertFalse(InstallSessionStore.isValidName(".."));
        assertFalse(InstallSessionStore.isValidName("../x.apk"));
        assertFalse(InstallSessionStore.isValidName("a/b"));
        assertTrue(InstallSessionStore.isValidName("split_config.es.apk"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void stagedFileConTraversalFalla() throws Exception {
        InstallSessionStore.stagedFile(store.create(0, "p"), "../../etc");
    }

    @Test
    public void stagedNamesOrdenados() throws Exception {
        SessionRecord a = store.create(0, "p");
        for (String n : new String[]{"b.apk", "a.apk"}) {
            assertTrue(new File(a.dir, n).createNewFile());
        }
        assertArrayEquals(new String[]{"a.apk", "b.apk"}, InstallSessionStore.stagedNames(a));
    }

    @Test
    public void estadoExitoLlevaExtrasDelSistema() {
        Map<String, Object> m = InstallStatus.extras(InstallStatus.STATUS_SUCCESS, null, "com.whatsapp", 1001);
        assertEquals(0, m.get("android.content.pm.extra.STATUS"));
        assertEquals("com.whatsapp", m.get("android.content.pm.extra.PACKAGE_NAME"));
        assertEquals(1001, m.get("android.content.pm.extra.SESSION_ID"));
        assertEquals("INSTALL_SUCCEEDED", m.get("android.content.pm.extra.STATUS_MESSAGE"));
        assertEquals(1, m.get("android.content.pm.extra.LEGACY_STATUS"));
    }

    @Test
    public void estadoFalloConservaMensajeYOmiteSesionSiNegativa() {
        Map<String, Object> m = InstallStatus.extras(InstallStatus.STATUS_FAILURE, "boom", "p", -1);
        assertEquals(1, m.get("android.content.pm.extra.STATUS"));
        assertEquals("boom", m.get("android.content.pm.extra.STATUS_MESSAGE"));
        assertFalse(m.containsKey("android.content.pm.extra.SESSION_ID"));
    }

    @Test
    public void inheritSustituyeSplitsYDescartaOtraVersion() {
        List<InheritPlanner.Candidate> installed = Arrays.asList(
                new InheritPlanner.Candidate(null, "/b.apk", 5),
                new InheritPlanner.Candidate("config.es", "/es.apk", 5),
                new InheritPlanner.Candidate("config.en", "/en.apk", 4),
                new InheritPlanner.Candidate("config.fr", "/fr.apk", 5));
        // la sesión trae base nuevo (v5) y config.es
        List<InheritPlanner.Candidate> out = InheritPlanner.plan(
                new HashSet<>(Arrays.asList("config.es")), true, 5, installed);
        assertEquals(1, out.size());
        assertEquals("config.fr", out.get(0).splitName);
        // sesión solo de split: se hereda el base
        out = InheritPlanner.plan(new HashSet<>(Arrays.asList("config.es")), false, 5, installed);
        assertEquals(2, out.size());
        assertNull(out.get(0).splitName);
    }
}
