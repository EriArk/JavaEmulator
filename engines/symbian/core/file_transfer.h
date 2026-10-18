// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include <algorithm>
#include <cstdint>
#include <optional>

namespace abyssme {
struct file_transfer {
    std::uint64_t position;
    std::uint32_t length;
};

// RFile writes beyond EOF append; reads beyond EOF return an empty descriptor.
inline std::optional<file_transfer> plan_file_transfer(bool write, std::uint64_t size,
    std::uint64_t current, std::int64_t position, std::int32_t length,
    std::uint64_t descriptor_size, bool legacy_position) {
    if (length < 0 || static_cast<std::uint64_t>(length) > descriptor_size) return {};
    const bool use_current = position == -1 || (legacy_position && position == INT32_MIN);
    if (!use_current && position < 0) return {};
    std::uint64_t offset = use_current ? current : static_cast<std::uint64_t>(position);
    auto count = static_cast<std::uint32_t>(length);
    if (write) offset = std::min(offset, size);
    else count = static_cast<std::uint32_t>(std::min<std::uint64_t>(count,
        offset < size ? size - offset : 0));
    return file_transfer{offset, count};
}
}
