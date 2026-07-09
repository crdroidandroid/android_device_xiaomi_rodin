/*
 * SPDX-FileCopyrightText: 2025 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.display

import android.os.UserHandle
import android.provider.Settings
import android.view.Display
import android.view.WindowManagerGlobal
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiaomi.settings.R
import com.xiaomi.settings.ui.Motion
import com.xiaomi.settings.utils.PartsToast
import com.xiaomi.settings.utils.dlog

private const val TAG        = "ScreenResolutionScreen"
private const val PREF_KEY   = "custom_screen_resolution_key"
private const val NATIVE_KEY = "res_1220p"

private const val BASE_WIDTH   = 1220
private const val BASE_DENSITY = 446

private data class ResolutionOption(
    val key:        String,
    val labelRes:   Int,
    val summaryRes: Int,
    val width:      Int,
    val height:     Int,
    val isNative:   Boolean = false,
)

private val RESOLUTIONS = listOf(
    ResolutionOption("res_1440p", R.string.screen_resolution_1440p, R.string.screen_resolution_1440p_summary, 1440, 3200),
    ResolutionOption("res_1220p", R.string.screen_resolution_1220p, R.string.screen_resolution_1220p_summary, 1220, 2712, isNative = true),
    ResolutionOption("res_1080p", R.string.screen_resolution_1080p, R.string.screen_resolution_1080p_summary, 1080, 2400),
    ResolutionOption("res_720p",  R.string.screen_resolution_720p,  R.string.screen_resolution_720p_summary,   720, 1600),
)

private fun applyResolution(option: ResolutionOption) {
    val wm = requireNotNull(WindowManagerGlobal.getWindowManagerService()) {
        "WindowManager service is unavailable"
    }
    if (option.isNative) {
        wm.clearForcedDisplaySize(Display.DEFAULT_DISPLAY)
        wm.clearForcedDisplayDensityForUser(Display.DEFAULT_DISPLAY, UserHandle.USER_CURRENT)
    } else {
        val density = (option.width * BASE_DENSITY) / BASE_WIDTH
        wm.setForcedDisplaySize(Display.DEFAULT_DISPLAY, option.width, option.height)
        wm.setForcedDisplayDensityForUser(Display.DEFAULT_DISPLAY, density, UserHandle.USER_CURRENT)
    }
}

private fun restartSystemUi() {
    Runtime.getRuntime().exec(arrayOf("killall", "com.android.systemui"))
}

@Composable
private fun InfoCard(
    title:          String? = null,
    body:           String,
    iconTint:       Color,
    containerColor: Color,
    border:         BorderStroke? = null,
    modifier:       Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape    = MaterialTheme.shapes.extraLarge,
        colors   = CardDefaults.cardColors(containerColor = containerColor),
        border   = border,
    ) {
        Row(
            modifier          = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector        = Icons.Filled.Info,
                contentDescription = null,
                modifier           = Modifier
                    .padding(end = 16.dp)
                    .size(24.dp),
                tint               = iconTint,
            )
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
fun ScreenResolutionScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    var selectedKey   by remember {
        mutableStateOf(
            Settings.System.getString(context.contentResolver, PREF_KEY) ?: NATIVE_KEY
        )
    }
    var pendingOption by remember { mutableStateOf<ResolutionOption?>(null) }

    pendingOption?.let { option ->
        AlertDialog(
            onDismissRequest = { pendingOption = null },
            icon = {
                Icon(
                    imageVector        = Icons.Filled.Info,
                    contentDescription = null,
                )
            },
            title = {
                Text(stringResource(R.string.screen_resolution_dialog_title))
            },
            text = {
                Text(stringResource(R.string.screen_resolution_restart_hint_body))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingOption = null
                        runCatching {
                            applyResolution(option)
                            Settings.System.putString(context.contentResolver, PREF_KEY, option.key)
                            selectedKey = option.key
                            restartSystemUi()
                        }.onFailure { e ->
                            dlog(TAG, "Failed to apply resolution: ${e.message}")
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text     = stringResource(R.string.screen_resolution_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_up),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        val horizontalGutter = 20.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    top    = innerPadding.calculateTopPadding() + 12.dp,
                    bottom = innerPadding.calculateBottomPadding() + 32.dp,
                ),
        ) {
            InfoCard(
                body           = stringResource(R.string.screen_resolution_description),
                iconTint       = MaterialTheme.colorScheme.onSurfaceVariant,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier       = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalGutter)
                    .padding(bottom = 12.dp),
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalGutter),
                shape  = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                RESOLUTIONS.forEachIndexed { index, option ->
                    val isLast = index == RESOLUTIONS.lastIndex

                    ResolutionRow(
                        option     = option,
                        isSelected = selectedKey == option.key,
                        onClick    = { pendingOption = option },
                    )

                    if (!isLast) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ResolutionRow(
    option:     ResolutionOption,
    isSelected: Boolean,
    onClick:    () -> Unit,
) {
    val targetContainer = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent

    Card(
        onClick  = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape    = RectangleShape,
        colors   = CardDefaults.cardColors(containerColor = targetContainer),
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text  = stringResource(option.labelRes),
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            supportingContent = {
                Text(
                    text  = stringResource(option.summaryRes),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            leadingContent = {
                RadioButton(selected = isSelected, onClick = null)
            },
            trailingContent = {
                AnimatedContent(
                    targetState    = isSelected,
                    transitionSpec = {
                        fadeIn(Motion.checkFadeInSpec()) togetherWith fadeOut(Motion.checkFadeOutSpec())
                    },
                    label = "check_${option.key}",
                ) { selected ->
                    if (selected) {
                        Icon(
                            imageVector        = Icons.Filled.Check,
                            contentDescription = null,
                            modifier           = Modifier.size(24.dp),
                        )
                    } else {
                        Box(Modifier.size(24.dp))
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}
