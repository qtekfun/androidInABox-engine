package top.niunaijun.blackbox.utils;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import top.niunaijun.blackbox.utils.compat.SigningCompat;

public class SigningCompatTest {
    private static final String[] ACTUAL = {"actual"};
    private static final String[] LINAJE = {"antiguo", "medio", "actual"};

    @Test
    public void detectaLinaje() {
        assertFalse(SigningCompat.hasLineage(null));
        assertFalse(SigningCompat.hasLineage(new String[0]));
        assertTrue(SigningCompat.hasLineage(LINAJE));
    }

    @Test
    public void firmasLegacyConRotacionSonLaOriginal() {
        assertArrayEquals(new String[]{"antiguo"}, SigningCompat.legacySignatures(ACTUAL, LINAJE));
    }

    @Test
    public void firmasLegacySinRotacionSonLasActuales() {
        assertSame(ACTUAL, SigningCompat.legacySignatures(ACTUAL, null));
        assertSame(ACTUAL, SigningCompat.legacySignatures(ACTUAL, new String[0]));
    }

    @Test
    public void historialIncluyeTodosLosCertificados() {
        assertSame(LINAJE, SigningCompat.history(ACTUAL, LINAJE));
        assertSame(ACTUAL, SigningCompat.history(ACTUAL, null));
    }
}
