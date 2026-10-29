package top.niunaijun.blackbox.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import top.niunaijun.blackbox.proxy.record.BroadcastDedup;

public class BroadcastDedupTest {
    @Test
    public void soloLaPrimeraVezEsNueva() {
        BroadcastDedup d = new BroadcastDedup(4);
        assertTrue(d.firstTime(1));
        assertFalse(d.firstTime(1));
        assertTrue(d.firstTime(2));
    }

    @Test
    public void olvidaLosMasAntiguosAlLlenarse() {
        BroadcastDedup d = new BroadcastDedup(2);
        d.firstTime(1);
        d.firstTime(2);
        d.firstTime(3);
        assertTrue(d.firstTime(1));
        assertFalse(d.firstTime(3));
    }
}
