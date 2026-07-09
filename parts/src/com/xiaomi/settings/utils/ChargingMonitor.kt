/*
 * SPDX-FileCopyrightText: 2025 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 *
 * ChargingMonitor — reliable charging-state + battery-detail observer
 *                    for system apps running as android.uid.system.
 *
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │ Why not use BroadcastReceiver for battery events?                       │
 * ├─────────────────────────────────────────────────────────────────────────┤
 * │ A non-system app MUST use broadcast receivers because it lacks direct   │
 * │ BatteryManager access and must be woken from the background.            │
 * │                                                                          │
 * │ XiaomiParts runs as android.uid.system. That means:                     │
 * │   • BatteryManager.isCharging() is available directly — no permission  │
 * │   • BATTERY_STATS is implicitly granted by the system UID               │
 * │   • registerReceiver(null, filter) sticky read works without any flag   │
 * │   • No foreground service or WAKE_LOCK needed for the poll itself       │
 * │   • No DYNAMIC_RECEIVER_NOT_EXPORTED workaround needed                  │
 * └─────────────────────────────────────────────────────────────────────────┘
 *
 * isCharging truth gate — per Android battery-monitoring docs:
 *   https://developer.android.com/training/monitoring-device-state/battery-monitoring
 *
 *   The canonical test for "charger is physically connected" is
 *   EXTRA_PLUGGED != 0, NOT the status enum. BATTERY_STATUS_FULL with a
 *   charger still plugged in must be treated as charging for thermal purposes
 *   (sconfig=27 must remain active until the cable is removed). The status
 *   enum transitions to FULL before the plug is removed, creating a window
 *   where the old BATTERY_STATUS_CHARGING || STATUS_FULL check would drop
 *   the charging profile prematurely.
 *
 * Doze / wake-lock strategy:
 *   ThermalService calls stop() on ACTION_SCREEN_OFF which removes all poll
 *   callbacks — the CPU is not kept awake during screen-off / Doze at all.
 *   On ACTION_SCREEN_ON, start() is called from a BroadcastReceiver. The OS
 *   holds a wake-lock for the duration of onReceive(), but releases it the
 *   moment onReceive() returns — before the Handler has had a chance to run
 *   the first readBatteryInfo() sticky call on the HandlerThread. A short
 *   PARTIAL_WAKE_LOCK (WAKE_LOCK_TIMEOUT_MS = 500 ms) bridges that gap.
 *
 * HandlerThread:
 *   All polling and BatteryManager calls happen on a private HandlerThread,
 *   never on the main/UI looper. This prevents main-thread congestion from
 *   delaying poll callbacks and avoids StrictMode violations on AOSP 16.
 */

package com.xiaomi.settings.utils

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import android.util.Log

