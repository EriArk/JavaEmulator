// SPDX-License-Identifier: GPL-3.0-only
package ru.playsoftware.j2meloader.mophun;

import java.util.function.LongSupplier;

/** Phone VM throughput, independent of the host display's presentation cap. */
final class MophunCpuPacer {
    interface Waiter { void waitMillis(long millis) throws InterruptedException; }
    private final LongSupplier clock;
    private final Waiter waiter;
    private final Runnable checkpoint;
    private final int unitsPerMillisecond;
    private volatile boolean reset=true;
    private long previous;
    private double deadline;

    MophunCpuPacer(LongSupplier clock, Waiter waiter, Runnable checkpoint, int unitsPerMillisecond) {
        this.clock=clock; this.waiter=waiter; this.checkpoint=checkpoint;
        this.unitsPerMillisecond=unitsPerMillisecond;
    }
    void reset() { reset=true; }
    void await(long work, int budget) throws InterruptedException {
        long now=clock.getAsLong();
        if(reset) { previous=work; deadline=now; reset=false; return; }
        long delta=work-previous; previous=work;
        // Do not bank unlimited execution credit while waiting for input or display.
        deadline=Math.max(deadline,now-16) + delta*100.0/(unitsPerMillisecond*budget);
        while(!reset && deadline-clock.getAsLong()>=1) {
            checkpoint.run();
            if(reset)return;
            long remaining=(long)(deadline-clock.getAsLong());
            if(remaining>0)waiter.waitMillis(Math.min(10,remaining));
        }
    }
}
