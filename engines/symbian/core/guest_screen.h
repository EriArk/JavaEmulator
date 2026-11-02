// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include <algorithm>
#include <atomic>
#include <iterator>

namespace abyss::guest_screen {
// Set only while replacing a stopped guest. Never resize a live framebuffer.
inline std::atomic<int> requested{0};
inline std::atomic<bool> applied{false};
// Original is always available. A trial must never rewrite the phone's mode table.
inline std::atomic<int> available{1};
inline bool valid(int preset) { return preset >= 0 && preset <= 2; }

template <typename Screen>
bool supports(const Screen &screen, int preset) {
    if (!valid(preset) || preset == 0 || screen.screen_number != 0 || screen.modes.empty()) return false;
    auto panel = std::find_if(screen.modes.begin(), screen.modes.end(),
        [](const auto &mode) { return mode.rotation == 0; });
    if (panel == screen.modes.end()) return false;
    int width = panel->size.x, height = panel->size.y;
    if (width <= 0 || height <= 0 || width > 1024 || height > 1024) return false;
    if (preset == 1) { width = std::min(panel->size.x, panel->size.y); height = std::max(panel->size.x, panel->size.y); }
    if (preset == 2) { width = std::max(panel->size.x, panel->size.y); height = std::min(panel->size.x, panel->size.y); }
    return std::any_of(screen.modes.begin(), screen.modes.end(), [&](const auto &mode) {
            return mode.size.x == width && mode.size.y == height;
        });
}

template <typename Screen>
int options(const Screen &screen) {
    return 1 | (supports(screen, 1) ? 2 : 0) | (supports(screen, 2) ? 4 : 0);
}

template <typename Screen>
int initial_mode(const Screen &screen, int preset) {
    if (!supports(screen, preset)) return 0;
    auto panel = std::find_if(screen.modes.begin(), screen.modes.end(),
        [](const auto &mode) { return mode.rotation == 0; });
    int width = preset == 1 ? std::min(panel->size.x, panel->size.y) : std::max(panel->size.x, panel->size.y);
    int height = preset == 1 ? std::max(panel->size.x, panel->size.y) : std::min(panel->size.x, panel->size.y);
    // Games and Avkon refer to these modes by index. Changing their dimensions can
    // corrupt framebuffer users even when every individual dimension is valid.
    auto mode = std::find_if(screen.modes.begin(), screen.modes.end(), [&](const auto &candidate) {
        return candidate.size.x == width && candidate.size.y == height;
    });
    return static_cast<int>(std::distance(screen.modes.begin(), mode));
}
}
