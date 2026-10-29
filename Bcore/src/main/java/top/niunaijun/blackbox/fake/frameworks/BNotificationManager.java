package top.niunaijun.blackbox.fake.frameworks;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.os.RemoteException;

import java.util.ArrayList;
import java.util.List;

import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.core.system.ServiceManager;
import top.niunaijun.blackbox.core.system.notification.IBNotificationManagerService;


public class BNotificationManager extends BlackManager<IBNotificationManagerService> {
    private static final BNotificationManager sNotificationManager = new BNotificationManager();

    public static BNotificationManager get() {
        return sNotificationManager;
    }

    @Override
    protected String getServiceName() {
        return ServiceManager.NOTIFICATION_MANAGER;
    }

    /** Etiqueta de la app invitada (el host no puede leerla del APK virtual). */
    private static String appLabel() {
        String label = BActivityThread.getAppPackageName();
        try {
            Application app = BActivityThread.getApplication();
            CharSequence l = app.getPackageManager().getApplicationLabel(app.getApplicationInfo());
            if (l != null && l.length() > 0) {
                label = l.toString();
            }
        } catch (Throwable ignored) {
        }
        return label;
    }

    public NotificationChannel getNotificationChannel(String channelId) {
        try {
            return getService().getNotificationChannel(channelId, BActivityThread.getUserId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<NotificationChannelGroup> getNotificationChannelGroups(String packageName) {
        try {
            return getService().getNotificationChannelGroups(packageName, BActivityThread.getUserId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void createNotificationChannel(NotificationChannel notificationChannel) {
        try {
            getService().createNotificationChannel(notificationChannel, BActivityThread.getUserId(), appLabel());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void deleteNotificationChannel(String channelId) {
        try {
            getService().deleteNotificationChannel(channelId, BActivityThread.getUserId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void createNotificationChannelGroup(NotificationChannelGroup notificationChannelGroup) {
        try {
            getService().createNotificationChannelGroup(notificationChannelGroup, BActivityThread.getUserId(), appLabel());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void deleteNotificationChannelGroup(String groupId) {
        try {
            getService().deleteNotificationChannelGroup(groupId, BActivityThread.getUserId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void enqueueNotificationWithTag(int id, String tag, Notification notification) {
        try {
            getService().enqueueNotificationWithTag(id, tag, notification, BActivityThread.getUserId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void cancelNotificationWithTag(int id, String tag) {
        try {
            getService().cancelNotificationWithTag(id, tag, BActivityThread.getUserId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void cancelAllNotifications() {
        try {
            getService().cancelAllNotifications(BActivityThread.getUserId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public List<NotificationChannel> getNotificationChannels(String packageName) {
        try {
            return getService().getNotificationChannels(packageName, BActivityThread.getUserId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

    // ---- escucha de notificaciones (spec 06) ----

    public boolean isListenerAccessGranted(String component) {
        try {
            return getService().isListenerAccessGranted(component, BActivityThread.getUserId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean isListenerPackageGranted(String packageName) {
        try {
            return getService().isListenerPackageGranted(packageName, BActivityThread.getUserId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return false;
    }

    public List<String> getEnabledListeners() {
        try {
            return getService().getEnabledListeners(BActivityThread.getUserId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

    public List<android.service.notification.StatusBarNotification> getActiveNotificationsForListener() {
        try {
            return getService().getActiveNotificationsForListener(BActivityThread.getUserId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

    public void cancelNotificationFromListener(String pkg, String tag, int id) {
        try {
            getService().cancelNotificationFromListener(pkg, tag, id, BActivityThread.getUserId());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    /** Solo desde el host (pantalla de ajustes): concede o retira el acceso de un oyente invitado. */
    public void setListenerAccessGranted(String component, int userId, boolean granted) {
        try {
            getService().setListenerAccessGranted(component, userId, granted);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    /** Solo desde el host: puente del sistema real. */
    public void onRealNotificationPosted(android.service.notification.StatusBarNotification sbn) {
        try {
            getService().onRealNotificationPosted(sbn);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void onRealNotificationRemoved(android.service.notification.StatusBarNotification sbn) {
        try {
            getService().onRealNotificationRemoved(sbn);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }
}
