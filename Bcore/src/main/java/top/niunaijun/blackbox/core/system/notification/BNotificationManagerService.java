package top.niunaijun.blackbox.core.system.notification;

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
import java.util.List;

import black.android.app.BRNotificationChannel;
import black.android.app.BRNotificationChannelGroup;
import black.android.app.BRNotificationO;
import black.android.app.NotificationOContext;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.core.system.BProcessManagerService;
import top.niunaijun.blackbox.core.system.ISystemService;
import top.niunaijun.blackbox.core.system.ProcessRecord;
import top.niunaijun.blackbox.core.system.pm.BPackageManagerService;
import top.niunaijun.blackbox.core.system.pm.PackageMonitor;
import top.niunaijun.blackbox.core.system.user.BUserHandle;
import top.niunaijun.blackbox.fake.frameworks.notification.NotificationPresenter;
import top.niunaijun.blackbox.utils.Slog;
import top.niunaijun.blackbox.utils.compat.BuildCompat;


/**
 * Publica en el NotificationManager real lo que crean las apps invitadas. Todo id real lleva el
 * espacio {@code bb:<userId>:<paquete>:} (ver {@link NotificationIds}); el propio NotificationManager
 * es la fuente de la verdad, por lo que no se guarda estado y reiniciar :black no pierde nada.
 */
public class BNotificationManagerService extends IBNotificationManagerService.Stub implements ISystemService, PackageMonitor {
    public static final String TAG = "BNotificationManagerService";
    /** Sufijos del esquema antiguo, borrados en el primer arranque. */
    private static final String LEGACY_CHANNEL = "@black-";
    private static final String LEGACY_GROUP = "@black-group-";

    private final static BNotificationManagerService sService = new BNotificationManagerService();

    private NotificationChannelManager mNotificationChannelManager;

    private final NotificationManager mRealNotificationManager =
            (NotificationManager) BlackBoxCore.getContext().getSystemService(Context.NOTIFICATION_SERVICE);

    public static BNotificationManagerService get() {
        return sService;
    }

    @Override
    public void systemReady() {
        mNotificationChannelManager = NotificationChannelManager.get();
        BPackageManagerService.get().addPackageMonitor(this);
        deleteLegacyChannels();
        BNotificationListenerService.get().systemReady();
    }

    // Las notificaciones solo se retiran al desinstalar o borrar datos, no al morir un proceso.
    @Override
    public void onPackageUninstalled(String packageName, boolean isRemove, int userId) {
        deletePackageNotification(packageName, userId);
    }

    @Override
    public void onPackageInstalled(String packageName, int userId) {
    }

    private ProcessRecord callerOrNull() {
        return BProcessManagerService.get().findProcessByPid(Binder.getCallingPid());
    }

    // ---- lectura de canales y grupos (desde el NotificationManager real) ----

    @Override
    @TargetApi(Build.VERSION_CODES.O)
    public NotificationChannel getNotificationChannel(String channelId, int userId) throws RemoteException {
        ProcessRecord processByPid = callerOrNull();
        if (processByPid == null)
            return null;
        NotificationChannel real = mRealNotificationManager.getNotificationChannel(
                NotificationIds.scoped(userId, processByPid.getPackageName(), channelId));
        return real == null ? null : toGuestChannel(real, userId, processByPid.getPackageName());
    }

    @Override
    @TargetApi(Build.VERSION_CODES.O)
    public List<NotificationChannel> getNotificationChannels(String packageName, int userId) throws RemoteException {
        List<NotificationChannel> out = new ArrayList<>();
        if (!BuildCompat.isOreo()) {
            return out;
        }
        for (NotificationChannel real : mRealNotificationManager.getNotificationChannels()) {
            if (NotificationIds.belongsTo(real.getId(), userId, packageName)) {
                out.add(toGuestChannel(real, userId, packageName));
            }
        }
        return out;
    }

