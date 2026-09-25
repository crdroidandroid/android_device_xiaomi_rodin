#!/usr/bin/env -S PYTHONPATH=../../../tools/extract-utils python3
#
# SPDX-FileCopyrightText: The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#

import os
import shutil

from extract_utils.file import File
from extract_utils.fixups_blob import (
    BlobFixupCtx,
    File,
    blob_fixup,
    blob_fixups_user_type,
)
from extract_utils.fixups_lib import (
    lib_fixups,
    lib_fixups_user_type,
)
from extract_utils.main import (
    ExtractUtils,
    ExtractUtilsModule,
)
from extract_utils.tools import (
    llvm_objdump_path,
)
from extract_utils.utils import (
    run_cmd,
)

namespace_imports = [
    'device/xiaomi/rodin',
    'hardware/mediatek',
    'hardware/mediatek/libion_mtk',
    'hardware/mediatek/libmtkperf_client',
    'hardware/xiaomi',
    'vendor/mediatek/ims'
]


def blob_fixup_graphic_buffer_size(
    ctx: BlobFixupCtx,
    file: File,
    file_path: str,
    *args,
    **kwargs,
):
    for line in run_cmd(
        [
            llvm_objdump_path,
            '--disassemble-all',
            file_path,
        ]
    ).splitlines():
        line = line.split(maxsplit=5)
        if len(line) != 6:
            continue
        # The size of GraphicBuffer changed from 0x100 to 0xd30
        offset, _, instruction, register, value, _ = line
        if instruction == 'mov' and register[:-1] == 'w0' and value == '#0x100':
            with open(file_path, 'rb+') as f:
                f.seek(int(offset[:-1], 16))
                f.write(b'\x00\xa6\x81\x52')  # AArch64 mov w0, #0xd30


def blob_fixup_camera_lut(
    ctx: BlobFixupCtx,
    file: File,
    file_path: str,
    *args,
    **kwargs,
):
    lut_path = os.path.join(
        ctx.module_dir,
        'camera-luts',
        os.path.basename(file.dst),
    )
    shutil.copyfile(lut_path, file_path)


def lib_fixup_vendor_suffix(lib: str, partition: str, *args, **kwargs):
    return f'{lib}_{partition}' if partition == 'vendor' else None


lib_fixups: lib_fixups_user_type = {
    **lib_fixups,
    (
        'libneuron_graph_delegate.mtk',
        'libnir_neon_driver_ndk.mtk.vndk',
        'libtflite_mtk',
        'vendor.mediatek.hardware.apuware.apusys-V5-ndk',
        'vendor.mediatek.hardware.apuware.utils-V1-ndk',
        'vendor.mediatek.hardware.apuware.utils@2.0',
        'vendor.mediatek.hardware.neuropilot.agent-V1-ndk',
    ): lib_fixup_vendor_suffix,
}


