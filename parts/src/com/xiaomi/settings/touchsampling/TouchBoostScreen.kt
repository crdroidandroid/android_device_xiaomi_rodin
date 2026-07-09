/*
 * SPDX-FileCopyrightText: 2025 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.touchsampling

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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiaomi.settings.R
import com.xiaomi.settings.utils.PartsToast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TouchBoostScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(TouchSamplingService.isEnabled(context)) }

    val horizontalGutter = 20.dp

    fun toggle(newValue: Boolean) {
        runCatching {
            TouchSamplingService.setEnabled(context, newValue)
            enabled = newValue
        }.onFailure {
            PartsToast.show(context, R.string.touch_boost_failed)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text     = stringResource(R.string.touch_boost_title),
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    top    = innerPadding.calculateTopPadding() + 16.dp,
                    bottom = innerPadding.calculateBottomPadding() + 16.dp,
                ),
        ) {
            Card(
                onClick  = { toggle(!enabled) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalGutter)
                    .padding(bottom = 12.dp),
                shape  = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            ) {
                ListItem(
                    headlineContent = {
                        Text(
                            text  = stringResource(R.string.touch_boost_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked         = enabled,
                            onCheckedChange = { toggle(it) },
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalGutter),
                shape  = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
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
                    )
                    Text(
                        text       = stringResource(R.string.touch_boost_description),
                        style      = MaterialTheme.typography.bodyMedium,
                        lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.2f,
                    )
                }
            }
        }
    }
}
