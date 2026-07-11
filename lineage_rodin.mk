#
# SPDX-FileCopyrightText: The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#

# Inherit from the custom device configuration.
$(call inherit-product, device/xiaomi/rodin/device.mk)

# Inherit from the LineageOS configuration.
$(call inherit-product, vendor/lineage/config/common_full_phone.mk)

#crDroid flags
IS_PHONE := true
TARGET_FACE_UNLOCK_SUPPORTED := true
TARGET_SUPPORTS_QUICK_TAP := true
TARGET_SUPPORTS_BLUR := true
TARGET_HAS_UDFPS := true

PRODUCT_BRAND := Xiaomi
PRODUCT_DEVICE := rodin
PRODUCT_MANUFACTURER := Xiaomi
PRODUCT_MODEL := 2412DPC0AG
PRODUCT_NAME := lineage_rodin

PRODUCT_BRAND_FOR_ATTESTATION := $(PRODUCT_BRAND)
PRODUCT_DEVICE_FOR_ATTESTATION := $(PRODUCT_DEVICE)
PRODUCT_MODEL_FOR_ATTESTATION := $(PRODUCT_MODEL)
PRODUCT_NAME_FOR_ATTESTATION := rodin_global
PRODUCT_MANUFACTURER_FOR_ATTESTATION := $(PRODUCT_MANUFACTURER)

PRODUCT_CHARACTERISTICS := nosdcard
PRODUCT_GMS_CLIENTID_BASE := android-xiaomi

PRODUCT_BUILD_PROP_OVERRIDES += \
    BuildDesc="missi-user 16 BP2A.250605.031.A3 OS3.0.300.0.WOJMIXM release-keys" \
    BuildFingerprint=POCO/rodin_global/rodin:15/AP3A.240905.015.A2/OS3.0.300.0.WOJMIXM:user/release-keys \
    DeviceName=rodin \
    DeviceProduct=rodin_global \
    SystemDevice=rodin \
    SystemName=rodin_global
