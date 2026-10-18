// SPDX-License-Identifier: GPL-3.0-only
#include "emulation_speed.h"
#include "frame_rate.h"
#include <cassert>
#include <cstdio>
int main() {
    abyss::speed::clock clock(1000);
    assert(clock.read(1100) == 100);
    clock.set(1100,50); assert(clock.read(1200) == 150);
    clock.pause(1200,true); clock.set(2000,200); assert(clock.read(2100) == 150);
    clock.pause(2100,false); assert(clock.read(2200) == 350);
    clock.set(2200,100); assert(clock.read(2300) == 450);
    assert(clock.read(2250) == 450); assert(clock.read(2400) == 550);
    for (int speed : {50,75,100,125,150,200}) {
        abyss::speed::set(speed);
        assert(abyss::speed::host_delay(speed * 10) == 1000);
        abyss::frame_rate::set(30);
        const auto delay = abyss::speed::host_delay(abyss::frame_rate::interval());
        assert(delay >= 33333 && delay <= 33336);
    }
    abyss::speed::set(100);
    std::puts("Emulation speed: continuity, pause, delays and independent FPS passed");
}
