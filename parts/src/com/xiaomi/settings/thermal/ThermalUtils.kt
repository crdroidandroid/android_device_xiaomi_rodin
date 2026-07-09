/*
 * SPDX-FileCopyrightText: 2020 The LineageOS Project
 * SPDX-FileCopyrightText: 2025 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.thermal

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.UserHandle
import android.provider.MediaStore
import android.telecom.TelecomManager
import androidx.annotation.StringRes
import androidx.preference.PreferenceManager
import com.xiaomi.settings.R
import com.xiaomi.settings.utils.dlog
import com.xiaomi.settings.utils.writeLine

/** Lightweight model for per-app thermal UI — lives in the data layer. */
data class AppThermalEntry(
    val packageName: String,
    val label:       String,
    val profileId:   Int,
)

class ThermalUtils private constructor(private val context: Context) {

    private val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
    private val serviceIntent = Intent(context, ThermalService::class.java)

    var enabled: Boolean = sharedPrefs.getBoolean(THERMAL_ENABLED, true)
        set(value) {
            if (field == value) return
            field = value
            sharedPrefs.edit().putBoolean(THERMAL_ENABLED, value).apply()
            if (value) {
                startService()
            } else {
                stopService()
                setDefaultThermalProfile()
            }
        }

    fun startService() {
        if (!enabled) return
        runCatching {
            context.startServiceAsUser(serviceIntent, UserHandle.CURRENT)
        }
    }

    fun stopService() {
        runCatching {
            context.stopServiceAsUser(serviceIntent, UserHandle.CURRENT)
        }
    }

    fun setThermalProfile(packageName: String) {
        setThermalProfileInternal(packageName)
    }

    fun setChargingThermalProfile() {
        writeSconfig(SCONFIG_CHARGE)
        dlog(TAG, "Charging profile applied (sconfig=$SCONFIG_CHARGE)")
    }

    fun setDefaultThermalProfile() {
        writeSconfig(ThermalState.DEFAULT.sconfig)
        dlog(TAG, "Default profile applied (sconfig=${ThermalState.DEFAULT.sconfig})")
    }

    private fun setThermalProfileInternal(packageName: String) {
        val state = getStateForPackage(packageName)
        writeSconfig(state.sconfig)
        dlog(TAG, "setThermalProfileInternal: $packageName -> $state (sconfig=${state.sconfig})")
    }

    private fun writeSconfig(sconfig: String) {
        runCatching { writeLine(THERMAL_SCONFIG, sconfig) }
            .onFailure { dlog(TAG, "writeSconfig($sconfig) failed: ${it.message}") }
    }

    fun getStateForPackage(packageName: String): ThermalState {
        val key = "$THERMAL_PACKAGE_PREFIX$packageName"
        // Use contains() to distinguish "key absent" from "key set to DEFAULT (id=0)"
        if (sharedPrefs.contains(key)) {
            val savedId = sharedPrefs.getInt(key, ThermalState.DEFAULT.id)
            ThermalState.entries.find { it.id == savedId }?.let { return it }
        }
        return classifyApp(packageName)
    }

    private fun classifyApp(packageName: String): ThermalState {
        val pm = context.packageManager
        runCatching {
            val appInfo = pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            return when {
                isYuanshenApp(packageName)        -> ThermalState.YUANSHEN
                isBenchmarkApp(packageName)       -> ThermalState.NOLIMITS
                isCompetitiveGameApp(packageName) -> ThermalState.GAMING_COMPETITIVE
                isHeavyGameApp(packageName)       -> ThermalState.GAMING_HEAVY
                isGameApp(appInfo)                -> ThermalState.GAMING
                isCameraApp(packageName, pm)      -> ThermalState.CAMERA
                isDialerApp(packageName)          -> ThermalState.DIALER
                isVideoCallApp(packageName)       -> ThermalState.VIDEO_CALL
                is4KVideoApp(packageName)         -> ThermalState.VIDEO_4K
                isVideoApp(packageName)           -> ThermalState.VIDEO
                isMusicApp(appInfo, pm)           -> ThermalState.VIDEO
                isSocialApp(appInfo)              -> ThermalState.VIDEO
                isNavigationApp(packageName, pm)  -> ThermalState.NAVIGATION
                else                              -> ThermalState.DEFAULT
            }
        }.onFailure { e ->
            dlog(TAG, "classifyApp($packageName) failed: ${e.message}")
        }
        return ThermalState.DEFAULT
    }

    private fun isYuanshenApp(packageName: String): Boolean =
        YUANSHEN_PKGS.any { packageName.startsWith(it) }

