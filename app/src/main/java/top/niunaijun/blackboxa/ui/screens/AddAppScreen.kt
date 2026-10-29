@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package top.niunaijun.blackboxa.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
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
import top.niunaijun.blackboxa.ui.HostViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAppScreen(vm: HostViewModel, userId: Int, onBack: () -> Unit) {
    val installed by vm.installed.collectAsStateWithLifecycle()
    val loading by vm.installedLoading.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    val snack = remember { SnackbarHostState() }

    LaunchedEffect(userId) { vm.loadInstalled(userId) }
    LaunchedEffect(Unit) { vm.messages.collect { snack.showSnackbar(it) } }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) vm.install(uris, userId) { ok -> if (ok) onBack() }
    }

    Scaffold(
        topBar = { AiabTopBar(stringResource(R.string.add_title), onBack) },
        snackbarHost = { SnackbarHost(snack) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.add_tab_installed)) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.add_tab_files)) })
            }
            if (busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if (tab == 0) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.add_search_hint)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.ui_close))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
                val filtered = installed.filter {
                    query.isBlank() || it.name.contains(query, true) || it.packageName.contains(query, true)
                }
                Box(Modifier.fillMaxSize()) {
                    when {
                        loading && installed.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                        filtered.isEmpty() -> Text(
                            stringResource(R.string.add_no_results),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center),
                        )
                        else -> LazyColumn(Modifier.fillMaxSize()) {
                            items(filtered, key = { it.packageName }) { app ->
                                ListItem(
                                    modifier = Modifier.clickable(enabled = !app.alreadyAdded && !busy) {
                                        vm.install(app.packageName, userId) { ok -> if (ok) onBack() }
                                    },
                                    leadingContent = { AppIcon(app.icon, 44.dp) },
                                    headlineContent = { Text(app.name, maxLines = 1) },
                                    supportingContent = { Text(app.packageName, maxLines = 1) },
                                    trailingContent = {
                                        if (app.alreadyAdded) {
                                            Icon(
                                                Icons.Filled.CheckCircle,
                                                contentDescription = stringResource(R.string.add_already),
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    },
                                    colors = ListItemDefaults.colors(),
                                )
                            }
                        }
                    }
                }
            } else {
                EmptyState(
                    Icons.Filled.FolderOpen,
                    stringResource(R.string.add_files_title),
                    stringResource(R.string.add_files_body),
                    Modifier.fillMaxWidth(),
                ) {
                    Button(enabled = !busy, onClick = { picker.launch("*/*") }) {
                        Text(if (busy) stringResource(R.string.add_installing) else stringResource(R.string.add_files_pick))
                    }
                }
            }
        }
    }
}
