package top.niunaijun.blackboxa.view.main

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModelProvider
import top.niunaijun.blackboxa.R
import top.niunaijun.blackboxa.util.InjectionUtil
import top.niunaijun.blackboxa.view.list.ListViewModel

class WelcomeActivity : AppCompatActivity() {

    private var notificationDialog: AlertDialog? = null

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        proceed()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        previewInstalledAppList()
        proceed()
    }

    override fun onResume() {
        super.onResume()
        // De vuelta de los ajustes de notificaciones: si ya estan activas, se sigue.
        if (notificationDialog != null && notificationsEnabled()) {
            jump()
        }
    }

    override fun onDestroy() {
        notificationDialog?.dismiss()
        notificationDialog = null
        super.onDestroy()
    }

    private fun proceed() {
        if (!notificationsEnabled() && notificationDialog == null) {
            showNotificationDialog()
        } else if (notificationDialog == null) {
            jump()
        }
    }

    /** En Android 13+ las apps del box publican como el host, por lo que el permiso es el del host. */
    private fun notificationsEnabled(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                NotificationManagerCompat.from(this).areNotificationsEnabled()
    }

    private fun showNotificationDialog() {
        notificationDialog = AlertDialog.Builder(this)
            .setTitle(R.string.notification_permission_title)
            .setMessage(R.string.notification_permission_message)
            .setCancelable(false)
            .setPositiveButton(R.string.notification_permission_open_settings, null)
            .setNegativeButton(R.string.notification_permission_later) { _, _ -> jump() }
            .create()
            .also { it.show() }
        // El boton positivo no cierra el dialogo: sigue visible al volver de los ajustes.
        notificationDialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            )
        }
    }

    private fun jump() {
        MainActivity.start(this)
        finish()
    }

    private fun previewInstalledAppList(){
        val viewModel = ViewModelProvider(this,InjectionUtil.getListFactory()).get(ListViewModel::class.java)
        viewModel.previewInstalledList()
    }
}
