/*
 * SPDX-FileCopyrightText: The XPerience Project
 * SPDX-License-Identifier: Apache-2.0
 *
 * Legacy Codec2 block-pool ABI compatibility shim.
 *
 * Android 16 vendor blobs still use C2PlatformAllocatorDesc, while Android 17
 * uses C2PlatformAllocatorDescV2 / C2IgbaInterface.
 *
 * Normal MTK requests are translated directly to the Android 17 descriptor.
 * Dolby Vision requests are forwarded through the same V2 path; C2Store.cpp
 * provides the Dolby-specific IGBA compatibility handling while keeping the
 * block pool registered through the Android 17 platform cache.
 *
 * This shim is intentionally limited to the legacy ABI translation layer.
 */

#include <atomic>
#include <memory>
#include <utility>

#include <cstdlib>
#include <cstring>

#include <android-base/unique_fd.h>
#include <log/log.h>

#include <C2AllocatorGralloc.h>
#include <C2BlockInternal.h>
#include <C2IgbaBufferPriv.h>
#include <C2IgbaInterfaceImpl.h>
#include <C2PlatformSupport.h>
#include <FilterWrapper.h>

namespace android {

using LegacyIgba =
        ::aidl::android::hardware::media::c2::IGraphicBufferAllocator;

/*
 *
 * Process detection
 *
 */
static const bool gIsDolbyVisionService = []() {
    const char* name = getprogname();

    return name &&
            strcmp(
                    name,
                    "vendor.dolby.media.c2-service-vision") == 0;
}();


/*
 * Diagnostics
 */
static std::atomic<uint64_t> gFilterCreateCalls{0};
static std::atomic<uint64_t> gFilterCreatedPools{0};

static std::atomic<uint64_t> gFreeCreateCalls{0};
static std::atomic<uint64_t> gFreeCreatedPools{0};

/*
 *
 * Exact pre-refactor ABI layout.
 *
 *
 * DO NOT reorder/add/remove fields.
 */
struct C2PlatformAllocatorDescLegacy {
    C2PlatformAllocatorStore::id_t allocatorId;
    std::shared_ptr<LegacyIgba> igba;
    ::android::base::unique_fd waitableFd;
    bool blockFenceSupport;
};

static bool isDolbyLegacyIgbaRequest(
        const C2PlatformAllocatorDescLegacy& allocatorParam) {

    return gIsDolbyVisionService &&
            static_cast<unsigned>(allocatorParam.allocatorId) == 20u &&
            allocatorParam.igba != nullptr;
}


/*
 *
 * Normal legacy -> Android 17 descriptor conversion.
 *
 */
static C2PlatformAllocatorDescV2 toV2(
        C2PlatformAllocatorDescLegacy& legacy) {

    ALOGW("shim: toV2 allocatorId=%u igba=%s fence=%d fd=%d sizeof legacy=%zu v2=%zu",
          static_cast<unsigned>(legacy.allocatorId), legacy.igba ? "YES" : "NULL",
          static_cast<int>(legacy.blockFenceSupport), legacy.waitableFd.get(),
          sizeof(C2PlatformAllocatorDescLegacy), sizeof(C2PlatformAllocatorDescV2));

    C2PlatformAllocatorDescV2 v2{};

    v2.allocatorId =
            legacy.allocatorId;

    v2.blockFenceSupport =
            legacy.blockFenceSupport;

    if (legacy.igba) {
        v2.igba = 
            std::make_shared<C2IgbaInterfaceImpl>(legacy.igba);

        v2.waitableFd = std::move(legacy.waitableFd);

        ALOGW("shim: normal IGBA allocator=%u legacyIgba=%p wrappedIgba=%p fd=%d",
              static_cast<unsigned>(legacy.allocatorId), legacy.igba.get(),
              v2.igba.get(), v2.waitableFd.get());
    }

    return v2;
}


/*
 * Dolby Vision requests are forwarded to the Android 17 factory unchanged
 * after legacy-to-V2 translation. C2Store.cpp applies the Dolby-specific
 * backing allocator compatibility while preserving the registered IGBA pool.
 */

/*
 *
 * FilterWrapper::createBlockPool - legacy ABI
 *
 */
int filterWrapper_createBlockPool(
        void* thisPtr,
        C2PlatformAllocatorDescLegacy& allocatorParam,
        std::shared_ptr<const C2Component> component,
        std::shared_ptr<C2BlockPool>* pool)
    asm("_ZN7android13FilterWrapper15createBlockPoolERNS_23C2PlatformAllocatorDescENSt3__110shared_ptrIK11C2ComponentEEPNS4_I11C2BlockPoolEE");


int filterWrapper_createBlockPool(
        void* thisPtr,
        C2PlatformAllocatorDescLegacy& allocatorParam,
        std::shared_ptr<const C2Component> component,
        std::shared_ptr<C2BlockPool>* pool) {

    if (!thisPtr || !pool) {
        return C2_BAD_VALUE;
    }

    pool->reset();

    const uint64_t call =
            ++gFilterCreateCalls;

    const bool dolby = isDolbyLegacyIgbaRequest(allocatorParam);

    ALOGW("shim: FILTER CREATE #%llu component=%p allocator=%u igba=%s fd=%d dolby=%s",
          static_cast<unsigned long long>(call), component.get(),
          static_cast<unsigned>(allocatorParam.allocatorId),
          allocatorParam.igba ? "YES" : "NULL", allocatorParam.waitableFd.get(),
          dolby ? "YES" : "NO");

    auto* wrapper = reinterpret_cast<FilterWrapper*>(thisPtr);

    C2PlatformAllocatorDescV2 v2 = toV2( allocatorParam);

    if (dolby) {
        ALOGW("shim: DOLBY V9 FILTER forwarding allocator=%u wrappedIgba=%p fd=%d to Android17 factory/cache",
              static_cast<unsigned>(v2.allocatorId), v2.igba.get(), v2.waitableFd.get());
    }

    const int ret = wrapper->createBlockPool( v2, component, pool);

    ALOGW("shim: FILTER CREATE #%llu RESULT ret=%d pool=%p",
          static_cast<unsigned long long>(call), ret,
          (pool && *pool) ? static_cast<void*>(pool->get()) : nullptr);

    if (ret == C2_OK && pool && *pool) {
        const uint64_t created = ++gFilterCreatedPools;
        ALOGW("shim: FILTER NEW POOL #%llu request=%llu pool=%p alloc=%u localId=%llu component=%p",
              static_cast<unsigned long long>(created), static_cast<unsigned long long>(call),
              static_cast<void*>(pool->get()), static_cast<unsigned>((*pool)->getAllocatorId()),
              static_cast<unsigned long long>((*pool)->getLocalId()), component.get());
    }

    return ret;
}

/*
 *
 * CreateCodec2BlockPool - legacy ABI free function
 *
 */
int createCodec2BlockPool(
        C2PlatformAllocatorDescLegacy& allocatorParam,
        std::shared_ptr<const C2Component> component,
        std::shared_ptr<C2BlockPool>* pool)
    asm("_ZN7android21CreateCodec2BlockPoolERNS_23C2PlatformAllocatorDescENSt3__110shared_ptrIK11C2ComponentEEPNS3_I11C2BlockPoolEE");


int createCodec2BlockPool(
        C2PlatformAllocatorDescLegacy& allocatorParam,
        std::shared_ptr<const C2Component> component,
        std::shared_ptr<C2BlockPool>* pool) {

    if (!pool) {
        return C2_BAD_VALUE;
    }

    pool->reset();

    const uint64_t call = ++gFreeCreateCalls;
    const bool dolby = isDolbyLegacyIgbaRequest(allocatorParam);

    ALOGW("shim: FREE CREATE #%llu component=%p allocator=%u igba=%s fd=%d dolby=%s",
          static_cast<unsigned long long>(call), component.get(),
          static_cast<unsigned>(allocatorParam.allocatorId),
          allocatorParam.igba ? "YES" : "NULL", allocatorParam.waitableFd.get(),
          dolby ? "YES" : "NO");

    C2PlatformAllocatorDescV2 v2 = toV2(allocatorParam);

    if (dolby) {
        ALOGW("shim: DOLBY V9 FREE forwarding allocator=%u wrappedIgba=%p fd=%d to Android17 factory/cache",
              static_cast<unsigned>(v2.allocatorId), v2.igba.get(), v2.waitableFd.get());
    }

    const int ret = ::android::CreateCodec2BlockPool( v2, component, pool);

    ALOGW("shim: FREE CREATE #%llu RESULT ret=%d pool=%p",
          static_cast<unsigned long long>(call), ret,
          (pool && *pool) ? static_cast<void*>(pool->get()) : nullptr);

    if (ret == C2_OK && pool && *pool) {
        const uint64_t created =
                ++gFreeCreatedPools;

        ALOGW("shim: FREE NEW POOL #%llu request=%llu pool=%p alloc=%u localId=%llu component=%p",
              static_cast<unsigned long long>(created), static_cast<unsigned long long>(call),
              static_cast<void*>(pool->get()), static_cast<unsigned>((*pool)->getAllocatorId()),
              static_cast<unsigned long long>((*pool)->getLocalId()), component.get());
    }

    return ret;
}

}  // namespace android
