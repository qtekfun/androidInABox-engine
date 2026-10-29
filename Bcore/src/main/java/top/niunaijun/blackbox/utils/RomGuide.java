package top.niunaijun.blackbox.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Deteccion de ROM y lista de pantallas de ajustes de autoarranque conocidas (spec 04, R04.11).
 * Logica pura, sin Android, para poder probarla en la JVM. TODOS los nombres de componente son
 * (sin verificar): cambian entre versiones de la ROM y deben comprobarse en dispositivo; por eso el
 * llamador intenta cada uno con resolveActivity y cae a la ficha de la app (R04.13).
 */
public final class RomGuide {
    public enum Rom {COLOROS, HYPEROS, MAGICOS, ORIGINOS, OTHER}

    private RomGuide() {
    }

    /** Detecta la ROM por fabricante y marca (sin verificar cuales cubre cada marca). */
    public static Rom detect(String manufacturer, String brand) {
        String m = lower(manufacturer);
        String b = lower(brand);
        if (any(m, b, "oppo", "realme", "oneplus")) {
            return Rom.COLOROS;
        }
        if (any(m, b, "xiaomi", "redmi", "poco")) {
            return Rom.HYPEROS;
        }
        if (any(m, b, "honor", "huawei")) {
            return Rom.MAGICOS;
        }
        if (any(m, b, "vivo", "iqoo")) {
            return Rom.ORIGINOS;
        }
        return Rom.OTHER;
    }

    /** Componentes "paquete/clase" a probar, en orden (sin verificar). */
    public static List<String> candidates(Rom rom) {
        switch (rom) {
            case COLOROS:
                return Arrays.asList(
                        "com.coloros.safecenter/com.coloros.safecenter.permission.startup.StartupAppListActivity",
                        "com.coloros.safecenter/com.coloros.safecenter.startupapp.StartupAppListActivity",
                        "com.oppo.safe/com.oppo.safe.permission.startup.StartupAppListActivity");
            case HYPEROS:
                return Arrays.asList(
                        "com.miui.securitycenter/com.miui.permcenter.autostart.AutoStartManagementActivity");
            case MAGICOS:
                return Arrays.asList(
                        "com.hihonor.systemmanager/com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
                        "com.huawei.systemmanager/com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
                        "com.huawei.systemmanager/com.huawei.systemmanager.optimize.process.ProtectActivity");
            case ORIGINOS:
                return Arrays.asList(
                        "com.vivo.permissionmanager/com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
                        "com.iqoo.secure/com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity");
            default:
                return new ArrayList<>();
        }
    }

    /** Primer candidato que {@code resolves} acepta, o null si ninguno resuelve (R04.13). */
    public static String firstResolvable(List<String> candidates, Resolver resolves) {
        for (String c : candidates) {
            try {
                if (resolves.canResolve(c)) {
                    return c;
                }
            } catch (RuntimeException ignored) {
            }
        }
        return null;
    }

    public interface Resolver {
        boolean canResolve(String component);
    }

    private static boolean any(String m, String b, String... names) {
        for (String n : names) {
            if (m.contains(n) || b.contains(n)) {
                return true;
            }
        }
        return false;
    }

    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}
