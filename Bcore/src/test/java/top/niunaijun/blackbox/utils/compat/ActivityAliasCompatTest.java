package top.niunaijun.blackbox.utils.compat;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ActivityAliasCompatTest {
    @Test
    public void aliasUsaDestino() {
        assertEquals("a.b.Main", ActivityAliasCompat.effectiveClassName("a.b.Alias", "a.b.Main"));
    }

    @Test
    public void sinDestinoUsaNombre() {
        assertEquals("a.b.Alias", ActivityAliasCompat.effectiveClassName("a.b.Alias", null));
        assertEquals("a.b.Alias", ActivityAliasCompat.effectiveClassName("a.b.Alias", ""));
    }
}
