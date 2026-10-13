package ru.playsoftware.j2meloader.mophun;

import java.util.function.LongSupplier;

/** Monotonic game time excludes time spent in host menus or the background. */
final class MophunClock implements LongSupplier {
    private final LongSupplier source;
    private long origin, pausedAt, excluded;
    private boolean paused;

    MophunClock() { this(() -> android.os.SystemClock.elapsedRealtime()); }

    MophunClock(LongSupplier source) {
        this.source = source;
        origin = source.getAsLong();
    }

    synchronized void pause(boolean value) {
        if (paused == value) return;
        long now = source.getAsLong();
        if (value) pausedAt = now;
        else excluded += now - pausedAt;
        paused = value;
    }

    @Override public synchronized long getAsLong() {
        return (paused ? pausedAt : source.getAsLong()) - origin - excluded;
    }
}
