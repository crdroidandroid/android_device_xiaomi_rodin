/*
 * SPDX-FileCopyrightText: 2025 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.thermal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import com.xiaomi.settings.PartsCategory
import com.xiaomi.settings.R
import com.xiaomi.settings.ui.Motion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class InstalledAppEntry(val packageName: String, val label: String)

@Composable
private fun appIcon(packageName: String): ImageBitmap? {
    val context = LocalContext.current
    var bitmap by remember(packageName) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(packageName) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getApplicationIcon(packageName).toBitmap().asImageBitmap()
            }.getOrNull()
        }
    }
    return bitmap
}

private fun readIsCharging(context: Context): Boolean {
    val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    return (sticky?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
}

@Composable
@ReadOnlyComposable
private fun groupedShape(index: Int, total: Int): Shape {
    val outer = MaterialTheme.shapes.extraLarge
    val inner = MaterialTheme.shapes.extraSmall
    return when {
        total == 1         -> outer
        index == 0         -> outer.copy(bottomStart = inner.bottomStart, bottomEnd = inner.bottomEnd)
        index == total - 1 -> outer.copy(topStart = inner.topStart, topEnd = inner.topEnd)
        else               -> inner
    }
}

@Composable
private fun InfoCard(
    title:          String? = null,
    body:           String,
    icon:           @Composable (() -> Unit)? = null,
    iconTint:       Color = MaterialTheme.colorScheme.onSurfaceVariant,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    modifier:       Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape    = MaterialTheme.shapes.extraLarge,
        colors   = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Row(
            modifier          = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Box(
                    modifier         = Modifier.padding(end = 16.dp).size(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    icon()
                }
            }
            Column {
                if (title != null) {
                    Text(
                        text  = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = iconTint,
                    )
                }
                Text(
                    text       = body,
                    style      = MaterialTheme.typography.bodyMedium,
                    color      = iconTint,
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.3f,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun ThermalManagementScreen(onBack: () -> Unit) {
    val context      = LocalContext.current
    val thermalUtils = remember { ThermalUtils.getInstance(context) }
    val scope        = rememberCoroutineScope()

    var thermalEnabled   by remember { mutableStateOf(thermalUtils.enabled) }
    var appList          by remember { mutableStateOf<List<AppThermalEntry>>(emptyList()) }
    var isCharging       by remember { mutableStateOf(readIsCharging(context)) }
    var showResetDialog  by remember { mutableStateOf(false) }
    var pendingApp       by remember { mutableStateOf<AppThermalEntry?>(null) }
    var showAddSheet     by remember { mutableStateOf(false) }
    var pendingNewApp    by remember { mutableStateOf<InstalledAppEntry?>(null) }

    val controlsEnabled = thermalEnabled && !isCharging
    val addSheetState   = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                isCharging = (intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) != 0
            }
        }
        @Suppress("UnspecifiedRegisterReceiverFlag")
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        onDispose { context.unregisterReceiver(receiver) }
    }

    LaunchedEffect(Unit) {
        appList = withContext(Dispatchers.IO) {
            runCatching { ThermalService.getAppList(context) }.getOrDefault(emptyList())
        }
    }

    val profiles         = remember { ThermalService.profiles() }
    val horizontalGutter = 20.dp

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text     = stringResource(R.string.thermal_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.navigate_up))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        floatingActionButton = {
            if (controlsEnabled) {
                FloatingActionButton(
                    onClick        = { showAddSheet = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor   = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.thermal_add_app))
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier       = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top    = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 88.dp,
            ),
        ) {
            item(key = "charging-banner") {
                AnimatedVisibility(
                    visible = isCharging,
                    enter   = expandVertically(animationSpec = Motion.defaultEffectsSpec()) + fadeIn(),
                    exit    = shrinkVertically(animationSpec = Motion.defaultEffectsSpec()) + fadeOut(),
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = horizontalGutter)
                            .padding(bottom = 8.dp),
                        shape  = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    ) {
                        Row(
                            modifier              = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Icon(
                                imageVector        = Icons.Filled.BatteryChargingFull,
                                contentDescription = null,
                                tint               = MaterialTheme.colorScheme.primary,
                                modifier           = Modifier.size(24.dp),
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text  = stringResource(R.string.thermal_charging_toast_connected),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text  = stringResource(R.string.thermal_charging_banner_body),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }

            item(key = "enable+info") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = horizontalGutter)
                        .padding(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Card(
                        onClick  = {
                            if (!isCharging) {
                                thermalEnabled = !thermalEnabled
                                thermalUtils.enabled = thermalEnabled
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape    = MaterialTheme.shapes.extraLarge,
                        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    ) {
                        ListItem(
                            modifier = Modifier.alpha(if (isCharging) 0.38f else 1f),
                            headlineContent = {
                                Text(
                                    text  = stringResource(R.string.thermal_enable_title),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked         = thermalEnabled,
                                    enabled         = !isCharging,
                                    onCheckedChange = { checked ->
                                        thermalEnabled = checked
                                        thermalUtils.enabled = checked
                                    },
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }

                    InfoCard(
                        body = stringResource(R.string.thermal_enable_summary),
                        icon = {
                            Icon(
                                imageVector        = Icons.Filled.Info,
                                contentDescription = null,
                                tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier           = Modifier.size(20.dp),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item(key = "reset-card") {
                Card(
                    onClick  = { if (controlsEnabled) showResetDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = horizontalGutter)
                        .padding(bottom = 16.dp),
                    shape  = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    ListItem(
                        modifier = Modifier.alpha(if (controlsEnabled) 1f else 0.38f),
                        headlineContent = {
                            Text(
                                text  = stringResource(R.string.thermal_reset_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        supportingContent = {
                            Text(
                                text  = stringResource(R.string.thermal_reset_summary),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector        = Icons.Filled.RestartAlt,
                                contentDescription = null,
                                tint               = MaterialTheme.colorScheme.error,
                                modifier           = Modifier.size(24.dp),
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }

            item(key = "per-app-label") {
                PartsCategory(stringResource(R.string.thermal_per_app_label))
            }

            if (appList.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text     = stringResource(R.string.thermal_no_apps),
                        style    = MaterialTheme.typography.bodyLarge,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp),
                    )
                }
            } else {
                itemsIndexed(
                    items = appList,
                    key   = { _, app -> app.packageName },
                ) { index, app ->
                    val shape     = groupedShape(index, appList.size)
                    val bottomPad = if (index == appList.lastIndex) 0.dp else 2.dp
                    AppThermalCard(
                        entry    = app,
                        enabled  = controlsEnabled,
                        shape    = shape,
                        modifier = Modifier
                            .padding(horizontal = horizontalGutter)
                            .padding(bottom = bottomPad),
                        onClick  = { if (controlsEnabled) pendingApp = app },
                    )
                }
            }
        }
    }

    if (showAddSheet) {
        AppPickerSheet(
            sheetState        = addSheetState,
            alreadyOverridden = appList.map { it.packageName }.toSet(),
            onDismiss         = {
                scope.launch { addSheetState.hide() }.invokeOnCompletion { showAddSheet = false }
            },
            onAppSelected     = { picked ->
                scope.launch { addSheetState.hide() }.invokeOnCompletion {
                    showAddSheet  = false
                    pendingNewApp = picked
                }
            },
        )
    }

    pendingNewApp?.let { newApp ->
        var tempProfile by remember(newApp.packageName) { mutableIntStateOf(ThermalUtils.ThermalState.DEFAULT.id) }
        ProfilePickerDialog(
            title      = newApp.label,
            profiles   = profiles,
            selectedId = tempProfile,
            onSelect   = { tempProfile = it },
            onDismiss  = { pendingNewApp = null },
            onConfirm  = {
                if (tempProfile != ThermalUtils.ThermalState.DEFAULT.id) {
                    runCatching {
                        ThermalService.setAppProfile(context, newApp.packageName, tempProfile)
                        val newEntry = AppThermalEntry(
                            packageName = newApp.packageName,
                            label       = newApp.label,
                            profileId   = tempProfile,
                        )
                        appList = (appList + newEntry).sortedBy { it.label.lowercase() }
                    }
                }
                pendingNewApp = null
            },
        )
    }

    pendingApp?.let { app ->
        var tempProfile by remember(app.packageName) { mutableIntStateOf(app.profileId) }
        ProfilePickerDialog(
            title      = app.label,
            profiles   = profiles,
            selectedId = tempProfile,
            onSelect   = { tempProfile = it },
            onDismiss  = { pendingApp = null },
            onConfirm  = {
                runCatching {
                    ThermalService.setAppProfile(context, app.packageName, tempProfile)
                    appList = if (tempProfile == ThermalUtils.ThermalState.DEFAULT.id) {
                        appList.filter { it.packageName != app.packageName }
                    } else {
                        appList.map {
                            if (it.packageName == app.packageName) it.copy(profileId = tempProfile) else it
                        }
                    }
                }
                pendingApp = null
            },
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            icon = {
                Icon(imageVector = Icons.Filled.RestartAlt, contentDescription = null)
            },
            title = {
                Text(
                    text  = stringResource(R.string.thermal_reset_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
            },
            text = {
                Text(
                    text  = stringResource(R.string.thermal_reset_confirm_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        runCatching {
                            ThermalUtils.getInstance(context).resetProfiles()
                            appList = emptyList()
                        }
                        showResetDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.thermal_reset_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppPickerSheet(
    sheetState:        androidx.compose.material3.SheetState,
    alreadyOverridden: Set<String>,
    onDismiss:         () -> Unit,
    onAppSelected:     (InstalledAppEntry) -> Unit,
) {
    val context       = LocalContext.current
    var query         by remember { mutableStateOf("") }
    var installedApps by remember { mutableStateOf<List<InstalledAppEntry>>(emptyList()) }

    LaunchedEffect(Unit) {
        installedApps = withContext(Dispatchers.IO) {
            val pm    = context.packageManager
            val flags = PackageManager.GET_META_DATA
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(intent, flags)
                .map { it.activityInfo.packageName }
                .distinct()
                .filter { it !in alreadyOverridden }
                .mapNotNull { pkg ->
                    runCatching {
                        val info = pm.getApplicationInfo(pkg, 0)
                        InstalledAppEntry(pkg, pm.getApplicationLabel(info).toString())
                    }.getOrNull()
                }
                .sortedBy { it.label.lowercase() }
        }
    }

    val filtered = remember(query, installedApps) {
        if (query.isBlank()) installedApps
        else installedApps.filter {
            it.label.contains(query, ignoreCase = true) ||
            it.packageName.contains(query, ignoreCase = true)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        dragHandle       = null,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text     = stringResource(R.string.thermal_add_app),
                style    = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            )
            OutlinedTextField(
                value         = query,
                onValueChange = { query = it },
                placeholder   = { Text(stringResource(R.string.thermal_search_apps)) },
                leadingIcon   = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine    = true,
                shape         = MaterialTheme.shapes.extraLarge,
                modifier      = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp),
            )
            LazyColumn(
                modifier       = Modifier.heightIn(max = 480.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(filtered, key = { it.packageName }) { app ->
                    val icon = appIcon(app.packageName)
                    ListItem(
                        modifier = Modifier.clickable { onAppSelected(app) },
                        leadingContent = {
                            if (icon != null) {
                                Image(
                                    bitmap             = icon,
                                    contentDescription = null,
                                    modifier           = Modifier.size(40.dp).clip(CircleShape),
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector        = Icons.Filled.Apps,
                                        contentDescription = null,
                                        modifier           = Modifier.size(24.dp),
                                    )
                                }
                            }
                        },
                        headlineContent = {
                            Text(
                                text     = app.label,
                                style    = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        supportingContent = {
                            Text(
                                text     = app.packageName,
                                style    = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        }
    }
}

@Composable
private fun AppThermalCard(
    entry:    AppThermalEntry,
    enabled:  Boolean,
    shape:    Shape,
    modifier: Modifier = Modifier,
    onClick:  () -> Unit,
) {
    val context   = LocalContext.current
    val icon      = appIcon(entry.packageName)
    val isDefault = entry.profileId == ThermalUtils.ThermalState.DEFAULT.id

    Card(
        onClick  = onClick,
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.38f),
        shape    = shape,
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        enabled  = enabled,
    ) {
        Row(
            modifier          = Modifier.fillMaxWidth().heightIn(min = 72.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier              = Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (icon != null) {
                    Image(
                        bitmap             = icon,
                        contentDescription = null,
                        modifier           = Modifier.size(40.dp).clip(CircleShape),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector        = Icons.Filled.Apps,
                            contentDescription = null,
                            modifier           = Modifier.size(24.dp),
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text     = entry.label,
                        style    = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text     = entry.packageName,
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            VerticalDivider(
                modifier = Modifier.height(32.dp),
                color    = MaterialTheme.colorScheme.outlineVariant,
            )
            Box(
                modifier         = Modifier.width(100.dp).padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text     = ThermalService.profileLabel(context, entry.profileId),
                    style    = MaterialTheme.typography.labelLarge,
                    color    = if (isDefault) MaterialTheme.colorScheme.onSurfaceVariant
                               else          MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ProfilePickerDialog(
    title:      String,
    profiles:   List<ThermalUtils.ThermalState>,
    selectedId: Int,
    onSelect:   (Int) -> Unit,
    onDismiss:  () -> Unit,
    onConfirm:  () -> Unit,
) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape  = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)) {
                Text(
                    text     = title,
                    style    = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer(Modifier.height(16.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(max = 336.dp),
                ) {
                    items(profiles) { profile ->
                        val selected = profile.id == selectedId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.RadioButton) { onSelect(profile.id) }
                                .padding(vertical = 14.dp, horizontal = 24.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            RadioButton(selected = selected, onClick = null)
                            Text(
                                text  = context.getString(profile.label),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier              = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onConfirm) { Text(stringResource(R.string.thermal_apply)) }
                }
            }
        }
    }
}
