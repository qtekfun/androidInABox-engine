package top.niunaijun.blackbox.core.system.pm;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class MicrogSignatureSpoofTest {
    @Test
    public void suplantaConMetadataYPerfilMicrog() {
        assertArrayEquals(new byte[]{(byte) 0xab, 0x01, (byte) 0xff},
                MicrogSignatureSpoof.decide("ab01FF", true));
    }

    @Test
    public void noSuplantaSinModoMicrog() {
        assertNull(MicrogSignatureSpoof.decide("ab01ff", false));
    }

    @Test
    public void noSuplantaSinMetadataOValorInvalido() {
        assertNull(MicrogSignatureSpoof.decide(null, true));
        assertNull(MicrogSignatureSpoof.decide(Integer.valueOf(3), true));
        assertNull(MicrogSignatureSpoof.decide("  ", true));
        assertNull(MicrogSignatureSpoof.decide("no es base64 ni hex!", true));
    }

    @Test
    public void aceptaBase64() {
        assertNotNull(MicrogSignatureSpoof.decide("q80=", true));
    }
}
