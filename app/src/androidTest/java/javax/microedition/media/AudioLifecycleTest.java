package javax.microedition.media;

import android.os.SystemClock;
import com.nokia.mid.sound.Sound;
import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

public class AudioLifecycleTest {
    private static byte[] shortWave() {
        int data = 22050 / 3 * 2;
        byte[] bytes = Arrays.copyOf(AudioGainTest.wave(), 44 + data);
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(4, 36 + data).putInt(40, data);
        return bytes;
    }

    private static MicroPlayer wavePlayer() throws Exception {
        MicroPlayer p = new MicroPlayer(new InternalDataSource(new ByteArrayInputStream(shortWave()), "audio/wav"));
        p.setMute(true);
        return p;
    }

    private static void untilStopped(Player p) {
        long deadline = SystemClock.elapsedRealtime() + 5000;
        while (p.getState() == Player.STARTED && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(20);
        assertEquals(Player.PREFETCHED, p.getState());
    }

    @Test public void finiteWavLoopsReplayTheirFullCountWithOrderedEvents() throws Exception {
        MicroPlayer p = wavePlayer();
        List<String> events = Collections.synchronizedList(new ArrayList<>());
        List<Long> endTimes = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch ends = new CountDownLatch(4);
        p.addPlayerListener((player, event, data) -> {
            events.add(event);
            if (event.equals(PlayerListener.END_OF_MEDIA)) { endTimes.add((Long)data); ends.countDown(); }
        });
        try {
            p.setLoopCount(2); p.start(); untilStopped(p);
            assertTrue("End position must survive completion: time=" + p.getMediaTime()
                    + " duration=" + p.getDuration() + " events=" + endTimes, p.getMediaTime() >= 300_000);
            p.start(); untilStopped(p);
            assertTrue(ends.await(2, TimeUnit.SECONDS));
            assertEquals(Arrays.asList("started", "endOfMedia", "started", "endOfMedia",
                    "started", "endOfMedia", "started", "endOfMedia"), events);
            for (long time : endTimes) assertTrue(time >= 300_000);
        } finally { p.close(); }
    }

    @Test public void endCallbackCanReplayAndThenCloseTheSameWav() throws Exception {
        MicroPlayer p = wavePlayer();
        AtomicInteger ends = new AtomicInteger();
        AtomicReference<Exception> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        p.addPlayerListener((player, event, data) -> {
            if (event.equals(PlayerListener.END_OF_MEDIA)) {
                try { if (ends.incrementAndGet() == 1) p.start(); else p.close(); }
                catch (Exception e) { failure.set(e); done.countDown(); }
            }
            if (event.equals(PlayerListener.CLOSED)) done.countDown();
        });
        try {
            p.start(); assertTrue(done.await(5, TimeUnit.SECONDS));
            assertNull(failure.get()); assertEquals(2, ends.get()); assertEquals(Player.CLOSED, p.getState());
        } finally { p.close(); }
    }

    @Test public void toneSequenceRepeatsAndRetainsEndTimeAfterReplay() throws Exception {
        TonePlayer p = new TonePlayer();
        AtomicInteger ends = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(4);
        p.setSequence(new byte[]{javax.microedition.media.control.ToneControl.VERSION, 1, 60, 16});
        p.setMute(true);
        p.addPlayerListener((player, event, data) -> {
            if (event.equals(PlayerListener.END_OF_MEDIA)) { ends.incrementAndGet(); done.countDown(); }
        });
        try {
            p.setLoopCount(2); p.start(); untilStopped(p);
            assertTrue(p.getMediaTime() > 0);
            p.start(); untilStopped(p);
            assertTrue(done.await(2, TimeUnit.SECONDS)); assertEquals(4, ends.get());
        } finally { p.close(); }
    }

    @Test public void failingAndSelfRemovingListenersDoNotBreakOtherDelivery() throws Exception {
        BasePlayer p = new BasePlayer();
        CountDownLatch done = new CountDownLatch(3);
        AtomicInteger calls = new AtomicInteger();
        p.addPlayerListener((player, event, data) -> { throw new IllegalStateException("Expected guest listener failure"); });
        PlayerListener[] self = new PlayerListener[1];
        self[0] = (player, event, data) -> { calls.incrementAndGet(); player.removePlayerListener(self[0]); };
        CountDownLatch removed = new CountDownLatch(1);
        p.addPlayerListener(self[0]);
        p.addPlayerListener((player, event, data) -> { removed.countDown(); done.countDown(); });
        try {
            p.start(); assertTrue(removed.await(2, TimeUnit.SECONDS));
            p.stop(); p.close();
            assertTrue(done.await(2, TimeUnit.SECONDS)); assertEquals(1, calls.get());
        } finally { p.close(); }
    }

    private static BasePlayer backing(Sound sound) throws Exception {
        Field field = Sound.class.getDeclaredField("player"); field.setAccessible(true);
        return (BasePlayer) field.get(sound);
    }

    @Test public void nokiaFiniteRepeatDoesNotReportStoppedBetweenLoops() throws Exception {
        Sound sound = new Sound(shortWave(), Sound.FORMAT_WAV);
        AtomicInteger intermediate = new AtomicInteger(-1);
        CountDownLatch done = new CountDownLatch(1);
        AtomicInteger ends = new AtomicInteger();
        backing(sound).addPlayerListener((player, event, data) -> {
            if (event.equals(PlayerListener.END_OF_MEDIA)) {
                if (ends.incrementAndGet() == 1) intermediate.set(sound.getState());
                else done.countDown();
            }
        });
        try {
            sound.setGain(0); sound.play(2);
            assertTrue(done.await(5, TimeUnit.SECONDS));
            assertEquals(Sound.SOUND_PLAYING, intermediate.get());
            assertEquals(Sound.SOUND_STOPPED, sound.getState());
        } finally { sound.release(); }
    }

    @Test public void queuedOldSoundCompletionCannotStopReplacement() throws Exception {
        Sound sound = new Sound(AudioGainTest.wave(), Sound.FORMAT_WAV);
        BasePlayer old = backing(sound);
        CountDownLatch blocked = new CountDownLatch(1), release = new CountDownLatch(1), drained = new CountDownLatch(1);
        old.addPlayerListener((player, event, data) -> {
            if (event.equals(PlayerListener.STARTED)) {
                blocked.countDown();
                try { release.await(4, TimeUnit.SECONDS); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            if (event.equals(PlayerListener.CLOSED)) drained.countDown();
        });
        try {
            sound.setGain(0); sound.play(1);
            assertTrue(blocked.await(2, TimeUnit.SECONDS));
            old.postEvent(PlayerListener.END_OF_MEDIA, 2_000_000L);
            sound.init(AudioGainTest.wave(), Sound.FORMAT_WAV); sound.play(0);
            release.countDown(); assertTrue(drained.await(2, TimeUnit.SECONDS));
            assertEquals(Sound.SOUND_PLAYING, sound.getState());
            assertEquals(Player.STARTED, backing(sound).getState());
        } finally { release.countDown(); sound.release(); }
    }
}
