/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.DurationBasedAnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import com.android.settingslib.spa.framework.theme.SettingsTheme

/** Uses the same dynamic-colour and typography pipeline as Lineage Settings. */
@Composable
fun XiaomiPartsTheme(content: @Composable () -> Unit) {
    SettingsTheme(content)
}

/** Android motion tokens shared by navigation and state changes. */
object Motion {
    private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    fun <T> navSpatialSpec(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.86f,
        stiffness = 420f,
    )

    fun <T> navEffectsSpec(): FiniteAnimationSpec<T> =
        tween(durationMillis = 220, easing = EmphasizedDecelerate)

    fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    fun <T> shimmerSpec(): DurationBasedAnimationSpec<T> =
        tween(durationMillis = 2_800, easing = LinearEasing)

}

const val shimmerAlpha: Float = 0.05f
