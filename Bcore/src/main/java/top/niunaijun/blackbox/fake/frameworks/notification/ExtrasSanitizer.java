package top.niunaijun.blackbox.fake.frameworks.notification;

import android.os.Bundle;

import java.util.ArrayList;
import java.util.List;

/**
 * Quita de {@code Notification.extras} los valores cuya clase no es del framework (R02.5): el
 * host no tiene las clases del invitado y un Parcelable propio rompe el desmarshalado en el
 * sistema o en :black.
 */
public final class ExtrasSanitizer {
    private ExtrasSanitizer() {
    }

    /** True si la clase la carga el cargador de arranque (framework, java.*, android.*). */
    public static boolean isFrameworkClass(Class<?> c) {
        while (c.isArray()) {
            c = c.getComponentType();
        }
        if (c.isPrimitive()) {
            return true;
        }
        ClassLoader l = c.getClassLoader();
        return l == null || l == Object.class.getClassLoader();
    }

    /** Limpia {@code b} en el sitio; los Bundle anidados se sustituyen por copias limpias. */
    public static void clean(Bundle b) {
        if (b == null) {
            return;
        }
        for (String key : new ArrayList<>(b.keySet())) {
            Object v = b.get(key);
            Object cleaned = cleanValue(v);
            if (cleaned == REMOVE) {
                b.remove(key);
            } else if (cleaned != v) {
                putBack(b, key, cleaned);
            }
        }
    }

    private static final Object REMOVE = new Object();

    private static void putBack(Bundle b, String key, Object v) {
        if (v instanceof Bundle) {
            b.putBundle(key, (Bundle) v);
        } else if (v instanceof android.os.Parcelable[]) {
            b.putParcelableArray(key, (android.os.Parcelable[]) v);
        } else if (v instanceof ArrayList) {
            @SuppressWarnings("unchecked")
            ArrayList<android.os.Parcelable> l = (ArrayList<android.os.Parcelable>) v;
            b.putParcelableArrayList(key, l);
        }
    }

    private static Object cleanValue(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Bundle) {
            Bundle copy = new Bundle((Bundle) v);
            clean(copy);
            return copy;
        }
        if (!isFrameworkClass(v.getClass())) {
            return REMOVE;
        }
        if (v instanceof android.os.Parcelable[]) {
            android.os.Parcelable[] in = (android.os.Parcelable[]) v;
            List<android.os.Parcelable> out = new ArrayList<>(in.length);
            boolean changed = false;
            for (android.os.Parcelable p : in) {
                Object c = cleanValue(p);
                if (c == REMOVE) {
                    changed = true;
                } else {
                    changed |= c != p;
                    out.add((android.os.Parcelable) c);
                }
            }
            return changed ? out.toArray(new android.os.Parcelable[0]) : v;
        }
        if (v instanceof ArrayList) {
            ArrayList<?> in = (ArrayList<?>) v;
            ArrayList<Object> out = new ArrayList<>(in.size());
            boolean changed = false;
            for (Object o : in) {
                Object c = cleanValue(o);
                if (c == REMOVE) {
                    changed = true;
                } else {
                    changed |= c != o;
                    out.add(c);
                }
            }
            return changed ? out : v;
        }
        return v;
    }
}
