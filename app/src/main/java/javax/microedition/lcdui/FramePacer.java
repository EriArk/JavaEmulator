package javax.microedition.lcdui;

import java.util.function.IntSupplier;

/** Paces guest frame submissions, not guest clocks or Android presentation. */
public final class FramePacer {
    interface Clock {
        long now();
        void await(Object lock, long nanos) throws InterruptedException;
    }
    private final IntSupplier limit;
    private final Clock clock;
    private long last;
    private int previousLimit;
    private boolean ready;

    public FramePacer(IntSupplier limit) {
        this(limit, new Clock() {
            public long now() { return System.nanoTime(); }
            public void await(Object lock, long nanos) throws InterruptedException {
                lock.wait(nanos / 1_000_000L, (int) (nanos % 1_000_000L));
            }
        });
    }

    FramePacer(IntSupplier limit, Clock clock) { this.limit = limit; this.clock = clock; }

    public synchronized void reset() { ready = false; notifyAll(); }

    public synchronized void awaitFrame() {
        for (;;) {
            int fps = limit.getAsInt();
            long now = clock.now();
            if (fps <= 0) { ready = false; return; }
            if (!ready || fps != previousLimit) {
                previousLimit = fps;
                last = now;
                ready = true;
                return;
            }
            long remaining = Math.max(1, 1_000_000_000L / fps) - (now - last);
            if (remaining <= 0) {
                // Never accumulate catch-up frames after a pause or slow paint.
                last = now;
                return;
            }
            try {
                // Recheck live changes even when a legacy profile uses a very low cap.
                // wait releases the monitor: reset and other producers cannot deadlock.
                clock.await(this, Math.min(remaining, 20_000_000L));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
