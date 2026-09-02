/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.monitor

import android.app.ActivityManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.xiaomi.settings.R
import com.xiaomi.settings.ui.Motion
import com.xiaomi.settings.ui.PartsGroupShape
import com.xiaomi.settings.ui.PartsInfoCard
import com.xiaomi.settings.ui.PartsMetricCard
import com.xiaomi.settings.ui.PartsPageGutter
import com.xiaomi.settings.ui.PartsPreference
import com.xiaomi.settings.ui.PartsPreferenceGroup
import com.xiaomi.settings.ui.PartsScaffold
import com.xiaomi.settings.utils.PartsToast
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun MemoryMonitorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val activityManager = remember { context.getSystemService(ActivityManager::class.java) }
    val sampler = remember { ProcessSampler(context) }
    var memory by remember { mutableStateOf<MemorySnapshot?>(null) }
    var processes by remember { mutableStateOf<List<ProcessSnapshot>>(emptyList()) }

    LaunchedEffect(Unit) {
        while (true) {
            val sampled = withContext(Dispatchers.IO) {
                readMemorySnapshot() to sampler.sample()
            }
            memory = sampled.first
            processes = sampled.second
            delay(2_000)
        }
    }

    val ownUid = android.os.Process.myUid()

    PartsScaffold(
        title = stringResource(R.string.memory_title),
        onBack = onBack,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            item(key = "memory_overview") {
                if (memory != null) {
                    MemoryOverview(memory!!)
                } else {
                    PartsInfoCard(body = stringResource(R.string.memory_unavailable))
                }
            }

            memory?.let { snapshot ->
                item(key = "memory_metrics_1") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = PartsPageGutter, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PartsMetricCard(
                            label = stringResource(R.string.memory_total),
                            value = formatMemory(snapshot.totalKb),
                            icon = Icons.Filled.Memory,
                            modifier = Modifier.weight(1f),
                        )
                        PartsMetricCard(
                            label = stringResource(R.string.memory_available),
                            value = formatMemory(snapshot.availableKb),
                            icon = Icons.Filled.Speed,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                item(key = "memory_metrics_2") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = PartsPageGutter, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PartsMetricCard(
                            label = stringResource(R.string.memory_cached),
                            value = formatMemory(snapshot.cachedKb),
                            icon = Icons.Filled.SdStorage,
                            modifier = Modifier.weight(1f),
                        )
                        PartsMetricCard(
                            label = stringResource(R.string.memory_swap),
                            value = if (snapshot.swapTotalKb > 0) {
                                formatMemory(snapshot.swapFreeKb)
                            } else "—",
                            icon = Icons.Filled.SwapVert,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (snapshot.usedKb > snapshot.totalKb * 0.9) {
                    item(key = "memory_warning") {
                        PartsInfoCard(
                            body = stringResource(R.string.memory_low_warning),
                            icon = Icons.Filled.Warning,
                            accent = true,
                        )
                    }
                }
            }

            item(key = "memory_processes") {
                PartsPreferenceGroup(title = stringResource(R.string.memory_processes)) {
                    if (processes.isEmpty()) {
                        PartsPreference(title = stringResource(R.string.memory_no_processes))
                    } else {
                        processes.forEach { process ->
                            val killable = process.uid != ownUid && process.uid != 0 &&
                                !process.name.startsWith("com.xiaomi.settings")
                            val appIcon = processAppIcon(process.packageName)
                            PartsPreference(
                                title = process.name,
                                summary = stringResource(
                                    R.string.memory_process_summary,
                                    formatMemory(process.pssKb),
                                    formatMemory(process.rssKb),
                                    String.format(Locale.getDefault(), "%.1f", process.cpuPercent),
                                ),
                                icon = Icons.Filled.Apps,
                                iconBitmap = appIcon,
                                trailing = if (killable) {
                                    {
                                        IconButton(
                                            onClick = {
                                                runCatching {
                                                    activityManager.killBackgroundProcesses(
                                                        process.name.substringBefore(':'),
                                                    )
                                                }
                                                PartsToast.show(
                                                    context,
                                                    R.string.memory_process_stopped,
                                                )
                                            },
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Close,
                                                contentDescription = stringResource(R.string.memory_stop),
                                            )
                                        }
                                    }
                                } else null,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun processAppIcon(packageName: String): ImageBitmap? {
    val context = LocalContext.current
    var bitmap by remember(packageName) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(packageName) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager
                    .getApplicationIcon(packageName)
                    .toBitmap()
                    .asImageBitmap()
            }.getOrNull()
        }
    }

    return bitmap
}

@Composable
private fun MemoryOverview(memory: MemorySnapshot) {
    val fraction = if (memory.totalKb > 0) {
        memory.usedKb.toFloat() / memory.totalKb
    } else 0f
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = Motion.defaultEffectsSpec(),
        label = "memoryUsage",
    )
    val color = when {
        fraction > 0.9f -> MaterialTheme.colorScheme.error
        fraction > 0.7f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PartsPageGutter, vertical = 6.dp),
        shape = PartsGroupShape,
        color = MaterialTheme.colorScheme.surfaceBright,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.memory_used),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${formatMemory(memory.usedKb)} / ${formatMemory(memory.totalKb)}",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Text(
                text = stringResource(R.string.memory_free_summary, formatMemory(memory.freeKb)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { animated },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(CircleShape),
                color = color,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                strokeCap = StrokeCap.Round,
            )
        }
    }
}
