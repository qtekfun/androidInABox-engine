package top.niunaijun.blackbox.fake.service;

import android.os.Build;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import top.niunaijun.blackbox.utils.Slog;

/**
 * Localiza el LaunchActivityItem dentro de un ClientTransaction probando, en orden,
 * getTransactionItems(), el campo mTransactionItems y el campo mActivityCallbacks.
 */
public final class LaunchItemLocator {
    private static final String TAG = "LaunchItemLocator";
    private static final AtomicBoolean sLogged = new AtomicBoolean(false);

    private LaunchItemLocator() {
    }

    public static Object find(Object clientTransaction, Class<?> itemClass) {
        if (clientTransaction == null || itemClass == null) {
            return null;
        }
        Class<?> clazz = clientTransaction.getClass();
        Object item = findIn(callGetter(clazz, clientTransaction, "getTransactionItems"), itemClass);
        if (item == null) {
            item = findIn(readField(clazz, clientTransaction, "mTransactionItems"), itemClass);
        }
        if (item == null) {
            item = findIn(readField(clazz, clientTransaction, "mActivityCallbacks"), itemClass);
        }
        if (item == null && sLogged.compareAndSet(false, true)) {
            Slog.e(TAG, "LaunchActivityItem no encontrado en ClientTransaction; Android "
                    + Build.VERSION.SDK_INT + ", campos: " + describeFields(clazz));
        }
        return item;
    }

    private static Object findIn(Object value, Class<?> itemClass) {
        if (!(value instanceof Collection)) {
            return null;
        }
        for (Object obj : (Collection<?>) value) {
            if (obj != null && itemClass.isInstance(obj)) {
                return obj;
            }
        }
        return null;
    }

    private static Object callGetter(Class<?> clazz, Object target, String name) {
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            try {
                Method method = c.getDeclaredMethod(name);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (NoSuchMethodException ignored) {
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }

    private static Object readField(Class<?> clazz, Object target, String name) {
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            try {
                Field field = c.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }

    private static String describeFields(Class<?> clazz) {
        List<String> names = new ArrayList<>();
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                for (Field field : c.getDeclaredFields()) {
                    names.add(field.getName());
                }
            } catch (Throwable ignored) {
            }
        }
        return names.toString();
    }

    static void resetLogForTest() {
        sLogged.set(false);
    }
}
