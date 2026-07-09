/*
 * SPDX-FileCopyrightText: 2023-2025 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 *
 * Thin wrapper around the vendor.xiaomi.hardware.displayfeature_aidl
 * IDisplayFeature AIDL service.
 *
 * All calls are dispatched on a background Thread so callers on the
 * main thread are never blocked. The binder is cached and re-obtained
 * automatically after a service death.
 */

package com.xiaomi.settings.display

import android.os.IBinder
import android.os.ServiceManager
import android.util.Log
import vendor.xiaomi.hardware.displayfeature_aidl.IDisplayFeature

object DisplayFeatureWrapper {
    private const val TAG = "DisplayFeatureWrapper"
    private val DEBUG = Log.isLoggable(TAG, Log.DEBUG)

    @Volatile private var displayFeature: IDisplayFeature? = null

    /** Clears the cached binder on service death so it will be re-fetched next call. */
    private val deathRecipient = IBinder.DeathRecipient {
        if (DEBUG) Log.d(TAG, "DisplayFeature service died — binder cleared")
        displayFeature = null
    }

    /** Returns a live IDisplayFeature binder, blocking until it is available. */
    @Synchronized
    private fun getDisplayFeature(): IDisplayFeature? {
        displayFeature?.let { if (it.asBinder().isBinderAlive) return it }
        return runCatching {
            val binder = ServiceManager.waitForService(
                "vendor.xiaomi.hardware.displayfeature_aidl.IDisplayFeature/default",
            )
            IDisplayFeature.Stub.asInterface(binder).also { service ->
                service?.asBinder()?.linkToDeath(deathRecipient, 0)
                displayFeature = service
                if (DEBUG) Log.d(TAG, "Connected to DisplayFeature service")
            }
        }.onFailure { e ->
            Log.e(TAG, "Failed to get DisplayFeature service", e)
        }.getOrNull()
    }

    /**
     * Calls [IDisplayFeature.setFeature] on display 0 asynchronously.
     *
     * @param mode   Feature mode identifier
     * @param value  Feature value
     * @param cookie Feature cookie
     */
    fun setFeature(mode: Int, value: Int, cookie: Int) {
        Thread {
            val feature = getDisplayFeature() ?: run {
                if (DEBUG) Log.d(TAG, "DisplayFeature is null — skipping setFeature")
                return@Thread
            }
            runCatching {
                if (DEBUG) Log.d(TAG, "setFeature: mode=$mode value=$value cookie=$cookie")
                feature.setFeature(/* displayId= */ 0, mode, value, cookie)
            }.onFailure { e ->
                Log.e(TAG, "setFeature failed!", e)
            }
        }.start()
    }
}
