/*
 * SPDX-FileCopyrightText: 2023-2025 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.display

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.hardware.display.AmbientDisplayConfiguration
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemProperties
import android.os.UserHandle
import android.provider.Settings
import android.util.Log
import androidx.annotation.StringRes
import androidx.core.os.postDelayed
import com.xiaomi.settings.R

class ColorService : Service() {

    companion object {
        private const val TAG = "ColorService"
        private val DEBUG     = Log.isLoggable(TAG, Log.DEBUG)
        private val DEFAULT_COLOR_MODE =
            SystemProperties.getInt("persist.sys.sf.native_mode", 0)

        /** Reads the current colour-mode id from Settings.System. */
        fun getColorMode(context: Context): Int =
            Settings.System.getIntForUser(
                context.contentResolver,
                Settings.System.DISPLAY_COLOR_MODE,
                DEFAULT_COLOR_MODE,
                UserHandle.USER_CURRENT,
            )

        /** Writes a new colour-mode id to Settings.System. */
        fun setColorMode(context: Context, id: Int) {
            Settings.System.putIntForUser(
                context.contentResolver,
                Settings.System.DISPLAY_COLOR_MODE,
                id,
                UserHandle.USER_CURRENT,
            )
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var ambientConfig: AmbientDisplayConfiguration
    private var isDozing = false

    private val settingObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            if (DEBUG) Log.d(TAG, "SettingObserver: onChange")
            setCurrentColorMode()
        }
    }

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (DEBUG) Log.d(TAG, "onReceive: ${intent.action}")
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> {
                    if (isDozing) {
                        isDozing = false
                        handler.removeCallbacksAndMessages(null)
                        handler.postDelayed(100) {
                            if (DEBUG) Log.d(TAG, "Exiting AOD — restoring colour mode")
                            setCurrentColorMode()
                        }
                    }
                }
                Intent.ACTION_SCREEN_OFF -> {
                    if (!ambientConfig.alwaysOnEnabled(UserHandle.USER_CURRENT)) {
                        if (DEBUG) Log.d(TAG, "AOD not enabled — no colour override needed")
                        isDozing = false
                        return
                    }
                    isDozing = true
                    handler.removeCallbacksAndMessages(null)
                    if (DEBUG) Log.d(TAG, "Entering AOD — forcing STANDARD colour mode")
                    handler.post { ColorMode.STANDARD.setCurrent() }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (DEBUG) Log.d(TAG, "onCreate")
        ambientConfig = AmbientDisplayConfiguration(this)
        contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.DISPLAY_COLOR_MODE),
            false, settingObserver, UserHandle.USER_CURRENT,
        )
        // ACTION_SCREEN_ON and ACTION_SCREEN_OFF are system-protected broadcasts
        // sent exclusively by the OS. RECEIVER_NOT_EXPORTED must NOT be used:
        // it is incorrect for system broadcasts and causes a StrictMode warning
        // on AOSP 16. The OS delivers these to all registered receivers
        // regardless of the export flag.
        @Suppress("UnspecifiedRegisterReceiverFlag")
        registerReceiver(
            screenStateReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            },
        )
        setCurrentColorMode()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (DEBUG) Log.d(TAG, "onStartCommand")
        return START_STICKY
    }

    override fun onDestroy() {
        if (DEBUG) Log.d(TAG, "onDestroy")
        contentResolver.unregisterContentObserver(settingObserver)
        unregisterReceiver(screenStateReceiver)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun setCurrentColorMode() {
        if (isDozing) {
            if (DEBUG) Log.d(TAG, "setCurrentColorMode: skipping — in AOD")
            return
        }
        val colorModeId = getColorMode(this)
        val mode = ColorMode.fromId(colorModeId) ?: run {
            Log.e(TAG, "Unknown colour mode id: $colorModeId")
            return
        }
        if (DEBUG) Log.d(TAG, "setCurrentColorMode: $mode")
        handler.post { mode.setCurrent() }
    }

    enum class ColorMode(
        val id: Int,
        val mode: Int,
        val value: Int,
        val cookie: Int,
        val isExpert: Boolean = false,
        @param:StringRes val label: Int,
        @param:StringRes val description: Int,
    ) {
        VIVID(258, 0, 2, 255,
            label = R.string.color_mode_vivid,
            description = R.string.color_mode_vivid_summary),
        SATURATED(256, 1, 2, 255,
            label = R.string.color_mode_saturated,
            description = R.string.color_mode_saturated_summary),
        STANDARD(257, 2, 2, 255,
            label = R.string.color_mode_standard,
            description = R.string.color_mode_standard_summary),
        ORIGINAL(269, 26, 1, 0, isExpert = true,
            label = R.string.color_mode_original,
            description = R.string.color_mode_original_summary),
        P3(268, 26, 2, 0, isExpert = true,
            label = R.string.color_mode_p3,
            description = R.string.color_mode_p3_summary),
        SRGB(267, 26, 3, 0, isExpert = true,
            label = R.string.color_mode_srgb,
            description = R.string.color_mode_srgb_summary);

        fun setCurrent() {
            DisplayFeatureWrapper.setFeature(mode, value, cookie)
            if (isExpert) DisplayFeatureWrapper.setFeature(EXPERT_MODE, EXPERT_VALUE, EXPERT_COOKIE)
        }

        companion object {
            private const val EXPERT_MODE   = 26
            private const val EXPERT_VALUE  = 0
            private const val EXPERT_COOKIE = 10
            fun fromId(id: Int): ColorMode? = entries.find { it.id == id }
        }
    }
}
