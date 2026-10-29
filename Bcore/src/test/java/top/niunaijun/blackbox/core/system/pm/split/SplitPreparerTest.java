package top.niunaijun.blackbox.core.system.pm.split;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;

public class SplitPreparerTest {
    private File dir;

    @Before
    public void setUp() throws Exception {
        dir = ZipTestUtil.tempDir();
    }

    @After
    public void tearDown() {
        ZipTestUtil.deleteAll(dir);
    }

    private void put(String fixture, String as) throws Exception {
        Files.copy(Fixtures.apk(fixture).toPath(), new File(dir, as).toPath());
    }

    private byte[] bytes(String fixture) throws Exception {
        return Files.readAllBytes(Fixtures.apk(fixture).toPath());
    }

    private String failMsg(long free) {
        try {
            SplitPreparer.prepare(dir, free);
            fail("Debía fallar");
            return null;
        } catch (InvalidSplitSetException e) {
            return e.getMessage();
        } catch (java.io.IOException e) {
            return "IO " + e.getMessage();
        }
    }

    @Test
    public void multiApk() throws Exception {
        put("base.apk", "in-0");
        put("config_arm64.apk", "in-1");
        put("feature_x.apk", "in-2");
        SplitSet s = SplitPreparer.prepare(dir, Long.MAX_VALUE);
        assertEquals(2, s.splits.size());
        assertEquals("in-0", s.base.file.getName());
    }

    @Test
    public void bundleWithSplitsDir() throws Exception {
        ZipTestUtil.zip(dir, "in-0", "splits/base-master.apk", bytes("base.apk"),
                "splits/base-arm64_v8a.apk", bytes("config_arm64.apk"), "toc.pb", "x");
        SplitSet s = SplitPreparer.prepare(dir, Long.MAX_VALUE);
        assertEquals(1, s.splits.size());
        assertEquals("config.arm64_v8a", s.splits.get(0).splitName);
    }

    @Test
    public void bundlePlusLooseSplit() throws Exception {
        ZipTestUtil.zip(dir, "in-0", "base.apk", bytes("base.apk"));
        put("config_xxhdpi.apk", "in-1");
        assertEquals(1, SplitPreparer.prepare(dir, Long.MAX_VALUE).splits.size());
    }

    @Test
    public void invalidSets() throws Exception {
        put("config_arm64.apk", "in-0");
        assertTrue(failMsg(Long.MAX_VALUE).contains("base"));
        put("base.apk", "in-1");
        put("base_plain.apk", "in-2");
        assertTrue(failMsg(Long.MAX_VALUE).contains("más de un APK base"));
        new File(dir, "in-2").delete();
        put("other_package.apk", "in-3");
        assertTrue(failMsg(Long.MAX_VALUE).contains("paquete"));
        new File(dir, "in-3").delete();
        put("other_version.apk", "in-4");
        assertTrue(failMsg(Long.MAX_VALUE).contains("versionCode"));
    }

    @Test
    public void notAnApkOrBundle() throws Exception {
        Files.write(new File(dir, "in-0").toPath(), "hola".getBytes());
        assertTrue(failMsg(Long.MAX_VALUE).contains("no es un APK"));
    }

    @Test
    public void emptyDir() {
        assertTrue(failMsg(Long.MAX_VALUE).contains("No hay ficheros"));
    }

    @Test
    public void noSpaceAbortsBeforeExtracting() throws Exception {
        ZipTestUtil.zip(dir, "in-0", "base.apk", new byte[10_000]);
        assertTrue(failMsg(10_000).contains("Espacio insuficiente"));
        assertTrue(failMsg(10_999).contains("Espacio insuficiente"));
        assertFalse(new File(dir, "extracted-0").exists());
        // con margen del 10 % sí cabe (aunque luego no sea un APK válido)
        assertTrue(failMsg(11_000).contains("ilegible"));
    }
}
