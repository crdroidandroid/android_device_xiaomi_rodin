/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BatteryUnknown
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xiaomi.settings.R
import com.xiaomi.settings.ui.Motion
import com.xiaomi.settings.ui.PartsCategoryTitle
import com.xiaomi.settings.ui.PartsGroupShape
import com.xiaomi.settings.ui.PartsMetricCard
import com.xiaomi.settings.ui.PartsPageGutter
import com.xiaomi.settings.ui.PartsScaffold
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.delay

private data class BatterySnapshot(
    val level: Int,
    val status: Int,
    val health: Int,
    val plugged: Int,
    val tempTenthsC: Int,
    val voltageMv: Int,
    val cycleCount: Int,
    val technology: String,
)

private fun readSnapshot(context: Context): BatterySnapshot {
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    return BatterySnapshot(
        level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1,
        status = intent?.getIntExtra(
            BatteryManager.EXTRA_STATUS,
            BatteryManager.BATTERY_STATUS_UNKNOWN,
        ) ?: BatteryManager.BATTERY_STATUS_UNKNOWN,
        health = intent?.getIntExtra(
            BatteryManager.EXTRA_HEALTH,
            BatteryManager.BATTERY_HEALTH_UNKNOWN,
        ) ?: BatteryManager.BATTERY_HEALTH_UNKNOWN,
        plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0,
        tempTenthsC = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0,
        voltageMv = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0,
        cycleCount = intent?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1) ?: -1,
        technology = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "—",
    )
}

private fun BatteryManager.validLongProperty(id: Int): Long =
    runCatching { getLongProperty(id) }
        .getOrDefault(0L)
        .takeUnless { it == Long.MIN_VALUE } ?: 0L

@Composable
fun BatteryStatusScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val batteryManager = remember { context.getSystemService(BatteryManager::class.java) }
    var snapshot by remember { mutableStateOf(readSnapshot(context)) }
    var chargeCounterUah by remember { mutableLongStateOf(0L) }
    var currentUa by remember { mutableLongStateOf(0L) }

    fun refresh() {
        snapshot = readSnapshot(context)
        chargeCounterUah = batteryManager.validLongProperty(
            BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER,
        )
        currentUa = batteryManager.validLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
    }

    LaunchedEffect(Unit) {
        while (true) {
            refresh()
            delay(2_000)
        }
    }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) = refresh()
        }
        @Suppress("UnspecifiedRegisterReceiverFlag")
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        onDispose { context.unregisterReceiver(receiver) }
    }

    val level = snapshot.level.coerceIn(0, 100)
    val tempC = snapshot.tempTenthsC / 10f
    val isCharging = snapshot.plugged != 0
    val statusText = stringResource(statusLabel(snapshot.status))
    val voltage = if (snapshot.voltageMv > 0) {
        String.format(Locale.getDefault(), "%.3f V", snapshot.voltageMv / 1000.0)
    } else "—"
    val current = if (currentUa != 0L) {
        String.format(Locale.getDefault(), "%.0f mA", currentUa / 1000.0)
    } else "—"
    val power = if (snapshot.voltageMv > 0 && currentUa != 0L) {
        String.format(
            Locale.getDefault(),
            "%.2f W",
            snapshot.voltageMv * abs(currentUa).toDouble() / 1_000_000_000.0,
        )
    } else "—"

    PartsScaffold(
        title = stringResource(R.string.battery_status_title),
        onBack = onBack,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            item(key = "battery_hero") {
                BatteryHero(
                    level = level,
                    status = statusText,
                    temperature = String.format(Locale.getDefault(), "%.1f °C", tempC),
                    charging = isCharging,
                )
            }
            item(key = "battery_live_title") {
                PartsCategoryTitle(stringResource(R.string.battery_live_section))
            }
            item(key = "battery_live_1") {
                MetricRow(
                    first = Metric(
                        stringResource(R.string.battery_status),
                        statusText,
                        if (isCharging) Icons.Filled.Bolt else Icons.Filled.BatteryFull,
                    ),
                    second = Metric(
                        stringResource(R.string.battery_plug_type),
                        stringResource(plugLabel(snapshot.plugged)),
                        Icons.Filled.Cable,
                    ),
                )
            }
            item(key = "battery_live_2") {
                MetricRow(
                    first = Metric(
                        stringResource(R.string.battery_temperature),
                        String.format(Locale.getDefault(), "%.1f °C", tempC),
                        Icons.Filled.DeviceThermostat,
                        temperatureColor(tempC),
                    ),
                    second = Metric(
                        stringResource(R.string.battery_voltage),
                        voltage,
                        Icons.Filled.ElectricBolt,
                    ),
                )
            }
            item(key = "battery_live_3") {
                MetricRow(
                    first = Metric(
                        stringResource(R.string.battery_current_now),
                        current,
                        Icons.Filled.Bolt,
                    ),
                    second = Metric(
                        stringResource(R.string.battery_power),
                        power,
                        Icons.Filled.ElectricBolt,
                    ),
                )
            }
            item(key = "battery_live_4") {
                MetricRow(
                    first = Metric(
                        stringResource(R.string.battery_charge_counter),
                        if (chargeCounterUah > 0) {
                            String.format(Locale.getDefault(), "%.0f mAh", chargeCounterUah / 1000.0)
                        } else "—",
                        Icons.Filled.Memory,
                    ),
                    second = Metric(
                        stringResource(R.string.battery_technology),
                        snapshot.technology,
                        Icons.Filled.Info,
                    ),
                )
            }
            item(key = "battery_health_title") {
                PartsCategoryTitle(stringResource(R.string.battery_wear_section))
            }
            item(key = "battery_health") {
                val good = snapshot.health == BatteryManager.BATTERY_HEALTH_GOOD
                MetricRow(
                    first = Metric(
                        stringResource(R.string.battery_health),
                        stringResource(healthLabel(snapshot.health)),
                        when {
                            good -> Icons.Filled.Favorite
                            snapshot.health == BatteryManager.BATTERY_HEALTH_UNKNOWN ->
                                Icons.AutoMirrored.Filled.BatteryUnknown
                            else -> Icons.Filled.Dangerous
                        },
                        if (good) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    ),
                    second = Metric(
                        stringResource(R.string.battery_cycle_count),
                        snapshot.cycleCount.takeIf { it >= 0 }?.toString() ?: "—",
                        Icons.Filled.Replay,
                    ),
                )
            }
        }
    }
}