    private fun isBenchmarkApp(packageName: String): Boolean =
        BENCHMARK_PKGS.any { packageName.startsWith(it) }

    private fun isCompetitiveGameApp(packageName: String): Boolean =
        COMPETITIVE_GAME_PKGS.any { packageName.startsWith(it) }

    private fun isHeavyGameApp(packageName: String): Boolean =
        HEAVY_GAME_PKGS.any { packageName.startsWith(it) }

    private fun isGameApp(appInfo: ApplicationInfo): Boolean =
        appInfo.category == ApplicationInfo.CATEGORY_GAME

    private fun isCameraApp(packageName: String, pm: PackageManager): Boolean =
        pm.queryIntentActivities(
            Intent(MediaStore.ACTION_IMAGE_CAPTURE), PackageManager.MATCH_DEFAULT_ONLY,
        ).any { it.activityInfo.packageName == packageName }

    private fun isDialerApp(packageName: String): Boolean =
        runCatching {
            context.getSystemService(TelecomManager::class.java)
                ?.defaultDialerPackage == packageName
        }.getOrDefault(false)

    private fun isVideoCallApp(packageName: String): Boolean =
        VIDEO_CALL_PKGS.any { packageName.startsWith(it) }

    private fun is4KVideoApp(packageName: String): Boolean =
        VIDEO_4K_PKGS.any { packageName.startsWith(it) }

    private fun isVideoApp(packageName: String): Boolean =
        VIDEO_PKGS.any { packageName.startsWith(it) }

    private fun isMusicApp(appInfo: ApplicationInfo, pm: PackageManager): Boolean =
        appInfo.category == ApplicationInfo.CATEGORY_AUDIO ||
            MUSIC_PKGS.any { appInfo.packageName.startsWith(it) } ||
            pm.queryIntentActivities(
                Intent(Intent.ACTION_VIEW).setType("audio/*"),
                PackageManager.MATCH_DEFAULT_ONLY,
            ).any { it.activityInfo.packageName == appInfo.packageName }

    private fun isSocialApp(appInfo: ApplicationInfo): Boolean =
        appInfo.category == ApplicationInfo.CATEGORY_SOCIAL ||
            SOCIAL_PKGS.any { appInfo.packageName.startsWith(it) }

    private fun isNavigationApp(packageName: String, pm: PackageManager): Boolean =
        pm.queryIntentActivities(
            Intent(Intent.ACTION_VIEW).apply {
                data = android.net.Uri.parse("geo:0,0")
            },
            PackageManager.MATCH_DEFAULT_ONLY,
        ).any { it.activityInfo.packageName == packageName }

    fun writePackage(packageName: String, stateId: Int) {
        val key = "$THERMAL_PACKAGE_PREFIX$packageName"
        if (stateId == ThermalState.DEFAULT.id) {
            // Explicitly remove so contains() correctly returns false = auto-classify
            sharedPrefs.edit().remove(key).apply()
        } else {
            sharedPrefs.edit().putInt(key, stateId).apply()
        }
    }

    fun resetProfiles() {
        sharedPrefs.edit()
            .also { editor ->
                sharedPrefs.all.keys
                    .filter { it.startsWith(THERMAL_PACKAGE_PREFIX) }
                    .forEach { editor.remove(it) }
            }
            .apply()
    }

    /**
     * sconfig → thermal conf mapping (real confs available on device):
     *   0   thermal-normal.conf        DEFAULT
     *   5   thermal-phone.conf         DIALER
     *   6   thermal-nolimits.conf      NOLIMITS (benchmarks)
     *   10  thermal-navigation.conf    NAVIGATION
     *   11  thermal-video.conf         VIDEO (video, music, social)
     *   14  thermal-videochat.conf     VIDEO_CALL
     *   15  thermal-camera.conf        CAMERA
     *   16  thermal-4k.conf            VIDEO_4K
     *   18  thermal-tgame.conf         GAMING_HEAVY
     *   19  thermal-mgame.conf         GAMING
     *   20  thermal-yuanshen.conf      YUANSHEN
     *   27  thermal-charge.conf        CHARGE (internal)
     *   700 thermal-cgame.conf         GAMING_COMPETITIVE
     */
    enum class ThermalState(
        val id: Int,
        val sconfig: String,
        @param:StringRes val label: Int,
        val userSelectable: Boolean = true,
    ) {
        DEFAULT            (0,  "0",   R.string.thermal_default),
        NOLIMITS           (1,  "6",   R.string.thermal_nolimits),
        NAVIGATION         (2,  "10",  R.string.thermal_navigation),
        DIALER             (3,  "5",   R.string.thermal_dialer),
        VIDEO_CALL         (4,  "14",  R.string.thermal_video_call),
        CAMERA             (5,  "15",  R.string.thermal_camera),
        VIDEO              (6,  "11",  R.string.thermal_video),
        VIDEO_4K           (7,  "16",  R.string.thermal_video_4k),
        GAMING             (8,  "19",  R.string.thermal_gaming),
        GAMING_HEAVY       (9,  "18",  R.string.thermal_gaming_heavy),
        GAMING_COMPETITIVE (10, "700", R.string.thermal_gaming_competitive),
        YUANSHEN           (11, "20",  R.string.thermal_yuanshen),
        CHARGE             (12, "27",  R.string.thermal_charge, userSelectable = false),
    }

