#include "capture_routing.h"
#include "capture_cleanup.h"
#include <common/queue.h>
#include <cassert>
#include <cstdio>
#include <limits>
#include <unordered_map>

struct Owner {};
enum class Type { normal, up_down };
struct Request {
    Owner *user;
    unsigned id;
    Type type_;
    unsigned modifiers_mask_, modifiers_;
    std::int32_t pri_;
    bool operator<(const Request &other) const {
        return pri_ == other.pri_ ? id < other.id : pri_ < other.pri_;
    }
};

int main() {
    Owner focus, first, second, third;
    std::unordered_map<unsigned, eka2l1::cp_queue<Request>> captures;
    unsigned checks = 0;
    auto expect = [&](unsigned key, unsigned modifiers, Type type, Owner *expected, Owner *fallback) {
        assert(abyssme::capture_target(captures, key, modifiers, type, fallback) == expected);
        ++checks;
    };
    const auto normal = Type::normal, raw = Type::up_down;
    expect(0xa4, 0, raw, &focus, &focus);
    assert(captures.empty()); ++checks;
    expect(0xa4, 0, raw, nullptr, nullptr);
    // Normal captures use the logical key code, raw captures the scan code.
    captures[0xf842].push({&first, 1, normal, 0, 0, 0});
    captures[0xa4].push({&second, 2, raw, 0, 0, 0});
    expect(0xf842, 0, normal, &first, &focus);
    expect(0xa4, 0, raw, &second, &focus);
    expect(0xa4, 0, normal, &focus, &focus);
    expect(0xf842, 0, raw, &focus, &focus);
    expect(0xa4, 0, raw, &second, nullptr);
    // Capture priority is signed and must not be boosted implicitly.
    captures[0xa4].push({&third, 3, raw, 0, 0, -1});
    expect(0xa4, 0, raw, &second, &focus);
    captures[0xa4].push({&third, 4, raw, 0, 0, 5});
    expect(0xa4, 0, raw, &third, &focus);
    captures[0xa4].push({&first, 5, raw, 0, 0, 0});
    expect(0xa4, 0, raw, &third, &focus);
    captures[0xa4].push({&first, 6, raw, 0, 0, 5});
    expect(0xa4, 0, raw, &first, &focus);
    // A nonmatching high-priority entry cannot hide a matching lower one.
    captures[0xa4].push({&second, 7, raw, 3, 1, 10});
    expect(0xa4, 0, raw, &first, &focus);
    expect(0xa4, 1, raw, &second, &focus);
    expect(0xa4, 3, raw, &first, &focus);
    expect(0xa4, 5, raw, &second, &focus);
    captures[0xa4].push({&focus, 8, raw, 0, 0, 11});
    expect(0xa4, 0, raw, &focus, &focus);
    abyssme::remove_captures(captures, &focus, 8, raw, false);
    expect(0xa4, 0, raw, &first, &focus);
    abyssme::remove_captures(captures, &first, 0, normal, true);
    expect(0xa4, 0, raw, &third, &focus);
    expect(0xf842, 0, normal, &focus, &focus);
    // Normal repeat events retain capture routing when repeat flags are set.
    captures[0xf842].push({&first, 9, normal, 1, 0, -3});
    expect(0xf842, 0x100, normal, &first, &focus);
    expect(0xf842, 0x101, normal, &focus, &focus);
    captures[0xf842].push({&second, 10, normal, 0, 0, -2});
    expect(0xf842, 0, normal, &second, &focus);
    assert(abyssme::valid_capture(3, 1, -1)); ++checks;
    assert(abyssme::valid_capture(0, 0, 0)); ++checks;
    assert(!abyssme::valid_capture(1, 3, 0)); ++checks;
    assert(!abyssme::valid_capture(0, 0, std::numeric_limits<std::int32_t>::min())); ++checks;
    std::printf("capture routing: %u checks passed\n", checks);
}
