package top.niunaijun.blackbox.core.system.pm.split;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;

public class SplitPersistTest {
    private File dir;

    @Before
    public void setUp() throws Exception {
        dir = ZipTestUtil.tempDir();
    }

    @After
    public void tearDown() {
        ZipTestUtil.deleteAll(dir);
    }

    private File touch(String name) throws Exception {
        File f = new File(dir, name);
        Files.write(f.toPath(), new byte[]{1});
        return f;
    }

    @Test
    public void roundTripKeepsOrder() throws Exception {
        touch("split_config.en.apk");
        touch("split_feature_x.apk");
        File conf = new File(dir, "splits.conf");
        SplitsConf.write(conf, Arrays.asList("config.en", "feature_x"),
                Arrays.asList("split_config.en.apk", "split_feature_x.apk"));
        SplitsConf r = SplitsConf.read(conf, dir);
        assertEquals(Arrays.asList("config.en", "feature_x"), r.names);
        assertEquals(new File(dir, "split_config.en.apk").getAbsolutePath(), r.codePaths.get(0));
        assertEquals(new File(dir, "split_feature_x.apk").getAbsolutePath(), r.codePaths.get(1));
    }

    @Test
    public void missingConfMeansNoSplits() {
        SplitsConf r = SplitsConf.read(new File(dir, "splits.conf"), dir);
        assertTrue(r.names.isEmpty());
        assertTrue(r.codePaths.isEmpty());
    }

    @Test
    public void absolutePathAllowed() throws Exception {
        File abs = touch("abs.apk");
        File conf = new File(dir, "splits.conf");
        SplitsConf.write(conf, Arrays.asList("a"), Arrays.asList(abs.getAbsolutePath()));
        assertEquals(Arrays.asList(abs.getAbsolutePath()), SplitsConf.read(conf, new File("/nonexistent")).codePaths);
    }

    @Test
    public void skipsMalformedMissingAndDuplicates() throws Exception {
        touch("ok.apk");
        File conf = new File(dir, "splits.conf");
        Files.write(conf.toPath(), ("sin_tab\n\tvacio\nnombre\t\nmissing\tmissing.apk\nok\tok.apk\nok\tok.apk\n").getBytes("UTF-8"));
        SplitsConf r = SplitsConf.read(conf, dir);
        assertEquals(Arrays.asList("ok"), r.names);
    }

    @Test(expected = java.io.IOException.class)
    public void rejectsSeparatorsOnWrite() throws Exception {
        SplitsConf.write(new File(dir, "splits.conf"), Arrays.asList("a\tb"), Arrays.asList("x.apk"));
    }

    @Test
    public void writeReplacesAtomically() throws Exception {
        touch("a.apk");
        File conf = new File(dir, "splits.conf");
        SplitsConf.write(conf, Arrays.asList("x", "y"), Arrays.asList("a.apk", "a.apk"));
        SplitsConf.write(conf, Arrays.asList("z"), Arrays.asList("a.apk"));
        assertEquals(Arrays.asList("z"), SplitsConf.read(conf, dir).names);
        assertFalse(new File(dir, "splits.conf.tmp").exists());
    }
}
