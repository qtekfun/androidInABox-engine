package top.niunaijun.blackbox.core.system;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.core.content.ContextCompat;

import top.niunaijun.blackbox.core.system.am.BActivityManagerService;
import top.niunaijun.blackbox.core.system.keepalive.KeepAliveManager;
import top.niunaijun.blackbox.entity.am.PendingResultData;

/**
 * Arranque del motor tras reiniciar el dispositivo o actualizar el host (spec 04, R04.6).
 * Corre en {@code :black}: arranca el servicio central, restaura los procesos "mantener vivos" y
 * entrega BOOT_COMPLETED a las apps del box que lo hayan registrado.
 */
public class BootReceiver extends BroadcastReceiver {
    public static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (!BackgroundSettings.isRestartAction(action)) {
            return;
        }
        if (!BackgroundSettings.isStartOnBoot(context.getFilesDir())) {
            Log.i(TAG, action + " ignorado: 'iniciar con el sistema' desactivado");
            return;
        }
        Log.i(TAG, "recibido " + action + ", arrancando servicio central");
        try {
            ContextCompat.startForegroundService(context, new Intent(context, DaemonService.class));
        } catch (Throwable e) {
            // Restricciones de arranque en segundo plano (Android 12+): se registra el motivo (R04.4)
            Log.e(TAG, "no se pudo arrancar el servicio central desde " + action + ": " + e, e);
        }
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            return;
        }
        final PendingResult pending = goAsync();
        final Context app = context.getApplicationContext();
        new Thread("bb-boot") {
            @Override
            public void run() {
                try {
                    KeepAliveManager.get().restoreMarked();
                    int n = BActivityManagerService.get().deliverSystemBroadcast(
                            new Intent(Intent.ACTION_BOOT_COMPLETED), new PendingResultData(pending));
                    Log.i(TAG, "BOOT_COMPLETED entregado a " + n + " receptores del box");
                } catch (Throwable e) {
                    Log.e(TAG, "fallo entregando BOOT_COMPLETED al box", e);
                    pending.finish();
                }
            }
        }.start();
    }
}
