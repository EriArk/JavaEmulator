package ru.playsoftware.j2meloader.mophun;

import java.util.function.LongSupplier;

/** Monotonic game time excludes time spent in host menus or the background. */
final class MophunClock implements LongSupplier {
    private final ru.playsoftware.j2meloader.runtime.EmulationClock clock;

    MophunClock() { this(() -> android.os.SystemClock.elapsedRealtime()); }

    MophunClock(LongSupplier source) {
        clock = new ru.playsoftware.j2meloader.runtime.EmulationClock(source);
    }

    synchronized void pause(boolean value) {
        clock.pause(value);
    }

    @Override public synchronized long getAsLong() {
        return clock.getAsLong();
    }
    void speed(int value) { clock.speed(value); }
    int speed() { return clock.speed(); }
}
