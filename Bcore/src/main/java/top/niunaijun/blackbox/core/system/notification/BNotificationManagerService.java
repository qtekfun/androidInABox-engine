package top.niunaijun.blackbox.core.system.notification;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Binder;
import android.os.Build;
import android.os.RemoteException;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import black.android.app.BRNotificationChannel;
import black.android.app.BRNotificationChannelGroup;
import black.android.app.BRNotificationO;
import black.android.app.NotificationChannelContext;
import black.android.app.NotificationChannelGroupContext;
import black.android.app.NotificationOContext;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.core.system.BProcessManagerService;
import top.niunaijun.blackbox.core.system.ISystemService;
import top.niunaijun.blackbox.core.system.ProcessRecord;
import top.niunaijun.blackbox.core.system.pm.BPackageManagerService;
import top.niunaijun.blackbox.core.system.user.BUserHandle;
import top.niunaijun.blackbox.core.system.pm.PackageMonitor;
import top.niunaijun.blackbox.fake.frameworks.notification.NotificationPresenter;
import top.niunaijun.blackbox.utils.Slog;
import top.niunaijun.blackbox.utils.compat.BuildCompat;


public class BNotificationManagerService extends IBNotificationManagerService.Stub implements ISystemService, PackageMonitor {
    public static final String TAG = "BNotificationManagerService";
    private final static BNotificationManagerService sService = new BNotificationManagerService();
    public static final String CHANNEL_BLACK = "@black-";
    public static final String GROUP_BLACK = "@black-group-";

    private NotificationChannelManager mNotificationChannelManager;
    private final Map<String, NotificationRecord> mNotificationRecords = new HashMap<>();

    private final NotificationManager mRealNotificationManager =
            (NotificationManager) BlackBoxCore.getContext().getSystemService(Context.NOTIFICATION_SERVICE);

    public static BNotificationManagerService get() {
        return sService;
    }

    @Override
    public void systemReady() {
        mNotificationChannelManager = NotificationChannelManager.get();
        BPackageManagerService.get().addPackageMonitor(this);
    }

    // Las notificaciones solo se retiran al desinstalar o borrar datos, no al morir un proceso.
    @Override
    public void onPackageUninstalled(String packageName, boolean isRemove, int userId) {
        deletePackageNotification(packageName, userId);
    }

    @Override
    public void onPackageInstalled(String packageName, int userId) {
    }


    private NotificationRecord getNotificationRecord(String packageName, int userId) {
        String key = packageName + "-" + userId;
        synchronized (mNotificationRecords) {
            NotificationRecord notificationRecord = mNotificationRecords.get(key);
            if (notificationRecord == null) {
                notificationRecord = new NotificationRecord();
                mNotificationRecords.put(key, notificationRecord);
            }
            return notificationRecord;
        }
    }

    private void removeNotificationRecord(String packageName, int userId) {
        String key = packageName + "-" + userId;
        synchronized (mNotificationRecords) {
            mNotificationRecords.remove(key);
        }
    }

    @Override
    @TargetApi(Build.VERSION_CODES.O)
    public NotificationChannel getNotificationChannel(String channelId, int userId) throws RemoteException {
        int callingPid = getCallingPid();
        ProcessRecord processByPid = BProcessManagerService.get().findProcessByPid(callingPid);
        if (processByPid == null)
            return null;
        NotificationRecord notificationRecord = getNotificationRecord(processByPid.getPackageName(), userId);
        synchronized (notificationRecord.mNotificationChannels) {
            return notificationRecord.mNotificationChannels.get(channelId);
        }
    }

    @Override
    public List<NotificationChannel> getNotificationChannels(String packageName, int userId) throws RemoteException {
        NotificationRecord notificationRecord = getNotificationRecord(packageName, userId);
        synchronized (notificationRecord.mNotificationChannels) {
            return new ArrayList<>(notificationRecord.mNotificationChannels.values());
        }
    }

    @Override
    public List<NotificationChannelGroup> getNotificationChannelGroups(String packageName, int userId) throws RemoteException {
        NotificationRecord notificationRecord = getNotificationRecord(packageName, userId);
        synchronized (notificationRecord.mNotificationChannelGroups) {
            return new ArrayList<>(notificationRecord.mNotificationChannelGroups.values());
        }
    }