blob_fixups: blob_fixups_user_type = {
    (
        'odm/etc/camera/160_vivid.png',
        'odm/etc/camera/161_wind.png',
        'odm/etc/camera/162_nature.png',
        'odm/etc/camera/163_free.png',
        'odm/etc/camera/164_fc400.png',
        'odm/etc/camera/165_c_50d.png',
        'odm/etc/camera/166_revert.png',
    ): blob_fixup()
        .call(blob_fixup_camera_lut),
    (
        'odm/bin/hw/vendor.xiaomi.hw.touchfeature-service',
        'odm/lib64/libadaptivehdr.so',
        'odm/lib64/libcolortempmode.so',
        'odm/lib64/libdither.so',
        'odm/lib64/libflatmode.so',
        'odm/lib64/libhistprocess.so',
        'odm/lib64/libmiBrightness.so',
        'odm/lib64/libmiSensorCtrl.so',
        'odm/lib64/libpaperMode.so',
        'odm/lib64/librhytheyecare.so',
        'odm/lib64/libsdr2hdr.so',
        'odm/lib64/libsre.so',
        'odm/lib64/libtruetone.so',
        'odm/lib64/libvideomode.so',
        'vendor/lib64/mt6899/libaalservice.so',
        'vendor/lib64/mt6899/libpqconfig.so'
    ): blob_fixup()
        .replace_needed('android.hardware.sensors-V2-ndk.so', 'android.hardware.sensors-V3-ndk.so'),
    'vendor/bin/mnld': blob_fixup()
        .replace_needed('libmnl.so', 'libmnl_mtk.so')
        .replace_needed('android.hardware.sensors-V2-ndk.so', 'android.hardware.sensors-V3-ndk.so'),
    'odm/lib64/hw/displayfeature.default.so': blob_fixup()
        .replace_needed('android.hardware.sensors-V2-ndk.so', 'android.hardware.sensors-V3-ndk.so')
        .replace_needed('libtinyxml2.so', 'libtinyxml2-v36.so'),
    'odm/bin/hw/vendor.xiaomi.sensor.citsensorservice.aidl': blob_fixup()
        .replace_needed('android.hardware.sensors-V2-ndk.so', 'android.hardware.sensors-V3-ndk.so')
        .add_needed('libui_shim.so'),
    (
        'odm/lib64/camera/plugins/capture/com.xiaomi.plugin.gainmap.so',
        'odm/lib64/camera/plugins/capture/com.xiaomi.plugin.jpegrAggr.so',
    ): blob_fixup()
        .replace_needed('libultrahdr.so', 'libultrahdr_rodin.so'),
    (
        'odm/lib64/libAncHumanPreviewBokeh.so',
        'odm/lib64/libarcsoft_beautyshot.so',
        'odm/lib64/libMiEmojiEffect.so',
        'odm/lib64/libMiPhotoFilter.so',
        'odm/lib64/libMiVideoFilter.so',
        'odm/lib64/libTrueSight.so',
        'odm/lib64/libwa_widelens_undistort.so',
        'vendor/lib64/mt6899/libneuralnetworks_sl_driver_mtk_prebuilt.so',
        'vendor/lib64/mt6899/libneuron_adapter_mgvi.so',
        'vendor/lib64/libMiPhotoFilter.so',
        'vendor/lib64/libmcve.so'
    ): blob_fixup()
        .clear_symbol_version('AHardwareBuffer_allocate')
        .clear_symbol_version('AHardwareBuffer_createFromHandle')
        .clear_symbol_version('AHardwareBuffer_describe')
        .clear_symbol_version('AHardwareBuffer_isSupported')
        .clear_symbol_version('AHardwareBuffer_getNativeHandle')
        .clear_symbol_version('AHardwareBuffer_lock')
        .clear_symbol_version('AHardwareBuffer_lockPlanes')
        .clear_symbol_version('AHardwareBuffer_release')
        .clear_symbol_version('AHardwareBuffer_unlock'),
    (
        'odm/lib64/libmiXmlParser.so',
        'vendor/lib64/hw/mt6899/vendor.mediatek.hardware.pq_aidl-impl.so',
        'vendor/lib64/libHardwareBacklightcore.so',
        'vendor/lib64/libaudiocloudctrl.so',
        'vendor/lib64/libmicamera_aidl_provider.so',
        'vendor/lib64/libpqxmlflagparser.so',
        'vendor/lib64/libpqxmlparser.so',
        'vendor/lib64/librt_extamp_intf.so',
        'vendor/lib64/libsilkybrightnesscore.so',
        'vendor/lib64/mt6899/libmmlpqImpl.so'
    ): blob_fixup()
        .replace_needed('libtinyxml2.so', 'libtinyxml2-v36.so'),
    (
        'odm/lib64/libmt_mitee.so',
        'odm/lib64/libgoogleid.so',
    ): blob_fixup()
        .replace_needed('lib_android_keymaster_keymint_utils.so', 'lib_android_keymaster_keymint_utils_V3.so')
        .replace_needed('libkeymint.so', 'libkeymint_V3.so')
        .replace_needed('libkeymint_remote_prov_support.so', 'libkeymint_remote_prov_support_V3.so')
        .replace_needed('libkeymint_support.so', 'libkeymint_support_V3.so'),
    'system_ext/bin/hw/android.hardware.audio.parameter_parser.service': blob_fixup()
        .replace_needed('av-audio-types-aidl-ndk.so', 'av-audio-types-aidl-V3-ndk.so'),
    'vendor/bin/hw/android.hardware.audio.service-aidl.mediatek': blob_fixup()
        .replace_needed('android.media.audio.common.types-V5-ndk.so', 'android.media.audio.common.types-V3-ndk.so')
        .replace_needed('libaudio_aidl_conversion_common_ndk.so', 'libaudio_aidl_conversion_common_ndk_prebuilt.so'),
    (
        'vendor/bin/hw/mt6899/android.hardware.graphics.allocator-V2-service-mediatek.mt6899',
        'vendor/lib64/egl/mt6899/libGLES_mali.so',
        'vendor/lib64/hw/mt6899/android.hardware.graphics.allocator-V2-mediatek.so',
        'vendor/lib64/hw/mt6899/mapper.mediatek.so',
        'vendor/lib64/mt6899/libmtkcam_grallocutils.so',
        'vendor/lib64/libcodec2_vpp_AIMEMC_plugin.so',
        'vendor/lib64/libcodec2_vpp_AISR_plugin.so',
        'vendor/lib64/libgpud.so',
        'vendor/lib64/libmtkcam_grallocutils_aidlv2helper.so',
        'vendor/lib64/vendor.mediatek.hardware.camera.isphal-V1-ndk.so',
        'vendor/lib64/vendor.mediatek.hardware.pq_aidl-V2-ndk.so',
        'vendor/lib64/vendor.mediatek.hardware.pq_aidl-V4-ndk.so',
        'vendor/lib64/vendor.mediatek.hardware.pq_aidl-V7-ndk.so',
        'vendor/lib64/libui-v35.so'
    ): blob_fixup()
        .replace_needed('android.hardware.graphics.common-V5-ndk.so', 'android.hardware.graphics.common-V7-ndk.so'),
    (
        'vendor/bin/hw/android.hardware.media.c2-mediatek-64b',
        'vendor/bin/hw/vendor.dolby.media.c2-default-service-dax',
        'vendor/lib64/c2.dolby.client.so',
    ): blob_fixup()
        .replace_needed('libcodec2_aidl.so', 'libcodec2_aidl_prebuilt.so'),
    'vendor/bin/hw/vendor.dolby.media.c2-service-vision': blob_fixup()
        .replace_needed('libcodec2_aidl.so', 'libcodec2_aidl_prebuilt.so'),
    'vendor/lib64/libcodec2_aidl_prebuilt.so': blob_fixup()
        .add_needed('libcodec2_shim_blockpool.so')
        .replace_needed('libcodec2_hal_common.so', 'libcodec2_hal_common_prebuilt.so')
        .replace_needed('libui.so', 'libui-v35.so'),
    (
        'vendor/lib64/libcodec2_mtk_venc.so',
        'vendor/lib64/libcodec2_mtk_vdec.so',
    ): blob_fixup()
        .replace_needed('libcodec2_aidl.so', 'libcodec2_aidl_prebuilt.so')
        .replace_needed('libformatter.so', 'libformatter_mtk.so'),
    'vendor/bin/hw/android.hardware.security.keymint@3.0-service.mitee': blob_fixup()
        .replace_needed('lib_android_keymaster_keymint_utils.so', 'lib_android_keymaster_keymint_utils_V3.so')
        .replace_needed('android.hardware.security.keymint-V3-ndk.so', 'android.hardware.security.keymint-V3-ndk-v35.so')
        .replace_needed('libkeymint.so', 'libkeymint_V3.so'),
    'vendor/etc/init/hw/init.batterysecret.rc': blob_fixup()
        .regex_replace('    seclabel u:r:batterysecret:s0\n', ''),
    (
        'vendor/etc/init/android.hardware.graphics.allocator-V2-service-mediatek.rc',
        'vendor/etc/init/android.hardware.graphics.composer@3.3-service.rc',
        'vendor/etc/init/arm.mali.platform-mediatek.rc'
    ): blob_fixup()
        .regex_replace('.*writepid.*\n', ''),
    'vendor/lib64/hw/android.hardware.audio.effect.aidl-impl-mediatek.so': blob_fixup()
        .replace_needed('android.media.audio.common.types-V5-ndk.so', 'android.media.audio.common.types-V3-ndk.so')
        .replace_needed('libtinyxml2.so', 'libtinyxml2-v36.so'),
    'vendor/lib64/hw/android.hardware.soundtrigger3-impl.so': blob_fixup()
        .replace_needed('libaudio_aidl_conversion_common_ndk.so', 'libaudio_aidl_conversion_common_ndk_prebuilt.so'),
    'vendor/lib64/hw/hwcomposer.mtk_common.so': blob_fixup()
        .add_needed('libprocessgroup_shim.so')
        .replace_needed('libtinyxml2.so', 'libtinyxml2-v36.so'),
    (
        'vendor/lib64/mt6899/libmtkcam_taskmgr.so',
        'vendor/lib64/mt6899/libcam.hal3a.so',
        'vendor/lib64/mt6899/libcam.hal3a.ctrl.so',
        'vendor/lib64/libcameraopt.so',
    ): blob_fixup()
        .add_needed('libprocessgroup_shim.so'),
    (
         'vendor/lib64/soundfx/libswdapaidl.so',
         'vendor/lib64/soundfx/libswgamedapaidl.so',
         'vendor/lib64/soundfx/libswspatializeraidl.so'
    ): blob_fixup()
        .replace_needed('android.media.audio.common.types-V5-ndk.so', 'android.media.audio.common.types-V3-ndk.so')
        .replace_needed('libaudio_aidl_conversion_common_ndk.so', 'libaudio_aidl_conversion_common_ndk_prebuilt.so'),
    'vendor/lib64/soundfx/libdlbvolaidl.so': blob_fixup()
        .replace_needed('android.media.audio.common.types-V5-ndk.so', 'android.media.audio.common.types-V3-ndk.so'),
    'vendor/lib64/android.hardware.audio.core-impl-mediatek.so': blob_fixup()
        .add_needed('libaudioutils_shim.so')
        .replace_needed('android.media.audio.common.types-V5-ndk.so', 'android.media.audio.common.types-V3-ndk.so')
        .replace_needed('libaudio_aidl_conversion_common_ndk.so', 'libaudio_aidl_conversion_common_ndk_prebuilt.so'),
    'vendor/lib64/libaudio_aidl_conversion_common_ndk_prebuilt.so': blob_fixup()
        .replace_needed('android.media.audio.common.types-V5-ndk.so', 'android.media.audio.common.types-V3-ndk.so'),
    'vendor/lib64/libcodec2_fsr.so': blob_fixup()
        .call(blob_fixup_graphic_buffer_size)
        .replace_needed('android.hardware.graphics.common-V5-ndk.so', 'android.hardware.graphics.common-V7-ndk.so'),
     'odm/lib64/camera/plugins/capture/com.xiaomi.plugin.filter.so': blob_fixup()
        .call(blob_fixup_graphic_buffer_size),
    (
      'vendor/lib64/libcom.xiaomi.grallocutils.so',
      'vendor/lib64/libmialgoengine.so'
    ): blob_fixup()
        .add_needed('libprocessgroup_shim.so')
        .call(blob_fixup_graphic_buffer_size),
    'vendor/lib64/libkeymint_V3.so': blob_fixup()
        .replace_needed('lib_android_keymaster_keymint_utils.so', 'lib_android_keymaster_keymint_utils_V3.so'),
    'vendor/lib64/libkeymint_remote_prov_support_V3.so': blob_fixup()
        .replace_needed('libbase.so', 'libbase-v35.so')
        .replace_needed('android.hardware.security.keymint-V3-ndk.so', 'android.hardware.security.keymint-V3-ndk-v35.so')
        .replace_needed('libcppcose_rkp.so', 'libcppcose_rkp-v35.so'),
    'vendor/lib64/libmicamera_hal_core.so': blob_fixup()
        .call(blob_fixup_graphic_buffer_size)
        .add_needed('libprocessgroup_shim.so')
        .replace_needed('libtinyxml2.so', 'libtinyxml2-v36.so'),
    'vendor/lib64/libultrahdr_rodin.so': blob_fixup()
        .replace_needed('libjpegencoder.so', 'libjpegencoder_rodin.so')
        .replace_needed('libjpegdecoder.so', 'libjpegdecoder_rodin.so'),
    (
        'vendor/lib64/vendor.xiaomi.hardware.camera.injection-V1-ndk.so',
        'vendor/lib64/vendor.xiaomi.hardware.camera.injection-client.so',
        'vendor/lib64/vendor.xiaomi.hardware.camera.injection-service.so'
    ): blob_fixup()
        .replace_needed('android.hardware.camera.device-V1-ndk.so', 'android.hardware.camera.device-V2-ndk.so'),
    'vendor/lib64/libcamera2ndk_vendor.so': blob_fixup()
        .replace_needed('android.frameworks.cameraservice.service-V2-ndk.so', 'android.frameworks.cameraservice.service-V3-ndk.so')
        .replace_needed('android.frameworks.cameraservice.device-V2-ndk', 'android.frameworks.cameraservice.device-V3-ndk'),
    'vendor/lib64/libdlbdsservice.so': blob_fixup()
        .replace_needed('libstagefright_foundation.so', 'libstagefright_foundation-v33.so'),
    (
        'vendor/lib64/c2.dolby.hevc.dec.so',
        'vendor/lib64/c2.dolby.hevc.sec.dec.so',
    ): blob_fixup()
        .replace_needed('libcodec2_aidl.so', 'libcodec2_aidl_prebuilt.so')
        .replace_needed('libstagefright_foundation.so', 'libstagefright_foundation-v35.so')
        .replace_needed('libui.so','libui-v35.so'),
    'vendor/etc/init/android.hardware.audio.service-aidl.mediatek.rc': blob_fixup()
        .regex_replace(
            'onrestart restart audioserver',
            'onrestart restart audioserver\n    onrestart restart vendor.sensors-hal-multihal\n    onrestart restart citsensorservice'
        ),
    'vendor/lib64/hw/audio.primary.mediatek.so': blob_fixup()
        .replace_needed('libalsautils.so', 'libalsautils-mtk.so')
        .replace_needed('libtinyxml2.so', 'libtinyxml2-v36.so')
        .binary_regex_replace(b'A2dpsuspendonly', b'A2dpSuspended\x00\x00')
        .binary_regex_replace(b'BTAudiosuspend', b'A2dpSuspended\x00'),
    (
        'system_ext/bin/spkcal',
        'system_ext/bin/spkcal_tfa',
    ): blob_fixup()
        .add_needed('libaudiobase.so'),
}  # fmt: skip

module = ExtractUtilsModule(
    'rodin',
    'xiaomi',
    add_firmware_proprietary_file=True,
    blob_fixups=blob_fixups,
    lib_fixups=lib_fixups,
    namespace_imports=namespace_imports,
)

if __name__ == '__main__':
    utils = ExtractUtils.device(module)
    utils.run()
