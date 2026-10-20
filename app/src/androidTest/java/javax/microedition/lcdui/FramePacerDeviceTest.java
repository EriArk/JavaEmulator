package javax.microedition.lcdui;

import android.os.Bundle;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class FramePacerDeviceTest {
    @Test public void concurrentProducersShareOneFrameBudget() throws Exception {
        FramePacer pacer = new FramePacer(() -> 20);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Void> producer = () -> {
                start.await();
                for (int i = 0; i < 10; i++) pacer.awaitFrame();
                return null;
            };
            Future<Void> first = workers.submit(producer), second = workers.submit(producer);
            long began = System.nanoTime();
            start.countDown();
            first.get(5, TimeUnit.SECONDS); second.get(5, TimeUnit.SECONDS);
            long elapsed = System.nanoTime() - began;
            report("20 shared submissions at 20 FPS: " + elapsed / 1_000_000 + " ms");
            assertTrue("Concurrent callers must not double the cap", elapsed >= 950_000_000L);
        } finally { workers.shutdownNow(); }
    }

    @Test public void renderPacingDoesNotStopIndependentGameTimer() throws Exception {
        FramePacer pacer = new FramePacer(() -> 20);
        ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
        AtomicInteger ticks = new AtomicInteger();
        try {
            timer.scheduleAtFixedRate(ticks::incrementAndGet, 0, 10, TimeUnit.MILLISECONDS);
            long began = System.nanoTime();
            for (int i = 0; i < 21; i++) pacer.awaitFrame();
            long elapsed = System.nanoTime() - began;
            report("20 intervals: " + elapsed / 1_000_000 + " ms; independent ticks: " + ticks.get());
            assertTrue(elapsed >= 1_000_000_000L);
            assertTrue("Independent logic is not capped to the render rate", ticks.get() >= 50);
        } finally { timer.shutdownNow(); }
    }

    @Test public void sixtyFpsCadencePreservesWallClock() {
        FramePacer pacer = new FramePacer(() -> 60);
        long began = System.nanoTime();
        pacer.awaitFrame();
        for (int i = 0; i < 120; i++) pacer.awaitFrame();
        long elapsed = System.nanoTime() - began;
        report("120 intervals at 60 FPS: " + elapsed / 1_000_000.0 + " ms");
        assertTrue("No premature 60 FPS budget", elapsed >= 1_999_999_000L);
    }

    @Test public void mainLooperRetainsFrameRequestedDuringPresentation() throws Exception {
        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1),
                second = new CountDownLatch(1);
        AtomicInteger frame = new AtomicInteger(1), drawn = new AtomicInteger();
        FramePresenter presenter = new FramePresenter(handler::post, () -> {
            int captured = frame.get();
            if (captured == 1) {
                entered.countDown();
                try { release.await(5, TimeUnit.SECONDS); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            drawn.set(captured);
            if (captured == 2) second.countDown();
        });
        try {
            presenter.request(); assertTrue(entered.await(5, TimeUnit.SECONDS));
            frame.set(2);
            for (int i = 0; i < 100; i++) presenter.request();
            release.countDown();
            assertTrue("Latest frame must not wait for a third game submission",
                    second.await(5, TimeUnit.SECONDS));
            assertEquals(2, drawn.get());
        } finally { release.countDown(); }
    }

    private static void report(String message) {
        Bundle result = new Bundle();
        result.putString("stream", "\nFramePacer: " + message + "\n");
        InstrumentationRegistry.getInstrumentation().sendStatus(0, result);
    }
}
