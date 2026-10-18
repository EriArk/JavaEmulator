// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include <algorithm>
#include <chrono>
#include <cstdint>
#include <mutex>

namespace abyss::speed {
class clock {
    std::mutex lock;
    std::uint64_t anchor = 0, elapsed = 0, remainder = 0;
    int percent = 100;
    bool paused = false;
    void advance(std::uint64_t now) {
        if (now < anchor) return; // A concurrent reader can acquire the lock after a newer sample.
        const auto delta = now - anchor;
        anchor = now;
        if (!paused) {
            const auto scaled = delta * percent + remainder;
            elapsed += scaled / 100; remainder = scaled % 100;
        }
    }
public:
    explicit clock(std::uint64_t now) : anchor(now) {}
    std::uint64_t read(std::uint64_t now) { std::lock_guard<std::mutex> guard(lock); advance(now); return elapsed; }
    void set(std::uint64_t now, int value) { std::lock_guard<std::mutex> guard(lock); advance(now); percent = value <= 0 ? 100 : std::clamp(value, 50, 200); }
    void pause(std::uint64_t now, bool value) { std::lock_guard<std::mutex> guard(lock); advance(now); paused = value; }
    int get() { std::lock_guard<std::mutex> guard(lock); return percent; }
};
inline std::uint64_t real_now() {
    return std::chrono::duration_cast<std::chrono::microseconds>(std::chrono::steady_clock::now().time_since_epoch()).count();
}
inline clock &session() { static clock value(real_now()); return value; }
inline std::uint64_t now() { return session().read(real_now()); }
inline void set(int value) { session().set(real_now(), value); }
inline int get() { return session().get(); }
inline void pause(bool value) { session().pause(real_now(), value); }
inline std::uint64_t host_delay(std::uint64_t guest) {
    const auto percent = get();
    return guest / percent * 100 + (guest % percent * 100 + percent - 1) / percent;
}
inline std::uint64_t guest_delay(std::uint64_t host) {
    const auto percent = get();
    return host / 100 * percent + (host % 100 * percent + 99) / 100;
}
}
