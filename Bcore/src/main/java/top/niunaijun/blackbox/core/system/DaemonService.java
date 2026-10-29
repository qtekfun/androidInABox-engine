package top.niunaijun.blackbox.core.system;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.PendingIntent;
import android.content.Context;
import android.content.pm.ServiceInfo;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.R;
import top.niunaijun.blackbox.core.system.keepalive.KeepAliveManager;
import top.niunaijun.blackbox.utils.compat.BuildCompat;


public class DaemonService extends Service {
    public static final String TAG = "DaemonService";
    private static final int NOTIFY_ID = BlackBoxCore.getHostPkg().hashCode();
    /** Canal del servicio central (spec 04, R04.1): importancia baja, sin sonido. */
    public static final String CHANNEL_ID = "bb_core_service";

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "DaemonService onCreate");
        
        
        if (BuildCompat.isOreo()) {
            createNotificationChannel();
        }
        // Cumple la promesa de startForegroundService cuanto antes
        startForegroundService();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "DaemonService onStartCommand");
        
        try {
            
            // La notificacion del servicio central no se cancela ni se oculta (R04.3)
            if (BuildCompat.isOreo() && !startForegroundService()) {
                Log.w(TAG, "Failed to start foreground service, falling back to regular service");
                return START_STICKY;
            }
            // Tras arrancar o reiniciarse :black se restauran los procesos de las apps marcadas (R04.8)
            KeepAliveManager.get().restoreMarked();
            
            Log.d(TAG, "DaemonService started successfully");
            return START_STICKY;
            
        } catch (Exception e) {
            Log.e(TAG, "Error starting DaemonService: " + e.getMessage(), e);
            
            return START_STICKY;
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "DaemonService onDestroy");
        super.onDestroy();
    }

    
    private void createNotificationChannel() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.bb_core_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                );
                channel.setDescription(getString(R.string.bb_core_channel_desc));
                channel.setShowBadge(false);
                channel.setSound(null, null);
                channel.enableVibration(false);
                
                NotificationManager notificationManager = getSystemService(NotificationManager.class);
                if (notificationManager != null) {
                    notificationManager.createNotificationChannel(channel);
                    Log.d(TAG, "Notification channel created successfully");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to create notification channel: " + e.getMessage(), e);
        }
    }

    
    private boolean startForegroundService() {
        try {
            Notification notification = createNotification();
            if (notification != null) {
                if (Build.VERSION.SDK_INT >= 34) {
                    startForeground(NOTIFY_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
                } else {
                    startForeground(NOTIFY_ID, notification);
                }
                Log.i(TAG, "Servicio central en primer plano (canal " + CHANNEL_ID + ", id " + NOTIFY_ID + ")");
                return true;
            } else {
                Log.e(TAG, "Failed to create notification");
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to start foreground service: " + e.getMessage(), e);
            return false;
        }
    }

    
    /** Toque en la notificacion: abre el host. */
    private PendingIntent contentIntent() {
        try {
            Intent launch = getPackageManager().getLaunchIntentForPackage(getPackageName());
            if (launch == null) {
                return null;
            }
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }
            return PendingIntent.getActivity(this, 0, launch, flags);
        } catch (Throwable e) {
            return null;
        }
    }

    private Notification createNotification() {
        try {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.bb_core_title))
                .setContentText(getString(R.string.bb_core_text))
                .setContentIntent(contentIntent())
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setAutoCancel(false);
            
            return builder.build();
        } catch (Exception e) {
            Log.e(TAG, "Failed to create notification: " + e.getMessage(), e);
            return null;
        }
    }

    
    public static class DaemonInnerService extends Service {
        @Override
        public void onCreate() {
            Log.i(TAG, "DaemonInnerService -> onCreate");
            super.onCreate();
        }

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            Log.i(TAG, "DaemonInnerService -> onStartCommand");
            
            // Inocuo: ya no cancela la notificacion del servicio central (R04.3)
            stopSelf();
            return START_NOT_STICKY;
        }

        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }

        @Override
        public void onDestroy() {
            Log.i(TAG, "DaemonInnerService -> onDestroy");
            super.onDestroy();
        }
    }
}
