@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package top.niunaijun.blackboxa.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import top.niunaijun.blackboxa.R
import top.niunaijun.blackboxa.ui.HostViewModel
import top.niunaijun.blackboxa.util.ContextUtil.openAppSystemSettings

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun SwitchRow(title: String, body: String, key: String, vm: HostViewModel) {
    var checked by remember(key) { mutableStateOf(vm.getSetting(key)) }
    ListItem(
        modifier = Modifier.clickable(role = Role.Switch) { checked = !checked; vm.setSetting(key, checked) },
        headlineContent = { Text(title) },
        supportingContent = { Text(body) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
    )
}

@Composable
fun SettingsScreen(
    vm: HostViewModel,
    onBack: () -> Unit,
    onGms: () -> Unit,
    onBattery: () -> Unit,
    onAbout: () -> Unit,
) {
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snack.showSnackbar(it) } }
    Scaffold(
        topBar = { AiabTopBar(stringResource(R.string.settings_title), onBack) },
        snackbarHost = { SnackbarHost(snack) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SectionTitle(stringResource(R.string.settings_section_privacy))
            SwitchRow(stringResource(R.string.settings_hide_root), stringResource(R.string.settings_hide_root_body), "root_hide", vm)
            SwitchRow(stringResource(R.string.settings_flag_secure), stringResource(R.string.settings_flag_secure_body), "disable_flag_secure", vm)
            SwitchRow(stringResource(R.string.settings_notif_prefix), stringResource(R.string.settings_notif_prefix_body), "notification_title_prefix", vm)
            SectionTitle(stringResource(R.string.settings_section_system))
            SwitchRow(stringResource(R.string.settings_daemon), stringResource(R.string.settings_daemon_body), "daemon_enable", vm)
            SwitchRow(stringResource(R.string.settings_vpn), stringResource(R.string.settings_vpn_body), "use_vpn_network", vm)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            ListItem(
                modifier = Modifier.clickable(onClick = onGms),
                leadingContent = { Icon(Icons.Filled.Cloud, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.settings_gms)) },
            )
            ListItem(
                modifier = Modifier.clickable(onClick = onBattery),
                leadingContent = { Icon(Icons.Filled.BatterySaver, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.settings_battery)) },
                supportingContent = { Text(stringResource(R.string.settings_battery_body)) },
            )
            ListItem(
                modifier = Modifier.clickable(onClick = onAbout),
                leadingContent = { Icon(Icons.Filled.Info, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.settings_about)) },
            )
        }
    }
}

@Composable
fun BatteryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val pm = context.getSystemService(PowerManager::class.java)
    val excluded = pm?.isIgnoringBatteryOptimizations(context.packageName) == true
    Scaffold(topBar = { AiabTopBar(stringResource(R.string.battery_title), onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.battery_intro), style = MaterialTheme.typography.bodyLarge)
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                    Text("1. " + stringResource(R.string.battery_step1))
                    Text("2. " + stringResource(R.string.battery_step2))
                    Text("3. " + stringResource(R.string.battery_step3))
                }
            }
            Button(onClick = { openBatterySettings(context) }) {
                Text(stringResource(if (excluded) R.string.battery_excluded else R.string.battery_open_exclusion))
            }
            OutlinedButton(onClick = { context.openAppSystemSettings() }) {
                Text(stringResource(R.string.battery_open_app))
            }
        }
    }
}

fun openBatterySettings(context: android.content.Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    } catch (_: Throwable) {
        context.openAppSystemSettings()
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val version = remember {
        try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "" } catch (_: Throwable) { "" }
    }
    Scaffold(topBar = { AiabTopBar(stringResource(R.string.about_title), onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.about_version, version), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.about_tagline), style = MaterialTheme.typography.bodyLarge)
            OutlinedButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/qtekfun/androidInABox-engine")))
            }) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                Text(stringResource(R.string.about_repo), Modifier.padding(start = 8.dp))
            }
            HorizontalDivider()
            Text(stringResource(R.string.about_licenses), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.about_newblackbox), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.about_other_libs), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
