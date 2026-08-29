#
# SPDX-FileCopyrightText: The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#

# Inherit from those products. Most specific first.
$(call inherit-product, $(SRC_TARGET_DIR)/product/core_64_bit_only.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/full_base_telephony.mk)

# A/B
ifeq ($(WITH_GMS),true)
PRODUCT_SYSTEM_PARTITIONS_FILE_SYSTEM_TYPE ?= erofs
else
PRODUCT_SYSTEM_PARTITIONS_FILE_SYSTEM_TYPE ?= ext4
endif

ifeq ($(PRODUCT_SYSTEM_PARTITIONS_FILE_SYSTEM_TYPE),ext4)
$(call inherit-product, $(SRC_TARGET_DIR)/product/virtual_ab_ota/launch_with_vendor_ramdisk.mk)
else
$(call inherit-product, $(SRC_TARGET_DIR)/product/virtual_ab_ota/vabc_features.mk)
PRODUCT_VIRTUAL_AB_COMPRESSION_METHOD := lz4
PRODUCT_VENDOR_PROPERTIES += ro.virtual_ab.compression.threads=true
endif

# FUN time
PRODUCT_VENDOR_PROPERTIES += \
    ro.adb.secure=0 \
    persist.sys.usb.config=adb \
    persist.sys.disable_rescue=1 \
    ro.logd.kernel=true \
    persist.logd.logpersistd.enable=true \
    persist.logd.logpersistd.buffer=all

AB_OTA_POSTINSTALL_CONFIG += \
    RUN_POSTINSTALL_system=true \
    POSTINSTALL_PATH_system=system/bin/otapreopt_script \
    FILESYSTEM_TYPE_system=$(PRODUCT_SYSTEM_PARTITIONS_FILE_SYSTEM_TYPE) \
    POSTINSTALL_OPTIONAL_system=true

PRODUCT_PACKAGES += \
    android.hardware.boot-service.mediatek \
    android.hardware.boot-service.mediatek_recovery

PRODUCT_PACKAGES += \
    create_pl_dev \
    create_pl_dev.recovery

PRODUCT_PACKAGES += \
    otapreopt_script \
    update_engine \
    update_engine_sideload \
    update_verifier

# API
BOARD_SHIPPING_API_LEVEL := 202404
PRODUCT_SHIPPING_API_LEVEL := 35

# Audio
PRODUCT_PACKAGES += \
    audio.primary.default \
    audio.bluetooth.default \
    audio_policy.stub \
    audio.r_submix.default \
    audio.usb.default

PRODUCT_PACKAGES += \
    libaecsw \
    libagc1sw \
    libagc2sw \
    libbassboostsw \
    libbundleaidl \
    libdownmixaidl \
    libdynamicsprocessingaidl \
    libequalizersw \
    libhapticgeneratoraidl \
    libloudnessenhanceraidl \
    libnssw \
    libpreprocessingaidl \
    libpresetreverbsw \
    libreverbaidl \
    libspatializersw \
    libvirtualizersw \
    libvisualizeraidl \
    libvolumesw \
    libextensioneffect

PRODUCT_PACKAGES += \
    MtkInCallService \
    LunarisDolby

PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/configs/audio/audio_effects_config.xml:$(TARGET_COPY_OUT_VENDOR)/etc/audio_effects_config.xml \
    $(LOCAL_PATH)/configs/audio/audio_policy_configuration_a2dp_offload_enable_cg_enable.xml:$(TARGET_COPY_OUT_VENDOR)/etc/audio_policy_configuration.xml \
    $(LOCAL_PATH)/configs/audio/audio_policy_configuration_a2dp_offload_enable_cg_enable.xml:$(TARGET_COPY_OUT_VENDOR)/etc/audio_policy_configuration_a2dp_offload_enable_cg_enable_cts.xml \
    $(LOCAL_PATH)/configs/audio/aurisys_config_hifi3.xml:$(TARGET_COPY_OUT_VENDOR)/etc/aurisys_config_hifi3.xml \
    $(LOCAL_PATH)/configs/audio/aurisys_config_hifi3_fs.xml:$(TARGET_COPY_OUT_VENDOR)/etc/aurisys_config_hifi3_fs.xml \
    $(LOCAL_PATH)/configs/audio/le_audio_codec_capabilities.xml:$(TARGET_COPY_OUT_VENDOR)/etc/le_audio_codec_capabilities.xml \
    $(LOCAL_PATH)/configs/audio/bluetooth_a2dp_offload_ums_offload_audio_policy_configuration.xml:$(TARGET_COPY_OUT_VENDOR)/etc/bluetooth_a2dp_offload_ums_offload_audio_policy_configuration.xml \
    $(LOCAL_PATH)/configs/audio/usb_audio_accessory_only_policy_configuration.xml:$(TARGET_COPY_OUT_VENDOR)/etc/usb_audio_accessory_only_policy_configuration.xml

