/*
 * SPDX-FileCopyrightText: 2018 The LineageOS Project
 * SPDX-FileCopyrightText: 2025 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.UserHandle
import android.util.Log
import android.view.Display
import android.view.Display.HdrCapabilities
import com.xiaomi.settings.display.ColorService
import com.xiaomi.settings.thermal.ThermalUtils
import com.xiaomi.settings.touchsampling.TouchSamplingService

/** Restores all XiaomiParts feature states at locked-boot. */
class BootCompletedReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
        private val DEBUG = Log.isLoggable(TAG, Log.DEBUG)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED) return
        if (DEBUG) Log.d(TAG, "LOCKED_BOOT_COMPLETED received")
        onLockedBootCompleted(context)
    }

    /**
     * Called early at locked-boot — starts all background services so display,
     * thermal, and touch settings are applied before the user sees the lock screen.
     * Each service start is wrapped in runCatching to prevent a single failure
     * from stopping the remaining services from starting.
     */
    private fun onLockedBootCompleted(context: Context) {
        // Display colour mode
        runCatching {
            context.startServiceAsUser(
                Intent(context, ColorService::class.java),
                UserHandle.CURRENT,
            )
        }.onFailure { e -> Log.e(TAG, "Failed to start ColorService", e) }

        // Touch boost / high touch sampling rate
        runCatching {
            context.startServiceAsUser(
                Intent(context, TouchSamplingService::class.java),
                UserHandle.CURRENT,
            )
        }.onFailure { e -> Log.e(TAG, "Failed to start TouchSamplingService", e) }

        // Per-app thermal profiles
        runCatching {
            ThermalUtils.getInstance(context.applicationContext).startService()
        }.onFailure { e -> Log.e(TAG, "Failed to start ThermalService", e) }

        // Force-enable all HDR types (Dolby Vision, HDR10, HLG, HDR10+)
        runCatching {
            context.getSystemService(DisplayManager::class.java)
                ?.overrideHdrTypes(
                    Display.DEFAULT_DISPLAY,
                    intArrayOf(
                        HdrCapabilities.HDR_TYPE_DOLBY_VISION,
                        HdrCapabilities.HDR_TYPE_HDR10,
                        HdrCapabilities.HDR_TYPE_HLG,
                        HdrCapabilities.HDR_TYPE_HDR10_PLUS,
                    ),
                )
        }.onFailure { e -> Log.e(TAG, "Failed to override HDR types", e) }
    }
}
