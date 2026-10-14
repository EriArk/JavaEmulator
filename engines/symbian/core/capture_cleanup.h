#pragma once

#include <utility>

namespace abyssme {
// Cancellation is uncommon; rebuilding preserves the queue's priority order.
template <typename Captures, typename Owner, typename Type>
void remove_captures(Captures &captures, Owner *owner, unsigned id, Type type, bool all) {
    for (auto it = captures.begin(); it != captures.end();) {
        auto &queue = it->second;
        auto retained = queue;
        while (!retained.empty()) retained.pop();
        while (!queue.empty()) {
            auto request = queue.top();
            queue.pop();
            if (request.user != owner || (!all && (request.id != id || request.type_ != type)))
                retained.push(std::move(request));
        }
        queue = std::move(retained);
        if (queue.empty()) it = captures.erase(it);
        else ++it;
    }
}
}
