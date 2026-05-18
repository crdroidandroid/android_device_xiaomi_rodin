#!/bin/bash

# Define the root of your AOSP tree
AOSP_ROOT="${PWD}"
PATCH_DIR="${AOSP_ROOT}/device/xiaomi/rodin/source-patches"

# Safety check: Ensure we are at AOSP root (Space fixed here)
if [ ! -f "build/envsetup.sh" ]; then
    echo "Please run this script from the root of your AOSP tree."
    exit 1
fi

echo "======================================"
echo " Applying source patches for rodin... "
echo "======================================"

# Array of patches: "Destination_Repo_Path Patch_File_Name"
PATCHES=(
    "hardware/ril 0001-Android-RIL.patch"
    "packages/apps/Aperture 0001-Aperture-Enable-MediaTek-HFPS-Mode-for-60-FPS-video-.patch"
    "packages/apps/Aperture 0001-DNM-Aperture-Enable-MediaTek-EIS-and-EIS-preview-mod.patch"
    "packages/modules/Bluetooth 0001-Add-L2CAP-and-A2DP-offload-coex-mechanism-for-MTK.patch"
    "build/soong 0001-Simplify-package-allowlist-by-consolidating-vendor-e.patch"
)

for entry in "${PATCHES[@]}"; do
    set -- $entry
    REPO_PATH=$1
    PATCH_FILE=$2

    echo -e "\n---> Checking: $REPO_PATH"

    # Check if directory exists (Space fixed here)
    if [ ! -d "${AOSP_ROOT}/${REPO_PATH}" ]; then
        echo "ERROR: Directory $REPO_PATH does not exist! Skipping."
        continue
    fi

    cd "${AOSP_ROOT}/${REPO_PATH}" || exit 1

    # Extract patch subject to check if already applied
    PATCH_SUBJECT=$(grep -m 1 "^Subject: " "${PATCH_DIR}/${PATCH_FILE}" | sed 's/^Subject: //g' | sed 's/\[.*\] //g')

    if git log --oneline -n 30 | grep -Fq "$PATCH_SUBJECT"; then
        echo "Status: Already applied. Skipping."
    else
        echo "Status: Applying $PATCH_FILE..."

        # Apply patch with 3-way merge
        git am -3 "${PATCH_DIR}/${PATCH_FILE}"

        # Check if apply was successful (Space fixed here)
        if [ $? -eq 0 ]; then
            echo "Success: Patched $REPO_PATH"
        else
            echo "ERROR: Failed to apply $PATCH_FILE to $REPO_PATH!"
            echo "Aborting git am to prevent repository lock..."
            git am --abort
        fi
    fi
done

cd "${AOSP_ROOT}"
echo -e "\n======================================"
echo " All rodin patches processed!         "
echo "======================================"
echo
