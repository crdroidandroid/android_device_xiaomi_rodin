/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.monitor

import android.app.ActivityManager
import android.content.Context
import com.xiaomi.settings.utils.readFile
import com.xiaomi.settings.utils.readOneLine
import java.util.Locale

data class MemorySnapshot(
    val totalKb: Long,
    val freeKb: Long,
    val availableKb: Long,
    val cachedKb: Long,
    val swapTotalKb: Long,
    val swapFreeKb: Long,
) {
    val usedKb: Long get() = (totalKb - availableKb).coerceAtLeast(0L)
}

fun readMemorySnapshot(): MemorySnapshot? {
    val values = readFile("/proc/meminfo")
        ?.lineSequence()
        ?.mapNotNull { line ->
            val key = line.substringBefore(':', "").trim()
            val value = Regex("(\\d+)").find(line.substringAfter(':', ""))
                ?.groupValues?.get(1)?.toLongOrNull()
            if (key.isNotEmpty() && value != null) key to value else null
        }
        ?.toMap()
        ?: return null

    return MemorySnapshot(
        totalKb = values["MemTotal"] ?: return null,
        freeKb = values["MemFree"] ?: return null,
        availableKb = values["MemAvailable"] ?: return null,
        cachedKb = values["Cached"] ?: 0L,
        swapTotalKb = values["SwapTotal"] ?: 0L,
        swapFreeKb = values["SwapFree"] ?: 0L,
    )
}

data class ProcessSnapshot(
    val pid: Int,
    val uid: Int,
    val name: String,
    val packageName: String,
    val pssKb: Long,
    val rssKb: Long,
    val cpuPercent: Float,
)

class ProcessSampler(context: Context) {
    private val activityManager = context.getSystemService(ActivityManager::class.java)
    private val lastTicks = mutableMapOf<Int, Long>()
    private var lastSystemTicks = 0L

    private fun processTicks(pid: Int): Long? {
        val content = readOneLine("/proc/$pid/stat") ?: return null
        val commandEnd = content.lastIndexOf(')')
        if (commandEnd < 0) return null
        val fields = content.substring(commandEnd + 1).trim().split(' ')
        val user = fields.getOrNull(11)?.toLongOrNull() ?: return null
        val system = fields.getOrNull(12)?.toLongOrNull() ?: return null
        return user + system
    }

    private fun systemTicks(): Long = readOneLine("/proc/stat")
        ?.split(Regex("\\s+"))
        ?.drop(1)
        ?.mapNotNull { it.toLongOrNull() }
        ?.sum()
        ?: 0L

    fun sample(): List<ProcessSnapshot> {
        val processes = runCatching { activityManager.runningAppProcesses }
            .getOrNull() ?: return emptyList()
        val pids = processes.map { it.pid }.distinct().toIntArray()
        if (pids.isEmpty()) return emptyList()

        val memory = runCatching { activityManager.getProcessMemoryInfo(pids) }.getOrNull()
        val pss = memory?.mapIndexed { index, info ->
            pids[index] to info.totalPss.toLong()
        }?.toMap() ?: emptyMap()
        val rss = memory?.mapIndexed { index, info ->
            pids[index] to info.totalRss.toLong()
        }?.toMap() ?: emptyMap()

        val nowSystemTicks = systemTicks()
        val cpu = mutableMapOf<Int, Float>()
        for (pid in pids) {
            val now = processTicks(pid) ?: continue
            val previous = lastTicks.put(pid, now)
            if (previous != null && nowSystemTicks > lastSystemTicks && now >= previous) {
                cpu[pid] = ((now - previous).toFloat() / (nowSystemTicks - lastSystemTicks) * 100f)
                    .coerceIn(0f, 100f)
            }
        }
        lastSystemTicks = nowSystemTicks
        lastTicks.keys.retainAll(pids.toSet())

        return processes.map { process ->
            val processName = process.processName ?: process.pkgList?.firstOrNull() ?: "unknown"
            ProcessSnapshot(
                pid = process.pid,
                uid = process.uid,
                name = processName,
                packageName = process.pkgList?.firstOrNull() ?: processName.substringBefore(':'),
                pssKb = pss[process.pid] ?: 0L,
                rssKb = rss[process.pid] ?: 0L,
                cpuPercent = cpu[process.pid] ?: 0f,
            )
        }.sortedByDescending { it.pssKb }
    }
}

fun formatMemory(kb: Long): String = when {
    kb >= 1_048_576 -> String.format(Locale.getDefault(), "%.2f GB", kb / 1_048_576.0)
    kb >= 1024 -> String.format(Locale.getDefault(), "%.0f MB", kb / 1024.0)
    kb > 0 -> "$kb KB"
    else -> "—"
}
