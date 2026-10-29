package top.niunaijun.blackbox.proxy.record;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;

import java.util.ArrayList;

/**
 * Registro que el stub de un PendingIntent lleva hacia el host. El Intent original viaja como
 * {@code byte[]} (Parcel.marshall) para que ningun proceso del host tenga que desmarshalar
 * extras con clases del invitado (R02.19); solo se reconstruye en el proceso invitado de destino.
 */
public class ProxyPendingRecord {
    public static final int KIND_ACTIVITY = 0;
    public static final int KIND_BROADCAST = 1;
    public static final int KIND_SERVICE = 2;
    public static final int KIND_FOREGROUND_SERVICE = 3;

    private static final String PREFIX = "_B_|_P_";
    private static final String KEY_USER = PREFIX + "user_id_";
    private static final String KEY_BYTES = PREFIX + "bytes_";
    private static final String KEY_PACKAGE = PREFIX + "pkg_";
    private static final String KEY_KIND = PREFIX + "kind_";
    private static final String KEY_ROUTING = PREFIX + "routing_";
    /** Formato anterior: el Intent como Parcelable en el extra. */
    private static final String KEY_LEGACY_TARGET = PREFIX + "target_";

    public int mUserId;
    public String mPackage;
    public int mKind = -1;
    public byte[] mTargetBytes;
    /** Solo accion, datos, tipo, componente y categorias del original: se puede abrir en :black. */
    public Intent mRouting;
    /** Original completo; solo se usa para actividades (se pasa tal cual a startActivity). */
    public Intent mTarget;

    public static void saveStub(Intent shadow, Intent target, int userId, String packageName, int kind) {
        shadow.putExtra(KEY_USER, userId);
        shadow.putExtra(KEY_PACKAGE, packageName);
        shadow.putExtra(KEY_KIND, kind);
        try {
            shadow.putExtra(KEY_BYTES, marshal(target));
        } catch (RuntimeException e) {
            // Parcel.marshall() no admite binders ni descriptores (p. ej. la respuesta del autenticador
            // que GMS pone en el intent): se guarda como Parcelable, el formato anterior que create() lee.
            shadow.putExtra(KEY_LEGACY_TARGET, target);
        }
        shadow.putExtra(KEY_ROUTING, target.cloneFilter());
    }

    /**
     * Data unico por (perfil, paquete, tipo, requestCode, filterEquals, indice): el sistema
     * identifica un PendingIntent por filterEquals del stub, y sin esto todos serian iguales.
     */
    public static Uri uniqueUri(int userId, String packageName, int kind, int requestCode, Intent target, int index) {
        return Uri.parse("bbpi://" + userId + "/" + packageName + "/" + kind + "/" + requestCode + "/"
                + Integer.toHexString(target.filterHashCode()) + "/" + index);
    }

    public static ProxyPendingRecord create(Intent stub) {
        ProxyPendingRecord r = new ProxyPendingRecord();
        r.mUserId = stub.getIntExtra(KEY_USER, 0);
        r.mPackage = stub.getStringExtra(KEY_PACKAGE);
        r.mKind = stub.getIntExtra(KEY_KIND, KIND_ACTIVITY);
        r.mTargetBytes = stub.getByteArrayExtra(KEY_BYTES);
        r.mRouting = stub.getParcelableExtra(KEY_ROUTING);
        if (r.mTargetBytes != null) {
            r.mTarget = unmarshal(r.mTargetBytes);
        } else {
            r.mTarget = stub.getParcelableExtra(KEY_LEGACY_TARGET);
        }
        return r;
    }

    /** Un registro sin bytes o sin ruta no se entrega (un PendingIntent que no paso por el motor). */
    public boolean isDeliverable() {
        return mTargetBytes != null && mRouting != null && mPackage != null
                && mKind >= KIND_BROADCAST && mKind <= KIND_FOREGROUND_SERVICE;
    }

    /**
     * Datos que el sistema fusiona en el stub al enviarlo (extras, ClipData con las respuestas de
     * RemoteInput, sourceBounds), sin las claves del registro y sin action/data/component/type,
     * que son los del stub.
     */
    public static Intent fillOf(Intent stub) {
        Intent fill = new Intent();
        Bundle extras = stub.getExtras();
        if (extras != null) {
            for (String key : new ArrayList<>(extras.keySet())) {
                if (key.startsWith(PREFIX)) {
                    extras.remove(key);
                }
            }
            fill.putExtras(extras);
        }
        fill.setClipData(stub.getClipData());
        fill.setSourceBounds(stub.getSourceBounds());
        return fill;
    }

    /** Reconstruye el Intent original (en el invitado) y le fusiona el relleno. */
    public static Intent materialize(byte[] targetBytes, Intent fill, ClassLoader classLoader) {
        Intent target = unmarshal(targetBytes);
        if (target == null) {
            return null;
        }
        target.setExtrasClassLoader(classLoader);
        if (fill != null) {
            Bundle fillExtras = fill.getExtras();
            if (fillExtras != null) {
                target.putExtras(fillExtras);
            }
            target.fillIn(fill, Intent.FILL_IN_CLIP_DATA | Intent.FILL_IN_SOURCE_BOUNDS);
        }
        return target;
    }

    public static byte[] marshal(Intent intent) {
        Parcel p = Parcel.obtain();
        try {
            intent.writeToParcel(p, 0);
            return p.marshall();
        } finally {
            p.recycle();
        }
    }

    /**
     * Los extras del Intent resultante quedan sin abrir (Bundle perezoso) y apuntan al Parcel, que
     * por eso no se recicla; lo libera el recolector.
     */
    public static Intent unmarshal(byte[] bytes) {
        try {
            Parcel p = Parcel.obtain();
            p.unmarshall(bytes, 0, bytes.length);
            p.setDataPosition(0);
            return Intent.CREATOR.createFromParcel(p);
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public String toString() {
        return "ProxyPendingRecord{" +
                "mUserId=" + mUserId +
                ", mPackage=" + mPackage +
                ", mKind=" + mKind +
                ", mRouting=" + mRouting +
                '}';
    }
}
