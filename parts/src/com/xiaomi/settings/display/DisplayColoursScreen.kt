/*
 * SPDX-FileCopyrightText: 2025 Paranoid Android
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.display

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xiaomi.settings.R
import com.xiaomi.settings.ui.Motion
import com.xiaomi.settings.ui.PartsGroupShape
import com.xiaomi.settings.ui.PartsInfoCard
import com.xiaomi.settings.ui.PartsPageGutter
import com.xiaomi.settings.ui.PartsPreferenceGroup
import com.xiaomi.settings.ui.PartsScaffold
import com.xiaomi.settings.ui.PartsSelectionPreference
import com.xiaomi.settings.ui.shimmerAlpha
import com.xiaomi.settings.utils.PartsToast
import kotlinx.coroutines.launch

private typealias ColorMode = ColorService.ColorMode

private val ColorMode.previewHue: Color
    get() = when (this) {
        ColorMode.VIVID -> Color(0xFFFF3B5C)
        ColorMode.SATURATED -> Color(0xFFFF8800)
        ColorMode.STANDARD -> Color(0xFF00C2FF)
        ColorMode.ORIGINAL -> Color(0xFFBBBBBB)
        ColorMode.P3 -> Color(0xFF00E676)
        ColorMode.SRGB -> Color(0xFF7C4DFF)
    }

private val blobPositions = listOf(
    0.18f to 0.30f,
    0.50f to 0.20f,
    0.82f to 0.30f,
    0.18f to 0.72f,
    0.50f to 0.80f,
    0.82f to 0.72f,
)

@Composable
private fun ColourPreviewHero(
    selectedId: Int,
    modifier: Modifier = Modifier,
) {
    val modes = ColorMode.entries
    val scope = rememberCoroutineScope()
    val radiusScales = modes.map { mode ->
        remember(mode) { Animatable(if (mode.id == selectedId) 1.35f else 0.85f) }
    }

    LaunchedEffect(selectedId) {
        modes.forEachIndexed { index, mode ->
            scope.launch {
                radiusScales[index].animateTo(
                    targetValue = if (mode.id == selectedId) 1.35f else 0.85f,
                    animationSpec = Motion.defaultEffectsSpec(),
                )
            }
        }
    }

    val alphas = modes.map { mode ->
        animateFloatAsState(
            targetValue = if (mode.id == selectedId) 0.90f else 0.26f,
            animationSpec = Motion.defaultEffectsSpec(),
            label = "colourPreviewAlpha_${mode.name}",
        )
    }
    val shimmerOffset by rememberInfiniteTransition(label = "colourPreviewShimmer")
        .animateFloat(
            initialValue = -1f,
            targetValue = 2f,
            animationSpec = infiniteRepeatable(
                animation = Motion.shimmerSpec(),
                repeatMode = RepeatMode.Restart,
            ),
            label = "colourPreviewShimmerOffset",
        )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(216.dp)
            .clip(PartsGroupShape)
            .background(MaterialTheme.colorScheme.surfaceBright),
    ) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val baseRadius = widthPx * 0.26f

        Canvas(modifier = Modifier.matchParentSize()) {
            modes.forEachIndexed { index, mode ->
                val (xFraction, yFraction) = blobPositions[index]
                val center = Offset(xFraction * size.width, yFraction * size.height)
                val radius = baseRadius * radiusScales[index].value
                val hue = mode.previewHue
                drawCircle(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0f to hue.copy(alpha = alphas[index].value),
                            0.5f to hue.copy(alpha = alphas[index].value * 0.55f),
                            1f to Color.Transparent,
                        ),
                        center = center,
                        radius = radius,
                    ),
                    radius = radius,
                    center = center,
                )
            }

            val shimmerStart = shimmerOffset * size.width
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0f),
                        Color.White.copy(alpha = shimmerAlpha),
                        Color.White.copy(alpha = 0f),
                    ),
                    start = Offset(shimmerStart, 0f),
                    end = Offset(shimmerStart + size.width * 0.2f, size.height),
                ),
            )
        }

        modes.firstOrNull { it.id == selectedId }?.let { activeMode ->
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Text(
                    text = stringResource(activeMode.label),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
fun DisplayColoursScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var selectedId by remember { mutableIntStateOf(ColorService.getColorMode(context)) }

    fun selectMode(mode: ColorMode) {
        runCatching {
            ColorService.setColorMode(context, mode.id)
            selectedId = mode.id
        }.onFailure {
            PartsToast.show(context, R.string.display_colours_failed)
        }
    }

    PartsScaffold(
        title = stringResource(R.string.display_colours_title),
        onBack = onBack,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 12.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            item(key = "preview") {
                ColourPreviewHero(
                    selectedId = selectedId,
                    modifier = Modifier.padding(
                        horizontal = PartsPageGutter,
                        vertical = 6.dp,
                    ),
                )
            }
            item(key = "description") {
                PartsInfoCard(body = stringResource(R.string.display_colours_description))
            }
            item(key = "modes") {
                PartsPreferenceGroup {
                    ColorMode.entries.forEach { mode ->
                        PartsSelectionPreference(
                            title = stringResource(mode.label),
                            summary = stringResource(mode.description),
                            selected = selectedId == mode.id,
                            onClick = { selectMode(mode) },
                        )
                    }
                }
            }
        }
    }
}
