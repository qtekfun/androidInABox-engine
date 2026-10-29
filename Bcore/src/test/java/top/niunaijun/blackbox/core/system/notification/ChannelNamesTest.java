package top.niunaijun.blackbox.core.system.notification;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ChannelNamesTest {
    @Test
    public void roundTrip() {
        String d = ChannelNames.display("WhatsApp", "Perfil 2", "Mensajes");
        assertEquals("WhatsApp (Perfil 2) · Mensajes", d);
        assertEquals("Mensajes", ChannelNames.original("Perfil 2", d));
    }

    @Test
    public void nameContainingSeparatorSurvives() {
        String d = ChannelNames.display("App", "Perfil 1", "a · b (Perfil 1) · c");
        assertEquals("a · b (Perfil 1) · c", ChannelNames.original("Perfil 1", d));
    }

    @Test
    public void foreignNameIsKept() {
        assertEquals("Otro", ChannelNames.original("Perfil 1", "Otro"));
    }

    @Test
    public void emptyNameGivesOwnerOnly() {
        assertEquals("App (Perfil 1)", ChannelNames.display("App", "Perfil 1", ""));
    }
}
