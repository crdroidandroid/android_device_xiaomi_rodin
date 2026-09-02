/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.cpu

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import java.util.concurrent.Executors

/** Applies the saved CPU configuration from a serialized worker at locked boot. */
class CpuControlService : Service() {

    private val executor = Executors.newSingleThreadExecutor()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        executor.execute {
            val applied = CpuControlManager(applicationContext).applySavedConfiguration()
            if (!applied) Log.w(TAG, "Saved CPU configuration was only partially applied")
            stopSelfResult(startId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "CpuControlService"

        fun applySavedConfiguration(context: Context) {
            context.startService(Intent(context, CpuControlService::class.java))
        }
    }
}
