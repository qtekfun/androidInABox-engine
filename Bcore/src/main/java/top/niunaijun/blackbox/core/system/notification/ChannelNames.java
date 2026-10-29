package top.niunaijun.blackbox.core.system.notification;

/** Nombres legibles de canales y grupos reales: {@code <App> (<Perfil>) · <nombre>}. */
public final class ChannelNames {
    private static final String SEP = " · ";

    private ChannelNames() {
    }

    /** {@code <App> (<Perfil>)}, nombre del grupo de canales por defecto. */
    public static String owner(String app, String profile) {
        return app + " (" + profile + ")";
    }

    public static String display(String app, String profile, CharSequence name) {
        String base = owner(app, profile);
        return name == null || name.length() == 0 ? base : base + SEP + name;
    }

    /** Inversa de {@link #display}; devuelve {@code displayName} tal cual si no tiene la forma. */
    public static String original(String profile, String displayName) {
        if (displayName == null) {
            return null;
        }
        String marker = " (" + profile + ")" + SEP;
        int i = displayName.indexOf(marker);
        return i < 0 ? displayName : displayName.substring(i + marker.length());
    }
}
