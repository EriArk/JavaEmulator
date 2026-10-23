/* Copyright 2026. GPL-3.0-only. */
package ru.playsoftware.j2meloader.runtime;

/** Presentation-only delay; never modifies guest clocks, sleep or input. */
public final class FrameInterpolationClock {
    private long last = -1, duration;
    public void reset() { last = -1; duration = 0; }
    public void frame(long now) {
        long interval = last < 0 ? 0 : now - last;
        // Do not interpolate startup, pauses or already-fast streams.
        duration = interval >= 20_000_000L && interval <= 200_000_000L
                ? Math.min(interval, 100_000_000L) : 0;
        last = now;
    }
    public float phase(long now) {
        return duration == 0 ? 1f : Math.max(0f, Math.min(1f, (float)(now-last)/duration));
    }
}
