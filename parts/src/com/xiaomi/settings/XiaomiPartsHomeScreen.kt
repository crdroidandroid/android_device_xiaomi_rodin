/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.xiaomi.settings.ui.PartsPreference
import com.xiaomi.settings.ui.PartsPreferenceGroup
import com.xiaomi.settings.ui.PartsScaffold
import com.xiaomi.settings.utils.CitLauncher
import com.xiaomi.settings.utils.PartsToast

@Composable
fun XiaomiPartsHomeScreen(
    onNavigateToDisplay: () -> Unit,
    onNavigateToResolution: () -> Unit,
    onNavigateToCpu: () -> Unit,
    onNavigateToBattery: () -> Unit,
    onNavigateToMemory: () -> Unit,
    onNavigateToThermal: () -> Unit,
    onNavigateToTouch: () -> Unit,
) {
    val context = LocalContext.current

    PartsScaffold(title = stringResource(R.string.xiaomi_parts_title)) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            item(key = "display") {
                PartsPreferenceGroup(title = stringResource(R.string.display_category)) {
                    PartsPreference(
                        icon = ImageVector.vectorResource(R.drawable.ic_display_colours),
                        title = stringResource(R.string.display_colours_title),
                        summary = stringResource(R.string.display_colours_summary),
                        onClick = onNavigateToDisplay,
                    )
                    PartsPreference(
                        icon = ImageVector.vectorResource(R.drawable.ic_screen_resolution),
                        title = stringResource(R.string.screen_resolution_title),
                        summary = stringResource(R.string.screen_resolution_home_summary),
                        onClick = onNavigateToResolution,
                    )
                }
            }

            item(key = "performance") {
                PartsPreferenceGroup(title = stringResource(R.string.performance_category)) {
                    PartsPreference(
                        icon = Icons.Filled.Memory,
                        title = stringResource(R.string.cpu_control_title),
                        summary = stringResource(R.string.cpu_control_summary),
                        onClick = onNavigateToCpu,
                    )
                    PartsPreference(
                        icon = ImageVector.vectorResource(R.drawable.ic_thermal_settings),
                        title = stringResource(R.string.thermal_title),
                        summary = stringResource(R.string.thermal_summary),
                        onClick = onNavigateToThermal,
                    )
                    PartsPreference(
                        icon = ImageVector.vectorResource(R.drawable.ic_touch_boost),
                        title = stringResource(R.string.touch_boost_title),
                        summary = stringResource(R.string.touch_boost_summary),
                        onClick = onNavigateToTouch,
                    )
                }
            }

            item(key = "system") {
                PartsPreferenceGroup(title = stringResource(R.string.system_category)) {
                    PartsPreference(
                        icon = Icons.Filled.BatteryChargingFull,
                        title = stringResource(R.string.battery_status_title),
                        summary = stringResource(R.string.battery_status_summary),
                        onClick = onNavigateToBattery,
                    )
                    PartsPreference(
                        icon = Icons.Filled.Memory,
                        title = stringResource(R.string.memory_title),
                        summary = stringResource(R.string.memory_summary),
                        onClick = onNavigateToMemory,
                    )
                }
            }

            item(key = "diagnostics") {
                PartsPreferenceGroup(
                    title = stringResource(R.string.xiaomi_parts_category_diagnostics),
                ) {
                    PartsPreference(
                        icon = Icons.Filled.Fingerprint,
                        title = stringResource(R.string.fingerprint_calibration_title),
                        summary = stringResource(R.string.fingerprint_calibration_summary),
                        onClick = {
                            if (!CitLauncher.launchFingerprintCalibration(context)) {
                                PartsToast.show(
                                    context,
                                    R.string.fingerprint_calibration_not_found,
                                )
                            }
                        },
                    )
                    PartsPreference(
                        icon = Icons.Filled.Speaker,
                        title = stringResource(R.string.speaker_calibration_title),
                        summary = stringResource(R.string.speaker_calibration_summary),
                        onClick = {
                            if (!CitLauncher.launchSpeakerCalibration(context)) {
                                PartsToast.show(context, R.string.speaker_calibration_not_found)
                            }
                        },
                    )
                }
            }
        }
    }
}
