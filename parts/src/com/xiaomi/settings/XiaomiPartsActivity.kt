/*
 * SPDX-FileCopyrightText: 2025 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.xiaomi.settings.display.DisplayColoursScreen
import com.xiaomi.settings.display.ScreenResolutionScreen
import com.xiaomi.settings.thermal.ThermalManagementScreen
import com.xiaomi.settings.touchsampling.TouchBoostScreen
import com.xiaomi.settings.ui.Motion
import com.xiaomi.settings.ui.XiaomiPartsTheme

class XiaomiPartsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            XiaomiPartsTheme {
                PartsNavHost()
            }
        }
    }
}

@Composable
private fun PartsNavHost() {
    val nav = rememberNavController()

    NavHost(
        navController      = nav,
        startDestination   = "home",
        enterTransition    = {
            slideInHorizontally(animationSpec = Motion.navSpatialSpec()) { it } +
                fadeIn(animationSpec = Motion.navEffectsSpec())
        },
        exitTransition     = {
            slideOutHorizontally(animationSpec = Motion.navSpatialSpec()) { -it / 4 } +
                fadeOut(animationSpec = Motion.navEffectsSpec())
        },
        popEnterTransition = {
            slideInHorizontally(animationSpec = Motion.navSpatialSpec()) { -it / 4 } +
                fadeIn(animationSpec = Motion.navEffectsSpec())
        },
        popExitTransition  = {
            slideOutHorizontally(animationSpec = Motion.navSpatialSpec()) { it } +
                fadeOut(animationSpec = Motion.navEffectsSpec())
        },
    ) {
        composable("home") {
            XiaomiPartsHomeScreen(
                onNavigateToDisplay     = { nav.navigate("displayColours") },
                onNavigateToResolution  = { nav.navigate("screenResolution") },
                onNavigateToThermal     = { nav.navigate("thermal") },
                onNavigateToTouch       = { nav.navigate("touchBoost") },
            )
        }
        composable("displayColours")    { DisplayColoursScreen { nav.popBackStack() } }
        composable("screenResolution")  { ScreenResolutionScreen { nav.popBackStack() } }
        composable("thermal")           { ThermalManagementScreen { nav.popBackStack() } }
        composable("touchBoost")        { TouchBoostScreen { nav.popBackStack() } }
    }
}
