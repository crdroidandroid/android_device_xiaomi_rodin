/*
 * SPDX-FileCopyrightText: 2020 The LineageOS Project
 * SPDX-FileCopyrightText: 2025 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 *
 * ThermalService — background service that applies thermal profiles.
 *
 * applyProfile() priority (highest → lowest):
 *   1. Screen off          → sconfig=0 (default, low-power)
 *   2. Foreground app known → per-app sconfig from ThermalUtils
 *   3. No foreground app yet → skip (TaskStackListener hasn't fired)
 */

package com.xiaomi.settings.thermal

import android.app.ActivityTaskManager
import android.app.Service
import android.app.TaskStackListener
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.preference.PreferenceManager
import com.xiaomi.settings.utils.dlog

/** Background service — monitors foreground app + screen state to apply thermal profiles. */
class ThermalService : Service() {

    private lateinit var thermalUtils: ThermalUtils

    private var currentApp = ""
        set(value) {
            if (field == value) return
            field = value
            dlog(TAG, "Top app: $value")
            applyProfile()
        }

    private var screenOn = true
        set(value) {
            if (field == value) return
            field = value
            dlog(TAG, "Screen: $value")
            applyProfile()
        }

    private val taskListener = object : TaskStackListener() {
        override fun onTaskStackChanged() {
            runCatching {
                val focusedTask = ActivityTaskManager.getService().focusedRootTaskInfo
                focusedTask?.topActivity?.let { currentApp = it.packageName }
            }
        }
    }

    @Suppress("UnspecifiedRegisterReceiverFlag")
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> screenOn = false
                Intent.ACTION_SCREEN_ON  -> screenOn = true
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        dlog(TAG, "onCreate")
        thermalUtils = ThermalUtils.getInstance(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        dlog(TAG, "onStartCommand")

        @Suppress("UnspecifiedRegisterReceiverFlag")
        registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            },
        )

        runCatching {
            ActivityTaskManager.getService().registerTaskStackListener(taskListener)
        }

        applyProfile()
        return START_STICKY
    }

    override fun onDestroy() {
        dlog(TAG, "onDestroy")
        if (thermalUtils.throttlingDisabled) {
            thermalUtils.setNoLimitsThermalProfile()
        } else {
            thermalUtils.setDefaultThermalProfile()
        }
        unregisterReceiver(screenReceiver)
        runCatching { ActivityTaskManager.getService().unregisterTaskStackListener(taskListener) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Applies the correct thermal sconfig based on current state.
     *
     * Priority (highest wins):
     *   1. Screen off    → sconfig=0
     *   2. App known     → per-app sconfig via ThermalUtils
     *   3. No app yet    → skip; TaskStackListener will fire shortly
     */
    private fun applyProfile() {
        runCatching {
            when {
                !screenOn               -> thermalUtils.setDefaultThermalProfile()
                currentApp.isNotEmpty() -> thermalUtils.setThermalProfile(currentApp)
                else -> dlog(TAG, "applyProfile: skipped — no foreground app yet")
            }
        }.onFailure { e ->
            dlog(TAG, "applyProfile failed: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "ThermalService"

        fun profiles(): List<ThermalUtils.ThermalState> =
            ThermalUtils.ThermalState.entries.filter { it.userSelectable }

        fun profileLabel(context: Context, profileId: Int): String =
            ThermalUtils.ThermalState.entries
                .firstOrNull { it.id == profileId }
                ?.let { context.getString(it.label) }
                .orEmpty()

        /**
         * Returns every installed app whose effective thermal profile is not DEFAULT.
         *
         * Two sources are merged:
         *  1. Explicit pref overrides  — keys written by writePackage() for non-DEFAULT states.
         *     writePackage() removes the key when DEFAULT is chosen, so any surviving key
         *     should already be non-DEFAULT; we still guard against stale zero-value keys.
         *  2. Auto-classified apps     — apps matched by the hardcoded listOf() package
         *     sets inside classifyApp(). These are never persisted, so scanning prefs
         *     alone misses them entirely. We enumerate installed launcher apps and call
         *     getStateForPackage() to discover their effective profile.
         *
         * Explicit overrides win over auto-classification for the same package.
         * Only entries with a final profileId != DEFAULT.id are included.
         */
        fun getAppList(context: Context): List<AppThermalEntry> {
            val pm           = context.packageManager
            val prefs        = PreferenceManager.getDefaultSharedPreferences(context)
            val thermalUtils = ThermalUtils.getInstance(context)
            val defaultId    = ThermalUtils.ThermalState.DEFAULT.id

            // --- source 1: explicit pref overrides ---
            val explicitMap = prefs.all.keys
                .filter { it.startsWith(ThermalUtils.THERMAL_PACKAGE_PREFIX) }
                .mapNotNull { key ->
                    val pkg       = key.removePrefix(ThermalUtils.THERMAL_PACKAGE_PREFIX)
                    val profileId = prefs.getInt(key, defaultId)
                    if (profileId == defaultId) return@mapNotNull null
                    val appInfo = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()
                        ?: return@mapNotNull null
                    pkg to AppThermalEntry(
                        packageName = pkg,
                        label       = pm.getApplicationLabel(appInfo).toString(),
                        profileId   = profileId,
                    )
                }
                .toMap()

            // --- source 2: auto-classified apps (not in prefs) ---
            val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val installedPkgs  = pm
                .queryIntentActivities(launcherIntent, PackageManager.GET_META_DATA)
                .map { it.activityInfo.packageName }
                .distinct()

            val autoMap = installedPkgs
                .filter { it !in explicitMap }          // explicit override already covers it
                .mapNotNull { pkg ->
                    val state = thermalUtils.getStateForPackage(pkg)
                    if (state.id == defaultId) return@mapNotNull null
                    val appInfo = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()
                        ?: return@mapNotNull null
                    pkg to AppThermalEntry(
                        packageName = pkg,
                        label       = pm.getApplicationLabel(appInfo).toString(),
                        profileId   = state.id,
                    )
                }
                .toMap()

            // merge: explicit overrides win
            return (autoMap + explicitMap)
                .values
                .sortedBy { it.label.lowercase() }
        }

        fun setAppProfile(context: Context, packageName: String, profileId: Int) {
            ThermalUtils.getInstance(context).writePackage(packageName, profileId)
        }
    }
}