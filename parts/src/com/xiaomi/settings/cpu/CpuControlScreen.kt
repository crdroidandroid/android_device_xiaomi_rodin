/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.cpu

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xiaomi.settings.R
import com.xiaomi.settings.ui.Motion
import com.xiaomi.settings.ui.PartsInfoCard
import com.xiaomi.settings.ui.PartsPreference
import com.xiaomi.settings.ui.PartsPreferenceGroup
import com.xiaomi.settings.ui.PartsScaffold
import com.xiaomi.settings.ui.PartsSelectionPreference
import com.xiaomi.settings.ui.PartsSwitchPreference
import com.xiaomi.settings.utils.PartsToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun CpuControlScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val manager = remember(context) { CpuControlManager(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(manager.readState()) }
    var applying by remember { mutableStateOf(false) }
    var editingPolicy by remember { mutableStateOf<CpuPolicyState?>(null) }
    var showThrottleWarning by remember { mutableStateOf(false) }

    fun runCpuOperation(operation: () -> Boolean) {
        if (applying) return
        scope.launch {
            applying = true
            val success = withContext(Dispatchers.IO) { operation() }
            state = withContext(Dispatchers.IO) { manager.readState() }
            applying = false
            if (!success) PartsToast.show(context, R.string.cpu_apply_failed)
        }
    }

    PartsScaffold(
        title = stringResource(R.string.cpu_control_title),
        onBack = onBack,
        actions = {
            IconButton(
                onClick = {
                    scope.launch {
                        state = withContext(Dispatchers.IO) { manager.readState() }
                    }
                },
                enabled = !applying,
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.cpu_refresh),
                )
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 6.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            item(key = "description") {
                PartsInfoCard(
                    body = stringResource(R.string.cpu_control_summary),
                    icon = Icons.Filled.Memory,
                )
            }

            item(key = "profiles") {
                PartsPreferenceGroup(title = stringResource(R.string.cpu_profiles_category)) {
                    if (state.profile == CpuProfile.CUSTOM) {
                        PartsPreference(
                            icon = Icons.Filled.Tune,
                            title = stringResource(R.string.cpu_profile_custom),
                            summary = stringResource(R.string.cpu_profile_custom_summary),
                            selected = true,
                        )
                    }
                    ProfilePreference(
                        profile = CpuProfile.ECONOMY,
                        selected = state.profile == CpuProfile.ECONOMY,
                        onClick = { runCpuOperation { manager.selectProfile(CpuProfile.ECONOMY) } },
                    )
                    ProfilePreference(
                        profile = CpuProfile.NORMAL,
                        selected = state.profile == CpuProfile.NORMAL,
                        onClick = { runCpuOperation { manager.selectProfile(CpuProfile.NORMAL) } },
                    )
                    ProfilePreference(
                        profile = CpuProfile.BOOST,
                        selected = state.profile == CpuProfile.BOOST,
                        onClick = { runCpuOperation { manager.selectProfile(CpuProfile.BOOST) } },
                    )
                    ProfilePreference(
                        profile = CpuProfile.EXTREME,
                        selected = state.profile == CpuProfile.EXTREME,
                        onClick = { runCpuOperation { manager.selectProfile(CpuProfile.EXTREME) } },
                    )
                }
            }

            if (state.profile == CpuProfile.EXTREME) {
                item(key = "extreme_warning") {
                    PartsInfoCard(
                        title = stringResource(R.string.cpu_extreme_warning_title),
                        body = stringResource(R.string.thermal_disable_warning_body),
                        icon = Icons.Filled.LocalFireDepartment,
                        accent = true,
                    )
                }
            }

            item(key = "thermal-throttling") {
                PartsPreferenceGroup(title = stringResource(R.string.thermal_safety_label)) {
                    PartsSwitchPreference(
                        title = stringResource(R.string.thermal_disable_throttling_title),
                        summary = stringResource(R.string.thermal_disable_throttling_summary),
                        checked = state.thermalThrottlingDisabled,
                        icon = Icons.Filled.Warning,
                        enabled = !applying && state.profile != CpuProfile.EXTREME,
                        onCheckedChange = { disabled ->
                            if (disabled) {
                                showThrottleWarning = true
                            } else {
                                runCpuOperation {
                                    manager.setThermalThrottlingDisabled(false)
                                }
                            }
                        },
                    )
                }
                AnimatedVisibility(
                    visible = state.thermalThrottlingDisabled &&
                        state.profile != CpuProfile.EXTREME,
                    enter = expandVertically(animationSpec = Motion.defaultEffectsSpec()) + fadeIn(),
                    exit = shrinkVertically(animationSpec = Motion.defaultEffectsSpec()) + fadeOut(),
                ) {
                    PartsInfoCard(
                        title = stringResource(R.string.thermal_disable_warning_title).trimEnd('?'),
                        body = stringResource(R.string.thermal_disable_warning_body),
                        icon = Icons.Filled.Warning,
                        accent = true,
                    )
                }
            }

            if (state.policies.isEmpty()) {
                item(key = "unsupported") {
                    PartsInfoCard(body = stringResource(R.string.cpu_not_supported))
                }
            } else {
                item(key = "frequencies") {
                    PartsPreferenceGroup(
                        title = stringResource(R.string.cpu_frequencies_category),
                    ) {
                        state.policies.forEachIndexed { index, policy ->
                            val clusterName = clusterName(index, state.policies.size)
                            PartsPreference(
                                icon = Icons.Filled.Tune,
                                title = clusterName,
                                summary = stringResource(
                                    R.string.cpu_frequency_range,
                                    formatFrequency(policy.minimumFrequency),
                                    formatFrequency(policy.maximumFrequency),
                                ),
                                enabled = !applying,
                                onClick = { editingPolicy = policy },
                            )
                        }
                    }
                }
            }

            item(key = "cores") {
                PartsPreferenceGroup(title = stringResource(R.string.cpu_cores_category)) {
                    state.cores.forEach { core ->
                        PartsSwitchPreference(
                            icon = Icons.Filled.Memory,
                            title = stringResource(R.string.cpu_core, core.id),
                            summary = when {
                                !core.canToggle -> stringResource(R.string.cpu_core_required)
                                core.online -> stringResource(R.string.cpu_core_online)
                                else -> stringResource(R.string.cpu_core_offline)
                            },
                            checked = core.online,
                            enabled = core.canToggle && !applying,
                            onCheckedChange = { online ->
                                runCpuOperation { manager.setCoreOnline(core.id, online) }
                            },
                        )
                    }
                }
            }

        }
    }

    editingPolicy?.let { policy ->
        FrequencyRangeDialog(
            policy = policy,
            clusterName = clusterName(
                state.policies.indexOfFirst { it.id == policy.id }.coerceAtLeast(0),
                state.policies.size,
            ),
            onDismiss = { editingPolicy = null },
            onApply = { minimum, maximum ->
                editingPolicy = null
                runCpuOperation { manager.setFrequencyRange(policy.id, minimum, maximum) }
            },
        )
    }

    if (showThrottleWarning) {
        AlertDialog(
            onDismissRequest = { showThrottleWarning = false },
            icon = {
                Icon(imageVector = Icons.Filled.Warning, contentDescription = null)
            },
            title = {
                Text(
                    text = stringResource(R.string.thermal_disable_warning_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.thermal_disable_warning_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showThrottleWarning = false
                        runCpuOperation { manager.setThermalThrottlingDisabled(true) }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(R.string.thermal_disable_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showThrottleWarning = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun ProfilePreference(
    profile: CpuProfile,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val title = when (profile) {
        CpuProfile.ECONOMY -> stringResource(R.string.cpu_profile_economy)
        CpuProfile.NORMAL -> stringResource(R.string.cpu_profile_normal)
        CpuProfile.BOOST -> stringResource(R.string.cpu_profile_boost)
        CpuProfile.EXTREME -> stringResource(R.string.cpu_profile_extreme)
        CpuProfile.CUSTOM -> stringResource(R.string.cpu_profile_custom)
    }
    val summary = when (profile) {
        CpuProfile.ECONOMY -> stringResource(R.string.cpu_profile_economy_summary)
        CpuProfile.NORMAL -> stringResource(R.string.cpu_profile_normal_summary)
        CpuProfile.BOOST -> stringResource(R.string.cpu_profile_boost_summary)
        CpuProfile.EXTREME -> stringResource(R.string.cpu_profile_extreme_summary)
        CpuProfile.CUSTOM -> null
    }
    val icon = when (profile) {
        CpuProfile.ECONOMY -> Icons.Filled.BatterySaver
        CpuProfile.NORMAL -> Icons.Filled.Speed
        CpuProfile.BOOST -> Icons.Filled.Bolt
        CpuProfile.EXTREME -> Icons.Filled.LocalFireDepartment
        CpuProfile.CUSTOM -> Icons.Filled.Tune
    }

    PartsSelectionPreference(
        icon = icon,
        title = title,
        summary = summary,
        selected = selected,
        onClick = onClick,
    )
}

@Composable
private fun clusterName(index: Int, clusterCount: Int): String = when {
    index == 0 -> stringResource(R.string.cpu_efficiency_cluster)
    clusterCount >= 3 && index == clusterCount - 1 -> stringResource(R.string.cpu_prime_cluster)
    clusterCount >= 2 && index == 1 -> stringResource(R.string.cpu_performance_cluster)
    else -> stringResource(R.string.cpu_cluster, index + 1)
}

@Composable
private fun FrequencyRangeDialog(
    policy: CpuPolicyState,
    clusterName: String,
    onDismiss: () -> Unit,
    onApply: (Long, Long) -> Unit,
) {
    val frequencies = policy.frequencies
    val initialMinimum = nearestIndex(frequencies, policy.minimumFrequency)
    val initialMaximum = nearestIndex(frequencies, policy.maximumFrequency)
    var selectedRange by remember(policy.id, policy.minimumFrequency, policy.maximumFrequency) {
        mutableStateOf(
            minOf(initialMinimum, initialMaximum).toFloat()..
                maxOf(initialMinimum, initialMaximum).toFloat(),
        )
    }

    val minimumIndex = selectedRange.start.roundToInt().coerceIn(frequencies.indices)
    val maximumIndex = selectedRange.endInclusive.roundToInt().coerceIn(frequencies.indices)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cpu_frequency_dialog_title, clusterName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(
                            R.string.cpu_min_frequency,
                            formatFrequency(frequencies[minimumIndex]),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(
                            R.string.cpu_max_frequency,
                            formatFrequency(frequencies[maximumIndex]),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (frequencies.size > 1) {
                    RangeSlider(
                        value = selectedRange,
                        onValueChange = { selectedRange = it },
                        valueRange = 0f..frequencies.lastIndex.toFloat(),
                        steps = (frequencies.size - 2).coerceAtLeast(0),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onApply(frequencies[minimumIndex], frequencies[maximumIndex])
                },
            ) {
                Text(stringResource(R.string.cpu_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

private fun nearestIndex(frequencies: List<Long>, value: Long): Int =
    frequencies.indices.minByOrNull { index -> kotlin.math.abs(frequencies[index] - value) } ?: 0

private fun formatFrequency(kilohertz: Long): String =
    if (kilohertz >= 1_000_000L) {
        String.format(Locale.getDefault(), "%.2f GHz", kilohertz / 1_000_000.0)
    } else {
        String.format(Locale.getDefault(), "%.0f MHz", kilohertz / 1_000.0)
    }