PRODUCT_COPY_FILES += \
    frameworks/av/services/audiopolicy/config/a2dp_in_audio_policy_configuration_7_0.xml:$(TARGET_COPY_OUT_VENDOR)/etc/a2dp_in_audio_policy_configuration.xml \
    frameworks/av/services/audiopolicy/config/audio_policy_volumes.xml:$(TARGET_COPY_OUT_VENDOR)/etc/audio_policy_volumes.xml \
    frameworks/av/services/audiopolicy/config/default_volume_tables.xml:$(TARGET_COPY_OUT_VENDOR)/etc/default_volume_tables.xml \
    frameworks/av/services/audiopolicy/config/r_submix_audio_policy_configuration.xml:$(TARGET_COPY_OUT_VENDOR)/etc/r_submix_audio_policy_configuration.xml

PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.hardware.audio.low_latency.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.audio.low_latency.xml \
	frameworks/native/data/etc/android.hardware.audio.pro.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.audio.pro.xml \
    frameworks/native/data/etc/android.software.midi.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.software.midi.xml

$(call soong_config_set_bool,android_hardware_audio,skip_speaker_layout_channel_mask_field,true)

# Bluetooth
PRODUCT_PACKAGES += \
    android.hardware.bluetooth-service.mediatek

PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.hardware.bluetooth_le.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.bluetooth_le.xml \
    frameworks/native/data/etc/android.hardware.bluetooth.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.bluetooth.xml

# Boot animation
TARGET_SCREEN_HEIGHT := 2772
TARGET_SCREEN_WIDTH := 1280

# Miui Camera
$(call inherit-product-if-exists, device/xiaomi/rodin-miuicamera/device.mk)

PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.hardware.camera.concurrent.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.camera.concurrent.xml \
    frameworks/native/data/etc/android.hardware.camera.flash-autofocus.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.camera.flash-autofocus.xml \
    frameworks/native/data/etc/android.hardware.camera.full.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.camera.full.xml \
    frameworks/native/data/etc/android.hardware.camera.front.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.camera.front.xml \
    frameworks/native/data/etc/android.hardware.camera.raw.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.camera.raw.xml \
    frameworks/native/data/etc/android.hardware.camera.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.camera.xml

# Camera Extensions permissions
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/configs/permissions/camerax-vendor-extensions.xml:$(TARGET_COPY_OUT_SYSTEM)/etc/permissions/camerax-vendor-extensions.xml

PRODUCT_PROPERTY_OVERRIDES += \
    ro.device.front_cam=20MP \
    ro.device.rear_cam="Dual 50MP+8MP"

# ConsumerIR
PRODUCT_PACKAGES += \
    android.hardware.ir-service.example

PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.hardware.consumerir.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.consumerir.xml

# Context Hub
PRODUCT_PACKAGES += \
    android.hardware.contexthub-service.tinysys

PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.hardware.context_hub.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.context_hub.xml

# Display
PRODUCT_PACKAGES += \
    android.hardware.memtrack-service.mediatek

PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.hardware.faketouch.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.faketouch.xml \
    frameworks/native/data/etc/android.hardware.opengles.aep.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.opengles.aep.xml \
    frameworks/native/data/etc/android.hardware.touchscreen.multitouch.distinct.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.touchscreen.multitouch.distinct.xml \
    frameworks/native/data/etc/android.hardware.touchscreen.multitouch.jazzhand.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.touchscreen.multitouch.jazzhand.xml \
    frameworks/native/data/etc/android.hardware.touchscreen.multitouch.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.touchscreen.multitouch.xml \
    frameworks/native/data/etc/android.hardware.touchscreen.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.touchscreen.xml \
    frameworks/native/data/etc/android.hardware.vulkan.compute-0.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.vulkan.compute.xml \
    frameworks/native/data/etc/android.hardware.vulkan.level-1.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.vulkan.level.xml \
    frameworks/native/data/etc/android.hardware.vulkan.version-1_3.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.vulkan.version.xml \
    frameworks/native/data/etc/android.software.opengles.deqp.level-2024-03-01.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.software.opengles.deqp.level.xml \
    frameworks/native/data/etc/android.software.vulkan.deqp.level-2024-03-01.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.software.vulkan.deqp.level.xml

# Dolby
#PRODUCT_PACKAGES += \
    XiaomiDolby

# DRM
PRODUCT_PACKAGES += \
    com.android.hardware.drm.clearkey

# Enforce generic ramdisk allow list
$(call inherit-product, $(SRC_TARGET_DIR)/product/generic_ramdisk.mk)

# Enable MTK GED KPI
$(call soong_config_set_bool,libgui,support_mtk_ged_kpi,true)

# Enable project quotas and casefolding for emulated storage without sdcardfs
$(call inherit-product, $(SRC_TARGET_DIR)/product/emulated_storage.mk)

# Fingerprint
PRODUCT_PACKAGES += \
    android.hardware.biometrics.fingerprint-service.xiaomi \
    libudfpshandler

PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.hardware.fingerprint.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.fingerprint.xml

$(call soong_config_set,XIAOMI_BIOMETRICS_FINGERPRINT,IMPL_VER,V2)

# GNSS
PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.hardware.location.gps.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.location.gps.xml

# Health
PRODUCT_PACKAGES += \
    android.hardware.health-service.mediatek \
    android.hardware.health-service.mediatek-recovery

PRODUCT_PACKAGES += \
    vendor.lineage.health-service.default

$(call soong_config_set,lineage_health,charging_control_charging_path,/sys/class/power_supply/battery/input_suspend)
$(call soong_config_set,lineage_health,charging_control_charging_enabled,0)
$(call soong_config_set,lineage_health,charging_control_charging_disabled,1)
$(call soong_config_set_bool,lineage_health,charging_control_supports_bypass,false)

# Init
PRODUCT_PACKAGES += \
    fstab.enableswap \
    fstab.mt6899 \
    fstab.mt6899.vendor_ramdisk \
    init_conninfra.rc \
    init.cgroup.rc \
    init.connectivity.common.rc \
    init.connectivity.rc \
    init.fingerprint.rc \
    init.mi_thermald.rc \
    init.modem.rc \
    init.mt6899.rc \
    init.mtkgki.rc \
    init.pstore.rc \
    init.project.rc \
    init.sensor_2_0.rc \
    ueventd.mt6899.rc \
    ueventd.xiaomi.rc

PRODUCT_PACKAGES += \
    init.insmod.sh \
    init.pstore_blk.sh

PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/init/init.recovery.mt6899.rc:$(TARGET_COPY_OUT_RECOVERY)/root/init.recovery.mt6899.rc

# Keymint
PRODUCT_PACKAGES += \
    android.hardware.security.keymint3-service.strongbox.nxp \
    android.hardware.weaver-service.nxp

PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/configs/permissions/com.android.se.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/com.android.se.xml

PRODUCT_COPY_FILES += \
    hardware/nxp/keymint/KM300/res/hal_uuid_map_config.xml:$(TARGET_COPY_OUT_VENDOR)/etc/hal_uuid_map_rodin.xml

PRODUCT_PACKAGES += \
    android.hardware.hardware_keystore_V3.xml

PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.hardware.keystore.app_attest_key.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.keystore.app_attest_key.xml \
    frameworks/native/data/etc/android.software.device_id_attestation.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.software.device_id_attestation.xml

# Lights
$(call soong_config_set_bool,lineagelight,scan_for_backlight_devices,$(TARGET_LIGHT_HAL_SCAN_FOR_BACKLIGHT_DEVICES))
PRODUCT_PACKAGES += \
    android.hardware.light-service.lineage

