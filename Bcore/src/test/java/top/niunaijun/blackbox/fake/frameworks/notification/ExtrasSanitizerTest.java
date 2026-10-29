package top.niunaijun.blackbox.fake.frameworks.notification;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ExtrasSanitizerTest {
    @Test
    public void frameworkClassesAreKept() {
        assertTrue(ExtrasSanitizer.isFrameworkClass(String.class));
        assertTrue(ExtrasSanitizer.isFrameworkClass(Integer.class));
        assertTrue(ExtrasSanitizer.isFrameworkClass(int[].class));
        assertTrue(ExtrasSanitizer.isFrameworkClass(java.util.ArrayList.class));
        assertTrue(ExtrasSanitizer.isFrameworkClass(String[].class));
    }

    @Test
    public void appClassesAreRejected() {
        assertFalse(ExtrasSanitizer.isFrameworkClass(ExtrasSanitizerTest.class));
        assertFalse(ExtrasSanitizer.isFrameworkClass(ExtrasSanitizerTest[].class));
    }
}
