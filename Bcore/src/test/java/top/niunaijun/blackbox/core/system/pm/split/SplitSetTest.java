package top.niunaijun.blackbox.core.system.pm.split;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SplitSetTest {
    private static List<SplitInfo> infos(String... names) throws Exception {
        List<SplitInfo> l = new ArrayList<>();
        for (String n : names) l.add(Fixtures.info(n));
        return l;
    }

    private static void assertInvalid(String contains, String... names) throws Exception {
        try {
            SplitSet.of(infos(names));
            fail("Debía fallar");
        } catch (InvalidSplitSetException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(contains));
        }
    }

    @Test
    public void valid() throws Exception {
        SplitSet s = SplitSet.of(infos("config_arm64.apk", "base.apk", "feature_x.apk", "config_xxhdpi.apk"));
        assertEquals("com.example.fx", s.getPackageName());
        assertEquals("base.apk", s.base.file.getName());
        assertEquals(3, s.splits.size());
        assertEquals("config.arm64_v8a", s.splits.get(0).splitName);
    }

    @Test
    public void onlyBase() throws Exception {
        assertEquals(0, SplitSet.of(infos("base.apk")).splits.size());
    }

    @Test
    public void noBase() throws Exception {
        assertInvalid("Falta el APK base", "config_arm64.apk", "feature_x.apk");
    }

    @Test
    public void twoBases() throws Exception {
        assertInvalid("más de un APK base", "base.apk", "base_plain.apk");
    }

    @Test
    public void mixedPackages() throws Exception {
        assertInvalid("Nombres de paquete distintos", "base.apk", "other_package.apk");
    }

    @Test
    public void versionMismatch() throws Exception {
        assertInvalid("versionCode", "base.apk", "other_version.apk");
    }

    @Test
    public void duplicateSplit() throws Exception {
        assertInvalid("duplicado", "base.apk", "config_arm64.apk", "config_arm64.apk");
    }

    @Test(expected = InvalidSplitSetException.class)
    public void empty() throws Exception {
        SplitSet.of(Arrays.asList());
    }
}