class ChargingMonitor(
    private val context : Context,
    private val onChange: (BatteryInfo) -> Unit,
) {
    private val batteryManager: BatteryManager =
        context.getSystemService(BatteryManager::class.java)

    private val powerManager: PowerManager =
        context.getSystemService(PowerManager::class.java)

    // Dedicated background thread — never touches the UI looper.
    private val handlerThread = HandlerThread("ChargingMonitor").also { it.start() }
    private val handler = Handler(handlerThread.looper)

    // PARTIAL_WAKE_LOCK: keeps the CPU awake for one poll cycle after
    // ACTION_SCREEN_ON so the first readBatteryInfo() completes before
    // the OS releases its own screen-on wake lock.
    private val wakeLock: PowerManager.WakeLock =
        powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "XiaomiParts:ChargingMonitor",
        ).also { it.setReferenceCounted(false) }

    /**
     * Plug type reported by the platform.
     * Mirrors [BatteryManager] BATTERY_PLUGGED_* constants.
     */
    enum class PlugType {
        /** Powered via standard AC/DC wall charger. */
        AC,
        /** Powered via USB port (any speed). */
        USB,
        /** Powered via Qi or similar wireless pad. */
        WIRELESS,
        /** Powered via a dock connector. */
        DOCK,
        /** Not charging — running on battery. */
        NONE;

        companion object {
            fun from(plugged: Int): PlugType = when (plugged) {
                BatteryManager.BATTERY_PLUGGED_AC       -> AC
                BatteryManager.BATTERY_PLUGGED_USB      -> USB
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> WIRELESS
                BatteryManager.BATTERY_PLUGGED_DOCK     -> DOCK
                else                                     -> NONE
            }
        }
    }

    /**
     * A snapshot of battery state read from the ACTION_BATTERY_CHANGED
     * sticky broadcast.
     *
     * All fields are read atomically from a single broadcast intent so
     * they are always internally consistent.
     *
     * @param isCharging   true when EXTRA_PLUGGED != 0 (charger physically
     *                     connected). This is the canonical thermal gate per
     *                     the Android battery-monitoring docs.
     * @param plugType     how the device is being charged (or [PlugType.NONE]).
     * @param level        battery level in the range [0, 100].
     * @param tempTenthsC  battery temperature in tenths of a degree Celsius
     *                     (e.g. 285 → 28.5 °C). Divide by 10 for display.
     */
    data class BatteryInfo(
        val isCharging  : Boolean,
        val plugType    : PlugType,
        val level       : Int,
        val tempTenthsC : Int,
    ) {
        companion object {
            /** Neutral sentinel — used before the first real read. */
            val UNKNOWN = BatteryInfo(
                isCharging  = false,
                plugType    = PlugType.NONE,
                level       = -1,
                tempTenthsC = 0,
            )
        }
    }

    /** Most-recently read battery information. Updated on every state change. */
    @Volatile
    var batteryInfo: BatteryInfo = BatteryInfo.UNKNOWN
        private set

    /**
     * Convenience accessor — avoids boilerplate in callers that only care
     * about charging state.
     */
    val isCharging: Boolean get() = batteryInfo.isCharging

    // ─────────────────────────────────────────────────────────────────────
    // Poll loop
    // ─────────────────────────────────────────────────────────────────────

    private val pollRunnable = object : Runnable {
        override fun run() {
            // Fast path: BatteryManager.isCharging() reads a cached in-memory
            // value from BatteryService — no I/O, no full sticky read.
            // We still cross-check against plugged state on a full read when
            // the fast-path says the state has changed.
            val nowCharging = batteryManager.isCharging
            if (nowCharging != batteryInfo.isCharging) {
                // State flipped: do a full sticky read so level / plugType /
                // temp are also updated, then notify the caller.
                val info = readBatteryInfo()
                batteryInfo = info
                dlog(TAG, "Charging state changed → ${info.isCharging} " +
                          "plug=${info.plugType} level=${info.level}% " +
                          "temp=${info.tempTenthsC / 10.0}°C")
                onChange(info)
            }
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Start (or restart) the monitor.
     *
     * Acquires a timed PARTIAL_WAKE_LOCK so the CPU stays awake for the
     * first readBatteryInfo() call even if the OS-held screen-on wake lock
     * has already been released by the time the HandlerThread runs.
     *
     * Performs an immediate full sticky read so [batteryInfo] is populated
     * before this call returns — no race window, no wait for first broadcast.
     * Safe to call multiple times; removes pending poll callbacks first.
     */
    fun start() {
        // Acquire a short wake lock so the first sticky read on the
        // HandlerThread completes before Doze can reclaim the CPU.
        wakeLock.acquire(WAKE_LOCK_TIMEOUT_MS)

        handler.removeCallbacks(pollRunnable)
        handler.post {
            val info    = readBatteryInfo()
            val changed = info.isCharging != batteryInfo.isCharging
            batteryInfo = info
            if (changed) onChange(info)
            handler.postDelayed(pollRunnable, POLL_INTERVAL_MS)
            // Release the wake lock after the first read + schedule completes.
            if (wakeLock.isHeld) wakeLock.release()
        }
    }

    /**
     * Stop the poll loop. [batteryInfo] remains readable as the last known
     * state. Call [start] to resume monitoring.
     *
     * @param final if true, also quits the HandlerThread. Pass true only
     *              when the owning service is being destroyed.
     */
    fun stop(final: Boolean = false) {
        handler.removeCallbacks(pollRunnable)
        if (wakeLock.isHeld) wakeLock.release()
        if (final) handlerThread.quitSafely()
    }

    // ─────────────────────────────────────────────────────────────────────
    // Sticky broadcast read
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Reads all battery fields in one atomic sticky-broadcast call.
     *
     * [Context.registerReceiver] with a null receiver returns the last
     * ACTION_BATTERY_CHANGED sticky intent synchronously without registering
     * a persistent listener.
     *
     * isCharging truth gate:
     *   EXTRA_PLUGGED != 0 means the charger is physically connected.
     *   This is the correct thermal gate per the Android docs. The status
     *   enum (CHARGING / FULL) is logged for debug but NOT used as the gate
     *   because BATTERY_STATUS_FULL fires before the cable is removed,
     *   which would prematurely drop sconfig=27 while still plugged in.
     *
     * Returns [BatteryInfo.UNKNOWN] if the sticky broadcast is not yet
     * available (should not happen after boot, but handled defensively).
     */
    private fun readBatteryInfo(): BatteryInfo {
        val intent: Intent? = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        )
        if (intent == null) {
            Log.w(TAG, "ACTION_BATTERY_CHANGED sticky broadcast not available yet")
            return BatteryInfo.UNKNOWN
        }

        val plugged     = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED,     0)
        val rawLevel    = intent.getIntExtra(BatteryManager.EXTRA_LEVEL,       -1)
        val scale       = intent.getIntExtra(BatteryManager.EXTRA_SCALE,       100)
        val temp        = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
        val statusInt   = intent.getIntExtra(
            BatteryManager.EXTRA_STATUS,
            BatteryManager.BATTERY_STATUS_UNKNOWN,
        )

        // Canonical charging gate: charger is physically connected.
        // EXTRA_PLUGGED != 0 covers AC, USB, WIRELESS, and DOCK.
        val isCharging = plugged != 0

        // Secondary cross-check logged for debug only — not used as the gate.
        val statusCharging = statusInt == BatteryManager.BATTERY_STATUS_CHARGING ||
                             statusInt == BatteryManager.BATTERY_STATUS_FULL
        if (isCharging != statusCharging) {
            dlog(TAG, "plugged/status mismatch: plugged=$plugged status=$statusInt " +
                      "— using plugged as truth")
        }

        val level = if (scale > 0 && rawLevel >= 0) (rawLevel * 100 / scale) else rawLevel

        return BatteryInfo(
            isCharging  = isCharging,
            plugType    = PlugType.from(plugged),
            level       = level,
            tempTenthsC = temp,
        )
    }

    companion object {
        private const val TAG = "ChargingMonitor"

        /**
         * Poll interval while screen is on.
         *
         * 3 seconds balances responsiveness (charging sconfig applied within
         * 3 s of plug-in) against CPU cost. BatteryManager.isCharging() is a
         * trivially cheap cached Binder call; the more expensive sticky read
         * only fires when the state actually changes.
         */
        private const val POLL_INTERVAL_MS = 3_000L

        /**
         * WakeLock timeout: long enough for one sticky broadcast read and
         * one HandlerThread dispatch after ACTION_SCREEN_ON; short enough
         * to never hold the CPU awake unnecessarily.
         */
        private const val WAKE_LOCK_TIMEOUT_MS = 500L
    }
}