    @Override
    @TargetApi(Build.VERSION_CODES.O)
    public List<NotificationChannelGroup> getNotificationChannelGroups(String packageName, int userId) throws RemoteException {
        List<NotificationChannelGroup> out = new ArrayList<>();
        if (!BuildCompat.isOreo()) {
            return out;
        }
        String profile = NotificationPresenter.profileLabel(userId);
        for (NotificationChannelGroup real : mRealNotificationManager.getNotificationChannelGroups()) {
            // El grupo por defecto (sin ':' final) es del motor, no del invitado.
            if (!NotificationIds.belongsTo(real.getId(), userId, packageName)) {
                continue;
            }
            NotificationChannelGroup g = new NotificationChannelGroup(
                    NotificationIds.guestId(real.getId()),
                    ChannelNames.original(profile, String.valueOf(real.getName())));
            List<NotificationChannel> channels = new ArrayList<>();
            if (real.getChannels() != null) {
                for (NotificationChannel c : real.getChannels()) {
                    channels.add(toGuestChannel(c, userId, packageName));
                }
            }
            BRNotificationChannelGroup.get(g)._set_mChannels(channels);
            out.add(g);
        }
        return out;
    }

    /** Copia del canal real con el id, el grupo y el nombre que espera el invitado. */
    @TargetApi(Build.VERSION_CODES.O)
    private NotificationChannel toGuestChannel(NotificationChannel real, int userId, String packageName) {
        BRNotificationChannel.get(real)._set_mId(NotificationIds.guestId(real.getId()));
        String group = real.getGroup();
        if (group == null || !NotificationIds.belongsTo(group, userId, packageName)) {
            real.setGroup(null);
        } else {
            real.setGroup(NotificationIds.guestId(group));
        }
        real.setName(ChannelNames.original(NotificationPresenter.profileLabel(userId), String.valueOf(real.getName())));
        return real;
    }

    // ---- escritura de canales y grupos ----

    @Override
    @TargetApi(Build.VERSION_CODES.O)
    public void createNotificationChannel(NotificationChannel notificationChannel, int userId, String appLabel) {
        ProcessRecord processByPid = callerOrNull();
        if (processByPid == null)
            return;
        String packageName = processByPid.getPackageName();
        String profile = NotificationPresenter.profileLabel(userId);
        String guestGroup = notificationChannel.getGroup();
        if (guestGroup == null) {
            String owner = NotificationIds.packageGroup(userId, packageName);
            mRealNotificationManager.createNotificationChannelGroup(
                    new NotificationChannelGroup(owner, ChannelNames.owner(appLabel, profile)));
            notificationChannel.setGroup(owner);
        } else {
            String realGroup = NotificationIds.scoped(userId, packageName, guestGroup);
            if (!groupExists(realGroup)) {
                mRealNotificationManager.createNotificationChannelGroup(
                        new NotificationChannelGroup(realGroup, ChannelNames.display(appLabel, profile, guestGroup)));
            }
            notificationChannel.setGroup(realGroup);
        }
        BRNotificationChannel.get(notificationChannel)._set_mId(
                NotificationIds.scoped(userId, packageName, notificationChannel.getId()));
        notificationChannel.setName(ChannelNames.display(appLabel, profile, notificationChannel.getName()));
        mRealNotificationManager.createNotificationChannel(notificationChannel);
    }

