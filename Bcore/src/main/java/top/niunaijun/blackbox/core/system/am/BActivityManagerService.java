package top.niunaijun.blackbox.core.system.am;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.core.system.BProcessManagerService;
import top.niunaijun.blackbox.core.system.ISystemService;
import top.niunaijun.blackbox.core.system.ProcessRecord;
import top.niunaijun.blackbox.core.system.pm.BPackageManagerService;
import top.niunaijun.blackbox.entity.AppConfig;
import top.niunaijun.blackbox.entity.UnbindRecord;
import top.niunaijun.blackbox.entity.am.PendingResultData;
import top.niunaijun.blackbox.entity.am.ReceiverData;
import top.niunaijun.blackbox.entity.am.RunningAppProcessInfo;
import top.niunaijun.blackbox.entity.am.RunningServiceInfo;
import top.niunaijun.blackbox.proxy.record.ProxyPendingRecord;
import top.niunaijun.blackbox.utils.Slog;

import static android.content.pm.PackageManager.GET_META_DATA;


public class BActivityManagerService extends IBActivityManagerService.Stub implements ISystemService {
    public static final String TAG = "BActivityManagerService";
    private static final BActivityManagerService sService = new BActivityManagerService();
    private final Map<Integer, UserSpace> mUserSpace = new HashMap<>();
    private final BPackageManagerService mPms = BPackageManagerService.get();
    private final BroadcastManager mBroadcastManager;

    public static BActivityManagerService get() {
        return sService;
    }

    public BActivityManagerService() {
        mBroadcastManager = BroadcastManager.startSystem(this, mPms);
    }

    @Override
    public ComponentName startService(Intent intent, String resolvedType, boolean requireForeground, int userId) {
        UserSpace userSpace = getOrCreateSpaceLocked(userId);
        synchronized (userSpace.mActiveServices) {
            userSpace.mActiveServices.startService(intent, resolvedType, requireForeground, userId);
        }
        return null;
    }

    @Override
    public IBinder acquireContentProviderClient(ProviderInfo providerInfo) throws RemoteException {
        int callingPid = Binder.getCallingPid();
        ProcessRecord processRecord = BProcessManagerService.get().startProcessLocked(providerInfo.packageName,
                providerInfo.processName,
                BProcessManagerService.get().getUserIdByCallingPid(callingPid),
                -1,
                Binder.getCallingPid());
        if (processRecord == null) {
            throw new RuntimeException("Unable to create process " + providerInfo.name);
        }
        try {
            return processRecord.bActivityThread.acquireContentProviderClient(providerInfo);
        } catch (Throwable t) {
            t.printStackTrace();
            return null;
        }
    }

