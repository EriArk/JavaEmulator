// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include "emulation_speed.h"
#include <common/time.h>

namespace abyss::speed {
class timer final : public eka2l1::common::teletimer {
    std::uint64_t origin = 0, end = 0;
    std::uint32_t frequency;
    bool stopped = true;
public:
    explicit timer(std::uint32_t value) : frequency(value) {}
    void start() override { pause(false); origin = now(); end = 0; stopped = false; }
    void stop() override { end = now(); stopped = true; }
    bool set_target_frequency(std::uint32_t value) override { frequency = value; return true; }
    std::uint64_t microseconds() override { return (stopped ? end : now()) - origin; }
    std::uint64_t ticks() override { return microseconds() * frequency / 1000000; }
    std::uint64_t nanoseconds() override { return microseconds() * 1000; }
};
}
