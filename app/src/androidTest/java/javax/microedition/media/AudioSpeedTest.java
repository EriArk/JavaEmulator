package javax.microedition.media;

import android.os.SystemClock;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import javax.microedition.shell.GuestTime;
import static org.junit.Assert.*;

public class AudioSpeedTest {
    @Test public void rejectedRateCannotBlockReturnToNormal() {
        class Backend extends android.media.MediaPlayer {
            float rate = 1;
            @Override public void setPlaybackParams(android.media.PlaybackParams params) {
                if (params.getSpeed() == .5f) throw new IllegalArgumentException("Fixture rejects half speed");
                rate = params.getSpeed();
            }
        }
        Backend backend = new Backend();
        try {
            ru.playsoftware.j2meloader.runtime.AudioSpeed speed = new ru.playsoftware.j2meloader.runtime.AudioSpeed();
            speed.applyPlaying(backend, 200); assertEquals(2f, backend.rate, 0);
            speed.applyPlaying(backend, 50); assertEquals(2f, backend.rate, 0);
            speed.applyPlaying(backend, 100); assertEquals(1f, backend.rate, 0);
        } finally { backend.release(); }
    }
    private static final byte[] MIDI = {
            'M','T','h','d',0,0,0,6,0,0,0,1,0,96,
            'M','T','r','k',0,0,0,13,
            0,(byte)0x90,60,40,(byte)0x98,0,(byte)0x80,60,0,0,(byte)0xff,0x2f,0
    };

    private File fixture(byte[] data, String suffix) throws Exception {
        File file = File.createTempFile("audio-speed-", suffix,
                InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir());
        Files.write(file.toPath(), data);
        return file;
    }

    @Test public void waveSpeedPauseUndoAndReset() throws Exception {
        int rate = 22050, samples = rate * 20;
        ByteBuffer wav = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN);
        wav.put(new byte[]{'R','I','F','F'}).putInt(36 + samples * 2)
                .put(new byte[]{'W','A','V','E','f','m','t',' '}).putInt(16)
                .putShort((short)1).putShort((short)1).putInt(rate).putInt(rate * 2)
                .putShort((short)2).putShort((short)16)
                .put(new byte[]{'d','a','t','a'}).putInt(samples * 2);
        for (int i = 0; i < samples; i++) wav.putShort((short)(Math.sin(i * 440 * 2 * Math.PI / rate) * 1000));
        check(fixture(wav.array(), ".wav"));
    }

    @Test public void midiSpeedPauseUndoAndReset() throws Exception { check(fixture(MIDI, ".mid")); }

    private void check(File file) throws Exception {
        AndroidPlayer player = new AndroidPlayer();
        try {
            GuestTime.speed(100);
            player.setDataSource(file.getAbsolutePath());
            player.start();
            for (int speed : new int[]{50, 200, 100}) {
                GuestTime.speed(speed);
                SystemClock.sleep(1200);
                int before = player.getCurrentPosition();
                long start = SystemClock.elapsedRealtime();
                SystemClock.sleep(1000);
                double ratio = (player.getCurrentPosition() - before) /
                        (double)(SystemClock.elapsedRealtime() - start);
                android.util.Log.i("AudioSpeedTest", file.getName() + " requested=" + speed
                        + " params=" + player.getPlaybackParams().getSpeed() + " measured=" + ratio);
                assertEquals("Actual media position at " + speed + "%", speed / 100.0, ratio, .30);
                player.pause();
                int paused = player.getCurrentPosition();
                GuestTime.speed(speed == 50 ? 200 : 50);
                SystemClock.sleep(250);
                assertFalse("Changing speed must not resume paused audio", player.isPlaying());
                assertEquals(paused, player.getCurrentPosition(), 45);
                GuestTime.speed(speed);
                player.start();
            }
            player.reset();
            GuestTime.speed(200);
            player.setDataSource(file.getAbsolutePath());
            player.start();
            SystemClock.sleep(200);
            assertEquals(2f, player.getPlaybackParams().getSpeed(), .01f);
        } finally {
            player.release();
            GuestTime.speed(100);
            assertTrue(file.delete());
        }
    }
}
