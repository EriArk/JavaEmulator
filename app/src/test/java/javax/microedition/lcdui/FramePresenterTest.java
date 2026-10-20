// SPDX-License-Identifier: Apache-2.0
package javax.microedition.lcdui;

import org.junit.Test;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class FramePresenterTest {
    @Test public void burstBeforeDispatchPresentsOnlyTheNewestFrame() {
        Queue<Runnable> queue = new ArrayDeque<>();
        AtomicInteger frame = new AtomicInteger(), drawn = new AtomicInteger();
        FramePresenter presenter = new FramePresenter(queue::add, () -> drawn.set(frame.get()));
        for (int i = 1; i <= 100; i++) { frame.set(i); presenter.request(); }
        assertEquals(1, queue.size());
        queue.remove().run(); assertEquals(100, drawn.get()); assertTrue(queue.isEmpty());
    }

    @Test public void requestDuringPresentationIsNeverLostAndStillCoalesces() throws Exception {
        Queue<Runnable> queue = new ConcurrentLinkedQueue<>();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger draws = new AtomicInteger();
        FramePresenter presenter = new FramePresenter(queue::add, () -> {
            if (draws.incrementAndGet() != 1) return;
            entered.countDown();
            try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
            catch (InterruptedException e) { throw new AssertionError(e); }
        });
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            presenter.request();
            Future<?> running = worker.submit(queue.remove());
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            for (int i = 0; i < 100; i++) presenter.request();
            assertEquals(1, queue.size());
            release.countDown(); running.get(5, TimeUnit.SECONDS);
            assertEquals("Completing the old frame must retain its successor", 1, queue.size());
            queue.remove().run(); assertEquals(2, draws.get());
            presenter.request(); queue.remove().run(); assertEquals(3, draws.get());
        } finally { release.countDown(); worker.shutdownNow(); }
    }

    @Test public void failedPresentationDoesNotLatchTheQueue() {
        Queue<Runnable> queue = new ArrayDeque<>();
        AtomicInteger attempts = new AtomicInteger();
        FramePresenter presenter = new FramePresenter(queue::add, () -> {
            if (attempts.incrementAndGet() == 1) throw new IllegalStateException("surface gone");
        });
        presenter.request();
        try { queue.remove().run(); fail(); } catch (IllegalStateException expected) { }
        presenter.request(); queue.remove().run();
        assertEquals(2, attempts.get());
    }
}
