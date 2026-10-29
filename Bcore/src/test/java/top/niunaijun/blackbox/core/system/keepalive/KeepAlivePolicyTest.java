package top.niunaijun.blackbox.core.system.keepalive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Set;

import org.junit.Test;

public class KeepAlivePolicyTest {
    @Test
    public void esperaCreciente() {
        assertEquals(30_000L, KeepAlivePolicy.delayMs(1));
        assertEquals(60_000L, KeepAlivePolicy.delayMs(2));
        assertEquals(300_000L, KeepAlivePolicy.delayMs(3));
        assertEquals(300_000L, KeepAlivePolicy.delayMs(9));
    }

    @Test
    public void procesoEstableReiniciaContador() {
        assertEquals(3, KeepAlivePolicy.nextFailures(2, 1_000L));
        assertEquals(1, KeepAlivePolicy.nextFailures(4, KeepAlivePolicy.STABLE_AFTER_MS));
    }

    @Test
    public void abandonoTrasCincoFallos() {
        assertFalse(KeepAlivePolicy.shouldGiveUp(4));
        assertTrue(KeepAlivePolicy.shouldGiveUp(5));
    }

    @Test
    public void listaIdaYVuelta() {
        Set<String> s = KeepAlivePolicy.parse("# c\n0:com.google.android.gms\n\nmal\nx:pkg\n0:\n10:com.a.b\n");
        assertEquals(2, s.size());
        String text = KeepAlivePolicy.serialize(s);
        assertEquals(s, KeepAlivePolicy.parse(text));
        assertEquals("com.a.b", KeepAlivePolicy.packageOf("10:com.a.b"));
        assertEquals(10, KeepAlivePolicy.userOf("10:com.a.b"));
        assertEquals("0:p", KeepAlivePolicy.key("p", 0));
    }
}
