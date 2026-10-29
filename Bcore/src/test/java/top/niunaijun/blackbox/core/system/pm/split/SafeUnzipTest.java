package top.niunaijun.blackbox.core.system.pm.split;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class SafeUnzipTest {
    private File dir;

    @Before
    public void setUp() throws Exception {
        dir = ZipTestUtil.tempDir();
    }

    @After
    public void tearDown() {
        ZipTestUtil.deleteAll(dir);
    }

    @Test
    public void extractsOnlyApks() throws Exception {
        File z = ZipTestUtil.zip(dir, "b.zip", "base.apk", "aaa", "splits/config.en.apk", "bb",
                "toc.pb", "zz", "Android/obb/x.obb", "big");
        File out = new File(dir, "out");
        List<File> files = new SafeUnzip().extractApks(z, out);
        assertEquals(2, files.size());
        assertTrue(new File(out, "base.apk").isFile());
        assertEquals(2, new File(out, "splits/config.en.apk").length());
        assertFalse(new File(out, "toc.pb").exists());
        assertFalse(new File(out, "Android").exists());
        assertEquals(5, SafeUnzip.declaredApkSize(z));
    }

    private void assertRejected(File z) throws Exception {
        File out = new File(dir, "out");
        try {
            new SafeUnzip().extractApks(z, out);
            fail("Debía fallar");
        } catch (IOException expected) {
        }
        assertFalse(new File(dir, "evil.apk").exists());
    }

    @Test
    public void zipSlip() throws Exception {
        assertRejected(ZipTestUtil.zip(dir, "a.zip", "../evil.apk", "x"));
        assertRejected(ZipTestUtil.zip(dir, "b.zip", "a/../../evil.apk", "x"));
    }

    @Test
    public void absoluteAndBackslash() throws Exception {
        assertRejected(ZipTestUtil.zip(dir, "a.zip", "/etc/evil.apk", "x"));
        assertRejected(ZipTestUtil.zip(dir, "b.zip", "..\\evil.apk", "x"));
        assertRejected(ZipTestUtil.zip(dir, "c.zip", "C:/evil.apk", "x"));
    }

    @Test
    public void tooManyEntries() throws Exception {
        Object[] nd = new Object[10];
        for (int i = 0; i < 5; i++) {
            nd[2 * i] = "f" + i + ".txt";
            nd[2 * i + 1] = "x";
        }
        File z = ZipTestUtil.zip(dir, "a.zip", nd);
        try {
            new SafeUnzip(4, 1000).extractApks(z, new File(dir, "out"));
            fail();
        } catch (IOException expected) {
        }
        new SafeUnzip(5, 1000).extractApks(z, new File(dir, "out2"));
    }

    @Test
    public void bombIsCountedByRealBytes() throws Exception {
        byte[] zeros = new byte[1024 * 1024];
        File z = ZipTestUtil.zip(dir, "a.zip", "a.apk", zeros, "b.apk", zeros);
        assertTrue(z.length() < 20_000);
        try {
            new SafeUnzip(10, 1024 * 1024 + 10).extractApks(z, new File(dir, "out"));
            fail();
        } catch (IOException expected) {
        }
        assertEquals(2, new SafeUnzip(10, 3L * 1024 * 1024).extractApks(z, new File(dir, "out2")).size());
    }
}