    @Override
    @TargetApi(Build.VERSION_CODES.O)
    public void createNotificationChannel(NotificationChannel notificationChannel, int userId) {
        int callingPid = getCallingPid();
        ProcessRecord processByPid = BProcessManagerService.get().findProcessByPid(callingPid);
        if (processByPid == null)
            return;
        handleNotificationChannel(notificationChannel, userId);
        mRealNotificationManager.createNotificationChannel(notificationChannel);

        resetNotificationChannel(notificationChannel);
        NotificationRecord notificationRecord = getNotificationRecord(processByPid.getPackageName(), userId);
        synchronized (notificationRecord.mNotificationChannels) {
            notificationRecord.mNotificationChannels.put(notificationChannel.getId(), notificationChannel);
        }
    }

    @Override
    @TargetApi(Build.VERSION_CODES.O)
    public void deleteNotificationChannel(String channelId, int userId) {
        int callingPid = getCallingPid();
        ProcessRecord processByPid = BProcessManagerService.get().findProcessByPid(callingPid);
        if (processByPid == null)
            return;
        NotificationRecord notificationRecord = getNotificationRecord(processByPid.getPackageName(), userId);
        synchronized (notificationRecord.mNotificationChannels) {
            NotificationChannel remove = notificationRecord.mNotificationChannels.remove(channelId);
            if (remove != null) {
                String blackChannelId = getBlackChannelId(remove.getId(), userId);
                mRealNotificationManager.deleteNotificationChannel(blackChannelId);
            }
        }
    }

    @Override
    @TargetApi(Build.VERSION_CODES.O)
    public void createNotificationChannelGroup(NotificationChannelGroup notificationChannelGroup, int userId) {
        int callingPid = getCallingPid();
        ProcessRecord processByPid = BProcessManagerService.get().findProcessByPid(callingPid);
        if (processByPid == null)
            return;
        handleNotificationGroup(notificationChannelGroup, userId);
        mRealNotificationManager.createNotificationChannelGroup(notificationChannelGroup);

        resetNotificationGroup(notificationChannelGroup);
        NotificationRecord notificationRecord = getNotificationRecord(processByPid.getPackageName(), userId);
        synchronized (notificationRecord.mNotificationChannelGroups) {
            notificationRecord.mNotificationChannelGroups.put(notificationChannelGroup.getId(), notificationChannelGroup);
        }
    }

    @Override
    @TargetApi(Build.VERSION_CODES.O)
    public void deleteNotificationChannelGroup(String groupId, int userId) {
        int callingPid = getCallingPid();
        ProcessRecord processByPid = BProcessManagerService.get().findProcessByPid(callingPid);
        if (processByPid == null)
            return;
        NotificationRecord notificationRecord = getNotificationRecord(processByPid.getPackageName(), userId);
        synchronized (notificationRecord.mNotificationChannelGroups) {
            NotificationChannelGroup remove = notificationRecord.mNotificationChannelGroups.remove(groupId);
            if (remove != null) {
                String blackGroupId = getBlackGroupId(remove.getId(), userId);
                mRealNotificationManager.deleteNotificationChannelGroup(blackGroupId);
            }
        }
    }

    @Override
    public void enqueueNotificationWithTag(int id, String tag, Notification notification, int userId) {
        ProcessRecord processByPid = BProcessManagerService.get().findProcessByPid(Binder.getCallingPid());
        if (processByPid == null)
            return;
        String packageName = processByPid.getPackageName();
        String realTag = NotificationIds.scoped(userId, packageName, tag);

        if (notification.extras != null) {
            notification.extras.remove(NotificationPresenter.EXTRA_APP_LABEL);
            notification.extras.remove(NotificationPresenter.EXTRA_PROFILE_LABEL);
        }
        if (BuildCompat.isOreo()) {
            NotificationOContext notificationOContext = BRNotificationO.get(notification);
            
            if (notificationOContext._check_mChannelId() != null) {
                String blackChannelId = getBlackChannelId(notificationOContext.mChannelId(), userId);
                notificationOContext._set_mChannelId(blackChannelId);
            }
            
            if (notificationOContext._check_mGroupKey() != null) {
                String blackGroupId = getBlackGroupId(notificationOContext.mGroupKey(), userId);
                notificationOContext._set_mGroupKey(blackGroupId);
            }
        }
        mRealNotificationManager.notify(realTag, id, notification);
    }

    @Override
    public void cancelNotificationWithTag(int id, String tag, int userId) throws RemoteException {
        ProcessRecord processByPid = BProcessManagerService.get().findProcessByPid(Binder.getCallingPid());
        if (processByPid == null)
            return;
        mRealNotificationManager.cancel(NotificationIds.scoped(userId, processByPid.getPackageName(), tag), id);
    }