    @TargetApi(Build.VERSION_CODES.O)
    private boolean groupExists(String realGroupId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return mRealNotificationManager.getNotificationChannelGroup(realGroupId) != null;
        }
        for (NotificationChannelGroup g : mRealNotificationManager.getNotificationChannelGroups()) {
            if (g.getId().equals(realGroupId)) {
                return true;
            }
        }
        return false;
    }

    @Override
    @TargetApi(Build.VERSION_CODES.O)
    public void deleteNotificationChannel(String channelId, int userId) {
        ProcessRecord processByPid = callerOrNull();
        if (processByPid == null)
            return;
        mRealNotificationManager.deleteNotificationChannel(
                NotificationIds.scoped(userId, processByPid.getPackageName(), channelId));
    }

    @Override
    @TargetApi(Build.VERSION_CODES.O)
    public void createNotificationChannelGroup(NotificationChannelGroup notificationChannelGroup, int userId, String appLabel) {
        ProcessRecord processByPid = callerOrNull();
        if (processByPid == null)
            return;
        String packageName = processByPid.getPackageName();
        String profile = NotificationPresenter.profileLabel(userId);
        List<NotificationChannel> channels = BRNotificationChannelGroup.get(notificationChannelGroup).mChannels();
        NotificationChannelGroup real = new NotificationChannelGroup(
                NotificationIds.scoped(userId, packageName, notificationChannelGroup.getId()),
                ChannelNames.display(appLabel, profile, notificationChannelGroup.getName()));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            real.setDescription(notificationChannelGroup.getDescription());
        }
        mRealNotificationManager.createNotificationChannelGroup(real);
        if (channels != null) {
            for (NotificationChannel channel : channels) {
                channel.setGroup(notificationChannelGroup.getId());
                createNotificationChannel(channel, userId, appLabel);
            }
        }
    }

    @Override
    @TargetApi(Build.VERSION_CODES.O)
    public void deleteNotificationChannelGroup(String groupId, int userId) {
        ProcessRecord processByPid = callerOrNull();
        if (processByPid == null)
            return;
        mRealNotificationManager.deleteNotificationChannelGroup(
                NotificationIds.scoped(userId, processByPid.getPackageName(), groupId));
    }

    // ---- escucha de notificaciones (spec 06) ----

    @Override
    public boolean isListenerAccessGranted(String component, int userId) {
        return BNotificationListenerService.get().isGranted(userId, component);
    }

    @Override
    public boolean isListenerPackageGranted(String packageName, int userId) {
        return BNotificationListenerService.get().isPackageGranted(userId, packageName);
    }

    @Override
    public List<String> getEnabledListeners(int userId) {
        return BNotificationListenerService.get().getEnabled(userId);
    }

    @Override
    public void setListenerAccessGranted(String component, int userId, boolean granted) {
        // Solo el host decide: un invitado no puede concederse acceso a si mismo
        if (callerOrNull() != null) {
            return;
        }
        BNotificationListenerService.get().setGranted(userId, component, granted);
    }

    @Override
    public List<StatusBarNotification> getActiveNotificationsForListener(int userId) {
        return BNotificationListenerService.get().getActive(userId);
    }

    /** Un oyente cancela una notificacion del box; las reales reenviadas no se pueden cancelar desde aqui. */
    @Override
    public void cancelNotificationFromListener(String pkg, String tag, int id, int userId) {
        if (!BNotificationListenerService.get().isGuestNotification(userId, pkg, tag, id)) {
            return;
        }
        mRealNotificationManager.cancel(NotificationIds.scoped(userId, pkg, tag), id);
        BNotificationListenerService.get().onGuestRemoved(userId, pkg, id, tag);
    }

    // ---- puente del sistema real (spec 06, 3.4) ----

    @Override
    public void onRealNotificationPosted(StatusBarNotification sbn) {
        if (callerOrNull() != null) {
            return;
        }
        BNotificationListenerService.get().onRealPosted(sbn);
    }

    @Override
    public void onRealNotificationRemoved(StatusBarNotification sbn) {
        if (callerOrNull() != null) {
            return;
        }
        BNotificationListenerService.get().onRealRemoved(sbn);
    }

    // ---- notificaciones ----

    @Override
    public void enqueueNotificationWithTag(int id, String tag, Notification notification, int userId) {
        ProcessRecord processByPid = callerOrNull();
        if (processByPid == null)
            return;
        String packageName = processByPid.getPackageName();
        String realTag = NotificationIds.scoped(userId, packageName, tag);

        if (notification.extras != null) {
            notification.extras.remove(NotificationPresenter.EXTRA_APP_LABEL);
            notification.extras.remove(NotificationPresenter.EXTRA_PROFILE_LABEL);
        }
        // Copia para los oyentes del box, antes de que canal y grupo pasen a ids reales
        Notification forListeners = notification.clone();
        if (BuildCompat.isOreo()) {
            NotificationOContext notificationOContext = BRNotificationO.get(notification);

            if (notificationOContext._check_mChannelId() != null) {
                notificationOContext._set_mChannelId(
                        NotificationIds.scoped(userId, packageName, notificationOContext.mChannelId()));
            }

            // El agrupado de notificaciones (groupKey) es distinto del grupo de canales.
            if (notificationOContext._check_mGroupKey() != null) {
                notificationOContext._set_mGroupKey(
                        NotificationIds.scoped(userId, packageName, notificationOContext.mGroupKey()));
            }
        }
        Slog.i(TAG, "BLACKNOTIF smallIcon=" + notification.getSmallIcon() + " icon=" + notification.icon);
        mRealNotificationManager.notify(realTag, id, notification);
        BNotificationListenerService.get().onGuestPosted(userId, packageName, id, tag, forListeners);
    }

    @Override
    public void cancelNotificationWithTag(int id, String tag, int userId) throws RemoteException {
        ProcessRecord processByPid = callerOrNull();
        if (processByPid == null)
            return;
        mRealNotificationManager.cancel(NotificationIds.scoped(userId, processByPid.getPackageName(), tag), id);
        BNotificationListenerService.get().onGuestRemoved(userId, processByPid.getPackageName(), id, tag);
    }

    @Override
    public void cancelAllNotifications(int userId) throws RemoteException {
        ProcessRecord processByPid = callerOrNull();
        if (processByPid == null)
            return;
        cancelAllOf(processByPid.getPackageName(), userId);
    }

    /** Cancela todo lo publicado por (paquete, perfil), sin tocar lo del host ni lo de otras apps. */
    private void cancelAllOf(String packageName, int userId) {
        BNotificationListenerService.get().onGuestRemovedAll(userId, packageName);
        try {
            for (StatusBarNotification sbn : mRealNotificationManager.getActiveNotifications()) {
                if (NotificationIds.matches(sbn.getTag(), userId, packageName)) {
                    mRealNotificationManager.cancel(sbn.getTag(), sbn.getId());
                }
            }
        } catch (Throwable t) {
            Slog.e(TAG, "No se pudieron listar las notificaciones activas de " + packageName, t);
        }
    }

    /** Desinstalacion o borrado de datos: retira notificaciones, canales y grupos de (paquete, perfil). */
    public void deletePackageNotification(String packageName, int userId) {
        cancelAllOf(packageName, userId);
        if (!BuildCompat.isOreo()) {
            return;
        }
        try {
            for (NotificationChannel c : mRealNotificationManager.getNotificationChannels()) {
                if (NotificationIds.matches(c.getId(), userId, packageName)) {
                    mRealNotificationManager.deleteNotificationChannel(c.getId());
                }
            }
            for (NotificationChannelGroup g : mRealNotificationManager.getNotificationChannelGroups()) {
                if (NotificationIds.matches(g.getId(), userId, packageName)) {
                    mRealNotificationManager.deleteNotificationChannelGroup(g.getId());
                }
            }
        } catch (Throwable t) {
            Slog.e(TAG, "No se pudieron borrar los canales de " + packageName, t);
        }
    }

    /** Borra los canales y grupos del esquema antiguo ({@code <id>@black-<userId>}); no se migran ajustes. */
    private void deleteLegacyChannels() {
        if (!BuildCompat.isOreo()) {
            return;
        }
        try {
            for (NotificationChannel c : mRealNotificationManager.getNotificationChannels()) {
                if (c.getId().contains(LEGACY_CHANNEL)) {
                    mRealNotificationManager.deleteNotificationChannel(c.getId());
                }
            }
            for (NotificationChannelGroup g : mRealNotificationManager.getNotificationChannelGroups()) {
                if (g.getId().contains(LEGACY_GROUP)) {
                    mRealNotificationManager.deleteNotificationChannelGroup(g.getId());
                }
            }
        } catch (Throwable t) {
            Slog.e(TAG, "No se pudieron borrar los canales antiguos", t);
        }
    }
}
