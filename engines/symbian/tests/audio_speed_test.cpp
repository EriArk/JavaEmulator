// SPDX-License-Identifier: GPL-3.0-only
#include "audio_speed.h"
#include <cassert>
#include <cmath>
#include <cstdio>
#include <vector>

int main() {
    using namespace abyss::audio;
    frame_history history;
    history.append(100, 50); history.append(100, 200); history.append(100, 0);
    assert(history.position(50) == 25);
    assert(history.position(150) == 150);
    assert(history.position(250) == 250);
    history.clear(); assert(history.position(100) == 0);
    for (unsigned channels : {1, 2}) for (int speed : {50, 75, 100, 125, 150, 200, 300, 400}) {
        stream_rate rate(22050, channels);
        std::uint64_t consumed = 0, output = 0;
        auto source = [&](std::int16_t *buffer, std::size_t requested) {
            auto count = std::min<std::size_t>(requested, 22050 - consumed);
            for (std::size_t i = 0; i < count; ++i) for (unsigned c = 0; c < channels; ++c)
                buffer[i * channels + c] = static_cast<short>(std::sin((consumed+i)*.05)*5000*(c ? -1 : 1));
            consumed += count; return count;
        };
        std::array<short, 1030> buffer;
        int peak = 0, crossings = 0, previous = 0;
        for (unsigned block = 0; block < 1000; ++block) {
            buffer.fill(12345);
            auto count = rate.render(buffer.data(), 511, speed, source);
            assert(count <= 511);
            assert(buffer[511 * channels] == 12345);
            if (channels == 2) for (std::size_t i = 0; i < count; ++i)
                assert(std::abs(buffer[i*2] + buffer[i*2+1]) <= 1);
            for (std::size_t i = 0; i < count; ++i) {
                int sample = buffer[i * channels];
                peak = std::max(peak, std::abs(sample));
                if (previous <= 0 && sample > 0) ++crossings;
                previous = sample;
            }
            output += count;
            if (count < 511) break;
        }
        assert(consumed == 22050);
        assert(peak > 4000 && crossings > 165 && crossings < 190);
        assert(std::abs(static_cast<double>(output) - 22050 * 100.0 / speed) < 300);
        assert(rate.render(buffer.data(), 511, speed, source) == 0);
        rate.reset(); consumed = 0;
        assert(rate.render(buffer.data(), 256, 100, source) == 256);
        assert(consumed == 256 && rate.history.position(256) == 256);
    }
    stream_rate live(44100, 2);
    std::uint64_t consumed = 0;
    auto source = [&](std::int16_t *buffer, std::size_t count) {
        std::fill_n(buffer, count * 2, 1000); consumed += count; return count;
    };
    std::array<short, 512> buffer;
    double expected = 0;
    for (int speed : {50, 200, 400, 300, 75, 100, 150, 125, 100}) {
        for (int i = 0; i < 100; ++i) {
            assert(live.render(buffer.data(), 256, speed, source) == 256);
            expected += 256 * speed / 100.0;
        }
        assert(std::abs(static_cast<double>(consumed) - expected) < 300);
    }
    std::puts("Audio speed: mono/stereo, eight rates, bounds, EOF, reset, live changes and position history passed");
}
