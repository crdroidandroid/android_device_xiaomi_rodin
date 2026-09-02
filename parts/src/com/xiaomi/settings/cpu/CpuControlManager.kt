/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.cpu

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.xiaomi.settings.thermal.ThermalUtils
import java.io.File
import kotlin.math.roundToInt

/** User-facing CPU profiles. Extreme mode also selects the vendor no-limits thermal profile. */
enum class CpuProfile(val preferenceValue: String) {
    ECONOMY("economy"),
    NORMAL("normal"),
    BOOST("boost"),
    EXTREME("extreme"),
    CUSTOM("custom");

    companion object {
        fun fromPreference(value: String?): CpuProfile =
            entries.firstOrNull { it.preferenceValue == value } ?: NORMAL
    }
}

data class CpuPolicyState(
    val id: Int,
    val cpus: List<Int>,
    val frequencies: List<Long>,
    val minimumFrequency: Long,
    val maximumFrequency: Long,
)

data class CpuCoreState(
    val id: Int,
    val online: Boolean,
    val canToggle: Boolean,
)

data class CpuControlState(
    val profile: CpuProfile,
    val policies: List<CpuPolicyState>,
    val cores: List<CpuCoreState>,
    val thermalThrottlingDisabled: Boolean,
)

/**
 * Discovers the cpufreq topology at runtime and uses rodin's stable product frequency tables.
 */
class CpuControlManager(context: Context) {

    private val storageContext = context.createDeviceProtectedStorageContext()
    private val preferences: SharedPreferences =
        storageContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val thermalUtils = ThermalUtils.getInstance(context.applicationContext)

    fun readState(): CpuControlState = CpuControlState(
        profile = selectedProfile,
        policies = discoverPolicies(),
        cores = discoverCores(),
        thermalThrottlingDisabled = thermalUtils.throttlingDisabled,
    )

    fun selectProfile(profile: CpuProfile): Boolean {
        require(profile != CpuProfile.CUSTOM) { "Custom is selected through manual controls" }
        val previousProfile = selectedProfile
        val editor = preferences.edit()

        if (profile == CpuProfile.EXTREME && previousProfile != CpuProfile.EXTREME) {
            editor.putBoolean(
                KEY_THERMAL_DISABLED_BEFORE_EXTREME,
                thermalUtils.throttlingDisabled,
            )
        } else if (previousProfile == CpuProfile.EXTREME && profile != CpuProfile.EXTREME) {
            thermalUtils.throttlingDisabled = preferences.getBoolean(
                KEY_THERMAL_DISABLED_BEFORE_EXTREME,
                false,
            )
            editor.remove(KEY_THERMAL_DISABLED_BEFORE_EXTREME)
        }

        editor.putString(KEY_PROFILE, profile.preferenceValue).apply()
        if (profile == CpuProfile.EXTREME) thermalUtils.throttlingDisabled = true
        return applySavedConfiguration()
    }

    fun setThermalThrottlingDisabled(disabled: Boolean): Boolean {
        if (selectedProfile == CpuProfile.EXTREME && !disabled) return false
        thermalUtils.throttlingDisabled = disabled
        return applySavedConfiguration()
    }

    fun setFrequencyRange(policyId: Int, minimum: Long, maximum: Long): Boolean {
        val policy = discoverPolicies().firstOrNull { it.id == policyId } ?: return false
        val selectedMinimum = nearestFrequency(policy.frequencies, minimum)
        val selectedMaximum = nearestFrequency(policy.frequencies, maximum)
        val low = minOf(selectedMinimum, selectedMaximum)
        val high = maxOf(selectedMinimum, selectedMaximum)

        val editor = preferences.edit()
        snapshotManualConfiguration(editor)
        editor
            .putLong(manualMinimumKey(policyId), low)
            .putLong(manualMaximumKey(policyId), high)
            .putString(KEY_PROFILE, CpuProfile.CUSTOM.preferenceValue)
            .apply()
        return applySavedConfiguration()
    }

    fun setCoreOnline(cpuId: Int, online: Boolean): Boolean {
        if (cpuId == 0 && !online) return false
        val core = discoverCores().firstOrNull { it.id == cpuId } ?: return false
        if (!core.canToggle) return online == core.online

        val editor = preferences.edit()
        snapshotManualConfiguration(editor)
        editor
            .putBoolean(manualCoreKey(cpuId), online)
            .putString(KEY_PROFILE, CpuProfile.CUSTOM.preferenceValue)
            .apply()
        return applySavedConfiguration()
    }

