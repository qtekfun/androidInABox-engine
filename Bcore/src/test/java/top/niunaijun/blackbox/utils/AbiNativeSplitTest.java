package top.niunaijun.blackbox.utils;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class AbiNativeSplitTest {
    private File dir;

    @Before
    public void setUp() throws Exception {
        dir = Files.createTempDirectory("abitest").toFile();
    }

    @After
    public void tearDown() {
        delete(dir);
    }

    private static void delete(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) delete(k);
        f.delete();
    }

    private File zip(String name, String... entries) throws Exception {
        File f = new File(dir, name);
        try (ZipOutputStream z = new ZipOutputStream(new FileOutputStream(f))) {
            for (String e : entries) {
                z.putNextEntry(new ZipEntry(e));
                z.write(e.getBytes("UTF-8"));
                z.closeEntry();
            }
        }
        return f;
    }

    @Test
    public void configSplit() throws Exception {
        File base = zip("base.apk", "classes.dex");
        File arm64 = zip("config.arm64_v8a.apk", "lib/arm64-v8a/libfoo.so");
        File arm32 = zip("config.armeabi_v7a.apk", "lib/armeabi-v7a/libfoo.so");

        assertTrue(new AbiUtils(Collections.singletonList(base)).isEmptyAib());
        AbiUtils a64 = new AbiUtils(Arrays.asList(base, arm64));
        assertFalse(a64.isEmptyAib());
        assertTrue(a64.matches(true));
        assertFalse(a64.matches(false));
        AbiUtils a32 = new AbiUtils(Arrays.asList(base, arm32));
        assertTrue(a32.matches(false));
        assertFalse(a32.matches(true));
        AbiUtils both = new AbiUtils(Arrays.asList(base, arm64, arm32));
        assertTrue(both.matches(true) && both.matches(false));
    }

    @Test
    public void copiesLibsFromBaseAndConfigSplits() throws Exception {
        File base = zip("base.apk", "classes.dex", "lib/arm64-v8a/libbase.so", "lib/armeabi-v7a/libbase.so");
        File arm64 = zip("config.arm64_v8a.apk", "lib/arm64-v8a/libsplit.so");
        File arm32 = zip("config.armeabi_v7a.apk", "lib/armeabi-v7a/libsplit32.so");
        File out = new File(dir, "lib");
        NativeUtils.copyNativeLib(Arrays.asList(base, arm64, arm32), out, Arrays.asList("arm64-v8a", "armeabi"));
        assertTrue(new File(out, "libbase.so").isFile());
        assertTrue(new File(out, "libsplit.so").isFile());
        assertFalse(new File(out, "libsplit32.so").exists());
        assertEquals("lib/arm64-v8a/libsplit.so", new String(Files.readAllBytes(new File(out, "libsplit.so").toPath())));
    }

    @Test
    public void libsOnlyInSplitAndFallbackAbi() throws Exception {
        File base = zip("base.apk", "classes.dex");
        File split = zip("config.armeabi.apk", "lib/armeabi/libold.so");
        File out = new File(dir, "lib");
        NativeUtils.copyNativeLib(Arrays.asList(base, split), out, Arrays.asList("armeabi-v7a", "armeabi"));
        assertTrue(new File(out, "libold.so").isFile());
    }

    @Test
    public void noLibsCreatesEmptyDir() throws Exception {
        File base = zip("base.apk", "classes.dex");
        File out = new File(dir, "lib");
        NativeUtils.copyNativeLib(Collections.singletonList(base), out, Arrays.asList("arm64-v8a"));
        assertTrue(out.isDirectory());
        assertEquals(0, out.list().length);
    }
}
