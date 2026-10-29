package top.niunaijun.blackbox.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BroadcastUserCompatTest {
    @Test
    public void pseudoUsuariosPasanAlHost() {
        assertEquals(0, BroadcastUserCompat.realUserId(-1, 0));
        assertEquals(10, BroadcastUserCompat.realUserId(-2, 10));
        assertEquals(10, BroadcastUserCompat.realUserId(-3, 10));
    }

    @Test
    public void usuarioConcretoSeConserva() {
        assertEquals(7, BroadcastUserCompat.realUserId(7, 0));
    }

    @Test
    public void reescribeSoloElUltimoInt() {
        Object[] args = {null, "x", 5, true, false, -1};
        BroadcastUserCompat.rewriteUserId(args, 0);
        assertEquals(5, args[2]);
        assertEquals(0, args[5]);
    }
}
