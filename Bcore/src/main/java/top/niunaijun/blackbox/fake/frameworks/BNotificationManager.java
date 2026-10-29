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
}
