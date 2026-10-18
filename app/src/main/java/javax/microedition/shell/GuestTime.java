/* Copyright 2026. Licensed under the Apache License, Version 2.0. */
package javax.microedition.shell;

import androidx.annotation.Keep;
import ru.playsoftware.j2meloader.runtime.EmulationClock;

/** Targets only converted guest bytecode, never Android/host timing. */
@Keep
public final class GuestTime {
    private static volatile boolean available = true;
    private static final long epoch = System.currentTimeMillis();
    private static final EmulationClock clock = new EmulationClock(() -> System.nanoTime() / 1000000);
    private GuestTime() { }
    public static void speed(int percent) {
        clock.speed(available ? percent : 100);
        javax.microedition.media.AndroidPlayer.refreshSpeed();
    }
    static void available(boolean value) { available = value; if (!value) speed(100); }
    public static boolean available() { return available; }
    public static int speed() { return clock.speed(); }
    public static long currentTimeMillis() { return epoch + clock.getAsLong(); }
    public static long nanoTime() { return clock.getAsLong() * 1000000; }
    public static long hostDelay(long millis) { return clock.hostDelay(millis); }
    public static void sleep(long millis) throws InterruptedException { sleep(millis, 0); }
    public static void sleep(long millis, int nanos) throws InterruptedException {
        if (millis < 0 || nanos < 0 || nanos > 999999) throw new IllegalArgumentException();
        if (Thread.interrupted()) throw new InterruptedException();
        if (nanos > 0 && millis < Long.MAX_VALUE) millis++;
        if (millis == 0) { Thread.yield(); return; }
        long start = clock.getAsLong();
        long remaining = millis;
        while (remaining > 0) {
            Thread.sleep(Math.min(20, clock.hostDelay(remaining)));
            remaining = millis - (clock.getAsLong() - start);
        }
    }
    public static void wait(Object monitor, long millis) throws InterruptedException { wait(monitor, millis, 0); }
    public static void wait(Object monitor, long millis, int nanos) throws InterruptedException {
        if (millis < 0 || nanos < 0 || nanos > 999999) throw new IllegalArgumentException();
        // Preserve monitor ownership, notification and indefinite-wait semantics.
        monitor.wait(hostDelay(millis), nanos);
    }
}
