package top.niunaijun.blackbox.core.system.pm;

import static org.junit.Assert.*;

import android.content.pm.ApplicationInfo;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class GenerateApplicationInfoTest {
    @Test
    public void fillsAlignedArrays() {
        ApplicationInfo ai = new ApplicationInfo();
        PackageManagerCompat.applySplits(ai, Arrays.asList("config.en", "feature_x"),
                Arrays.asList("/a/split_config.en.apk", "/a/split_feature_x.apk"));
        assertArrayEquals(new String[]{"config.en", "feature_x"}, ai.splitNames);
        assertArrayEquals(new String[]{"/a/split_config.en.apk", "/a/split_feature_x.apk"}, ai.splitSourceDirs);
        assertArrayEquals(ai.splitSourceDirs, ai.splitPublicSourceDirs);
        assertNotSame(ai.splitSourceDirs, ai.splitPublicSourceDirs);
    }

    @Test
    public void noSplitsKeepsInheritedValues() {
        ApplicationInfo ai = new ApplicationInfo();
        ai.splitSourceDirs = new String[]{"/system/split.apk"};
        ai.splitNames = new String[]{"x"};
        PackageManagerCompat.applySplits(ai, null, null);
        PackageManagerCompat.applySplits(ai, Collections.<String>emptyList(), Collections.<String>emptyList());
        assertArrayEquals(new String[]{"/system/split.apk"}, ai.splitSourceDirs);
        assertArrayEquals(new String[]{"x"}, ai.splitNames);
    }

    @Test
    public void mismatchedListsAreIgnored() {
        ApplicationInfo ai = new ApplicationInfo();
        PackageManagerCompat.applySplits(ai, Arrays.asList("a", "b"), Arrays.asList("/x"));
        assertNull(ai.splitNames);
        assertNull(ai.splitSourceDirs);
    }
}
