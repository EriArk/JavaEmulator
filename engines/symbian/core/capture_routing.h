#pragma once

#include <cstdint>
#include <limits>

namespace abyssme {
inline bool valid_capture(std::uint32_t mask, std::uint32_t modifiers, std::int32_t priority) {
    return (modifiers & ~mask) == 0 && priority != std::numeric_limits<std::int32_t>::min();
}

// The queue's backing array is a heap, not a sorted list. Choose among all
// matching requests; equal priorities are resolved by the newest handle.
template <typename Captures, typename Owner, typename Type>
Owner *capture_target(Captures &captures, unsigned key, unsigned modifiers, Type type, Owner *focus) {
    auto found = captures.find(key);
    if (found == captures.end()) return focus;
    Owner *target = focus;
    std::int32_t priority = std::numeric_limits<std::int32_t>::min();
    unsigned newest = 0;
    for (const auto &request : found->second) {
        const auto candidate = static_cast<std::int32_t>(request.pri_);
        if (request.user && request.type_ == type && candidate != std::numeric_limits<std::int32_t>::min()
            && (modifiers & request.modifiers_mask_) == request.modifiers_
            && (candidate > priority || (candidate == priority && request.id > newest))) {
            priority = candidate;
            newest = request.id;
            target = request.user;
        }
    }
    return target;
}
}