    /** Re-applies the persisted profile. Called from locked boot by [CpuControlService]. */
    fun applySavedConfiguration(): Boolean {
        val profile = selectedProfile
        if (profile == CpuProfile.EXTREME && !thermalUtils.throttlingDisabled) {
            thermalUtils.throttlingDisabled = true
        }
        val initialPolicies = discoverPolicies()
        val cores = discoverCores()
        if (initialPolicies.isEmpty() || cores.isEmpty()) return false

        val desiredCoreStates = desiredCoreStates(profile, initialPolicies, cores)
        var success = true

        // Bring required cores online before touching their policy directories.
        desiredCoreStates
            .filterValues { it }
            .forEach { (cpu, online) ->
                if (cpu != 0) success = writeCoreState(cpu, online) && success
            }

        // Policies belonging to a fully offline cluster become visible again here.
        val policies = discoverPolicies()
        policies.forEachIndexed { index, policy ->
            val range = desiredFrequencyRange(profile, policy, index, policies.size)
            success = writeFrequencyRange(policy.id, range.first, range.second) && success
        }

        // Offline last, in descending order, so the policy can be configured first.
        desiredCoreStates
            .filterValues { !it }
            .keys
            .sortedDescending()
            .forEach { cpu ->
                if (cpu != 0) success = writeCoreState(cpu, false) && success
            }

        return success
    }

    private val selectedProfile: CpuProfile
        get() = CpuProfile.fromPreference(
            preferences.getString(KEY_PROFILE, CpuProfile.NORMAL.preferenceValue),
        )

    private fun discoverPolicies(): List<CpuPolicyState> =
        CPUFREQ_ROOT.listFiles()
            ?.filter { it.isDirectory && POLICY_REGEX.matches(it.name) }
            ?.mapNotNull(::readPolicy)
            ?.sortedBy { it.id }
            .orEmpty()

    private fun readPolicy(directory: File): CpuPolicyState? {
        val id = POLICY_REGEX.matchEntire(directory.name)?.groupValues?.get(1)?.toIntOrNull()
            ?: return null
        val cpuInfoMinimum = readLong(File(directory, "cpuinfo_min_freq")) ?: return null
        val cpuInfoMaximum = readLong(File(directory, "cpuinfo_max_freq")) ?: return null
        val frequencies = readAvailableFrequencies(
            directory = directory,
            policyId = id,
            hardwareMinimum = cpuInfoMinimum,
            hardwareMaximum = cpuInfoMaximum,
        )
        val minimum = readLong(File(directory, "scaling_min_freq")) ?: cpuInfoMinimum
        val maximum = readLong(File(directory, "scaling_max_freq")) ?: cpuInfoMaximum
        val cpus = readCpuList(File(directory, "related_cpus"))
            .ifEmpty { readCpuList(File(directory, "affected_cpus")) }
            .ifEmpty { listOf(id) }

        return CpuPolicyState(
            id = id,
            cpus = cpus,
            frequencies = frequencies,
            minimumFrequency = minimum,
            maximumFrequency = maximum,
        )
    }

    private fun readAvailableFrequencies(
        directory: File,
        policyId: Int,
        hardwareMinimum: Long,
        hardwareMaximum: Long,
    ): List<Long> {
        val advertised = readNumbers(File(directory, "scaling_available_frequencies"))
        val productTable = if (thermalUtils.throttlingDisabled) {
            RODIN_UNRESTRICTED_POLICY_FREQUENCIES[policyId]
        } else {
            RODIN_THERMAL_POLICY_FREQUENCIES[policyId]
        }

        // MTK exposes cpuinfo_max_freq, time_in_state and boost frequencies as
        // dynamic values on this device. Reading them made the selectable range
        // grow only after a cluster had reached an OPP, then shrink again after
        // reopening XiaomiParts. Rodin's power profile is the stable source of
        // truth for the shipped CPU bin. The normal vendor thermal profile caps
        // policy0/4/7 at 1.20/2.80/2.70 GHz; the no-limits profile exposes the
        // full 2.10/3.00/3.25 GHz product table.
        if (productTable != null) {
            val supportedProductTable = productTable.filter { it >= hardwareMinimum }
            if (supportedProductTable.isNotEmpty()) {
                val productMaximum = supportedProductTable.last()
                return (advertised + supportedProductTable + hardwareMinimum)
                    .filter { it in hardwareMinimum..productMaximum }
                    .distinct()
                    .sorted()
            }
        }

        // Unknown policies retain the generic kernel-discovery path.
        return (advertised + listOf(hardwareMinimum, hardwareMaximum))
            .filter { it in hardwareMinimum..hardwareMaximum }
            .distinct()
            .sorted()
    }

