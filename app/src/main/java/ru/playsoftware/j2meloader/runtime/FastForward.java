// SPDX-License-Identifier: GPL-3.0-only
package ru.playsoftware.j2meloader.runtime;

import java.util.function.IntConsumer;

/** A session-only override. Never writes accelerated values into the saved profile. */
public final class FastForward {
    private final IntConsumer apply;
    private int base = 100, target = 200;
    private boolean toggleMode, pressed, latched;

    public FastForward(IntConsumer apply) { this.apply = apply; }
    public void configure(int base, int target, boolean toggleMode) {
        this.base = EmulationClock.normalize(base);
        this.target = Math.max(this.base, EmulationClock.normalize(target));
        this.toggleMode = toggleMode;
        pressed = latched = false;
        apply.accept(this.base);
    }
    public boolean active() { return latched || (!toggleMode && pressed); }
    public int speed() { return active() ? target : base; }
    public void button(boolean down) {
        if (pressed == down) return;
        pressed = down;
        if (down && toggleMode) latched = !latched;
        apply.accept(speed());
    }
    public void toggle() { latched = !active(); pressed = false; apply.accept(speed()); }
    public void stop() {
        boolean changed = active(); pressed = latched = false;
        if (changed) apply.accept(base);
    }
    public int frameLimit(int baseLimit) {
        return !active() || baseLimit <= 0 ? baseLimit
                : (int) Math.min(1000L, (long) baseLimit * speed() / 100);
    }
}
