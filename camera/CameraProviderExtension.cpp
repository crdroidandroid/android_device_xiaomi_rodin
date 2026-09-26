/*
 * Copyright (C) 2024 LibreMobileOS Foundation
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include "CameraProviderExtension.h"

#include <cstdio>
#include <cstdlib>
#include <fstream>
#include <string>

static const std::string kTorchBrightnessPath =
    "/sys/class/flashlight_core/flashlight/torchbrightness";

static constexpr int32_t kTorchMaxStrength     = 380;
static constexpr int32_t kTorchDefaultStrength = 100;

template <typename T>
static void set(const std::string& path, const T& value) {
    std::ofstream file(path);
    file << value;
}

static int32_t readTorchCurrent() {
    std::ifstream file(kTorchBrightnessPath);
    if (!file.is_open()) return 0;

    std::string line;
    std::getline(file, line);

    auto open  = line.rfind('[');
    auto close = line.rfind(']');
    if (open == std::string::npos || close == std::string::npos || close <= open)
        return 0;

    std::string num = line.substr(open + 1, close - open - 1);
    int32_t microamps = static_cast<int32_t>(std::strtol(num.c_str(), nullptr, 10));
    return microamps / 1000;
}

bool supportsTorchStrengthControlExt() {
    return true;
}

bool supportsSetTorchModeExt() {
    return false;
}

int32_t getTorchDefaultStrengthLevelExt() {
    return kTorchDefaultStrength;
}

int32_t getTorchMaxStrengthLevelExt() {
    return kTorchMaxStrength;
}

int32_t getTorchStrengthLevelExt() {
    return readTorchCurrent();
}

void setTorchStrengthLevelExt(int32_t torchStrength, bool enabled) {
    if (enabled) {
        if (torchStrength <= 0)
            torchStrength = kTorchDefaultStrength;
        if (torchStrength > kTorchMaxStrength)
            torchStrength = kTorchMaxStrength;

        set<int32_t>(kTorchBrightnessPath, torchStrength);
    } else {
        set<int32_t>(kTorchBrightnessPath, 0);
    }
}

void setTorchModeExt(bool enabled) {
    setTorchStrengthLevelExt(enabled ? kTorchDefaultStrength : 0, enabled);
}
