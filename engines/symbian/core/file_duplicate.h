// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include <cstdint>

namespace abyssme {
// Symbian duplicates a file share's position, not its mutable stream cursor.
// Adding read access selects the VFS non-truncating mode even for a created file.
// The caller retains the original fs_node's guest mode fields.
template<class File, class Seek, class Open>
auto duplicate_file_stream(File &source, int read_mode, Seek begin, Open open)
    -> decltype(open(source.file_name(), source.file_mode())) {
    const auto position = source.tell();
    if (position > INT64_MAX || !source.flush()) return {};
    auto copy = open(source.file_name(), source.file_mode() | read_mode);
    if (!copy || copy->seek(static_cast<std::int64_t>(position), begin) != position) return {};
    return copy;
}
}
