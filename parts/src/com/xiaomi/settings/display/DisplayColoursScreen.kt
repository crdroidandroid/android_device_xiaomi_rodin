/*
 * SPDX-FileCopyrightText: 2025 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.display

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiaomi.settings.R
import com.xiaomi.settings.ui.Motion
import com.xiaomi.settings.ui.shimmerAlpha
import com.xiaomi.settings.utils.PartsToast
import kotlinx.coroutines.launch
import kotlin.math.sqrt

private typealias ColorMode = ColorService.ColorMode

private val ColorMode.previewHue: Color
    get() = when (this) {
        ColorMode.VIVID     -> Color(0xFFFF3B5C)
        ColorMode.SATURATED -> Color(0xFFFF8800)
        ColorMode.STANDARD  -> Color(0xFF00C2FF)
        ColorMode.ORIGINAL  -> Color(0xFFBBBBBB)
        ColorMode.P3        -> Color(0xFF00E676)
        ColorMode.SRGB      -> Color(0xFF7C4DFF)
    }

private val blobPositions = listOf(
    Pair(0.18f, 0.30f), Pair(0.50f, 0.20f), Pair(0.82f, 0.30f),
    Pair(0.18f, 0.72f), Pair(0.50f, 0.80f), Pair(0.82f, 0.72f),
)

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
        androidx.compose.foundation.layout.Row(
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

@Composable
private fun ColourPreviewHero(
    selectedId: Int,
    onSelect:   (Int) -> Unit,
    modifier:   Modifier = Modifier,
) {
    val allModes = ColorMode.entries
    val scope    = rememberCoroutineScope()

    val radiusScales = allModes.map { mode ->
        remember(mode) { Animatable(if (mode.id == selectedId) 1.35f else 0.85f) }
    }

    LaunchedEffect(selectedId) {
        allModes.forEachIndexed { i, mode ->
            scope.launch {
                radiusScales[i].animateTo(
                    if (mode.id == selectedId) 1.35f else 0.85f,
                    animationSpec = Motion.defaultEffectsSpec(),
                )
            }
        }
    }

    val alphas = allModes.map { mode ->
        animateFloatAsState(
            targetValue   = if (mode.id == selectedId) 0.90f else 0.28f,
            animationSpec = Motion.defaultEffectsSpec(),
            label         = "alpha_${mode.name}",
        )
    }

    val shimmerOffset by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue  = -1f,
        targetValue   = 2f,
        animationSpec = infiniteRepeatable(
            animation  = Motion.shimmerSpec(),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart,
        ),
        label = "shimmerOffset",
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        val widthPx  = with(LocalDensity.current) { maxWidth.toPx() }
        val heightPx = with(LocalDensity.current) { maxHeight.toPx() }
        val baseRadius = widthPx * 0.26f

        Canvas(modifier = Modifier.matchParentSize()) {
            val shimmerStartX = shimmerOffset * size.width

            allModes.forEachIndexed { i, mode ->
                val (fx, fy) = blobPositions[i]
                val cx     = fx * size.width
                val cy     = fy * size.height
                val radius = baseRadius * radiusScales[i].value
                val hue    = mode.previewHue

                drawCircle(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0.0f to hue.copy(alpha = alphas[i].value),
                            0.5f to hue.copy(alpha = alphas[i].value * 0.55f),
                            1.0f to Color.Transparent,
                        ),
                        center = Offset(cx, cy),
                        radius = radius,
                    ),
                    radius = radius,
                    center = Offset(cx, cy),
                )
            }

            val shimmerW = size.width * 0.20f
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0f),
                        Color.White.copy(alpha = shimmerAlpha),
                        Color.White.copy(alpha = 0f),
                    ),
                    start = Offset(shimmerStartX, 0f),
                    end   = Offset(shimmerStartX + shimmerW, size.height),
                ),
            )
        }

        val activeMode = allModes.firstOrNull { it.id == selectedId }
        if (activeMode != null) {
            Text(
                text     = stringResource(activeMode.label),
                style    = MaterialTheme.typography.titleSmall,
                color    = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 12.dp),
            )
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun ColorModeList(
    selectedId: Int,
    onSelect:   (ColorMode) -> Unit,
    modifier:   Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape    = MaterialTheme.shapes.extraLarge,
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        ColorMode.entries.forEachIndexed { index, mode ->
            val isLast = index == ColorMode.entries.lastIndex
            SelectionRow(
                mode       = mode,
                isSelected = selectedId == mode.id,
                onClick    = { onSelect(mode) },
            )
            if (!isLast) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
        }
    }
}

@Composable
private fun SelectionRow(
    mode:       ColorMode,
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
                    text  = stringResource(mode.label),
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            supportingContent = {
                Text(
                    text  = stringResource(mode.description),
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
                    label = "checkIcon_${mode.name}",
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun DisplayColoursScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    var selectedId by remember {
        mutableIntStateOf(ColorService.getColorMode(context))
    }

    val horizontalGutter = 20.dp

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text     = stringResource(R.string.display_colours_title),
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
                    bottom = innerPadding.calculateBottomPadding() + 32.dp,
                ),
        ) {
            ColourPreviewHero(
                selectedId = selectedId,
                onSelect   = { id ->
                    runCatching {
                        ColorService.setColorMode(context, id)
                        selectedId = id
                    }.onFailure {
                        PartsToast.show(context, R.string.display_colours_failed)
                    }
                },
                modifier = Modifier
                    .padding(horizontal = horizontalGutter)
                    .padding(bottom = 16.dp),
            )

            InfoCard(
                body           = stringResource(R.string.display_colours_description),
                iconTint       = MaterialTheme.colorScheme.onSurfaceVariant,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier       = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalGutter)
                    .padding(bottom = 16.dp),
            )

            ColorModeList(
                selectedId = selectedId,
                onSelect   = { mode ->
                    runCatching {
                        ColorService.setColorMode(context, mode.id)
                        selectedId = mode.id
                    }.onFailure {
                        PartsToast.show(context, R.string.display_colours_failed)
                    }
                },
                modifier = Modifier.padding(horizontal = horizontalGutter),
            )
        }
    }
}
