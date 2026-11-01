// SPDX-License-Identifier: GPL-3.0-only
#include "display_geometry.h"
#include <cassert>
#include <chrono>
#include <cstdio>
#include <future>
#include <thread>

struct screen {
    struct point { int x, y; } absolute_pos{10, 20};
    struct mode { point size{240, 320}; } mode;
    std::mutex screen_mutex;
    float logic_scale_factor_x = 2, logic_scale_factor_y = 2;
    int ui_rotation = 0;
    const struct mode &current_mode() const { return mode; }
};

int main() {
    std::mutex kernel;
    screen display;
    abyss::display_geometry geometry{};
    auto read = [&] { return abyss::try_display_geometry(kernel, [&] { return &display; }, geometry); };
    assert(read()); assert((geometry == abyss::display_geometry{10,20,490,660,240,320}));
    display.ui_rotation = 90;
    assert(read()); assert((geometry == abyss::display_geometry{10,20,650,500,320,240}));
    for (std::mutex *busy : {&kernel, &display.screen_mutex}) {
        std::promise<void> locked, release;
        auto until_release = release.get_future();
        std::thread guest([&] {
            std::lock_guard<std::mutex> lock(*busy);
            locked.set_value();
            until_release.wait_for(std::chrono::seconds(2));
        });
        locked.get_future().wait();
        auto previous = geometry;
        auto start = std::chrono::steady_clock::now();
        bool sampled = read();
        auto elapsed = std::chrono::steady_clock::now() - start;
        release.set_value(); guest.join();
        assert(!sampled && geometry == previous);
        assert(elapsed < std::chrono::milliseconds(500));
        assert(read()); // Both locks were released, including kernel on screen contention.
    }
    assert(!abyss::try_display_geometry(kernel, [] { return static_cast<screen *>(nullptr); }, geometry));
    for (float scale : {0.f, -1.f, std::numeric_limits<float>::infinity(),
            std::numeric_limits<float>::quiet_NaN(), std::numeric_limits<float>::max()}) {
        display.logic_scale_factor_x = scale;
        assert(!read());
    }
    display.logic_scale_factor_x = 1;
    display.mode.size.x = 0; assert(!read());
    display.mode.size.x = 240; assert(read());
    std::puts("Display geometry: viewport, rotation, paused/busy locks, recovery and invalid sizes passed");
}
