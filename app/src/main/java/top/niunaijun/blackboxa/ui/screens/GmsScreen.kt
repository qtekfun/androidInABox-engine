@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package top.niunaijun.blackboxa.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.niunaijun.blackboxa.R
import top.niunaijun.blackboxa.bean.GmsBean
import top.niunaijun.blackboxa.ui.HostViewModel

@Composable
fun GmsScreen(vm: HostViewModel, onBack: () -> Unit) {
    val list by vm.gms.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    var confirmInstall by remember { mutableStateOf<GmsBean?>(null) }
    var confirmRemove by remember { mutableStateOf<GmsBean?>(null) }
    val supported = remember { vm.gmsSupported }

    LaunchedEffect(Unit) { vm.loadGms() }
    LaunchedEffect(Unit) { vm.messages.collect { snack.showSnackbar(it) } }

    Scaffold(
        topBar = { AiabTopBar(stringResource(R.string.gms_title), onBack) },
        snackbarHost = { SnackbarHost(snack) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        stringResource(if (supported) R.string.gms_intro else R.string.gms_unsupported),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(list, key = { it.userID }) { bean ->
                    GmsCard(
                        bean = bean,
                        enabled = supported && !busy,
                        vm = vm,
                        onInstall = { confirmInstall = bean },
                        onRemove = { confirmRemove = bean },
                    )
                }
            }
        }
    }

    confirmInstall?.let { b ->
        ConfirmDialog(
            stringResource(R.string.gms_title),
            stringResource(R.string.gms_confirm_install, b.userName),
            stringResource(R.string.gms_install),
            onConfirm = { vm.installGms(b.userID) },
            onDismiss = { confirmInstall = null },
        )
    }
    confirmRemove?.let { b ->
        ConfirmDialog(
            stringResource(R.string.gms_title),
            stringResource(R.string.gms_confirm_uninstall, b.userName),
            stringResource(R.string.gms_uninstall),
            onConfirm = { vm.uninstallGms(b.userID) },
            onDismiss = { confirmRemove = null },
        )
    }
}

@Composable
private fun GmsCard(bean: GmsBean, enabled: Boolean, vm: HostViewModel, onInstall: () -> Unit, onRemove: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    var showFiles by remember { mutableStateOf(false) }
    val steps = listOf(R.string.gms_step_gsf, R.string.gms_step_core, R.string.gms_step_store)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.install(uri, bean.userID) { ok -> if (ok && step < steps.size) step++ }
    }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(bean.userName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(if (bean.isInstalledGms) R.string.gms_installed else R.string.gms_not_installed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (bean.isInstalledGms) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    if (bean.isInstalledGms) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (bean.isInstalledGms) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (bean.isInstalledGms) {
                    OutlinedButton(enabled = enabled, onClick = onRemove) { Text(stringResource(R.string.gms_uninstall)) }
                } else {
                    Button(enabled = enabled, onClick = onInstall) { Text(stringResource(R.string.gms_install)) }
                }
                TextButton(enabled = enabled, onClick = { showFiles = !showFiles }) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = null)
                    Text(stringResource(R.string.add_tab_files), Modifier.padding(start = 8.dp))
                }
            }
            if (showFiles) {
                steps.forEachIndexed { i, label ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Icon(
                            if (i < step) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                            contentDescription = if (i < step) stringResource(R.string.onb_done_step) else null,
                            tint = if (i < step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        )
                        Text(stringResource(label), Modifier.weight(1f).padding(start = 12.dp))
                        TextButton(enabled = enabled && i == step, onClick = { picker.launch("*/*") }) {
                            Text(stringResource(R.string.add_files_pick))
                        }
                    }
                }
            }
        }
    }
}
