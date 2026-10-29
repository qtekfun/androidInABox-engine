@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package top.niunaijun.blackboxa.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.TopAppBarDefaults
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.niunaijun.blackboxa.R
import top.niunaijun.blackboxa.bean.AppInfo
import top.niunaijun.blackboxa.ui.HostViewModel
import top.niunaijun.blackboxa.ui.UiApp
import top.niunaijun.blackboxa.util.ShortcutUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
    vm: HostViewModel,
    userId: Int,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onAppSettings: (String) -> Unit,
) {
    val apps by vm.apps.collectAsStateWithLifecycle()
    val loading by vm.appsLoading.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val name = profiles.firstOrNull { it.id == userId }?.name ?: vm.profileName(userId)
    val snack = remember { SnackbarHostState() }
    var confirmClear by remember { mutableStateOf<UiApp?>(null) }
    var confirmUninstall by remember { mutableStateOf<UiApp?>(null) }
    val scroll = TopAppBarDefaults.pinnedScrollBehavior()

    LaunchedEffect(userId) { vm.loadApps(userId); vm.refreshProfiles() }
    LaunchedEffect(Unit) { vm.messages.collect { snack.showSnackbar(it) } }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = { AiabTopBar(name, onBack, scroll) },
        snackbarHost = { SnackbarHost(snack) },
        floatingActionButton = {
            if (apps.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onAdd,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.apps_add)) },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loading && apps.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                apps.isEmpty() -> EmptyState(
                    Icons.Filled.Apps,
                    stringResource(R.string.apps_empty_title),
                    stringResource(R.string.apps_empty_body),
                    Modifier.align(Alignment.Center),
                ) {
                    Button(onClick = onAdd) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text(stringResource(R.string.apps_add), Modifier.padding(start = 8.dp))
                    }
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(92.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(apps, key = { it.packageName }) { app ->
                        AppTile(
                            app = app,
                            userId = userId,
                            vm = vm,
                            onSettings = { onAppSettings(app.packageName) },
                            onClear = { confirmClear = app },
                            onUninstall = { confirmUninstall = app },
                        )
                    }
                }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }

    confirmClear?.let { a ->
        ConfirmDialog(
            title = stringResource(R.string.app_clear_data),
            text = stringResource(R.string.app_confirm_clear, a.name),
            confirmLabel = stringResource(R.string.app_delete),
            onConfirm = { vm.clearData(a.packageName, userId) },
            onDismiss = { confirmClear = null },
        )
    }
    confirmUninstall?.let { a ->
        ConfirmDialog(
            title = stringResource(R.string.app_uninstall),
            text = stringResource(R.string.app_confirm_uninstall, a.name),
            confirmLabel = stringResource(R.string.app_uninstall),
            onConfirm = { vm.uninstall(a.packageName, userId) },
            onDismiss = { confirmUninstall = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppTile(
    app: UiApp,
    userId: Int,
    vm: HostViewModel,
    onSettings: () -> Unit,
    onClear: () -> Unit,
    onUninstall: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val optionsLabel = stringResource(R.string.app_menu_for, app.name)

    Box {
        Column(
            modifier = Modifier
                .heightIn(min = 96.dp)
                .semantics { customActions = listOf(CustomAccessibilityAction(optionsLabel) { menu = true; true }) }
                .combinedClickable(
                    onClickLabel = stringResource(R.string.app_open),
                    onLongClickLabel = optionsLabel,
                    onClick = { vm.launch(app.packageName, userId) },
                    onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menu = true },
                )
                .padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppIcon(app.icon, 56.dp)
            Text(
                app.name,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.app_open)) },
                leadingIcon = { Icon(Icons.Filled.PlayArrow, null) },
                onClick = { menu = false; vm.launch(app.packageName, userId) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.app_details)) },
                leadingIcon = { Icon(Icons.Filled.Settings, null) },
                onClick = { menu = false; onSettings() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.app_stop_action)) },
                leadingIcon = { Icon(Icons.Filled.Stop, null) },
                onClick = { menu = false; vm.stop(app.packageName, userId) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.app_shortcut_action)) },
                leadingIcon = { Icon(Icons.Filled.Home, null) },
                onClick = {
                    menu = false
                    createShortcut(context, userId, app)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.app_clear_data)) },
                leadingIcon = { Icon(Icons.Filled.CleaningServices, null) },
                onClick = { menu = false; onClear() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.app_uninstall), color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
                onClick = { menu = false; onUninstall() },
            )
        }
    }
}

private fun createShortcut(context: android.content.Context, userId: Int, app: UiApp) {
    try {
        val drawable = top.niunaijun.blackbox.BlackBoxCore.getPackageManager()
            .getApplicationIcon(app.packageName.let {
                top.niunaijun.blackbox.BlackBoxCore.get().getInstalledApplications(0, userId)
                    .first { a -> a.packageName == it }
            })
        val info = AppInfo(app.name, drawable, app.packageName, app.sourceDir, false)
        ShortcutUtil.createShortcut(context, userId, info)
    } catch (t: Throwable) {
        Toast.makeText(context, R.string.cannot_create_shortcut, Toast.LENGTH_SHORT).show()
    }
}
