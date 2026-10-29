package top.niunaijun.blackbox.core.system.pm.split;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SplitSelectorTest {
    private static SplitInfo split(String name, String configFor, boolean feature) {
        SplitInfo s = new SplitInfo();
        s.packageName = "p";
        s.splitName = name;
        s.configForSplit = configFor;
        s.isFeatureSplit = feature;
        return s;
    }

    private static List<String> names(List<SplitInfo> l) {
        List<String> n = new ArrayList<>();
        for (SplitInfo s : l) n.add(s.splitName);
        return n;
    }

    private static SplitSet setConfig(String... names) throws Exception {
        List<SplitInfo> l = new ArrayList<>();
        SplitInfo base = new SplitInfo();
        base.packageName = "p";
        l.add(base);
        for (String n : names) {
            // formato "destino:nombre"; destino vacío = base ("*")
            String[] p = n.split(":");
            l.add(split(p[1], p[0].equals("*") ? "base" : p[0], false));
        }
        return SplitSet.of(l);
    }

    @Test
    public void abiPicksPrimary() throws Exception {
        SplitSet s = setConfig("*:config.arm64_v8a", "*:config.armeabi_v7a", "*:config.x86_64");
        SplitSelector.Result r = SplitSelector.select(s, Arrays.asList("arm64-v8a"), 480, Arrays.asList());
        assertEquals(Arrays.asList("config.arm64_v8a"), names(r.selected));
        assertEquals(2, r.discarded.size());
        assertFalse(r.abiMismatch);
    }

    @Test
    public void abi32BitFallsBackInOrder() throws Exception {
        SplitSet s = setConfig("*:config.armeabi", "*:config.arm64_v8a");
        SplitSelector.Result r = SplitSelector.select(s, Arrays.asList("armeabi-v7a", "armeabi"), 480, Arrays.asList());
        assertEquals(Arrays.asList("config.armeabi"), names(r.selected));
    }

    @Test
    public void abiMismatchDropsAllAndFlags() throws Exception {
        SplitSet s = setConfig("*:config.x86", "*:config.x86_64");
        SplitSelector.Result r = SplitSelector.select(s, Arrays.asList("arm64-v8a"), 480, Arrays.asList());
        assertTrue(r.selected.isEmpty());
        assertTrue(r.abiMismatch);
    }

    @Test
    public void densityClosest() throws Exception {
        SplitSet s = setConfig("*:config.mdpi", "*:config.hdpi", "*:config.xhdpi", "*:config.xxhdpi", "*:config.xxxhdpi");
        assertEquals(Arrays.asList("config.xxhdpi"),
                names(SplitSelector.select(s, Arrays.asList("arm64-v8a"), 440, Arrays.asList()).selected));
        assertEquals(Arrays.asList("config.mdpi"),
                names(SplitSelector.select(s, Arrays.asList("arm64-v8a"), 100, Arrays.asList()).selected));
        assertEquals(Arrays.asList("config.xxxhdpi"),
                names(SplitSelector.select(s, Arrays.asList("arm64-v8a"), 800, Arrays.asList()).selected));
    }

    @Test
    public void densityTieGoesHigher() throws Exception {
        SplitSet s = setConfig("*:config.hdpi", "*:config.xhdpi");
        // 280 equidista de 240 y 320
        assertEquals(Arrays.asList("config.xhdpi"),
                names(SplitSelector.select(s, Arrays.asList("arm64-v8a"), 280, Arrays.asList()).selected));
    }

    @Test
    public void localesSystemPlusEnglish() throws Exception {
        SplitSet s = setConfig("*:config.en", "*:config.es", "*:config.fr", "*:config.pt_BR", "*:config.pt_PT",
                "*:config.b+sr+Latn", "*:config.zh_rTW");
        SplitSelector.Result r = SplitSelector.select(s, Arrays.asList("arm64-v8a"), 480,
                Arrays.asList("es-ES", "pt-BR"));
        assertEquals(Arrays.asList("config.en", "config.es", "config.pt_BR"), names(r.selected));
    }

    @Test
    public void bcp47Locale() throws Exception {
        SplitSet s = setConfig("*:config.b+sr+Latn", "*:config.de");
        assertEquals(Arrays.asList("config.b+sr+Latn"),
                names(SplitSelector.select(s, Arrays.asList("arm64-v8a"), 480, Arrays.asList("sr")).selected));
    }

    @Test
    public void perTargetSelection() throws Exception {
        SplitSet s = setConfig("*:config.xxhdpi", "*:config.hdpi", "feature_x:feature_x.config.xxhdpi",
                "feature_x:feature_x.config.mdpi", "feature_x:feature_x.config.es");
        SplitSelector.Result r = SplitSelector.select(s, Arrays.asList("arm64-v8a"), 480, Arrays.asList("es"));
        assertEquals(Arrays.asList("config.xxhdpi", "feature_x.config.xxhdpi", "feature_x.config.es"),
                names(r.selected));
    }

    @Test
    public void featureAndUnknownConfigKept() throws Exception {
        List<SplitInfo> l = new ArrayList<>();
        SplitInfo base = new SplitInfo();
        base.packageName = "p";
        l.add(base);
        l.add(split("feature_x", null, true));
        l.add(split("config.weirdthing", "base", false));
        SplitSelector.Result r = SplitSelector.select(SplitSet.of(l), Arrays.asList("arm64-v8a"), 480, Arrays.asList());
        assertEquals(Arrays.asList("feature_x", "config.weirdthing"), names(r.selected));
    }

    @Test
    public void suffix() {
        assertEquals("arm64_v8a", SplitSelector.configSuffix("config.arm64_v8a"));
        assertEquals("es", SplitSelector.configSuffix("feature_x.config.es"));
        assertEquals("xxhdpi", SplitSelector.configSuffix("feature_x.config.xxhdpi"));
    }
}