# Linker
PRODUCT_VENDOR_LINKER_CONFIG_FRAGMENTS += \
    $(LOCAL_PATH)/configs/linker/linker.config.json

# Matlog
TARGET_DISABLE_MATLOG := true

# Media
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/configs/media/media_codecs_c2.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs_c2.xml \
    $(LOCAL_PATH)/configs/media/media_codecs_performance.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs_performance.xml \
    $(LOCAL_PATH)/configs/media/media_profiles_V1_0.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_profiles_V1_0.xml

# Net
PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.software.ipsec_tunnel_migration.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.software.ipsec_tunnel_migration.xml \
    frameworks/native/data/etc/android.software.ipsec_tunnels.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.software.ipsec_tunnels.xml

# NFC
PRODUCT_PACKAGES += \
    android.hardware.nfc2-service.nxp

PRODUCT_PACKAGES += \
    com.android.nfc_extras \
    SecureElement \
    Tag

PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.hardware.nfc.ese.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.nfc.ese.xml \
    frameworks/native/data/etc/android.hardware.nfc.hce.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.nfc.hce.xml \
    frameworks/native/data/etc/android.hardware.nfc.hcef.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.nfc.hcef.xml \
    frameworks/native/data/etc/android.hardware.nfc.uicc.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.nfc.uicc.xml \
    frameworks/native/data/etc/android.hardware.nfc.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.nfc.xml \
    frameworks/native/data/etc/android.hardware.se.omapi.ese.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.se.omapi.ese.xml \
    frameworks/native/data/etc/android.hardware.se.omapi.uicc.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.se.omapi.uicc.xml

# Overlays
$(call inherit-product, hardware/mediatek/overlay/mssi.mk)

PRODUCT_PACKAGES += \
    RodinCarrierConfigOverlay \
    RodinFrameworksOverlay \
    RodinNfcOverlay \
    RodinSecureElementOverlay \
    RodinSettingsOverlay \
    RodinSettingsProviderOverlay \
    RodinSystemUIOverlay

PRODUCT_PACKAGES += \
    RodinLineageSettingsOverlay

PRODUCT_PACKAGES += \
    NcmTetheringOverlay

# Page size
PRODUCT_NO_BIONIC_PAGE_SIZE_MACRO := true

# Partitions
PRODUCT_BUILD_PVMFW_IMAGE := true
PRODUCT_USE_DYNAMIC_PARTITIONS := true

PRODUCT_PACKAGES += \
    android.hardware.fastboot-service.example_recovery \
    fastbootd

# Parts
PRODUCT_PACKAGES += \
    XiaomiParts

# Platform
TARGET_BOARD_PLATFORM := mt6899

# Power
PRODUCT_PACKAGES += \
    android.hardware.power-service.lineage-libperfmgr \
    libmtkperf_client \
    libmtkperf_client_vendor \
    libperfctl_vendor \
    libpowerhalwrap \
    libpowerhalwrap_vendor

PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/configs/power/powerhint.json:$(TARGET_COPY_OUT_VENDOR)/etc/powerhint.json

