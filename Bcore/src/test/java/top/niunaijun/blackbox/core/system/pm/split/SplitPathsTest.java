package top.niunaijun.blackbox.core.system.pm.split;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.io.File;

public class SplitPathsTest {
    @Test
    public void baseOnly() {
        assertEquals("/a/base.apk", SplitPaths.dexPath("/a/base.apk", null));
        assertEquals("/a/base.apk", SplitPaths.dexPath("/a/base.apk", new String[0]));
    }

    @Test
    public void baseThenSplitsSkippingEmpty() {
        String sep = File.pathSeparator;
        assertEquals("/a/base.apk" + sep + "/a/s1.apk" + sep + "/a/s2.apk",
                SplitPaths.dexPath("/a/base.apk", new String[]{"/a/s1.apk", null, "", "/a/s2.apk"}));
    }
}
