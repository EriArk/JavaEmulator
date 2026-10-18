// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include <algorithm>
#include <atomic>
#include <cstdint>

namespace abyss::frame_rate {
// Separate from guest refresh-rate metadata and upstream per-application settings.
inline std::atomic<int> limit{60};
inline void set(int fps) { limit.store(std::clamp(fps, 0, 240), std::memory_order_relaxed); }
inline std::uint64_t interval() {
    const int fps = limit.load(std::memory_order_relaxed);
    return fps == 0 ? 0 : (1000000ULL + fps - 1) / fps;
}
inline std::uint64_t boundary(std::uint64_t now, std::uint64_t period) {
    return period == 0 ? now : now + period - now % period;
}
inline std::uint64_t poll_interval(std::uint64_t host_interval) {
    // Direct framebuffer sampling needs no extra cap in Unlimited mode, but
    // polling faster than the host's existing cadence does not reveal more frames.
    return std::max(host_interval, interval());
}
}
