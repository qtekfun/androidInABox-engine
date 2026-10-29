package top.niunaijun.blackbox.core.system.pm.split;

import static org.junit.Assert.*;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class SplitManifestReaderTest {
    @Test
    public void base() throws Exception {
        SplitInfo i = Fixtures.info("base.apk");
        assertTrue(i.isBase());
        assertEquals("com.example.fx", i.packageName);
        assertEquals(7, i.versionCode);
        assertTrue(i.isolatedSplits);
        assertNull(i.splitName);
        assertNull(i.configForSplit);
    }

    @Test
    public void basePlain() throws Exception {
        SplitInfo i = Fixtures.info("base_plain.apk");
        assertTrue(i.isBase());
        assertFalse(i.isolatedSplits);
    }

    @Test
    public void configSplit() throws Exception {
        SplitInfo i = Fixtures.info("config_arm64.apk");
        assertFalse(i.isBase());
        assertEquals("config.arm64_v8a", i.splitName);
        assertNull("configForSplit vacío significa base", i.configForSplit);
        assertFalse(i.isFeatureSplit);
    }

    @Test
    public void featureSplit() throws Exception {
        SplitInfo i = Fixtures.info("feature_x.apk");
        assertEquals("feature_x", i.splitName);
        assertTrue(i.isFeatureSplit);
        assertTrue(i.isSplitRequired);
    }

    @Test
    public void configOfFeature() throws Exception {
        SplitInfo i = Fixtures.info("config_feature_x_es.apk");
        assertEquals("feature_x.config.es", i.splitName);
        assertEquals("feature_x", i.configForSplit);
        assertTrue(i.isConfig());
    }

    @Test(expected = IOException.class)
    public void noManifest() throws Exception {
        File f = File.createTempFile("nomanifest", ".apk");
        try {
            try (ZipOutputStream z = new ZipOutputStream(new FileOutputStream(f))) {
                z.putNextEntry(new ZipEntry("x.txt"));
                z.write(1);
                z.closeEntry();
            }
            SplitManifestReader.read(f);
        } finally {
            f.delete();
        }
    }

    @Test(expected = IOException.class)
    public void garbage() throws Exception {
        SplitManifestReader.parse(new byte[]{3, 0, 8, 0, 0, 0, 0, 0, 1, 2, 3});
    }
}