    @Override
    public Intent sendBroadcast(Intent intent, String resolvedType, int userId) throws RemoteException {
        List<ResolveInfo> resolves = BPackageManagerService.get().queryBroadcastReceivers(intent, GET_META_DATA, resolvedType, userId);

        for (ResolveInfo resolve : resolves) {
            ProcessRecord processRecord = BProcessManagerService.get().findProcessRecord(resolve.activityInfo.packageName, resolve.activityInfo.processName, userId);
            if (processRecord == null || processRecord.bActivityThread == null) {
                // proceso aun sin hilo registrado (arrancando): el broadcast lo entrega el sistema
                continue;
            }
            try {
                processRecord.bActivityThread.bindApplication();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        Intent shadow = new Intent();
        shadow.setPackage(BlackBoxCore.getHostPkg());
        shadow.setComponent(null);
        shadow.setAction(intent.getAction());
        return shadow;
    }

    @Override
    public IBinder peekService(Intent intent, String resolvedType, int userId) throws RemoteException {
        UserSpace userSpace = getOrCreateSpaceLocked(userId);
        synchronized (userSpace.mActiveServices) {
            return userSpace.mActiveServices.peekService(intent, resolvedType, userId);
        }
    }

    @Override
    public void onActivityCreated(int taskId, IBinder token, IBinder activityRecord) throws RemoteException {
        int callingPid = Binder.getCallingPid();
        ProcessRecord process = BProcessManagerService.get().findProcessByPid(callingPid);
        if (process == null) {
            return;
        }
        ActivityRecord record = (ActivityRecord) activityRecord;
        UserSpace userSpace = getOrCreateSpaceLocked(process.userId);
        synchronized (userSpace.mStack) {
            userSpace.mStack.onActivityCreated(process, taskId, token, record);
        }
    }

    @Override
    public void onActivityResumed(IBinder token) throws RemoteException {
        int callingPid = Binder.getCallingPid();
        ProcessRecord process = BProcessManagerService.get().findProcessByPid(callingPid);
        if (process == null) {
            return;
        }
        UserSpace userSpace = getOrCreateSpaceLocked(process.userId);
        synchronized (userSpace.mStack) {
            userSpace.mStack.onActivityResumed(process.userId, token);
        }
    }

    @Override
    public void onActivityDestroyed(IBinder token) throws RemoteException {
        int callingPid = Binder.getCallingPid();
        ProcessRecord process = BProcessManagerService.get().findProcessByPid(callingPid);
        if (process == null) {
            return;
        }
        UserSpace userSpace = getOrCreateSpaceLocked(process.userId);
        synchronized (userSpace.mStack) {
            userSpace.mStack.onActivityDestroyed(process.userId, token);
        }
    }

    @Override
    public void onFinishActivity(IBinder token) throws RemoteException {
        int callingPid = Binder.getCallingPid();
        ProcessRecord process = BProcessManagerService.get().findProcessByPid(callingPid);
        if (process == null) {
            return;
        }
        UserSpace userSpace = getOrCreateSpaceLocked(process.userId);
        synchronized (userSpace.mStack) {
            userSpace.mStack.onFinishActivity(process.userId, token);
        }
    }

    @Override
    public RunningAppProcessInfo getRunningAppProcesses(String callerPackage, int userId) throws RemoteException {
        ActivityManager manager = (ActivityManager)
                BlackBoxCore.getContext().getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> runningAppProcesses = manager.getRunningAppProcesses();
        Map<Integer, ActivityManager.RunningAppProcessInfo> runningProcessMap = new HashMap<>();
        for (ActivityManager.RunningAppProcessInfo runningProcess : runningAppProcesses) {
            runningProcessMap.put(runningProcess.pid, runningProcess);
        }
        List<ProcessRecord> packageProcessAsUser = BProcessManagerService.get().getPackageProcessAsUser(callerPackage, userId);

        RunningAppProcessInfo appProcessInfo = new RunningAppProcessInfo();
        for (ProcessRecord processRecord : packageProcessAsUser) {
            ActivityManager.RunningAppProcessInfo runningAppProcessInfo = runningProcessMap.get(processRecord.pid);
            if (runningAppProcessInfo != null) {
                runningAppProcessInfo.processName = processRecord.processName;
                appProcessInfo.mAppProcessInfoList.add(runningAppProcessInfo);
            }
        }
        return appProcessInfo;
    }

    @Override
    public RunningServiceInfo getRunningServices(String callerPackage, int userId) throws RemoteException {
        UserSpace userSpace = getOrCreateSpaceLocked(userId);
        return userSpace.mActiveServices.getRunningServiceInfo(callerPackage, userId);
    }

    @Override
    public void scheduleBroadcastReceiver(Intent intent, PendingResultData pendingResultData, int userId) throws RemoteException {
        List<ResolveInfo> resolves = BPackageManagerService.get().queryBroadcastReceivers(intent, GET_META_DATA, null, userId);

        if (resolves.isEmpty()) {
            pendingResultData.build().finish();
            Slog.d(TAG, "scheduleBroadcastReceiver empty");
            return;
        }
        mBroadcastManager.sendBroadcast(pendingResultData);
        for (ResolveInfo resolve : resolves) {
            ProcessRecord processRecord = BProcessManagerService.get().findProcessRecord(resolve.activityInfo.packageName, resolve.activityInfo.processName, userId);
            if (processRecord != null) {
                ReceiverData data = new ReceiverData();
                data.intent = intent;
                data.activityInfo = resolve.activityInfo;
                data.data = pendingResultData;
                processRecord.bActivityThread.scheduleReceiver(data);
            }
        }
    }

    /**
     * Entrega un broadcast del sistema (BOOT_COMPLETED) a los receptores del box, arrancando sus
     * procesos si hace falta. Los receptores dinamicos de BroadcastManager descartan los broadcast
     * reales del sistema, asi que este es el unico camino para ellos. Devuelve cuantos receptores recibieron.
     */
    public int deliverSystemBroadcast(Intent intent, PendingResultData pendingResultData) {
        int delivered = 0;
        boolean registered = false;
        for (top.niunaijun.blackbox.core.system.user.BUserInfo user
                : top.niunaijun.blackbox.core.system.user.BUserManagerService.get().getUsers()) {
            List<ResolveInfo> resolves;
            try {
                resolves = BPackageManagerService.get().queryBroadcastReceivers(intent, GET_META_DATA, null, user.id);
            } catch (Throwable t) {
                Slog.w(TAG, "queryBroadcastReceivers fallo en el perfil " + user.id, t);
                continue;
            }
            for (ResolveInfo resolve : resolves) {
                try {
                    ProcessRecord processRecord = BProcessManagerService.get().startProcessLocked(
                            resolve.activityInfo.packageName, resolve.activityInfo.processName, user.id, -1, Process.myPid());
                    if (processRecord == null) {
                        continue;
                    }
                    if (!registered) {
                        mBroadcastManager.sendBroadcast(pendingResultData);
                        registered = true;
                    }
                    ReceiverData data = new ReceiverData();
                    data.intent = new Intent(intent);
                    data.activityInfo = resolve.activityInfo;
                    data.data = pendingResultData;
                    processRecord.bActivityThread.scheduleReceiver(data);
                    delivered++;
                    Slog.i(TAG, "broadcast " + intent.getAction() + " entregado a " + resolve.activityInfo.packageName
                            + "/" + resolve.activityInfo.name + " (perfil " + user.id + ")");
                } catch (Throwable t) {
                    Slog.w(TAG, "broadcast " + intent.getAction() + " fallo para " + resolve.activityInfo, t);
                }
            }
        }
        if (!registered) {
            pendingResultData.build().finish();
        }
        return delivered;
    }

    /**
     * Entrega un PendingIntent de broadcast o servicio del invitado, ya validado por
     * {@code ProxyPendingReceiver}. Solo se resuelve contra los paquetes del box: si no se puede,
     * se descarta y se registra el motivo (R02.20); el intent original nunca se manda al sistema.
     * {@code record.mRouting} no lleva extras, de modo que aqui no se abren clases del invitado.
     */
    public void deliverPending(ProxyPendingRecord record, Intent fill, PendingResultData pendingResultData) {
        int userId = record.mUserId;
        if (BPackageManagerService.get().getApplicationInfo(record.mPackage, 0, userId) == null) {
            discardPending(pendingResultData, "el paquete " + record.mPackage + " no esta instalado en el perfil " + userId);
            return;
        }
        try {
            if (record.mKind == ProxyPendingRecord.KIND_BROADCAST) {
                List<ResolveInfo> resolves = BPackageManagerService.get().queryBroadcastReceivers(record.mRouting, GET_META_DATA, null, userId);
                if (resolves.isEmpty()) {
                    discardPending(pendingResultData, "ningun receptor en el perfil " + userId + " para " + record.mRouting);
                    return;
                }
                mBroadcastManager.sendBroadcast(pendingResultData);
                for (ResolveInfo resolve : resolves) {
                    ProcessRecord processRecord = BProcessManagerService.get().startProcessLocked(
                            resolve.activityInfo.packageName, resolve.activityInfo.processName, userId, -1, Process.myPid());
                    if (processRecord == null) {
                        continue;
                    }
                    ReceiverData data = new ReceiverData();
                    data.intent = record.mRouting;
                    data.pendingTarget = record.mTargetBytes;
                    data.fillIn = fill;
                    data.activityInfo = resolve.activityInfo;
                    data.data = pendingResultData;
                    processRecord.bActivityThread.scheduleReceiver(data);
                }
            } else {
                UserSpace userSpace = getOrCreateSpaceLocked(userId);
                synchronized (userSpace.mActiveServices) {
                    userSpace.mActiveServices.startService(record.mRouting, null,
                            record.mKind == ProxyPendingRecord.KIND_FOREGROUND_SERVICE, userId, record.mTargetBytes, fill);
                }
                pendingResultData.build().finish();
            }
        } catch (Throwable t) {
            Slog.e(TAG, "PendingIntent descartado: fallo al entregarlo " + record, t);
            pendingResultData.build().finish();
        }
    }

    private void discardPending(PendingResultData pendingResultData, String reason) {
        Slog.w(TAG, "PendingIntent descartado: " + reason);
        pendingResultData.build().finish();
    }

    @Override
    public void finishBroadcast(PendingResultData data) throws RemoteException {
        mBroadcastManager.finishBroadcast(data);
    }

    @Override
    public String getCallingPackage(IBinder token, int userId) throws RemoteException {
        UserSpace userSpace = getOrCreateSpaceLocked(userId);
        synchronized (userSpace.mStack) {
            return userSpace.mStack.getCallingPackage(token, userId);
        }
    }

    @Override
    public ComponentName getCallingActivity(IBinder token, int userId) throws RemoteException {
        UserSpace userSpace = getOrCreateSpaceLocked(userId);
        synchronized (userSpace.mStack) {
            return userSpace.mStack.getCallingActivity(token, userId);
        }
    }

    @Override
    public void getIntentSender(IBinder target, String packageName, int uid, int userId) {
        UserSpace userSpace = getOrCreateSpaceLocked(userId);
        synchronized (userSpace.mIntentSenderRecords) {
            PendingIntentRecord record = new PendingIntentRecord();
            record.uid = uid;
            record.packageName = packageName;
            userSpace.mIntentSenderRecords.put(target, record);
        }
    }

    @Override
    public String getPackageForIntentSender(IBinder target, int userId) throws RemoteException {
        UserSpace userSpace = getOrCreateSpaceLocked(userId);
        synchronized (userSpace.mIntentSenderRecords) {
            PendingIntentRecord record = userSpace.mIntentSenderRecords.get(target);
            if (record != null) {
                return record.packageName;
            }
        }
        return null;
    }

    @Override
    public int getUidForIntentSender(IBinder target, int userId) throws RemoteException {
        UserSpace userSpace = getOrCreateSpaceLocked(userId);
        synchronized (userSpace.mIntentSenderRecords) {
            PendingIntentRecord record = userSpace.mIntentSenderRecords.get(target);
            if (record != null) {
                return record.uid;
            }
        }
        return -1;
    }

    @Override
    public void onStartCommand(Intent intent, int userId) throws RemoteException {
        UserSpace userSpace = getOrCreateSpaceLocked(userId);
        synchronized (userSpace.mActiveServices) {
            userSpace.mActiveServices.onStartCommand(intent, userId);
        }
    }

    @Override
    public UnbindRecord onServiceUnbind(Intent proxyIntent, int userId) throws RemoteException {
        UserSpace userSpace = getOrCreateSpaceLocked(userId);
        synchronized (userSpace.mActiveServices) {
            return userSpace.mActiveServices.onServiceUnbind(proxyIntent, userId);
        }
    }

    @Override
    public void onServiceDestroy(Intent proxyIntent, int userId) throws RemoteException {
        UserSpace userSpace = getOrCreateSpaceLocked(userId);
        synchronized (userSpace.mActiveServices) {
            userSpace.mActiveServices.onServiceDestroy(proxyIntent, userId);
        }
    }

    @Override
    public int stopService(Intent intent, String resolvedType, int userId) {
        UserSpace userSpace = getOrCreateSpaceLocked(userId);
        synchronized (userSpace.mActiveServices) {
            return userSpace.mActiveServices.stopService(intent, resolvedType, userId);
        }
    }

    @Override
    public Intent bindService(Intent service, IBinder binder, String resolvedType, int userId) throws RemoteException {
        UserSpace userSpace = getOrCreateSpaceLocked(userId);
        synchronized (userSpace.mActiveServices) {
            return userSpace.mActiveServices.bindService(service, binder, resolvedType, userId);
        }
    }

    @Override
    public void unbindService(IBinder binder, int userId) throws RemoteException {
        UserSpace userSpace = getOrCreateSpaceLocked(userId);
        synchronized (userSpace.mActiveServices) {
            userSpace.mActiveServices.unbindService(binder, userId);
        }
    }

    @Override
    public void stopServiceToken(ComponentName className, IBinder token, int userId) throws RemoteException {
        UserSpace userSpace = getOrCreateSpaceLocked(userId);
        synchronized (userSpace.mActiveServices) {
            userSpace.mActiveServices.stopServiceToken(className, token, userId);
        }
    }

    @Override
    public AppConfig initProcess(String packageName, String processName, int userId) throws RemoteException {
        ProcessRecord processRecord = BProcessManagerService.get().startProcessLocked(packageName, processName, userId, -1, Binder.getCallingPid());
        if (processRecord == null)
            return null;
        return processRecord.getClientConfig();
    }

    @Override
    public void restartProcess(String packageName, String processName, int userId) throws RemoteException {
        BProcessManagerService.get().restartAppProcess(packageName, processName, userId);
    }

    @Override
    public void startActivity(Intent intent, int userId) {
        UserSpace userSpace = getOrCreateSpaceLocked(userId);
        synchronized (userSpace.mStack) {
            userSpace.mStack.startActivityLocked(userId, intent, null, null, null, -1, -1, null);
        }
    }

    @Override
    public int startActivityAms(int userId, Intent intent, String resolvedType, IBinder resultTo, String resultWho, int requestCode, int flags, Bundle options) throws RemoteException {
        UserSpace space = getOrCreateSpaceLocked(userId);
        synchronized (space.mStack) {
            return space.mStack.startActivityLocked(userId, intent, resolvedType, resultTo, resultWho, requestCode, flags, options);
        }
    }

    @Override
    public int startActivities(int userId, Intent[] intent, String[] resolvedType, IBinder resultTo, Bundle options) throws RemoteException {
        UserSpace space = getOrCreateSpaceLocked(userId);
        synchronized (space.mStack) {
            return space.mStack.startActivitiesLocked(userId, intent, resolvedType, resultTo, options);
        }
    }

    private UserSpace getOrCreateSpaceLocked(int userId) {
        synchronized (mUserSpace) {
            UserSpace userSpace = mUserSpace.get(userId);
            if (userSpace != null)
                return userSpace;
            userSpace = new UserSpace();
            mUserSpace.put(userId, userSpace);
            return userSpace;
        }
    }

    @Override
    public void systemReady() {
        mBroadcastManager.startup();
    }
}
