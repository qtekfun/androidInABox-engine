package top.niunaijun.blackbox.core.system.pm.split;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;

import top.niunaijun.blackbox.core.system.pm.split.BundleDetector.Kind;

public class BundleDetectorTest {
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
    public void apkWithAnyName() throws Exception {
        File apk = Fixtures.apk("base.apk");
        for (String name : new String[]{"x.zip", "noext", "x.apks", "x.bin"}) {
            File copy = new File(dir, name);
            Files.copy(apk.toPath(), copy.toPath());
            assertEquals(name, Kind.APK, BundleDetector.detect(copy));
        }
    }

    @Test
    public void bundleWithRootApks() throws Exception {
        File f = ZipTestUtil.zip(dir, "b.zip", "base.apk", "x", "split_config.en.apk", "y", "info.json", "{}");
        assertEquals(Kind.BUNDLE, BundleDetector.detect(f));
    }

    @Test
    public void bundleWithSplitsDirAndUpperCase() throws Exception {
        File f = ZipTestUtil.zip(dir, "b", "toc.pb", "x", "splits/base-master.APK", "y");
        assertEquals(Kind.BUNDLE, BundleDetector.detect(f));
    }

    @Test
    public void zipWithoutApk() throws Exception {
        File f = ZipTestUtil.zip(dir, "b.apk", "readme.txt", "x");
        assertEquals(Kind.UNKNOWN, BundleDetector.detect(f));
    }

    @Test
    public void notAZipOrMissing() throws Exception {
        File f = new File(dir, "t.apk");
        Files.write(f.toPath(), "hola".getBytes());
        assertEquals(Kind.UNKNOWN, BundleDetector.detect(f));
        assertEquals(Kind.UNKNOWN, BundleDetector.detect(new File(dir, "nope")));
        assertEquals(Kind.UNKNOWN, BundleDetector.detect(dir));
    }
}
