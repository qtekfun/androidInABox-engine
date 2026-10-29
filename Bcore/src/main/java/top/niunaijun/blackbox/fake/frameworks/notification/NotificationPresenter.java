package top.niunaijun.blackbox.fake.frameworks.notification;

import android.app.Notification;
import android.app.Person;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;

import java.lang.reflect.Method;
import java.util.ArrayList;

import black.android.app.BRNotificationAction;
import black.android.app.BRNotificationM;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.utils.Slog;

/**
 * Prepara en el proceso invitado una notificacion antes de mandarla a :black. Todo lo que
 * necesita los Resources o las clases del invitado se resuelve aqui, porque el host no puede
 * abrir ni los recursos ni los Parcelable del invitado.
 */
public final class NotificationPresenter {
    public static final String TAG = "NotificationPresenter";

    /** Extras privados hacia :black; el servicio los lee y los elimina antes de publicar. */
    public static final String EXTRA_APP_LABEL = "bb.app_label";
    public static final String EXTRA_PROFILE_LABEL = "bb.profile_label";

    private static final int TYPE_RESOURCE = 2;

    private NotificationPresenter() {
    }

    /** Devuelve una copia lista para enviar al host; nunca lanza (R02.4). */
    public static Notification present(Notification original) {
        Notification n;
        try {
            n = original.clone();
        } catch (Throwable t) {
            Slog.w(TAG, "clone fallido, se publica sin presentar", t);
            return original;
        }
        Context guest = BActivityThread.getApplication();
        if (guest == null) {
            return n;
        }
        IconBudget budget = new IconBudget();
        convertSmallIcon(n, guest, budget);
        convertActions(n, guest, budget);
        convertExtras(n, guest, budget);
        convertLargeIcon(n, guest, budget);
        identify(n, guest, budget);
        return n;
    }

    /** Etiquetas de app y perfil: subText, largeIcon por defecto y extras privados (R02.1, R02.2). */
    private static void identify(Notification n, Context guest, IconBudget budget) {
        String app = guest.getPackageName();
        try {
            CharSequence label = guest.getPackageManager().getApplicationLabel(guest.getApplicationInfo());
            if (label != null && label.length() > 0) {
                app = label.toString();
            }
        } catch (Throwable t) {
            Slog.w(TAG, "etiqueta de app no disponible", t);
        }
        String profile = profileLabel(BActivityThread.getUserId());
        try {
            Bundle extras = n.extras;
            if (extras == null) {
                extras = new Bundle();
                n.extras = extras;
            }
            String own = null;
            CharSequence sub = extras.getCharSequence(Notification.EXTRA_SUB_TEXT);
            if (sub != null && sub.length() > 0) {
                own = sub.toString();
            }
            extras.putCharSequence(Notification.EXTRA_SUB_TEXT, subText(app, profile, own));
            if (BlackBoxCore.get().isNotificationTitlePrefix()) {
                for (String key : new String[]{Notification.EXTRA_TITLE, Notification.EXTRA_TITLE_BIG}) {
                    CharSequence title = extras.getCharSequence(key);
                    if (title != null) {
                        extras.putCharSequence(key, titlePrefix(app, profile, title));
                    }
                }
            }
            extras.putString(EXTRA_APP_LABEL, app);
            extras.putString(EXTRA_PROFILE_LABEL, profile);
        } catch (Throwable t) {
            Slog.w(TAG, "subText no aplicable", t);
        }
        defaultLargeIcon(n, guest, budget);
    }

    public static String profileLabel(int userId) {
        return "Perfil " + (userId + 1);
    }

    public static String subText(String app, String profile, String own) {
        String base = app + " \u00b7 " + profile;
        return own == null || own.isEmpty() ? base : base + " \u00b7 " + own;
    }

    /** R02.6: {@code [<App> · <Perfil>] <titulo>}. */
    public static String titlePrefix(String app, String profile, CharSequence title) {
        return "[" + app + " \u00b7 " + profile + "] " + title;
    }

