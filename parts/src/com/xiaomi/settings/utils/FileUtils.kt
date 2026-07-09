/*
 * SPDX-FileCopyrightText: 2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: 2025 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.utils

import android.util.Log
import java.io.File

private const val TAG = "FileUtils"

/** Reads the first line of [fileName]. Returns null on any failure. */
fun readOneLine(fileName: String): String? =
    runCatching { File(fileName).useLines { it.firstOrNull() } }
        .onFailure { e -> Log.e(TAG, "Could not read from file $fileName", e) }
        .getOrNull()

/** Writes [value] to [fileName]. Returns true on success, false on failure. */
fun writeLine(fileName: String, value: String): Boolean =
    runCatching { File(fileName).writeText(value) }
        .onFailure { e -> Log.e(TAG, "Could not write to file $fileName", e) }
        .isSuccess

/** Returns true if [fileName] exists on the filesystem. */
fun fileExists(fileName: String): Boolean = File(fileName).exists()

/** Returns true if [fileName] exists and is readable. */
fun isFileReadable(fileName: String): Boolean =
    File(fileName).let { it.exists() && it.canRead() }

/** Returns true if [fileName] exists and is writable. */
fun isFileWritable(fileName: String): Boolean =
    File(fileName).let { it.exists() && it.canWrite() }

/** Deletes [fileName]. Returns true on success. */
fun delete(fileName: String): Boolean =
    runCatching { File(fileName).delete() }
        .onFailure { e -> Log.w(TAG, "Failed to delete $fileName", e) }
        .getOrDefault(false)

/** Renames [srcPath] to [dstPath]. Returns true on success. */
fun rename(srcPath: String, dstPath: String): Boolean =
    runCatching { File(srcPath).renameTo(File(dstPath)) }
        .onFailure { e -> Log.w(TAG, "Failed to rename $srcPath to $dstPath", e) }
        .getOrDefault(false)
