// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include <algorithm>
#include <atomic>

namespace abyss::guest_screen {
// Set only while replacing a stopped guest. Never resize a live framebuffer.
inline std::atomic<int> requested{0};
inline std::atomic<bool> applied{false};
inline bool valid(int preset) { return preset >= 0 && preset <= 2; }

template <typename Screen>
bool configure(Screen &screen, int preset) {
    if (!valid(preset) || preset == 0 || screen.screen_number != 0 || screen.modes.empty()) return false;
    auto panel = std::find_if(screen.modes.begin(), screen.modes.end(),
        [](const auto &mode) { return mode.rotation == 0; });
    if (panel == screen.modes.end()) return false;
    int width = panel->size.x, height = panel->size.y;
    if (width <= 0 || height <= 0 || width > 1024 || height > 1024) return false;
    if (preset == 1) { width = std::min(panel->size.x, panel->size.y); height = std::max(panel->size.x, panel->size.y); }
    if (preset == 2) { width = std::max(panel->size.x, panel->size.y); height = std::min(panel->size.x, panel->size.y); }
    if (std::none_of(screen.modes.begin(), screen.modes.end(), [&](const auto &mode) {
            return mode.size.x == width && mode.size.y == height;
        })) return false;
    // Keep mode IDs and hardware-state references intact, including rotated modes.
    for (auto &mode : screen.modes) {
        const bool quarter = mode.rotation % 180 != 0;
        mode.size.x = quarter ? height : width;
        mode.size.y = quarter ? width : height;
    }
    return true;
}
}
