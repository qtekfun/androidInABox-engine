@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package top.niunaijun.blackboxa.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.DisposableEffect
import top.niunaijun.blackboxa.R

private class OnbStep(val icon: ImageVector, val title: Int, val body: Int, val grantable: Boolean)

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    val steps = remember {
        listOf(
            OnbStep(Icons.Filled.RocketLaunch, R.string.onb_welcome_title, R.string.onb_welcome_body, false),
            OnbStep(Icons.Filled.Notifications, R.string.onb_notif_title, R.string.onb_notif_body, true),
            OnbStep(Icons.Filled.BatterySaver, R.string.onb_battery_title, R.string.onb_battery_body, true),
            OnbStep(Icons.Filled.Folder, R.string.onb_storage_title, R.string.onb_storage_body, true),
        )
    }
    var index by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }

    // Releer el estado de los permisos al volver de los ajustes del sistema.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    fun granted(i: Int): Boolean {
        tick.hashCode()
        return when (i) {
            1 -> Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                NotificationManagerCompat.from(context).areNotificationsEnabled()
            2 -> context.getSystemService(PowerManager::class.java)
                ?.isIgnoringBatteryOptimizations(context.packageName) == true
            3 -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
            else ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            else -> false
        }
    }

    val legacyStorage = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { tick++ }

    fun grant(i: Int) {
        try {
            when (i) {
                1 -> context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                )
                2 -> openBatterySettings(context)
                3 -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        context.startActivity(
                            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                                .setData(Uri.parse("package:${context.packageName}"))
                        )
                    } catch (_: Throwable) {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                    }
                } else {
                    legacyStorage.launch(
                        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    )
                }
            }
        } catch (_: Throwable) { }
    }

    val last = index == steps.lastIndex
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
            Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.End) {
                if (!last) TextButton(onClick = onFinish) { Text(stringResource(R.string.onb_skip)) }
            }
            AnimatedContent(targetState = index, modifier = Modifier.weight(1f).fillMaxWidth(), label = "step") { i ->
                val s = steps[i]
                val ok = s.grantable && granted(i)
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        if (ok) Icons.Filled.CheckCircle else s.icon,
                        contentDescription = null,
                        modifier = Modifier.size(96.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(stringResource(s.title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(s.body),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    if (s.grantable) {
                        Spacer(Modifier.height(24.dp))
                        if (ok) {
                            Text(stringResource(R.string.onb_done_step), color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.titleMedium)
                        } else {
                            OutlinedButton(onClick = { grant(i) }) { Text(stringResource(R.string.onb_grant)) }
                        }
                    }
                }
            }
            Text(
                stringResource(R.string.onb_step_of, index + 1, steps.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 12.dp),
            )
            Button(
                onClick = { if (last) onFinish() else index++ },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(stringResource(if (last) R.string.onb_finish else R.string.onb_next)) }
        }
    }
}