    companion object {
        private const val TAG                  = "ThermalUtils"
        const val THERMAL_ENABLED              = "thermal_enabled"
        const val THERMAL_PACKAGE_PREFIX       = "thermal_package_"
        const val THERMAL_SCONFIG              = "/sys/devices/virtual/thermal/thermal_message/sconfig"
        const val SCONFIG_CHARGE               = "27"

        private val YUANSHEN_PKGS = listOf(
            "com.miHoYo.Yuanshen",
            "com.miHoYo.GenshinImpact",
            "com.miHoYo.hkrpg",
            "com.HoYoverse.Nap",
            "com.kurogame.wutheringwaves",
            "com.kurogame.gplay.punishing.grayraven.en"
        )
        private val BENCHMARK_PKGS = listOf(
            "com.antutu",
            "com.texts.throttlebench",
            "com.futuremark",
            "com.primatelabs",
            "com.passmark"
        )
        private val COMPETITIVE_GAME_PKGS = listOf(
            "com.tencent.ig",
            "com.vng.pubgmobile",
            "com.pubg.krmobile",
            "com.rekoo.pubgm",
            "com.dts.freefireth",
            "com.dts.freefiremax",
            "com.mobile.legends",
            "com.riotgames.league.wildrift",
            "com.epicgames.fortnite",
            "com.tencent.tmgp.sgame"
        )
        private val HEAVY_GAME_PKGS = listOf(
            "com.tencent.tmgp.pubgmhd",
            "com.activision.callofduty",
            "com.ea.games",
            "com.pubg.imobile",
            "com.LevelInfinite.Hotta.tof"
        )
        private val VIDEO_CALL_PKGS = listOf(
            "com.google.android.apps.tachyon",
            "com.whatsapp",
            "us.zoom.videomeetings",
            "com.microsoft.teams",
            "us.zoom.videomeetings",
            "com.discord",
            "com.viber.voip",
            "com.skype.raider",
            "com.streamlabs"
        )
        private val VIDEO_4K_PKGS = listOf(
            "com.netflix.mediaclient"
        )
        private val VIDEO_PKGS = listOf(
            "com.netflix",
            "com.amazon.avod",
            "com.amazon.amazonvideo.livingroom",
            "com.disney.disneyplus",
            "com.hotstar",
            "com.jio.media.jiotvplus",
            "com.jio.jioplay.tv",
            "in.startv.hotstar",
            "com.sonyliv",
            "com.hbo.hbonow",
            "com.hulu.plus",
            "com.apple.atve",
            "com.plexapp.android",
            "com.google.android.youtube",
            "com.google.android.apps.youtube.kids",
            "tv.twitch.android.app",
            "com.google.android.youtube.creator",
            "com.bilibili.app.in",
            "com.dailymotion.dailymotion",
            "com.viki.android",
            "com.ryzmedia.tatasky",
            "tv.accedo.airtel.wynk",
            "com.graymatrix.did",
            "com.crunchyroll.crunchyroid",
            "com.google.android.videos"
        )
        private val MUSIC_PKGS = listOf(
            "com.spotify.music",
            "com.apple.android.music",
            "com.soundcloud.android",
            "com.pandora.android",
            "com.amazon.mp3",
            "com.google.android.apps.youtube.music",
            "deezer.android.app"
        )
        private val SOCIAL_PKGS = listOf(
            "org.telegram.messenger",
            "com.twitter.android",
            "com.snapchat.android",
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill",
            "com.facebook.orca",
            "com.facebook.katana",
            "com.instagram.android",
            "com.vkontakte.android",
            "com.reddit.frontpage",
            "com.pinterest"
        )

        @Volatile private var instance: ThermalUtils? = null

        fun getInstance(context: Context): ThermalUtils =
            instance ?: synchronized(this) {
                instance ?: ThermalUtils(context.applicationContext).also { instance = it }
            }
    }
}