    /** Cancela todo lo publicado por (paquete, perfil), sin tocar lo del host ni lo de otras apps. */
    private void cancelAllOf(String packageName, int userId) {
        try {
            for (StatusBarNotification sbn : mRealNotificationManager.getActiveNotifications()) {
                if (userId == BUserHandle.USER_ALL ? NotificationIds.isOfPackage(sbn.getTag(), packageName)
                        : NotificationIds.belongsTo(sbn.getTag(), userId, packageName)) {
                    mRealNotificationManager.cancel(sbn.getTag(), sbn.getId());
                }
            }
        } catch (Throwable t) {
            Slog.e(TAG, "No se pudieron listar las notificaciones activas de " + packageName, t);
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    private void handleNotificationChannel(NotificationChannel notificationChannel, int userId) {
        NotificationChannelContext channelContext = BRNotificationChannel.get(notificationChannel);
        String channelId = channelContext.mId();
        String blackChannelId = getBlackChannelId(channelId, userId);
        channelContext._set_mId(blackChannelId);

        notificationChannel.setGroup(getBlackGroupId(notificationChannel.getGroup(), userId));
    }

    private void resetNotificationChannel(NotificationChannel notificationChannel) {
        NotificationChannelContext channelContext = BRNotificationChannel.get(notificationChannel);
        String channelId = channelContext.mId();
        String realChannelId = getRealChannelId(channelId);
        channelContext._set_mId(realChannelId);
    }

    private void handleNotificationGroup(NotificationChannelGroup notificationChannelGroup, int userId) {
        NotificationChannelGroupContext groupContext = BRNotificationChannelGroup.get(notificationChannelGroup);
        String groupId = groupContext.mId();
        String blackGroupId = getBlackGroupId(groupId, userId);
        groupContext._set_mId(blackGroupId);

        List<NotificationChannel> notificationChannels = groupContext.mChannels();
        if (notificationChannels != null) {
            for (NotificationChannel notificationChannel : notificationChannels) {
                createNotificationChannel(notificationChannel, userId);
            }
        }
    }

    private void resetNotificationGroup(NotificationChannelGroup notificationChannelGroup) {
        NotificationChannelGroupContext groupContext = BRNotificationChannelGroup.get(notificationChannelGroup);
        String groupId = groupContext.mId();
        String realGroupId = getRealGroupId(groupId);
        groupContext._set_mId(realGroupId);

        List<NotificationChannel> notificationChannels = groupContext.mChannels();
        if (notificationChannels != null) {
            for (NotificationChannel notificationChannel : notificationChannels) {
                resetNotificationChannel(notificationChannel);
            }
        }
    }

    @SuppressLint("NewApi")
    public void deletePackageNotification(String packageName, int userId) {
        NotificationRecord notificationRecord = getNotificationRecord(packageName, userId);
        if (BuildCompat.isOreo()) {
            for (NotificationChannelGroup value : notificationRecord.mNotificationChannelGroups.values()) {
                String blackGroupId = getBlackGroupId(value.getId(), userId);
                mRealNotificationManager.deleteNotificationChannelGroup(blackGroupId);
            }
            for (NotificationChannel value : notificationRecord.mNotificationChannels.values()) {
                String blackChannelId = getBlackChannelId(value.getId(), userId);
                mRealNotificationManager.deleteNotificationChannel(blackChannelId);
            }
        }
        cancelAllOf(packageName, userId);
        removeNotificationRecord(packageName, userId);
    }

    private String getBlackChannelId(String channelId, int userId) {
        if (channelId == null || channelId.contains(CHANNEL_BLACK)) {
            return channelId;
        }
        return channelId + CHANNEL_BLACK + userId;
    }

    private String getRealChannelId(String channelId) {
        if (channelId == null || !channelId.contains(CHANNEL_BLACK)) {
            return channelId;
        }
        return channelId.split(CHANNEL_BLACK)[0];
    }

    private String getBlackGroupId(String groupId, int userId) {
        if (groupId == null || groupId.contains(GROUP_BLACK))
            return groupId;
        return groupId + GROUP_BLACK + userId;
    }

    private String getRealGroupId(String groupId) {
        if (groupId == null || !groupId.contains(GROUP_BLACK))
            return groupId;
        return groupId.split(GROUP_BLACK)[0];
    }
}
