package top.niunaijun.blackbox.core;

import static org.junit.Assert.*;

import org.junit.Test;

public class GmsCoreProviderTest {
    @Test
    public void googleEnBoxNoPasaAlSistema() {
        assertFalse(GmsCore.shouldPassthroughProvider("com.google.android.gms.appdoctor", true));
        assertFalse(GmsCore.shouldPassthroughProvider("com.android.vending.billing", true));
        assertFalse(GmsCore.shouldPassthroughProvider("com.google.android.gsf.gservices", true));
    }

    @Test
    public void googleSinBoxPasaAlSistema() {
        assertTrue(GmsCore.shouldPassthroughProvider("com.google.android.gms.chimera", false));
        assertTrue(GmsCore.shouldPassthroughProvider("com.google.android.gsf.gservices", false));
    }

    @Test
    public void autoridadesDelSistemaSiguenPasando() {
        assertTrue(GmsCore.shouldPassthroughProvider("settings", false));
        assertTrue(GmsCore.shouldPassthroughProvider("media", true));
        assertFalse(GmsCore.shouldPassthroughProvider("com.ejemplo.app.provider", false));
        assertFalse(GmsCore.shouldPassthroughProvider(null, false));
    }
}
