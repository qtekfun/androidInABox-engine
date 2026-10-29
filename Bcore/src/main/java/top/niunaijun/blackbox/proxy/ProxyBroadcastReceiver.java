package top.niunaijun.blackbox.proxy;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.RemoteException;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.entity.am.PendingResultData;
import top.niunaijun.blackbox.proxy.record.ProxyBroadcastRecord;


public class ProxyBroadcastReceiver extends BroadcastReceiver {
    public static final String TAG = "ProxyBroadcastReceiver";

    private static final top.niunaijun.blackbox.proxy.record.BroadcastDedup sDedup =
            new top.niunaijun.blackbox.proxy.record.BroadcastDedup(128);

    @Override
    public void onReceive(Context context, Intent intent) {
        intent.setExtrasClassLoader(context.getClassLoader());
        ProxyBroadcastRecord record = ProxyBroadcastRecord.create(intent);
        if (record.mIntent == null) {
            return;
        }
        if (record.mId != 0 && !sDedup.firstTime(record.mId)) {
            // Copia del mismo broadcast que ya reparti otro receptor del host
            return;
        }
        PendingResult pendingResult = goAsync();
        try {
            BlackBoxCore.getBActivityManager().scheduleBroadcastReceiver(record.mIntent, new PendingResultData(pendingResult), record.mUserId);
        } catch (RemoteException e) {
            pendingResult.finish();
        }
    }
}