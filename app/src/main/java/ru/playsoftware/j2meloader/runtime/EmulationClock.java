/* Copyright 2026. Licensed under the Apache License, Version 2.0. */
package ru.playsoftware.j2meloader.runtime;

import java.util.function.LongSupplier;

/** Continuous guest time. Host deadlines, input and UI never use this clock. */
public final class EmulationClock implements LongSupplier {
    private final LongSupplier source;
    private long anchor, elapsed, remainder;
    private int percent = 100;
    private boolean paused;

    public EmulationClock(LongSupplier source) { this.source = source; anchor = source.getAsLong(); }
    public static int normalize(int value) { return value <= 0 ? 100 : Math.max(50, Math.min(200, value)); }
    private void advance() {
        long now = source.getAsLong();
        if (now < anchor) return;
        long delta = now - anchor;
        anchor = now;
        if (!paused) {
            long scaled = delta * percent + remainder;
            elapsed += scaled / 100; remainder = scaled % 100;
        }
    }
    public synchronized void speed(int value) { advance(); percent = normalize(value); }
    public synchronized int speed() { return percent; }
    public synchronized void pause(boolean value) { advance(); paused = value; }
    @Override public synchronized long getAsLong() { advance(); return elapsed; }
    public synchronized long hostDelay(long guestDelay) {
        if (guestDelay <= 0) return 0;
        long whole = guestDelay / percent;
        long fraction = (guestDelay % percent * 100 + percent - 1) / percent;
        if (whole > (Long.MAX_VALUE - fraction) / 100) return Long.MAX_VALUE;
        return Math.max(1, whole * 100 + fraction);
    }
}
