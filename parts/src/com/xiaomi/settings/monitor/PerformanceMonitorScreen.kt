/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.monitor

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiaomi.settings.R
import com.xiaomi.settings.ui.Motion
import com.xiaomi.settings.ui.MonitorBar
import com.xiaomi.settings.ui.PageGutter
import com.xiaomi.settings.ui.SectionHeader
import com.xiaomi.settings.ui.groupedCardShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerformanceMonitorScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    var clusters   by remember { mutableStateOf<List<CpuCluster>>(emptyList()) }
    var cpuLoad    by remember { mutableStateOf(0f) }
    var loadAvg    by remember { mutableStateOf<Triple<Float, Float, Float>?>(null) }
    var uptime     by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        val sampler = CpuLoadSampler()
        while (true) {
            val (clustersNow, loadNow, loadAvgNow, uptimeNow) =
                withContext(Dispatchers.IO) {
                    val c  = readCpuClusters()
                    val l  = sampler.sample()
                    MonitorData(
                        clusters = c,
                        cpuLoad  = l,
                        loadAvg  = readLoadAvg(),
                        uptime   = readUptimeSeconds() ?: 0L,
                    )
                }
            clusters = clustersNow
            cpuLoad  = loadNow
            loadAvg  = loadAvgNow
            uptime   = uptimeNow
            delay(1_000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text     = stringResource(R.string.performance_title),
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
    ) { innerPadding ->
        LazyColumn(
            modifier       = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top    = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 32.dp,
            ),
        ) {
            item(key = "overview") {
                OverviewCard(
                    cpuLoad = cpuLoad,
                    loadAvg = loadAvg,
                    uptime  = uptime,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PageGutter)
                        .padding(bottom = 8.dp),
                )
            }

            item(key = "cpu-label") { SectionHeader(stringResource(R.string.monitor_cpu_section)) }
            if (clusters.isEmpty()) {
                item(key = "cpu-empty") { UnavailableCard() }
            } else {
                items(
                    items = clusters,
                    key   = { "cluster-${it.name}" },
                ) { cluster ->
                    val index = clusters.indexOf(cluster)
                    MonitorClusterCard(
                        cluster = cluster,
                        shape   = groupedCardShape(index, clusters.size),
                        modifier = Modifier
                            .padding(horizontal = PageGutter)
                            .padding(bottom = if (index == clusters.lastIndex) 0.dp else 2.dp),
                    )
                }
            }
        }
    }
}

private data class MonitorData(
    val clusters: List<CpuCluster>,
    val cpuLoad:  Float,
    val loadAvg:  Triple<Float, Float, Float>?,
    val uptime:   Long,
)

@Composable
private fun OverviewCard(
    cpuLoad:  Float,
    loadAvg:  Triple<Float, Float, Float>?,
    uptime:   Long,
    modifier: Modifier = Modifier,
) {
    val fraction by animateFloatAsState(
        targetValue = cpuLoad,
        animationSpec = Motion.liveValueSpec(),
        label = "cpuLoadRing",
    )
    val loadColor = when {
        cpuLoad < 0.5f -> MaterialTheme.colorScheme.primary
        cpuLoad < 0.85f -> MaterialTheme.colorScheme.tertiary
        else            -> MaterialTheme.colorScheme.error
    }

    Card(
        modifier = modifier,
        shape    = MaterialTheme.shapes.extraLarge,
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress    = { fraction },
                    modifier    = Modifier.size(112.dp),
                    color       = loadColor,
                    trackColor  = MaterialTheme.colorScheme.surfaceContainerHighest,
                    strokeWidth = 13.dp,
                    strokeCap   = StrokeCap.Round,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text  = "${(cpuLoad * 100).toInt()}%",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text  = stringResource(R.string.monitor_cpu_load),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text  = stringResource(R.string.monitor_load_average),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text  = loadAvg?.let { "%.2f  %.2f  %.2f".format(it.first, it.second, it.third) }
                        ?: "—",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text  = stringResource(R.string.monitor_uptime),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text  = formatUptime(uptime),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun MonitorClusterCard(
    cluster:  CpuCluster,
    shape:    androidx.compose.ui.graphics.Shape,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape    = shape,
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MonitorBar(
                label   = cluster.name,
                valueText = formatFrequency(cluster.curFreqKhz),
                fraction = cluster.loadFraction,
                secondaryText = stringResource(
                    R.string.monitor_freq_range,
                    formatFrequency(cluster.minFreqKhz),
                    formatFrequency(cluster.maxFreqKhz),
                ),
            )
            Text(
                text  = stringResource(R.string.monitor_governor, cluster.governor),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GpuCard(
    gpuFreq:  Long?,
    gpuLoad:  Float?,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape    = MaterialTheme.shapes.extraLarge,
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            gpuLoad?.let { load ->
                MonitorBar(
                    label   = stringResource(R.string.monitor_gpu_load),
                    valueText = "${(load * 100).toInt()}%",
                    fraction = load,
                )
            }
            gpuFreq?.let { freq ->
                Text(
                    text  = stringResource(R.string.monitor_gpu_freq, formatFrequency(freq)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun UnavailableCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PageGutter),
        shape    = MaterialTheme.shapes.extraLarge,
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Text(
            text  = stringResource(R.string.monitor_unavailable),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
}
