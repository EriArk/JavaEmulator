package javax.microedition.lcdui;

import org.junit.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class FramePacerTest {
    private static class Time implements FramePacer.Clock {
        long now, waited;
        Runnable onWait;
        public long now() { return now; }
        public void await(Object lock, long nanos) throws InterruptedException {
            now += nanos; waited += nanos;
            if (onWait != null) { Runnable action = onWait; onWait = null; action.run(); }
        }
    }
    @Test public void fractionalIntervalsDoNotTurnSixtyIntoSixtyTwoFps() {
        for (int fps : new int[]{15, 20, 30, 60, 144}) {
            Time time = new Time(); FramePacer pacer = new FramePacer(() -> fps, time);
            pacer.awaitFrame();
            for (int i = 0; i < fps; i++) pacer.awaitFrame();
            assertEquals(fps * (1_000_000_000L / fps), time.now);
            assertTrue(time.now > 999_999_000L);
        }
    }
    @Test public void slowFrameAndResumeNeverAccumulateCatchUpWork() {
        Time time = new Time(); FramePacer pacer = new FramePacer(() -> 20, time);
        pacer.awaitFrame(); time.now += 10_000_000_000L;
        pacer.awaitFrame(); assertEquals(0, time.waited);
        pacer.awaitFrame(); assertEquals(50_000_000L, time.waited);
        pacer.reset(); pacer.awaitFrame(); assertEquals(50_000_000L, time.waited);
        pacer.awaitFrame(); assertEquals(100_000_000L, time.waited);
    }
    @Test public void lowLimitCanBeChangedWhileWaiting() {
        Time time = new Time(); AtomicInteger fps = new AtomicInteger(1);
        FramePacer pacer = new FramePacer(fps::get, time); pacer.awaitFrame();
        time.onWait = () -> fps.set(60); pacer.awaitFrame();
        assertEquals(20_000_000L, time.waited);
        pacer.awaitFrame(); assertEquals(36_666_666L, time.waited);
        fps.set(0); pacer.awaitFrame(); assertEquals(36_666_666L, time.waited);
    }
    @Test public void unlimitedAndIndependentCanvasesDoNotShareDeadlines() {
        Time time = new Time(); FramePacer one = new FramePacer(() -> 20, time);
        FramePacer two = new FramePacer(() -> 20, time);
        one.awaitFrame(); two.awaitFrame(); assertEquals(0, time.waited);
        for (int value : new int[]{0, -1}) {
            FramePacer unlimited = new FramePacer(() -> value, time);
            for (int i = 0; i < 100; i++) unlimited.awaitFrame();
        }
        assertEquals(0, time.waited);
    }
    @Test public void interruptedWaitPreservesTheCallerInterrupt() {
        FramePacer pacer = new FramePacer(() -> 20, new FramePacer.Clock() {
            public long now() { return 0; }
            public void await(Object lock, long nanos) throws InterruptedException { throw new InterruptedException(); }
        });
        pacer.awaitFrame();
        try { pacer.awaitFrame(); assertTrue(Thread.currentThread().isInterrupted()); }
        finally { Thread.interrupted(); }
    }
    @Test public void spuriousWakeupDoesNotAdmitAnEarlyFrame() {
        Time time = new Time() {
            boolean first = true;
            @Override public void await(Object lock, long nanos) throws InterruptedException {
                if (first) { first = false; return; }
                super.await(lock, nanos);
            }
        };
        FramePacer pacer = new FramePacer(() -> 20, time);
        pacer.awaitFrame(); pacer.awaitFrame(); assertEquals(50_000_000L, time.now);
    }
}
