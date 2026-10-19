// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include <algorithm>
#include <array>
#include <cstdint>
#include <mutex>
#include <stdexcept>
#include <speex/speex_resampler.h>

namespace abyss::audio {
// Map audible host frames through historical rates, not the rate at query time.
class frame_history {
    struct segment { std::uint64_t end; double guest_end; int percent; };
    std::array<segment, 512> segments{};
    std::size_t first = 0, size = 0;
    std::uint64_t host = 0;
    double guest = 0;
    std::mutex mutex;
public:
    void clear() {
        std::lock_guard<std::mutex> guard(mutex);
        first = size = host = 0; guest = 0;
    }
    void append(std::uint64_t frames, int percent) {
        if (!frames) return;
        std::lock_guard<std::mutex> guard(mutex);
        host += frames; guest += frames * (percent / 100.0);
        if (size && segments[(first + size - 1) % segments.size()].percent == percent) {
            segments[(first + size - 1) % segments.size()] = {host, guest, percent};
        } else {
            if (size == segments.size()) { first = (first + 1) % segments.size(); --size; }
            segments[(first + size++) % segments.size()] = {host, guest, percent};
        }
    }
    std::uint64_t position(std::uint64_t frame) {
        std::lock_guard<std::mutex> guard(mutex);
        for (std::size_t i = 0; i < size; ++i) {
            const auto &s = segments[(first + i) % segments.size()];
            if (frame <= s.end) return static_cast<std::uint64_t>(
                std::max(0.0, s.guest_end - (s.end - frame) * (s.percent / 100.0)));
        }
        return static_cast<std::uint64_t>(guest);
    }
};

// One output stream, bounded scratch space. Speex is already shipped with cubeb.
// This is rate conversion (pitch follows speed), not pitch-preserving stretching.
class stream_rate {
    SpeexResamplerState *resampler = nullptr;
    std::array<spx_int16_t, 1024> input{};
    const unsigned channels, sample_rate;
    unsigned offset = 0, available = 0, tail = 0;
    int previous = 100;
    bool active = false, ended = false;
public:
    frame_history history;
    stream_rate(unsigned rate, unsigned channel_count) : channels(channel_count), sample_rate(rate) {
        if (channels < 1 || channels > 2) throw std::invalid_argument("Audio channels");
        int error = 0;
        resampler = speex_resampler_init(channels, rate, rate, 4, &error);
        if (!resampler || error) {
            if (resampler) speex_resampler_destroy(resampler);
            throw std::runtime_error("Cannot initialize audio rate converter");
        }
    }
    ~stream_rate() { if (resampler) speex_resampler_destroy(resampler); }
    stream_rate(const stream_rate &) = delete;
    stream_rate &operator=(const stream_rate &) = delete;
    void reset() {
        offset = available = tail = 0; ended = active = false; previous = 100;
        speex_resampler_reset_mem(resampler);
        speex_resampler_set_rate(resampler, sample_rate, sample_rate);
        history.clear();
    }
    template<class Source> std::size_t render(std::int16_t *out, std::size_t frames, int percent, Source source) {
        percent = std::clamp(percent, 50, 400);
        if (!active && percent == 100) {
            auto produced = std::min<std::size_t>(frames, source(out, frames));
            history.append(produced, 100);
            return produced;
        }
        if (percent != previous) {
            if (speex_resampler_set_rate_frac(resampler, percent, 100,
                    sample_rate * percent / 100, sample_rate) != RESAMPLER_ERR_SUCCESS) return 0;
            previous = percent;
        }
        if (!active) { speex_resampler_skip_zeros(resampler); active = true; }
        std::size_t produced = 0;
        while (produced < frames) {
            if (!available) {
                offset = 0;
                const auto requested = static_cast<unsigned>(std::min<std::size_t>(512,
                    (std::min<std::size_t>(256, frames - produced) * percent + 99) / 100));
                if (!ended) {
                    available = static_cast<unsigned>(std::min<std::size_t>(requested, source(input.data(), requested)));
                    if (available < requested) {
                        ended = true;
                        tail = speex_resampler_get_input_latency(resampler) * 2;
                    }
                } else {
                    available = std::min(requested, tail);
                    tail -= available;
                    std::fill_n(input.data(), available * channels, 0);
                }
                if (!available) {
                    if (tail) continue;
                    break;
                }
            }
            spx_uint32_t in_count = available;
            spx_uint32_t out_count = static_cast<spx_uint32_t>(std::min<std::size_t>(256, frames - produced));
            const auto result = speex_resampler_process_interleaved_int(resampler,
                input.data() + offset * channels, &in_count, out + produced * channels, &out_count);
            if (result != RESAMPLER_ERR_SUCCESS || (!in_count && !out_count)) break;
            offset += in_count; available -= in_count; produced += out_count;
        }
        history.append(produced, percent);
        return produced;
    }
};
}
