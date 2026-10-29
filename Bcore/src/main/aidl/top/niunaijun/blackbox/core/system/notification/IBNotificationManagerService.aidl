// IBNotificationManagerService.aidl
package top.niunaijun.blackbox.core.system.notification;

// Declare any non-default types here with import statements
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.service.notification.StatusBarNotification;

interface IBNotificationManagerService {

    NotificationChannel getNotificationChannel(String channelId, int userId);

    List<NotificationChannel> getNotificationChannels(String packageName, int userId);

    List<NotificationChannelGroup> getNotificationChannelGroups(String packageName, int userId);

    void createNotificationChannel(in NotificationChannel notificationChannel, int userId, String appLabel);

    void deleteNotificationChannel(String channelId, int userId);

    void createNotificationChannelGroup(in NotificationChannelGroup notificationChannelGroup, int userId, String appLabel);

    void deleteNotificationChannelGroup(String groupId, int userId);

    void enqueueNotificationWithTag(int id, String tag, in Notification notification, int userId);

    void cancelNotificationWithTag(int id, String tag, int userId);

    void cancelAllNotifications(int userId);

    boolean isListenerAccessGranted(String component, int userId);

    boolean isListenerPackageGranted(String packageName, int userId);

    List<String> getEnabledListeners(int userId);

    void setListenerAccessGranted(String component, int userId, boolean granted);

    List<StatusBarNotification> getActiveNotificationsForListener(int userId);

    void cancelNotificationFromListener(String pkg, String tag, int id, int userId);

    void onRealNotificationPosted(in StatusBarNotification sbn);

    void onRealNotificationRemoved(in StatusBarNotification sbn);
}
