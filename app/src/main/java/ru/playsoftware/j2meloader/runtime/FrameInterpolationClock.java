/* Copyright 2026. GPL-3.0-only. */
package ru.playsoftware.j2meloader.runtime;

/** Presentation-only delay; never modifies guest clocks, sleep or input. */
public final class FrameInterpolationClock {
    // One real frame at 20 FPS; slower streams must not accumulate more presentation delay.
    public static final long MAX_DELAY_NANOS = 50_000_000L;
    private long last, interval, delay, presented;
    private boolean hasFrame, hasPresented;
    public void reset() { hasFrame = hasPresented = false; interval = delay = 0; }

    /** Timestamp of completed guest pixels, not the time the GL thread happens to upload them. */
    public void frame(long readyNanos) {
        interval = hasFrame ? readyNanos - last : 0;
        // A discontinuity starts a new timeline. nanoTime itself is allowed to be negative.
        if (interval <= 0) hasPresented = false;
        delay = interval >= 20_000_000L && interval <= 200_000_000L
                ? Math.min(interval, MAX_DELAY_NANOS) : 0;
        last = readyNanos;
        hasFrame = true;
    }

    public float phase(long now) {
        if (!hasFrame) return 1f;
        if (delay == 0) { presented = last; hasPresented = true; return 1f; }
        // Follow source time at its real rate, skipping an overdue prefix instead of speeding it up.
        long offset = interval - delay + Math.min(delay, Math.max(0, now - last));
        if (hasPresented) offset = Math.max(offset, Math.min(interval, presented - (last - interval)));
        presented = last - interval + offset;
        hasPresented = true;
        return (float) offset / interval;
    }
}
