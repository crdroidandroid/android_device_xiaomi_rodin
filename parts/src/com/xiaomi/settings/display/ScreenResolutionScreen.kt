/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.display

import android.os.UserHandle
import android.provider.Settings
import android.view.Display
import android.view.WindowManagerGlobal
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xiaomi.settings.R
import com.xiaomi.settings.ui.PartsInfoCard
import com.xiaomi.settings.ui.PartsPreferenceGroup
import com.xiaomi.settings.ui.PartsScaffold
import com.xiaomi.settings.ui.PartsSelectionPreference
import com.xiaomi.settings.utils.PartsToast
import com.xiaomi.settings.utils.dlog

private const val TAG = "ScreenResolutionScreen"
private const val PREF_KEY = "custom_screen_resolution_key"
private const val NATIVE_KEY = "res_1220p"

private const val BASE_WIDTH = 1220
private const val BASE_DENSITY = 446

private data class ResolutionOption(
    val key: String,
    val labelRes: Int,
    val summaryRes: Int,
    val width: Int,
    val height: Int,
    val isNative: Boolean = false,
)

private val RESOLUTIONS = listOf(
    ResolutionOption(
        "res_1440p",
        R.string.screen_resolution_1440p,
        R.string.screen_resolution_1440p_summary,
        1440,
        3200,
    ),
    ResolutionOption(
        "res_1220p",
        R.string.screen_resolution_1220p,
        R.string.screen_resolution_1220p_summary,
        1220,
        2712,
        isNative = true,
    ),
    ResolutionOption(
        "res_1080p",
        R.string.screen_resolution_1080p,
        R.string.screen_resolution_1080p_summary,
        1080,
        2400,
    ),
    ResolutionOption(
        "res_720p",
        R.string.screen_resolution_720p,
        R.string.screen_resolution_720p_summary,
        720,
        1600,
    ),
)

private fun applyResolution(option: ResolutionOption) {
    val windowManager = requireNotNull(WindowManagerGlobal.getWindowManagerService()) {
        "WindowManager service is unavailable"
    }
    if (option.isNative) {
        windowManager.clearForcedDisplaySize(Display.DEFAULT_DISPLAY)
        windowManager.clearForcedDisplayDensityForUser(
            Display.DEFAULT_DISPLAY,
            UserHandle.USER_CURRENT,
        )
    } else {
        val density = (option.width * BASE_DENSITY) / BASE_WIDTH
        windowManager.setForcedDisplaySize(
            Display.DEFAULT_DISPLAY,
            option.width,
            option.height,
        )
        windowManager.setForcedDisplayDensityForUser(
            Display.DEFAULT_DISPLAY,
            density,
            UserHandle.USER_CURRENT,
        )
    }
}

private fun restartSystemUi() {
    Runtime.getRuntime().exec(arrayOf("killall", "com.android.systemui"))
}

@Composable
fun ScreenResolutionScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var selectedKey by remember {
        mutableStateOf(Settings.System.getString(context.contentResolver, PREF_KEY) ?: NATIVE_KEY)
    }
    var pendingOption by remember { mutableStateOf<ResolutionOption?>(null) }

    pendingOption?.let { option ->
        AlertDialog(
            onDismissRequest = { pendingOption = null },
            icon = { Icon(Icons.Outlined.Info, contentDescription = null) },
            title = { Text(stringResource(R.string.screen_resolution_dialog_title)) },
            text = { Text(stringResource(R.string.screen_resolution_restart_hint_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingOption = null
                        runCatching {
                            applyResolution(option)
                            Settings.System.putString(
                                context.contentResolver,
                                PREF_KEY,
                                option.key,
                            )
                            selectedKey = option.key
                            restartSystemUi()
                        }.onFailure { error ->
                            dlog(TAG, "Failed to apply resolution: ${error.message}")
                            PartsToast.show(context, R.string.screen_resolution_failed)
                        }
                    },
                ) {
                    Text(stringResource(R.string.screen_resolution_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingOption = null }) {
                    Text(stringResource(R.string.screen_resolution_dialog_cancel))
                }
            },
        )
    }

    PartsScaffold(
        title = stringResource(R.string.screen_resolution_title),
        onBack = onBack,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 6.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            item(key = "description") {
                PartsInfoCard(body = stringResource(R.string.screen_resolution_description))
            }
            item(key = "resolutions") {
                PartsPreferenceGroup {
                    RESOLUTIONS.forEach { option ->
                        PartsSelectionPreference(
                            title = stringResource(option.labelRes),
                            summary = stringResource(option.summaryRes),
                            selected = selectedKey == option.key,
                            onClick = {
                                if (selectedKey != option.key) pendingOption = option
                            },
                        )
                    }
                }
            }
        }
    }
}
