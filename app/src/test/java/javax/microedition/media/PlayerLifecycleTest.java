package javax.microedition.media;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

public class PlayerLifecycleTest {
    static final class Probe extends BasePlayer {
        int starts, resets, realizes;
        long time;
        @Override public void doStart() { starts++; time = 0; }
        @Override public void doReset() { resets++; }
        @Override public void doRealize() { realizes++; }
        @Override public long doGetMediaTime() { return time; }
        void end() { time = 1000; complete(); }
    }

    @Test public void delayedListenerCannotReorderEventsOrHoldPlayerLock() throws Exception {
        Probe p = new Probe();
        List<String> events = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(3);
        AtomicBoolean underLock = new AtomicBoolean();
        p.addPlayerListener((player, event, data) -> {
            if (Thread.holdsLock(p)) underLock.set(true);
            if (event.equals(PlayerListener.STARTED)) {
                entered.countDown();
                try { release.await(3, TimeUnit.SECONDS); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            events.add(event); done.countDown();
        });
        try {
            p.start(); assertTrue(entered.await(2, TimeUnit.SECONDS));
            p.stop(); p.close();
            assertEquals(Player.CLOSED, p.getState());
            assertTrue(events.isEmpty());
            release.countDown();
            assertTrue(done.await(2, TimeUnit.SECONDS));
            assertEquals(Arrays.asList(PlayerListener.STARTED, PlayerListener.STOPPED,
                    PlayerListener.CLOSED), events);
            assertFalse(underLock.get());
        } finally { release.countDown(); p.close(); }
    }

    @Test public void finiteLoopCountSurvivesPauseAndRestartsAfterFullCompletion() throws Exception {
        Probe p = new Probe();
        try {
            p.setLoopCount(3); p.start(); p.end();
            assertEquals(2, p.starts);
            p.stop(); p.start(); p.end();
            assertEquals(Player.STARTED, p.getState());
            p.end();
            assertEquals(Player.PREFETCHED, p.getState());
            assertEquals(1000, p.getMediaTime());
            assertEquals(0, p.resets);
            p.start(); p.end(); p.end();
            assertEquals(Player.STARTED, p.getState());
            p.end(); assertEquals(Player.PREFETCHED, p.getState());
        } finally { p.close(); }
    }

    @Test public void lateCompletionDoesNotAlterStoppedOrDeallocatedPlayer() throws Exception {
        Probe p = new Probe();
        try {
            p.setLoopCount(2); p.start(); p.stop(); p.complete();
            assertEquals(1, p.starts);
            p.start(); p.end();
            assertEquals(Player.STARTED, p.getState());
            p.deallocate(); p.complete();
            assertEquals(Player.REALIZED, p.getState());
            assertEquals(1, p.realizes);
            p.close(); p.complete();
            assertEquals(Player.CLOSED, p.getState());
            assertEquals(1, p.realizes);
        } finally { p.close(); }
    }

    @Test public void callbacksCanRestartAndCloseTheirOwnPlayer() throws Exception {
        Probe p = new Probe();
        CountDownLatch restarted = new CountDownLatch(1), closed = new CountDownLatch(1);
        p.addPlayerListener((player, event, data) -> {
            if (event.equals(PlayerListener.END_OF_MEDIA)) {
                try { p.start(); restarted.countDown(); p.close(); }
                catch (MediaException e) { throw new AssertionError(e); }
            }
            if (event.equals(PlayerListener.CLOSED)) closed.countDown();
        });
        try {
            p.start(); p.end();
            assertTrue(restarted.await(2, TimeUnit.SECONDS));
            assertTrue(closed.await(2, TimeUnit.SECONDS));
            assertEquals(2, p.starts);
        } finally { p.close(); }
    }

    @Test public void blockedPlayerDoesNotBlockAnotherPlayersCallbacks() throws Exception {
        Probe p = new Probe(), other = new Probe();
        CountDownLatch blocked = new CountDownLatch(1), release = new CountDownLatch(1);
        CountDownLatch delivered = new CountDownLatch(1);
        p.addPlayerListener((player, event, data) -> {
            blocked.countDown();
            try { release.await(3, TimeUnit.SECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        other.addPlayerListener((player, event, data) -> delivered.countDown());
        try {
            p.start(); assertTrue(blocked.await(2, TimeUnit.SECONDS));
            other.start(); assertTrue(delivered.await(2, TimeUnit.SECONDS));
        } finally { release.countDown(); p.close(); other.close(); }
    }

    @Test public void loopValidationRejectsOtherNegativeCounts() {
        Probe p = new Probe();
        try {
            for (int count : new int[]{0, -2, Integer.MIN_VALUE}) {
                try { p.setLoopCount(count); fail("Invalid loop count"); }
                catch (IllegalArgumentException expected) { }
            }
            p.setLoopCount(-1); p.setLoopCount(1);
        } finally { p.close(); }
    }
}