$(call soong_config_set,power_libperfmgr,mode_extension_lib,//$(LOCAL_PATH):libpowermode-ext-rodin)

# Properties
include hardware/mediatek/configs/properties/vendor_logtag.mk

# Radio
ENABLE_VENDOR_RIL_SERVICE := true

$(call inherit-product, hardware/lineage/compat/frameworks/compat.mk)
$(call inherit-product, hardware/mediatek/frameworks/mediatek-frameworks.mk)

PRODUCT_PACKAGES += \
    mdota_symlink

PRODUCT_PACKAGES += \
    xiaomi-telephony-stub

PRODUCT_BOOT_JARS += \
    xiaomi-telephony-stub

PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/configs/permissions/privapp-permissions-com.mediatek.ims.xml:$(TARGET_COPY_OUT_SYSTEM_EXT)/etc/permissions/privapp-permissions-com.mediatek.ims.xml \
    $(LOCAL_PATH)/configs/permissions/privapp-permissions-com.mediatek.telephony.xml:$(TARGET_COPY_OUT_SYSTEM_EXT)/etc/permissions/privapp-permissions-com.mediatek.telephony.xml

PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.hardware.telephony.gsm.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.telephony.gsm.xml \
    frameworks/native/data/etc/android.hardware.telephony.ims.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.telephony.ims.xml \
    frameworks/native/data/etc/android.hardware.telephony.satellite.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.telephony.satellite.xml

# Screen density
PRODUCT_AAPT_CONFIG := normal
PRODUCT_AAPT_PREF_CONFIG := xxhdpi

# Security
BOOT_SECURITY_PATCH := 2026-05-01
INIT_BOOT_SECURITY_PATCH := $(BOOT_SECURITY_PATCH)
VENDOR_SECURITY_PATCH := $(BOOT_SECURITY_PATCH)

# Setup dalvik vm configs
$(call inherit-product, frameworks/native/build/phone-xhdpi-6144-dalvik-heap.mk)

# SKUs
PRODUCT_COPY_FILES += \
    $(call find-copy-subdir-files,*,$(LOCAL_PATH)/configs/skus/,$(TARGET_COPY_OUT_ODM)/etc)

# Soong namespaces
PRODUCT_SOONG_NAMESPACES += \
    $(LOCAL_PATH) \
    hardware/google/interfaces \
    hardware/google/pixel \
    hardware/lineage/interfaces/power-libperfmgr \
    hardware/mediatek \
    hardware/mediatek/libmtkperf_client \
    hardware/mediatek/wlan/wifi_hal \
    hardware/xiaomi

# Sensors
PRODUCT_PACKAGES += \
    android.hardware.sensors-service.xiaomi-multihal \
    sensors.xiaomi.v2

PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/configs/sensors/hals.conf:$(TARGET_COPY_OUT_ODM)/etc/sensors/hals.conf

PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.hardware.sensor.accelerometer.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.sensor.accelerometer.xml \
    frameworks/native/data/etc/android.hardware.sensor.compass.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.sensor.compass.xml \
    frameworks/native/data/etc/android.hardware.sensor.dynamic.head_tracker.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.sensor.dynamic.head_tracker.xml \
    frameworks/native/data/etc/android.hardware.sensor.gyroscope.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.sensor.gyroscope.xml \
    frameworks/native/data/etc/android.hardware.sensor.light.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.sensor.light.xml \
    frameworks/native/data/etc/android.hardware.sensor.proximity.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.sensor.proximity.xml \
    frameworks/native/data/etc/android.hardware.sensor.stepcounter.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.sensor.stepcounter.xml \
    frameworks/native/data/etc/android.hardware.sensor.stepdetector.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.sensor.stepdetector.xml

# Thermal
PRODUCT_PACKAGES += \
    android.hardware.thermal-service.mediatek \
    thermal_symlinks

PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/configs/thermal/thermal_info_config.json:$(TARGET_COPY_OUT_VENDOR)/etc/thermal_info_config.json

# USB
PRODUCT_PACKAGES += \
    android.hardware.usb-service.mediatek \
    android.hardware.usb.gadget-service.mediatek \
    init.mt6899.usb.rc

PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.hardware.usb.accessory.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.usb.accessory.xml \
    frameworks/native/data/etc/android.hardware.usb.host.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.usb.host.xml

$(call soong_config_set_bool,android_hardware_mediatek_usb,audio_accessory_supported,true)

# Verified boot
PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.software.verified_boot.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.software.verified_boot.xml

# Vibrator
$(call inherit-product, vendor/qcom/opensource/vibrator/vibrator-vendor-product.mk)

# Virtualization service
$(call inherit-product, packages/modules/Virtualization/apex/product_packages.mk)

# VNDK
PRODUCT_PACKAGES += \
    vndservicemanager

# Wifi
PRODUCT_CFI_INCLUDE_PATHS += hardware/mediatek/wlan/wpa_supplicant_8_lib

PRODUCT_PACKAGES += \
    wpa_supplicant \
    hostapd \
    android.hardware.wifi-service

PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.hardware.wifi.direct.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.wifi.direct.xml \
    frameworks/native/data/etc/android.hardware.wifi.passpoint.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.wifi.passpoint.xml \
    frameworks/native/data/etc/android.hardware.wifi.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.wifi.xml

# Inherit the proprietary files
$(call inherit-product, vendor/xiaomi/rodin/rodin-vendor.mk)