private data class Metric(
    val label: String,
    val value: String,
    val icon: ImageVector,
    val tint: Color? = null,
)

@Composable
private fun MetricRow(first: Metric, second: Metric) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PartsPageGutter, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PartsMetricCard(
            label = first.label,
            value = first.value,
            icon = first.icon,
            tint = first.tint ?: MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        PartsMetricCard(
            label = second.label,
            value = second.value,
            icon = second.icon,
            tint = second.tint ?: MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun BatteryHero(
    level: Int,
    status: String,
    temperature: String,
    charging: Boolean,
) {
    val progress by animateFloatAsState(
        targetValue = level / 100f,
        animationSpec = Motion.defaultEffectsSpec(),
        label = "batteryLevel",
    )
    val color = when {
        level <= 20 -> MaterialTheme.colorScheme.error
        level <= 40 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PartsPageGutter, vertical = 6.dp),
        shape = PartsGroupShape,
        color = MaterialTheme.colorScheme.surfaceBright,
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(112.dp),
                    color = color,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    strokeWidth = 12.dp,
                    strokeCap = StrokeCap.Round,
                )
                AnimatedContent(
                    targetState = charging,
                    transitionSpec = {
                        fadeIn(Motion.defaultEffectsSpec()) togetherWith
                            fadeOut(Motion.defaultEffectsSpec())
                    },
                    label = "batteryIcon",
                ) { isCharging ->
                    Icon(
                        imageVector = if (isCharging) {
                            Icons.Filled.BatteryChargingFull
                        } else {
                            Icons.Filled.BatteryFull
                        },
                        contentDescription = null,
                        modifier = Modifier.size(38.dp),
                        tint = color,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "$level%",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = status,
                    style = MaterialTheme.typography.titleMedium,
                    color = color,
                )
                Text(
                    text = temperature,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun temperatureColor(tempC: Float): Color = when {
    tempC < 30f -> MaterialTheme.colorScheme.primary
    tempC < 45f -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.error
}

private fun statusLabel(status: Int): Int = when (status) {
    BatteryManager.BATTERY_STATUS_CHARGING -> R.string.battery_status_charging
    BatteryManager.BATTERY_STATUS_DISCHARGING -> R.string.battery_status_discharging
    BatteryManager.BATTERY_STATUS_FULL -> R.string.battery_status_full
    BatteryManager.BATTERY_STATUS_NOT_CHARGING -> R.string.battery_status_not_charging
    else -> R.string.battery_status_unknown
}

private fun healthLabel(health: Int): Int = when (health) {
    BatteryManager.BATTERY_HEALTH_GOOD -> R.string.battery_health_good
    BatteryManager.BATTERY_HEALTH_OVERHEAT -> R.string.battery_health_overheat
    BatteryManager.BATTERY_HEALTH_DEAD -> R.string.battery_health_dead
    BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> R.string.battery_health_over_voltage
    BatteryManager.BATTERY_HEALTH_COLD -> R.string.battery_health_cold
    BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> R.string.battery_health_failure
    else -> R.string.battery_status_unknown
}

private fun plugLabel(plugged: Int): Int = when {
    plugged and BatteryManager.BATTERY_PLUGGED_AC != 0 -> R.string.battery_plug_ac
    plugged and BatteryManager.BATTERY_PLUGGED_USB != 0 -> R.string.battery_plug_usb
    plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0 -> R.string.battery_plug_wireless
    plugged and BatteryManager.BATTERY_PLUGGED_DOCK != 0 -> R.string.battery_plug_dock
    else -> R.string.battery_plug_none
}
