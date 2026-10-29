package black.android.app;

import android.graphics.drawable.Icon;

import top.niunaijun.blackreflection.annotation.BClassName;
import top.niunaijun.blackreflection.annotation.BField;

@BClassName("android.app.Notification$Action")
public interface NotificationAction {
    @BField
    Icon mIcon();
}
