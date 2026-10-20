// SPDX-License-Identifier: Apache-2.0
package javax.microedition.lcdui;

import java.util.function.Consumer;

/** Coalesces queued frames without discarding a request made during presentation. */
final class FramePresenter {
    private final Consumer<Runnable> schedule;
    private final Runnable present;
    private boolean pending;

    FramePresenter(Consumer<Runnable> schedule, Runnable present) {
        this.schedule = schedule;
        this.present = present;
    }

    synchronized void request() {
        if (pending) return;
        pending = true;
        schedule.accept(this::dispatch);
    }

    private void dispatch() {
        // The running frame no longer owns the queued slot. Producers may queue its successor.
        synchronized (this) { pending = false; }
        present.run();
    }
}
