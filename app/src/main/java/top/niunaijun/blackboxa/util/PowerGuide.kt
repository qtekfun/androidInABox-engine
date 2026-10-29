package top.niunaijun.blackboxa.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import top.niunaijun.blackbox.utils.RomGuide

/** Exencion de bateria y guia de autoarranque por ROM (spec 04, R04.9 a R04.13). */
object PowerGuide {
    private const val TAG = "PowerGuide"

    fun isIgnoringBatteryOptimizations(ctx: Context): Boolean {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(ctx.packageName)
    }

    /** Pide la exencion; si el sistema no resuelve la peticion directa, abre la lista de exenciones. */
    fun requestIgnoreBatteryOptimizations(ctx: Context) {
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:" + ctx.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val list = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val target = if (direct.resolveActivity(ctx.packageManager) != null) direct else list
        Log.i(TAG, "exencion de bateria: lanzando ${target.action}")
        try {
            ctx.startActivity(target)
        } catch (e: Exception) {
            Log.w(TAG, "no se pudo abrir la exencion de bateria: $e")
        }
    }

    fun rom(): RomGuide.Rom = RomGuide.detect(Build.MANUFACTURER, Build.BRAND)

    enum class Opened { ROM_SETTINGS, APP_DETAILS, NONE }

    /**
     * Abre la pantalla de autoarranque de la ROM; si ninguna resuelve, la ficha de la app. Devuelve
     * NONE solo si tampoco eso se puede abrir (la UI muestra entonces las instrucciones manuales).
     */
    fun openAutostartSettings(ctx: Context): Opened {
        val pm = ctx.packageManager
        val rom = rom()
        val comp = RomGuide.firstResolvable(RomGuide.candidates(rom)) { c ->
            pm.resolveActivity(Intent().setComponent(ComponentName.unflattenFromString(c)), 0) != null
        }
        Log.i(TAG, "rom=$rom componente resuelto=$comp")
        if (comp != null) {
            try {
                ctx.startActivity(
                    Intent().setComponent(ComponentName.unflattenFromString(comp))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return Opened.ROM_SETTINGS
            } catch (e: Exception) {
                Log.w(TAG, "fallo al abrir $comp: $e")
            }
        }
        return try {
            ctx.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:" + ctx.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            Opened.APP_DETAILS
        } catch (e: Exception) {
            Log.w(TAG, "fallo al abrir la ficha de la app: $e")
            Opened.NONE
        }
    }
}
