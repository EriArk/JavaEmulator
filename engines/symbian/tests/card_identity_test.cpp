// SPDX-License-Identifier: GPL-3.0-only
#include "card_identity.h"
#include <cassert>
#include <cstdio>
#include <set>

int main() {
    std::set<std::u16string> files {
        u"e:\\system\\apps\\card\\card.app", u"e:\\system\\apps\\card\\game.id",
        u"e:\\system\\apps\\card_1\\card_1.app",
        u"e:\\system\\apps\\sisgame\\sisgame.app",
        u"e:\\system\\apps\\second\\second.app", u"e:\\system\\apps\\second\\game.id"
    };
    auto exists = [&](const auto &path) { return files.count(path) != 0; };
    auto card = [&](const auto &path) { return abyssme::is_card_executable(path, exists); };
    assert(card(u"E:/System/Apps/Card/Card.app"));
    assert(card(u"e:\\system\\apps\\card_1\\card_1.app"));
    assert(card(u"e:\\system\\apps\\second\\second.app"));
    assert(!card(u"e:\\system\\apps\\sisgame\\sisgame.app"));
    assert(!card(u"z:\\system\\apps\\card\\card.app"));
    assert(!card(u"c:\\system\\apps\\card\\card.app"));
    assert(!card(u"e:\\system\\apps\\card\\service.app"));
    assert(!card(u"e:\\system\\apps\\card\\..\\card.app"));
    assert(!card(u"e:\\system\\apps\\card_2\\card_2.app"));
    files.erase(u"e:\\system\\apps\\card\\game.id");
    assert(!card(u"e:\\system\\apps\\card\\card.app"));
    assert(!card(u"e:\\system\\apps\\card_1\\card_1.app"));
    files.insert(u"e:\\system\\apps\\card\\game.id");
    files.erase(u"e:\\system\\apps\\card_1\\card_1.app");
    assert(!card(u"e:\\system\\apps\\card_1\\card_1.app"));
    assert(card(u"e:\\system\\apps\\second\\second.app"));
    puts("card_identity: mixed SIS/card installs, multiple cards, alternate builds and exclusions passed");
}
