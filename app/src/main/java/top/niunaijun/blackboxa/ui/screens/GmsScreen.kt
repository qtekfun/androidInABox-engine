@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package top.niunaijun.blackboxa.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import top.niunaijun.blackboxa.ui.GMS_CORE
import top.niunaijun.blackboxa.ui.GMS_GSF
import top.niunaijun.blackboxa.ui.GMS_PACKAGES
import top.niunaijun.blackboxa.ui.GMS_STORE
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
    var filesFor by remember { mutableStateOf<Int?>(null) }
    val pkgs by vm.gmsPkgs.collectAsStateWithLifecycle()
    val errors by vm.gmsError.collectAsStateWithLifecycle()
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
                        pkgs = pkgs[bean.userID].orEmpty(),
                        error = errors[bean.userID],
                        onInstall = { confirmInstall = bean },
                        onFiles = { filesFor = bean.userID },
                        onRemove = { confirmRemove = bean },
                    )
                }
            }
        }
    }

    list.firstOrNull { it.userID == filesFor }?.let { b ->
        GmsFilesDialog(
            bean = b,
            pkgs = pkgs[b.userID].orEmpty(),
            busy = busy,
            onPick = { vm.installGmsFiles(b.userID, it) },
            onDismiss = { filesFor = null },
        )
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
private fun GmsCard(
    bean: GmsBean,
    pkgs: Set<String>,
    error: String?,
    enabled: Boolean,
    onInstall: () -> Unit,
    onFiles: () -> Unit,
    onRemove: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val complete = GMS_PACKAGES.all { it in pkgs }
    val partial = !complete && pkgs.isNotEmpty()
    val statusRes = when {
        complete -> R.string.gms_installed
        partial -> R.string.gms_partial
        else -> R.string.gms_not_installed
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(bean.userName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(statusRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (complete) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    if (complete) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (complete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                )
            }
            GmsPackageRows(pkgs)
            if (error != null) {
                Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!complete) {
                    Button(enabled = enabled, onClick = onInstall) { Text(stringResource(R.string.gms_install)) }
                }
                OutlinedButton(enabled = enabled, onClick = onFiles) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = null)
                    Text(stringResource(R.string.gms_install_files), Modifier.padding(start = 8.dp))
                }
                Spacer(Modifier.weight(1f))
                if (pkgs.isNotEmpty()) {
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.profile_menu_for, bean.userName))
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                enabled = enabled,
                                text = { Text(stringResource(R.string.gms_uninstall), color = MaterialTheme.colorScheme.error) },
                                onClick = { menu = false; onRemove() },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GmsPackageRows(pkgs: Set<String>) {
    val rows = listOf(
        GMS_GSF to R.string.gms_step_gsf,
        GMS_CORE to R.string.gms_step_core,
        GMS_STORE to R.string.gms_step_store,
    )
    rows.forEach { (pkg, label) ->
        val ok = pkg in pkgs
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (ok) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = stringResource(if (ok) R.string.gms_installed else R.string.gms_not_installed),
                modifier = Modifier.size(18.dp),
                tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            )
            Text(stringResource(label), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Flujo guiado: elegir los APK de GSF, GmsCore y Play Store (en cualquier orden o todos a la vez). */
@Composable
private fun GmsFilesDialog(
    bean: GmsBean,
    pkgs: Set<String>,
    busy: Boolean,
    onPick: (List<Uri>) -> Unit,
    onDismiss: () -> Unit,
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) onPick(uris)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.gms_files_title, bean.userName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(R.string.gms_files_body), style = MaterialTheme.typography.bodyMedium)
                GmsPackageRows(pkgs)
            }
        },
        confirmButton = {
            Button(enabled = !busy, onClick = { picker.launch("*/*") }) {
                Text(stringResource(R.string.gms_files_pick))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.gms_files_close)) } },
    )
}
