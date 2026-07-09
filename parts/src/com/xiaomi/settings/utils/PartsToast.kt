/*
 * SPDX-FileCopyrightText: 2025 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.annotation.StringRes

object PartsToast {

    private val handler = Handler(Looper.getMainLooper())
    private var current: Toast? = null

    fun show(context: Context, @StringRes resId: Int, long: Boolean = false) {
        handler.post {
            current?.cancel()
            current = Toast.makeText(
                context.applicationContext,
                resId,
                if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT,
            ).also { it.show() }
        }
    }

    fun show(context: Context, message: String, long: Boolean = false) {
        handler.post {
            current?.cancel()
            current = Toast.makeText(
                context.applicationContext,
                message,
                if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT,
            ).also { it.show() }
        }
    }

    fun cancel() {
        handler.post { current?.cancel() }
    }
}
