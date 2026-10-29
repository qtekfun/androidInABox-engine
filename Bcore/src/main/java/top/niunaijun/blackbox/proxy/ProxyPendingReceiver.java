package top.niunaijun.blackbox.proxy;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import top.niunaijun.blackbox.core.system.am.BActivityManagerService;
import top.niunaijun.blackbox.entity.am.PendingResultData;
import top.niunaijun.blackbox.proxy.record.ProxyPendingRecord;
import top.niunaijun.blackbox.utils.Slog;


/**
 * Destino de los PendingIntent de broadcast y servicio del invitado. Vive en :black y
 * <b>no exportado</b>: solo el sistema lo dispara, al enviarse el PendingIntent. Nunca abre los
 * extras del intent original (el host no tiene las clases del invitado): lo entrega a
 * {@link BActivityManagerService#deliverPending}, que lo reenvia al proceso invitado.
 */
public class ProxyPendingReceiver extends BroadcastReceiver {
    public static final String TAG = "ProxyPendingReceiver";

    @Override
    public void onReceive(Context context, Intent stub) {
        ProxyPendingRecord record = ProxyPendingRecord.create(stub);
        if (!record.isDeliverable()) {
            Slog.w(TAG, "PendingIntent descartado: registro ilegible o sin datos del motor");
            return;
        }
        Slog.d(TAG, "ProxyPendingReceiver: " + record);
        final Intent fill = ProxyPendingRecord.fillOf(stub);
        final PendingResultData data = new PendingResultData(goAsync());
        // arrancar el proceso invitado puede bloquear: fuera del hilo principal
        new Thread(() -> BActivityManagerService.get().deliverPending(record, fill, data), "bb-pending").start();
    }
}
