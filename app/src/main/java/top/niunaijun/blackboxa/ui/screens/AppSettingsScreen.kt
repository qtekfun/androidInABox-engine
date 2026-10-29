@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package top.niunaijun.blackboxa.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhonelinkSetup
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.niunaijun.blackboxa.R
import top.niunaijun.blackboxa.ui.HostViewModel

@Composable
fun AppSettingsScreen(vm: HostViewModel, userId: Int, pkg: String, onBack: () -> Unit) {
    val apps = vm.apps.collectAsStateWithLifecycle().value
    val app = apps.firstOrNull { it.packageName == pkg }
    val context = LocalContext.current
    val profile = vm.profileName(userId)
    var spoof by remember(pkg, userId) { mutableStateOf(vm.isSpoofEnabled(pkg, userId)) }

    Scaffold(topBar = { AiabTopBar(stringResource(R.string.appsettings_title), onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            ListItem(
                leadingContent = { AppIcon(app?.icon, 56.dp) },
                headlineContent = { Text(app?.name ?: pkg, style = MaterialTheme.typography.titleLarge) },
                supportingContent = {
                    Column {
                        Text("${stringResource(R.string.appsettings_package)}: $pkg")
                        Text("${stringResource(R.string.appsettings_profile)}: $profile")
                    }
                },
            )
            ListItem(
                modifier = Modifier.clickable {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    )
                },
                leadingContent = { Icon(Icons.Filled.Notifications, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.appsettings_notifications)) },
                supportingContent = { Text(stringResource(R.string.appsettings_notifications_body)) },
            )
            ListItem(
                leadingContent = { Icon(Icons.Filled.PhonelinkSetup, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.appsettings_spoof)) },
                supportingContent = { Text(stringResource(R.string.appsettings_spoof_body)) },
                trailingContent = {
                    Switch(checked = spoof, onCheckedChange = { spoof = it; vm.setSpoofEnabled(pkg, userId, it) })
                },
            )
        }
    }
}
