package top.niunaijun.blackboxa.data

import top.niunaijun.blackboxa.R
import top.niunaijun.blackboxa.app.App
import top.niunaijun.blackboxa.app.AppManager

/** Nombre visible de un perfil: el renombrado por el usuario o "Perfil N" (1-based). */
object ProfileNames {
    fun of(id: Int): String =
        AppManager.mRemarkSharedPreferences.getString("Remark$id", null)?.takeIf { it.isNotBlank() }
            ?: App.getContext().getString(R.string.profile_default_name, id + 1)

    fun forget(id: Int) {
        AppManager.mRemarkSharedPreferences.edit().remove("Remark$id").remove("AppList$id").apply()
    }
}
