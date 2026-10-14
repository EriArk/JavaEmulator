#include "capture_cleanup.h"
#include <cassert>
#include <cstdio>
#include <queue>
#include <unordered_map>

struct Owner {};
enum class Type { normal, up_down };
struct Request {
    Owner *user;
    unsigned id;
    Type type_;
    int priority;
    bool operator<(const Request &other) const { return priority < other.priority; }
};

int main() {
    Owner first, second;
    using Captures = std::unordered_map<unsigned, std::priority_queue<Request>>;
    Captures captures;
    captures[164].push({&first, 1, Type::normal, 1});
    captures[164].push({&first, 2, Type::up_down, 3});
    captures[164].push({&second, 3, Type::up_down, 2});
    captures[165].push({&first, 4, Type::up_down, 5});
    abyssme::remove_captures(captures, &first, 2, Type::normal, false);
    assert(captures.at(164).size() == 3);
    abyssme::remove_captures(captures, &second, 2, Type::up_down, false);
    assert(captures.at(164).size() == 3);
    abyssme::remove_captures(captures, &first, 99, Type::up_down, false);
    assert(captures.at(164).size() == 3);
    abyssme::remove_captures(captures, &first, 2, Type::up_down, false);
    assert(captures.at(164).size() == 2);
    assert(captures.at(164).top().id == 3);
    assert(captures.at(165).top().id == 4);
    abyssme::remove_captures(captures, &first, 2, Type::up_down, false);
    assert(captures.at(164).size() == 2);
    abyssme::remove_captures(captures, &first, 0, Type::normal, true);
    assert(captures.size() == 1);
    assert(captures.at(164).size() == 1);
    assert(captures.at(164).top().id == 3);
    abyssme::remove_captures(captures, &second, 3, Type::up_down, false);
    assert(captures.empty());
    abyssme::remove_captures(captures, &first, 0, Type::normal, true);
    assert(captures.empty());
    std::puts("capture cleanup: 12 checks passed");
}