    @SuppressWarnings("deprecation")
    private static void defaultLargeIcon(Notification n, Context guest, IconBudget budget) {
        try {
            if (n.getLargeIcon() != null || n.largeIcon != null) {
                return;
            }
            Drawable d = guest.getPackageManager().getApplicationIcon(guest.getApplicationInfo());
            int[] size = IconBudget.fit(d.getIntrinsicWidth(), d.getIntrinsicHeight(), IconBudget.LARGE_PX);
            if (!budget.tryTake(IconBudget.bytesOf(size[0], size[1]))) {
                return;
            }
            Bitmap bm = Bitmap.createBitmap(size[0], size[1], Bitmap.Config.ARGB_8888);
            d.setBounds(0, 0, size[0], size[1]);
            d.draw(new Canvas(bm));
            BRNotificationM.get(n)._set_mLargeIcon(Icon.createWithBitmap(bm));
        } catch (Throwable t) {
            Slog.w(TAG, "icono de app como largeIcon no disponible", t);
        }
    }

    // ---- iconos pequeno y grande ----

    @SuppressWarnings("deprecation")
    private static void convertSmallIcon(Notification n, Context guest, IconBudget budget) {
        Icon bitmapIcon = null;
        try {
            Icon src = n.getSmallIcon();
            if (src != null) {
                bitmapIcon = toBitmapIcon(src, guest, IconBudget.SMALL_PX, budget, true);
            }
        } catch (Throwable t) {
            Slog.w(TAG, "icono pequeno no convertible, se usa el del host", t);
        }
        if (bitmapIcon == null) {
            bitmapIcon = hostIcon();
        }
        BRNotificationM.get(n)._set_mSmallIcon(bitmapIcon);
        // El sistema puede validar icon != 0; se deja un recurso del host.
        n.icon = BlackBoxCore.getContext().getApplicationInfo().icon;
    }

    @SuppressWarnings("deprecation")
    private static void convertLargeIcon(Notification n, Context guest, IconBudget budget) {
        try {
            Icon src = n.getLargeIcon();
            if (src == null) {
                return;
            }
            Icon converted = toBitmapIcon(src, guest, IconBudget.LARGE_PX, budget, false);
            BRNotificationM.get(n)._set_mLargeIcon(converted);
            if (converted == null) {
                n.largeIcon = null;
            }
        } catch (Throwable t) {
            Slog.w(TAG, "icono grande no convertible, se omite", t);
            try {
                BRNotificationM.get(n)._set_mLargeIcon(null);
                n.largeIcon = null;
            } catch (Throwable ignored) {
            }
        }
    }

    private static Icon hostIcon() {
        return Icon.createWithResource(BlackBoxCore.getContext(), BlackBoxCore.getContext().getApplicationInfo().icon);
    }

    // ---- acciones ----