    private fun discoverCores(): List<CpuCoreState> {
        val possible = readCpuRange(File(CPU_ROOT, "possible"))
            .ifEmpty {
                CPU_ROOT.listFiles()
                    ?.mapNotNull { CPU_REGEX.matchEntire(it.name)?.groupValues?.get(1)?.toIntOrNull() }
                    ?.sorted()
                    .orEmpty()
            }

        return possible.map { cpu ->
            val onlineFile = File(CPU_ROOT, "cpu$cpu/online")
            CpuCoreState(
                id = cpu,
                online = if (cpu == 0 || !onlineFile.exists()) {
                    true
                } else {
                    readLong(onlineFile) != 0L
                },
                canToggle = cpu != 0 && onlineFile.exists(),
            )
        }
    }

    private fun desiredCoreStates(
        profile: CpuProfile,
        policies: List<CpuPolicyState>,
        cores: List<CpuCoreState>,
    ): Map<Int, Boolean> {
        val efficiencyCpus = policies.firstOrNull()?.cpus.orEmpty().toSet()
        return cores.associate { core ->
            val online = when (profile) {
                CpuProfile.ECONOMY -> core.id == 0 || core.id in efficiencyCpus
                CpuProfile.NORMAL,
                CpuProfile.BOOST,
                CpuProfile.EXTREME -> true
                CpuProfile.CUSTOM -> preferences.getBoolean(
                    manualCoreKey(core.id),
                    core.online,
                )
            }
            core.id to online
        }
    }

    private fun desiredFrequencyRange(
        profile: CpuProfile,
        policy: CpuPolicyState,
        policyIndex: Int,
        policyCount: Int,
    ): Pair<Long, Long> {
        val frequencies = policy.frequencies
        val hardwareMinimum = frequencies.first()
        val hardwareMaximum = frequencies.last()
        return when (profile) {
            CpuProfile.ECONOMY -> hardwareMinimum to frequencyAt(frequencies, 0.50f)
            CpuProfile.NORMAL -> hardwareMinimum to hardwareMaximum
            CpuProfile.BOOST -> frequencyAt(frequencies, 0.40f) to hardwareMaximum
            CpuProfile.EXTREME -> hardwareMaximum to hardwareMaximum
            CpuProfile.CUSTOM -> {
                val savedMinimum = preferences.getLong(
                    manualMinimumKey(policy.id),
                    policy.minimumFrequency,
                )
                val savedMaximum = preferences.getLong(
                    manualMaximumKey(policy.id),
                    policy.maximumFrequency,
                )
                val minimum = nearestFrequency(frequencies, savedMinimum)
                val maximum = nearestFrequency(frequencies, savedMaximum)
                minOf(minimum, maximum) to maxOf(minimum, maximum)
            }
        }.also {
            if (DEBUG) {
                Log.d(
                    TAG,
                    "policy${policy.id} ($policyIndex/$policyCount): ${it.first}-${it.second}",
                )
            }
        }
    }

    private fun snapshotManualConfiguration(editor: SharedPreferences.Editor) {
        if (selectedProfile == CpuProfile.CUSTOM) return
        discoverPolicies().forEach { policy ->
            editor.putLong(manualMinimumKey(policy.id), policy.minimumFrequency)
            editor.putLong(manualMaximumKey(policy.id), policy.maximumFrequency)
        }
        discoverCores().forEach { core ->
            editor.putBoolean(manualCoreKey(core.id), core.online)
        }
    }

    private fun writeFrequencyRange(policyId: Int, minimum: Long, maximum: Long): Boolean {
        val directory = File(CPUFREQ_ROOT, "policy$policyId")
        val minimumFile = File(directory, "scaling_min_freq")
        val maximumFile = File(directory, "scaling_max_freq")
        val hardwareMinimum = readLong(File(directory, "cpuinfo_min_freq")) ?: minimum

        // Relax the floor first so lowering the ceiling cannot fail with EINVAL.
        return writeLong(minimumFile, hardwareMinimum) &&
            writeLong(maximumFile, maximum) &&
            writeLong(minimumFile, minimum)
    }

