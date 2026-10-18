// SPDX-License-Identifier: GPL-3.0-only
#include "file_transfer.h"
#include <cassert>
#include <array>
#include <cstdio>
#include <cstring>

int main() {
    using abyssme::plan_file_transfer;
    unsigned cases = 0;
    for (bool write : {false, true}) {
        for (std::uint64_t size : {0ULL, 4ULL, 100ULL}) {
            for (std::int64_t offset : {0LL, 2LL, 100LL, 0x100000000LL}) {
                for (std::int32_t length : {0, 1, 4}) {
                    auto plan = plan_file_transfer(write, size, 0, offset, length, 4, true);
                    assert(plan);
                    if (write) {
                        assert(plan->position == std::min<std::uint64_t>(offset, size));
                        assert(plan->length == length);
                    } else {
                        assert(plan->position == offset);
                        assert(plan->length <= length);
                        assert(offset < size ? plan->length <= size - offset : plan->length == 0);
                    }
                    ++cases;
                }
            }
        }
        assert(!plan_file_transfer(write, 10, 0, 0, -1, 10, true));
        assert(!plan_file_transfer(write, 10, 0, 0, 11, 10, true));
        assert(!plan_file_transfer(write, 10, 0, -2, 1, 10, true));
        assert(!plan_file_transfer(write, 10, 0, INT32_MIN, 1, 10, false));
        for (std::int64_t current_marker : {-1LL, static_cast<long long>(INT32_MIN)}) {
            auto plan = plan_file_transfer(write, 10, 3, current_marker, 1, 10, true);
            assert(plan && plan->position == 3 && plan->length == 1);
        }
        cases += 6;
    }
    auto eof = plan_file_transfer(false, 4, UINT64_MAX, -1, INT32_MAX, INT32_MAX, false);
    assert(eof && eof->length == 0);
    auto append = plan_file_transfer(true, 4, UINT64_MAX, -1, 1, 1, false);
    assert(append && append->position == 4 && append->length == 1);
    printf("file_transfer: %u boundary cases passed\n", cases + 2);

    // Real byte transfers: a short section read must preserve its byte count
    // and neither an EOF read nor an append may touch bytes outside the plan.
    std::FILE *file = std::tmpfile();
    assert(file);
    assert(std::fwrite("abcd", 1, 4, file) == 4);
    std::array<char, 6> buffer{'!', '!', '!', '!', '!', '!'};
    auto section = plan_file_transfer(false, 4, 0, 2, 4, 4, false);
    assert(section && section->length == 2);
    assert(std::fseek(file, section->position, SEEK_SET) == 0);
    assert(std::fread(buffer.data() + 1, 1, section->length, file) == 2);
    assert(std::memcmp(buffer.data(), "!cd!!!", 6) == 0);
    auto beyond = plan_file_transfer(false, 4, 0, 100, 4, 4, false);
    assert(beyond && beyond->length == 0);
    assert(std::fread(buffer.data() + 1, 1, beyond->length, file) == 0);
    assert(std::memcmp(buffer.data(), "!cd!!!", 6) == 0);
    auto tail = plan_file_transfer(true, 4, 0, 100, 2, 2, false);
    assert(tail && tail->position == 4);
    assert(std::fseek(file, tail->position, SEEK_SET) == 0);
    assert(std::fwrite("ef", 1, tail->length, file) == 2);
    assert(std::ftell(file) == 6);
    std::rewind(file);
    assert(std::fread(buffer.data(), 1, buffer.size(), file) == buffer.size());
    assert(std::memcmp(buffer.data(), "abcdef", 6) == 0);
    std::fclose(file);
    puts("file_transfer: partial read, EOF guard, and append round-trip passed");
}
