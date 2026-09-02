/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.touchsampling

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.xiaomi.settings.R
import com.xiaomi.settings.ui.PartsInfoCard
import com.xiaomi.settings.ui.PartsPreferenceGroup
import com.xiaomi.settings.ui.PartsScaffold
import com.xiaomi.settings.ui.PartsSwitchPreference
import com.xiaomi.settings.utils.PartsToast

@Composable
fun TouchBoostScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(TouchSamplingService.isEnabled(context)) }

    fun toggle(newValue: Boolean) {
        runCatching {
            TouchSamplingService.setEnabled(context, newValue)
            enabled = newValue
        }.onFailure {
            PartsToast.show(context, R.string.touch_boost_failed)
        }
    }

    PartsScaffold(
        title = stringResource(R.string.touch_boost_title),
        onBack = onBack,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    top = innerPadding.calculateTopPadding() + 6.dp,
                    bottom = innerPadding.calculateBottomPadding() + 24.dp,
                ),
        ) {
            PartsPreferenceGroup {
                PartsSwitchPreference(
                    icon = ImageVector.vectorResource(R.drawable.ic_touch_boost),
                    title = stringResource(R.string.touch_boost_title),
                    summary = stringResource(R.string.touch_boost_summary),
                    checked = enabled,
                    onCheckedChange = ::toggle,
                )
            }
            PartsInfoCard(body = stringResource(R.string.touch_boost_description))
        }
    }
}
