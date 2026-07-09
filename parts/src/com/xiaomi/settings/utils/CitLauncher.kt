/*
 * SPDX-FileCopyrightText: 2025 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 *
 * Launchers for individual Xiaomi / CIT calibration screens.
 *
 * Component targets are derived from logcat ActivityTaskManager output:
 *
 *   Fingerprint Calibration:
 *     cmp=com.jiiov.fingerprint_factorytest/.xiaomi.XiaomiAfterSalesCalibrationActivity
 *     (LAUNCH_SINGLE_TASK → FLAG_ACTIVITY_SINGLE_TOP)
 *
 *   Speaker Calibration:
 *     cmp=com.miui.cit/.auxiliary.CitAudioCaliSelfTest
 *     (LAUNCH_MULTIPLE → FLAG_ACTIVITY_MULTIPLE_TASK)
 *
 * Each function returns true if the activity resolved and was started,
 * false if the target package / activity is not present on the device.
 * The caller is responsible for showing a Toast on false.
 */

package com.xiaomi.settings.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

object CitLauncher {

    private const val TAG = "CitLauncher"

    // ── Fingerprint Calibration ─────────────────────────────────────────
    private const val FP_PACKAGE  = "com.jiiov.fingerprint_factorytest"
    private const val FP_ACTIVITY = "com.jiiov.fingerprint_factorytest.xiaomi.XiaomiAfterSalesCalibrationActivity"

    // ── Speaker Calibration ─────────────────────────────────────────────
    private const val SPEAKER_PACKAGE  = "com.miui.cit"
    private const val SPEAKER_ACTIVITY = "com.miui.cit.auxiliary.CitAudioCaliSelfTest"

    /**
     * Launches the Jiiov/Xiaomi after-sales fingerprint calibration screen.
     * Matches LAUNCH_SINGLE_TASK behaviour seen in logcat.
     *
     * @return true if the intent resolved and was fired, false otherwise.
     */
    fun launchFingerprintCalibration(context: Context): Boolean {
        return runCatching {
            val intent = Intent().apply {
                component = ComponentName(FP_PACKAGE, FP_ACTIVITY)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            context.startActivity(intent)
            Log.d(TAG, "Fingerprint calibration launched")
            true
        }.onFailure { e ->
            Log.e(TAG, "Unable to launch fingerprint calibration", e)
        }.getOrDefault(false)
    }

    /**
     * Launches the CIT speaker / audio calibration self-test screen.
     * Matches LAUNCH_MULTIPLE behaviour seen in logcat.
     *
     * @return true if the intent resolved and was fired, false otherwise.
     */
    fun launchSpeakerCalibration(context: Context): Boolean {
        return runCatching {
            val intent = Intent().apply {
                component = ComponentName(SPEAKER_PACKAGE, SPEAKER_ACTIVITY)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "Speaker calibration launched")
            true
        }.onFailure { e ->
            Log.e(TAG, "Unable to launch speaker calibration", e)
        }.getOrDefault(false)
    }
}
