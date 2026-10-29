package top.niunaijun.blackbox.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import org.junit.Test;

public class RomGuideTest {
    @Test
    public void detectaPorFabricanteYMarca() {
        assertEquals(RomGuide.Rom.COLOROS, RomGuide.detect("OPPO", "OPPO"));
        assertEquals(RomGuide.Rom.COLOROS, RomGuide.detect("realme", "realme"));
        assertEquals(RomGuide.Rom.HYPEROS, RomGuide.detect("Xiaomi", "Redmi"));
        assertEquals(RomGuide.Rom.MAGICOS, RomGuide.detect("HONOR", "HONOR"));
        assertEquals(RomGuide.Rom.ORIGINOS, RomGuide.detect("vivo", "vivo"));
        assertEquals(RomGuide.Rom.OTHER, RomGuide.detect("Google", "google"));
        assertEquals(RomGuide.Rom.OTHER, RomGuide.detect(null, null));
    }

    @Test
    public void ningunIntentResuelveNoFalla() {
        for (RomGuide.Rom rom : RomGuide.Rom.values()) {
            assertNull(RomGuide.firstResolvable(RomGuide.candidates(rom), c -> false));
        }
        assertNull(RomGuide.firstResolvable(Collections.<String>emptyList(), c -> true));
    }

    @Test
    public void devuelveElPrimeroQueResuelve() {
        java.util.List<String> c = RomGuide.candidates(RomGuide.Rom.COLOROS);
        assertTrue(c.size() > 1);
        assertEquals(c.get(1), RomGuide.firstResolvable(c, x -> x.equals(c.get(1)) || x.equals(c.get(2))));
    }

    @Test
    public void unResolverQueLanzaSeIgnora() {
        java.util.List<String> c = RomGuide.candidates(RomGuide.Rom.HYPEROS);
        assertNull(RomGuide.firstResolvable(c, x -> { throw new SecurityException(); }));
    }
}
