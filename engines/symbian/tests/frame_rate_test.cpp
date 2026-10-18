// SPDX-License-Identifier: GPL-3.0-only
#include "frame_rate.h"
#include <cassert>
#include <iostream>
#include <thread>

int main() {
    using namespace abyss::frame_rate;
    int checks = 0;
    for (int fps : {0, 15, 20, 25, 30, 50, 60, 37, 240}) {
        set(fps);
        const auto period = interval();
        assert((fps == 0) == (period == 0)); ++checks;
        for (std::uint64_t now : {0ULL, 1ULL, 16667ULL, 50000ULL, 1000000000000ULL}) {
            const auto next = boundary(now, period);
            assert(next >= now);
            if (fps == 0) assert(next == now);
            else { assert(next > now && next - now <= period); assert(next % period == 0); }
            ++checks;
        }
        assert(poll_interval(16667) >= 16667);
        assert(poll_interval(16667) >= period); ++checks;
    }
    set(-20); assert(interval() == 0); ++checks;
    set(1000); assert(limit.load() == 240); ++checks;
    std::thread writer([] { for (int i = 0; i < 10000; ++i) set(i % 241); });
    for (int i = 0; i < 10000; ++i) {
        const auto period = interval();
        assert(period == 0 || (period >= 4167 && period <= 1000000));
    }
    writer.join(); ++checks;
    std::cout << checks << " frame-rate checks passed (including concurrent live changes)\n";
}
