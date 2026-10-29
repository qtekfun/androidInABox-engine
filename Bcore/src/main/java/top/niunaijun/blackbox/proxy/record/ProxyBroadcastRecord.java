package top.niunaijun.blackbox.proxy.record;

import android.content.Intent;


public class ProxyBroadcastRecord {
    public Intent mIntent;
    public int mUserId;
    /** Identificador unico del broadcast; 0 si el stub no lo lleva. */
    public long mId;

    private static final java.util.concurrent.atomic.AtomicLong sNextId = new java.util.concurrent.atomic.AtomicLong();
    private static final long sBoot = System.nanoTime();

    public ProxyBroadcastRecord(Intent intent, int userId) {
        mIntent = intent;
        mUserId = userId;
    }

    public static void saveStub(Intent shadow, Intent target, int userId) {
        shadow.putExtra("_B_|_target_", target);
        shadow.putExtra("_B_|_user_id_", userId);
        shadow.putExtra("_B_|_id_", (sBoot << 20) ^ sNextId.incrementAndGet());
    }

    public static ProxyBroadcastRecord create(Intent intent) {
        Intent target = intent.getParcelableExtra("_B_|_target_");
        int userId = intent.getIntExtra("_B_|_user_id_", 0);
        ProxyBroadcastRecord record = new ProxyBroadcastRecord(target, userId);
        record.mId = intent.getLongExtra("_B_|_id_", 0L);
        return record;
    }

    @Override
    public String toString() {
        return "ProxyBroadcastRecord{" +
                "mIntent=" + mIntent +
                ", mUserId=" + mUserId +
                '}';
    }
}
