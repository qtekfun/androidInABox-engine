package top.niunaijun.blackbox.fake.frameworks.notification;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class NotificationPresenterTest {
    @Test
    public void subTextWithoutOwn() {
        assertEquals("WhatsApp · Perfil 2", NotificationPresenter.subText("WhatsApp", "Perfil 2", null));
        assertEquals("WhatsApp · Perfil 2", NotificationPresenter.subText("WhatsApp", "Perfil 2", ""));
    }

    @Test
    public void subTextKeepsOwnAfterwards() {
        assertEquals("App · Perfil 1 · 3 chats", NotificationPresenter.subText("App", "Perfil 1", "3 chats"));
    }

    @Test
    public void titlePrefixWrapsAppAndProfile() {
        assertEquals("[Chat \u00b7 Perfil 2] Ana", NotificationPresenter.titlePrefix("Chat", "Perfil 2", "Ana"));
    }

    @Test
    public void profileLabelIsOneBased() {
        assertEquals("Perfil 1", NotificationPresenter.profileLabel(0));
        assertEquals("Perfil 3", NotificationPresenter.profileLabel(2));
    }
}
