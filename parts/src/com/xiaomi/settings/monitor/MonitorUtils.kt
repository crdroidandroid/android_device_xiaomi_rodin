/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 *
 * Data collection for the System Monitor section. All reads are
 * best-effort: every source is wrapped in runCatching and returns
 * null/empty on failure so the UI degrades gracefully (sepolicy gaps,
 * unsupported kernels, MTK node layout differences, …).
 */

package com.xiaomi.settings.monitor

import android.util.Log
import com.xiaomi.settings.utils.readFile
import com.xiaomi.settings.utils.readOneLine
import java.io.File

private const val TAG = "MonitorUtils"

// ──────────────────────────────────────────────────────────────────────────
// CPU
// ──────────────────────────────────────────────────────────────────────────

data class CpuCluster(
    val name:        String,
    val cores:       List<Int>,
    val curFreqKhz:  Long,
    val maxFreqKhz:  Long,
    val minFreqKhz:  Long,
    val governor:    String,
) {
    val loadFraction: Float
        get() = if (maxFreqKhz > 0) (curFreqKhz.toFloat() / maxFreqKhz).coerceIn(0f, 1f) else 0f
}

private fun policyDirs(): List<File> =
    File("/sys/devices/system/cpu/cpufreq")
        .listFiles { f -> f.name.startsWith("policy") }
        ?.sortedBy { it.name.removePrefix("policy").toIntOrNull() ?: Int.MAX_VALUE }
        ?: emptyList()

fun readCpuClusters(): List<CpuCluster> = policyDirs().mapNotNull { dir ->
    val id = dir.name.removePrefix("policy").toIntOrNull() ?: return@mapNotNull null
    val related = readOneLine("${dir.absolutePath}/related_cpus")
        ?.split(" ")
        ?.mapNotNull { it.trim().toIntOrNull() }
        ?: listOf(id)
    CpuCluster(
        name       = if (related.size == 1) "CPU${related.first()}"
                     else "CPU ${related.first()}-${related.last()}",
        cores      = related,
        curFreqKhz = readOneLine("${dir.absolutePath}/scaling_cur_freq")?.toLongOrNull() ?: 0L,
        maxFreqKhz = readOneLine("${dir.absolutePath}/cpuinfo_max_freq")
            ?.toLongOrNull()
            ?: readOneLine("${dir.absolutePath}/scaling_max_freq")?.toLongOrNull()
            ?: 0L,
        minFreqKhz = readOneLine("${dir.absolutePath}/cpuinfo_min_freq")
            ?.toLongOrNull()
            ?: readOneLine("${dir.absolutePath}/scaling_min_freq")?.toLongOrNull()
            ?: 0L,
        governor   = readOneLine("${dir.absolutePath}/scaling_governor") ?: "—",
    )
}

fun readCpuCount(): Int {
    val present = readOneLine("/sys/devices/system/cpu/present")
    return present?.split("-")?.lastOrNull()?.toIntOrNull()?.plus(1)
        ?: Runtime.getRuntime().availableProcessors()
}

/** Samples overall CPU load from /proc/stat deltas. */
class CpuLoadSampler {
    private var lastTotal  = 0L
    private var lastIdle   = 0L

    fun sample(): Float {
        val line = readOneLine("/proc/stat") ?: return 0f
        val fields = line.split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
        if (fields.size < 4) return 0f
        val total = fields.sum()
        val idle  = fields.getOrElse(3) { 0L } + fields.getOrElse(4) { 0L }
        val fraction = if (lastTotal > 0 && total > lastTotal) {
            val busy = (total - lastTotal) - (idle - lastIdle)
            (busy.toFloat() / (total - lastTotal)).coerceIn(0f, 1f)
        } else 0f
        lastTotal = total
        lastIdle  = idle
        return fraction
    }
}

fun readLoadAvg(): Triple<Float, Float, Float>? {
    val parts = readOneLine("/proc/loadavg")?.split(" ")
    if (parts == null || parts.size < 3) return null
    return Triple(
        parts[0].toFloatOrNull() ?: 0f,
        parts[1].toFloatOrNull() ?: 0f,
        parts[2].toFloatOrNull() ?: 0f,
    )
}

// ──────────────────────────────────────────────────────────────────────────
// GPU (MTK: /proc/gpufreqv2, /proc/gpufreq, /proc/ged/hal/gpu_utilization)
// ──────────────────────────────────────────────────────────────────────────

fun readGpuFreqMhz(): Long? {
    val v2 = readFile("/proc/gpufreqv2")
        ?.let { Regex("GPUFreq=(\\d+)").find(it)?.groupValues?.get(1)?.toLongOrNull() }
    if (v2 != null) return if (v2 > 50_000_000) v2 / 1000 else v2

    val legacy = readFile("/proc/gpufreq/gpufreq_var_dump")
        ?.let { Regex("GPUFreq:\\s*(\\d+)").find(it)?.groupValues?.get(1)?.toLongOrNull() }
    if (legacy != null) return if (legacy > 50_000_000) legacy / 1000 else legacy
    return null
}