    private fun writeCoreState(cpuId: Int, online: Boolean): Boolean =
        writeLong(File(CPU_ROOT, "cpu$cpuId/online"), if (online) 1L else 0L)

    private fun writeLong(file: File, value: Long): Boolean = runCatching {
        file.writeText(value.toString())
        true
    }.onFailure { error ->
        Log.e(TAG, "Unable to write $value to ${file.path}", error)
    }.getOrDefault(false)

    private fun readLong(file: File): Long? =
        runCatching { file.bufferedReader().use { it.readLine()?.trim()?.toLongOrNull() } }
            .getOrNull()

    private fun readNumbers(file: File): List<Long> =
        runCatching {
            file.readText().trim().split(WHITESPACE_REGEX).mapNotNull(String::toLongOrNull)
        }.getOrDefault(emptyList())

    private fun readCpuList(file: File): List<Int> =
        runCatching { file.readText().trim().split(WHITESPACE_REGEX).mapNotNull(String::toIntOrNull) }
            .getOrDefault(emptyList())

    private fun readCpuRange(file: File): List<Int> = runCatching {
        file.readText().trim().split(',').flatMap { range ->
            val bounds = range.split('-').mapNotNull(String::toIntOrNull)
            when (bounds.size) {
                1 -> listOf(bounds[0])
                2 -> (bounds[0]..bounds[1]).toList()
                else -> emptyList()
            }
        }
    }.getOrDefault(emptyList())

    private fun frequencyAt(frequencies: List<Long>, fraction: Float): Long {
        val index = ((frequencies.lastIndex) * fraction)
            .roundToInt()
            .coerceIn(0, frequencies.lastIndex)
        return frequencies[index]
    }

    private fun nearestFrequency(frequencies: List<Long>, requested: Long): Long =
        frequencies.minByOrNull { kotlin.math.abs(it - requested) } ?: requested

    companion object {
        private const val TAG = "CpuControlManager"
        private val DEBUG = Log.isLoggable(TAG, Log.DEBUG)

        private const val PREFERENCES = "cpu_control"
        private const val KEY_PROFILE = "profile"
        private const val KEY_THERMAL_DISABLED_BEFORE_EXTREME =
            "thermal_disabled_before_extreme"
        private const val KEY_MANUAL_MINIMUM_PREFIX = "manual_minimum_"
        private const val KEY_MANUAL_MAXIMUM_PREFIX = "manual_maximum_"
        private const val KEY_MANUAL_CORE_PREFIX = "manual_core_"

        private val CPU_ROOT = File("/sys/devices/system/cpu")
        private val CPUFREQ_ROOT = File(CPU_ROOT, "cpufreq")
        private val POLICY_REGEX = Regex("policy(\\d+)")
        private val CPU_REGEX = Regex("cpu(\\d+)")
        private val WHITESPACE_REGEX = Regex("\\s+")

        // Mirrors overlay/RodinFrameworksOverlay/res/xml/power_profile.xml.
        // Values are kHz, matching the cpufreq sysfs ABI.
        private val RODIN_UNRESTRICTED_POLICY_FREQUENCIES = mapOf(
            0 to (300_000L..2_100_000L step 100_000L).toList(),
            4 to (400_000L..3_000_000L step 100_000L).toList(),
            7 to ((1_000_000L..3_200_000L step 100_000L).toList() + 3_250_000L),
        )

        private val RODIN_THERMAL_POLICY_FREQUENCIES = mapOf(
            0 to (300_000L..1_200_000L step 100_000L).toList(),
            4 to (400_000L..2_800_000L step 100_000L).toList(),
            7 to (1_000_000L..2_700_000L step 100_000L).toList(),
        )

        private fun manualMinimumKey(policyId: Int) = "$KEY_MANUAL_MINIMUM_PREFIX$policyId"
        private fun manualMaximumKey(policyId: Int) = "$KEY_MANUAL_MAXIMUM_PREFIX$policyId"
        private fun manualCoreKey(cpuId: Int) = "$KEY_MANUAL_CORE_PREFIX$cpuId"
    }
}
