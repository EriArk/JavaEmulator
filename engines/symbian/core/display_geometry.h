// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include <array>
#include <cmath>
#include <limits>
#include <mutex>

namespace abyss {
using display_geometry = std::array<int, 6>;

// The UI must never wait for guest work that may itself need the paused GPU.
// Resolve the screen under the kernel lock and keep its lifetime protected.
template <typename Kernel, typename Lookup>
bool try_display_geometry(Kernel &kernel, Lookup lookup, display_geometry &result) {
    std::unique_lock<Kernel> kernel_lock(kernel, std::try_to_lock);
    if (!kernel_lock.owns_lock()) return false;
    auto *screen = lookup();
    if (!screen) return false;
    std::unique_lock<std::mutex> screen_lock(screen->screen_mutex, std::try_to_lock);
    if (!screen_lock.owns_lock()) return false;
    int sw = screen->current_mode().size.x, sh = screen->current_mode().size.y;
    double width = std::round(sw * screen->logic_scale_factor_x);
    double height = std::round(sh * screen->logic_scale_factor_y);
    if (screen->ui_rotation % 180 != 0) { std::swap(width, height); std::swap(sw, sh); }
    if (sw <= 0 || sh <= 0 || !std::isfinite(width) || !std::isfinite(height)
            || width <= 0 || height <= 0) return false;
    double right = screen->absolute_pos.x + width, bottom = screen->absolute_pos.y + height;
    if (right > std::numeric_limits<int>::max() || bottom > std::numeric_limits<int>::max()) return false;
    result = {screen->absolute_pos.x, screen->absolute_pos.y,
        static_cast<int>(right), static_cast<int>(bottom), sw, sh};
    return true;
}
}