fun readGpuLoadPercent(): Float? {
    val dump = readFile("/proc/ged/hal/gpu_utilization") ?: return null
    val busy  = Regex("GPUBusy:\\s*(\\d+)").find(dump)?.groupValues?.get(1)?.toFloatOrNull()
    val total = Regex("GPUTotal:\\s*(\\d+)").find(dump)?.groupValues?.get(1)?.toFloatOrNull()
    if (busy != null && total != null && total > 0f) return (busy / total).coerceIn(0f, 1f)
    return null
}

// ──────────────────────────────────────────────────────────────────────────
// Thermal
// ──────────────────────────────────────────────────────────────────────────

data class ThermalZone(val name: String, val tempC: Float?)

private fun thermalZoneDirs(): List<File> =
    File("/sys/devices/virtual/thermal")
        .listFiles { f -> f.name.startsWith("thermal_zone") && f.name.removePrefix("thermal_zone").toIntOrNull() != null }
        ?.sortedBy { it.name.removePrefix("thermal_zone").toIntOrNull() }
        ?: emptyList()

fun readThermalZones(): List<ThermalZone> = thermalZoneDirs().map { dir ->
    val name = readOneLine("${dir.absolutePath}/type") ?: dir.name
    val raw  = readOneLine("${dir.absolutePath}/temp")?.toFloatOrNull()
    val tempC = when {
        raw == null                     -> null
        raw < -1000f || raw > 400000f   -> null // invalid sentinel values
        raw > 1000f                     -> raw / 1000f // milli°C
        else                            -> raw
    }
    ThermalZone(name, tempC)
}

/** Finds a zone whose type contains any of [needles] (case-insensitive). */
fun findZone(zones: List<ThermalZone>, needles: List<String>): ThermalZone? =
    zones.firstOrNull { zone ->
        needles.any { zone.name.contains(it, ignoreCase = true) }
    }

// ──────────────────────────────────────────────────────────────────────────
// Memory
// ──────────────────────────────────────────────────────────────────────────

data class MemInfo(
    val totalKb:   Long,
    val freeKb:    Long,
    val availableKb: Long,
    val cachedKb:  Long,
    val buffersKb: Long,
    val swapTotalKb: Long,
    val swapFreeKb: Long,
) {
    val usedKb: Long get() = (totalKb - availableKb).coerceAtLeast(0L)
}

private fun meminfoValue(key: String): Long? {
    val line = readFile("/proc/meminfo")?.lines()?.firstOrNull { it.startsWith(key) } ?: return null
    return Regex("(\\d+)").find(line)?.groupValues?.get(1)?.toLongOrNull()
}

fun readMemInfo(): MemInfo? {
    val total    = meminfoValue("MemTotal")
    val free     = meminfoValue("MemFree")
    val available = meminfoValue("MemAvailable")
    if (total == null || free == null || available == null) return null
    return MemInfo(
        totalKb     = total,
        freeKb      = free,
        availableKb = available,
        cachedKb    = meminfoValue("Cached") ?: 0L,
        buffersKb   = meminfoValue("Buffers") ?: 0L,
        swapTotalKb = meminfoValue("SwapTotal") ?: 0L,
        swapFreeKb  = meminfoValue("SwapFree") ?: 0L,
    )
}

// ──────────────────────────────────────────────────────────────────────────
// Processes (task manager)
// ──────────────────────────────────────────────────────────────────────────

// ──────────────────────────────────────────────────────────────────────────
// Misc / device info
// ──────────────────────────────────────────────────────────────────────────

fun readUptimeSeconds(): Long? =
    readOneLine("/proc/uptime")?.substringBefore(' ')?.toDoubleOrNull()?.toLong()

fun formatUptime(totalSeconds: Long): String {
    val days = totalSeconds / 86400
    val hours = (totalSeconds % 86400) / 3600
    val mins = (totalSeconds % 3600) / 60
    return when {
        days > 0   -> "${days}d ${hours}h ${mins}m"
        hours > 0  -> "${hours}h ${mins}m"
        else       -> "${mins}m"
    }
}

fun readKernelVersion(): String? =
    readOneLine("/proc/version")?.replace(Regex("\\s+"), " ")

fun readCpuHardware(): String? {
    val lines = readFile("/proc/cpuinfo")?.lines() ?: return null
    return lines.firstOrNull { it.startsWith("Hardware") }
        ?.substringAfter(':')?.trim()
}

fun formatFrequency(khz: Long?): String {
    if (khz == null || khz <= 0) return "—"
    return if (khz >= 1_000_000) "%.2f GHz".format(khz / 1_000_000.0)
    else "%.0f MHz".format(khz / 1000.0)
}

fun formatBytes(kb: Long): String {
    if (kb <= 0) return "—"
    return when {
        kb >= 1_048_576 -> "%.2f GB".format(kb / 1_048_576.0)
        kb >= 1024      -> "%.0f MB".format(kb / 1024.0)
        else            -> "$kb KB"
    }
}

fun logMonitor(tag: String, msg: String) = Log.d(TAG, "[$tag] $msg")