    private static void convertActions(Notification n, Context guest, IconBudget budget) {
        if (n.actions == null) {
            return;
        }
        for (Notification.Action a : n.actions) {
            if (a == null) {
                continue;
            }
            ExtrasSanitizer.clean(a.getExtras());
            try {
                Icon src = a.getIcon();
                if (src == null) {
                    continue;
                }
                Icon converted = toBitmapIcon(src, guest, IconBudget.SMALL_PX, budget, false);
                BRNotificationAction.get(a)._set_mIcon(converted);
            } catch (Throwable t) {
                Slog.w(TAG, "icono de accion no convertible, se omite", t);
                try {
                    BRNotificationAction.get(a)._set_mIcon(null);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    // ---- Person, imagen grande en extras ----

    private static void convertExtras(Notification n, Context guest, IconBudget budget) {
        Bundle extras = n.extras;
        if (extras == null) {
            return;
        }
        extras = new Bundle(extras);
        n.extras = extras;
        ExtrasSanitizer.clean(extras);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                convertPersonExtras(extras, guest, budget);
            } catch (Throwable t) {
                Slog.w(TAG, "Person no convertible", t);
            }
        }
        try {
            Parcelable pic = extras.getParcelable(Notification.EXTRA_PICTURE_ICON);
            if (pic instanceof Icon) {
                Icon c = toBitmapIcon((Icon) pic, guest, IconBudget.LARGE_PX, budget, false);
                if (c != null) {
                    extras.putParcelable(Notification.EXTRA_PICTURE_ICON, c);
                } else {
                    extras.remove(Notification.EXTRA_PICTURE_ICON);
                }
            }
            Parcelable big = extras.getParcelable("android.largeIcon.big");
            if (big instanceof Icon) {
                Icon c = toBitmapIcon((Icon) big, guest, IconBudget.LARGE_PX, budget, false);
                if (c != null) {
                    extras.putParcelable("android.largeIcon.big", c);
                } else {
                    extras.remove("android.largeIcon.big");
                }
            }
        } catch (Throwable t) {
            Slog.w(TAG, "iconos de extras no convertibles", t);
        }
    }

    private static void convertPersonExtras(Bundle extras, Context guest, IconBudget budget) {
        Parcelable user = extras.getParcelable(Notification.EXTRA_MESSAGING_PERSON);
        if (user instanceof Person) {
            extras.putParcelable(Notification.EXTRA_MESSAGING_PERSON, person((Person) user, guest, budget));
        }
        ArrayList<Person> people = extras.getParcelableArrayList(Notification.EXTRA_PEOPLE_LIST);
        if (people != null) {
            ArrayList<Person> out = new ArrayList<>(people.size());
            for (Person p : people) {
                out.add(person(p, guest, budget));
            }
            extras.putParcelableArrayList(Notification.EXTRA_PEOPLE_LIST, out);
        }
        for (String key : new String[]{Notification.EXTRA_MESSAGES, Notification.EXTRA_HISTORIC_MESSAGES}) {
            Parcelable[] msgs = extras.getParcelableArray(key);
            if (msgs == null) {
                continue;
            }
            Parcelable[] out = new Parcelable[msgs.length];
            for (int i = 0; i < msgs.length; i++) {
                out[i] = msgs[i];
                if (msgs[i] instanceof Bundle) {
                    Bundle m = new Bundle((Bundle) msgs[i]);
                    for (String pk : new String[]{"sender_person", "person"}) {
                        Parcelable p = m.getParcelable(pk);
                        if (p instanceof Person) {
                            m.putParcelable(pk, person((Person) p, guest, budget));
                        }
                    }
                    out[i] = m;
                }
            }
            extras.putParcelableArray(key, out);
        }
    }

    private static Person person(Person p, Context guest, IconBudget budget) {
        if (p == null || p.getIcon() == null) {
            return p;
        }
        try {
            Icon c = toBitmapIcon(p.getIcon(), guest, IconBudget.SMALL_PX, budget, false);
            return p.toBuilder().setIcon(c).build();
        } catch (Throwable t) {
            Slog.w(TAG, "icono de Person no convertible, se omite", t);
            return p.toBuilder().setIcon(null).build();
        }
    }

    // ---- conversor ----

    /**
     * Convierte un {@link Icon} a un Icon de bitmap acotado a {@code maxPx}. Devuelve null si no
     * cabe en el presupuesto (salvo {@code force}) o si el icono no se puede cargar.
     */
    static Icon toBitmapIcon(Icon icon, Context guest, int maxPx, IconBudget budget, boolean force) {
        Drawable d = load(icon, guest);
        if (d == null) {
            return null;
        }
        int[] size = IconBudget.fit(d.getIntrinsicWidth(), d.getIntrinsicHeight(), maxPx);
        int bytes = IconBudget.bytesOf(size[0], size[1]);
        if (force) {
            budget.take(bytes);
        } else if (!budget.tryTake(bytes)) {
            Slog.w(TAG, "presupuesto de bitmaps agotado, se omite un icono");
            return null;
        }
        Bitmap bm = Bitmap.createBitmap(size[0], size[1], Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bm);
        d.setBounds(0, 0, size[0], size[1]);
        d.draw(canvas);
        return Icon.createWithBitmap(bm);
    }

    private static Drawable load(Icon icon, Context guest) {
        try {
            if (intCall(icon, "getType") == TYPE_RESOURCE) {
                String pkg = (String) call(icon, "getResPackage");
                int id = intCall(icon, "getResId");
                String host = BlackBoxCore.getHostPkg();
                if (pkg == null || pkg.isEmpty() || pkg.equals(guest.getPackageName()) || pkg.equals(host)) {
                    // El id es del APK invitado aunque el paquete diga host: se resuelve con sus Resources.
                    return guest.getResources().getDrawable(id, guest.getTheme());
                }
            }
        } catch (Throwable t) {
            Slog.w(TAG, "tipo de icono no legible, se usa loadDrawable", t);
        }
        try {
            return icon.loadDrawable(guest);
        } catch (Throwable t) {
            Slog.w(TAG, "loadDrawable fallido", t);
            return null;
        }
    }

    private static Object call(Icon icon, String name) throws Exception {
        Method m = Icon.class.getDeclaredMethod(name);
        m.setAccessible(true);
        return m.invoke(icon);
    }

    private static int intCall(Icon icon, String name) throws Exception {
        return (Integer) call(icon, name);
    }
}
