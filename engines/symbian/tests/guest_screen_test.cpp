// SPDX-License-Identifier: GPL-3.0-only
#include "../core/guest_screen.h"
#include <cassert>
#include <vector>
struct Size { int x, y; };
struct Mode { Size size; int rotation; int id; };
struct Screen { int screen_number; std::vector<Mode> modes; };
int main() {
    Screen original{0, {{{176,208},0,1},{{208,176},90,2},{{176,208},180,3}}};
    auto screen = original;
    assert(abyss::guest_screen::options(screen) == 7);
    assert(!abyss::guest_screen::configure(screen,0));
    assert(screen.modes[0].size.x == 176);
    assert(abyss::guest_screen::configure(screen,2));
    assert(screen.modes[0].size.x == 208 && screen.modes[0].size.y == 176);
    assert(screen.modes[1].size.x == 176 && screen.modes[1].size.y == 208);
    assert(screen.modes[1].id == 2 && screen.modes[1].rotation == 90);
    assert(abyss::guest_screen::configure(screen,1));
    assert(screen.modes[0].size.x == 176 && screen.modes[0].size.y == 208);
    assert(screen.modes[1].size.x == 208 && screen.modes[1].size.y == 176);
    screen = original; screen.screen_number = 1;
    assert(abyss::guest_screen::options(screen) == 1);
    assert(!abyss::guest_screen::configure(screen,2));
    assert(screen.modes[0].size.x == 176);
    screen = original;
    assert(!abyss::guest_screen::configure(screen,-1));
    assert(!abyss::guest_screen::configure(screen,3));
    assert(!abyss::guest_screen::configure(screen,4));
    screen.modes.clear(); assert(!abyss::guest_screen::configure(screen,1));
    assert(abyss::guest_screen::options(screen) == 1);
    screen = original; screen.modes[0].size.x = 0;
    assert(!abyss::guest_screen::configure(screen,2));
    screen = original; screen.modes.resize(1);
    assert(abyss::guest_screen::options(screen) == 3);
    assert(!abyss::guest_screen::configure(screen,2));
    screen.modes[0].size = {320,240};
    assert(abyss::guest_screen::options(screen) == 5);
    screen.modes[0].rotation = 90;
    assert(abyss::guest_screen::options(screen) == 1);
    screen.modes[0].rotation = 0; screen.modes[0].size = {0,240};
    assert(abyss::guest_screen::options(screen) == 1);
}
