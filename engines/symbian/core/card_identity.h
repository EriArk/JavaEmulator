// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include <string>

namespace abyssme {
// Identify a registered card executable, not the contents of the whole E drive.
// An alternate _1 build may use the base card directory's game.id marker.
template<class Exists>
bool is_card_executable(std::u16string path, Exists exists) {
    for (auto &c : path) {
        if (c == u'/') c = u'\\';
        if (c >= u'A' && c <= u'Z') c += u'a' - u'A';
    }
    const std::u16string root = u"e:\\system\\apps\\";
    if (path.compare(0, root.size(), root) != 0) return false;
    const auto slash = path.find(u'\\', root.size());
    if (slash == std::u16string::npos) return false;
    const auto folder = path.substr(root.size(), slash - root.size());
    if (folder.empty() || folder == u"." || folder == u".."
            || path.substr(slash + 1) != folder + u".app" || !exists(path)) return false;
    if (exists(root + folder + u"\\game.id")) return true;
    if (folder.size() <= 2 || folder.substr(folder.size() - 2) != u"_1") return false;
    const auto base = folder.substr(0, folder.size() - 2);
    return exists(root + base + u"\\game.id") && exists(root + base + u"\\" + base + u".app");
}
}
