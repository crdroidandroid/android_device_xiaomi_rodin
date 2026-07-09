/*
 * SPDX-FileCopyrightText: 2023-2025 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.utils

import android.util.Log

private const val MAIN_TAG = "XMParts"

/**
 * Emits a debug log under the combined tag "XMParts-[tag]" when either the
 * main tag or the per-component tag has debug logging enabled via:
 *   adb shell setprop log.tag.XMParts DEBUG
 */
fun dlog(tag: String, msg: String) {
    if (Log.isLoggable(MAIN_TAG, Log.DEBUG) || Log.isLoggable(tag, Log.DEBUG)) {
        Log.d("$MAIN_TAG-$tag", msg)
    }
}
