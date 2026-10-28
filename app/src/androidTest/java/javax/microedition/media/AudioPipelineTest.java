package javax.microedition.media;

import android.os.SystemClock;
import androidx.test.platform.app.InstrumentationRegistry;
import com.arthenica.mobileffmpeg.FFprobe;
import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import static org.junit.Assert.*;

/** Generated audio only; exercises the same stream and player path used by games. */
public class AudioPipelineTest {
    static File fixture(byte[] bytes, String suffix) throws Exception {
        File file = File.createTempFile("audio pipeline ", suffix,
                InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir());
        Files.write(file.toPath(), bytes);
        return file;
    }

    @Test public void availableIsNotTheAudioLength() throws Exception {
        byte[] wave = AudioGainTest.wave();
        InternalDataSource source = new InternalDataSource(new ByteArrayInputStream(wave) {
            @Override public int available() { return wave.length + 1234; }
        }, "audio/wav");
        try { assertArrayEquals(wave, Files.readAllBytes(new File(source.getLocator()).toPath())); }
        finally { source.disconnect(); }
    }

    @Test public void brokenStreamFailsInsteadOfPlayingTruncatedAudio() throws Exception {
        boolean[] closed = {false};
        InputStream stream = new InputStream() {
            @Override public int read() throws IOException { throw new IOException("fixture read failed"); }
            @Override public void close() { closed[0] = true; }
        };
        try {
            InternalDataSource source = new InternalDataSource(stream, "audio/wav");
            source.disconnect();
            fail("A failed audio copy must propagate IOException");
        } catch (IOException expected) { assertEquals("fixture read failed", expected.getMessage()); }
        assertTrue(closed[0]);
    }

    @Test public void failedLoadCanRecoverWithAValidSource() throws Exception {
        File file = fixture(AudioGainTest.wave(), ".wav");
        AndroidPlayer player = new AndroidPlayer();
        try {
            player.setDataSource(file.getPath() + ".missing");
            try { player.start(); fail("Missing source must fail synchronously"); }
            catch (IllegalStateException expected) { /* Retry on this same instance. */ }
            player.setDataSource(file.getPath());
            player.start();
            assertTrue(player.isPlaying());
        } finally { player.release(); file.delete(); }
    }

    @Test public void resetClearsPendingSeekButPreservesVolumeAndLooping() throws Exception {
        File file = fixture(AudioGainTest.wave(), ".wav");
        AndroidPlayer player = new AndroidPlayer();
        try {
            player.setDataSource(file.getPath());
            player.setVolume(0, 0);
            player.setLooping(true);
            player.seekTo(1500);
            player.reset();
            player.start();
            SystemClock.sleep(120);
            assertTrue("Replay must start at the beginning", player.getCurrentPosition() < 700);
            assertTrue(player.isLooping());
            Field volume = AndroidPlayer.class.getDeclaredField("leftVolume");
            volume.setAccessible(true);
            assertEquals(0f, volume.getFloat(player), 0f);
        } finally { player.release(); file.delete(); }
    }

    @Test public void longMediaTimeDoesNotOverflowBeforeMillisecondsConversion() throws Exception {
        InternalDataSource source = new InternalDataSource(new ByteArrayInputStream(AudioGainTest.wave()), "audio/wav");
        MicroPlayer player = new MicroPlayer(source);
        try {
            player.doSetMediaTime(3_600_000_000L);
            assertEquals(3_600_000_000L, player.doGetMediaTime());
            player.doSetMediaTime(-1);
            assertEquals(0, player.doGetMediaTime());
        } finally { player.close(); }
    }

    @Test public void adpcmKeepsOriginalSampleRate() throws Exception {
        // One valid mono IMA block, encoded directly: no optional FFmpeg encoder required.
        ByteBuffer wav = ByteBuffer.allocate(60 + 256).order(ByteOrder.LITTLE_ENDIAN);
        wav.put(new byte[]{'R','I','F','F'}).putInt(308).put(new byte[]{'W','A','V','E'});
        wav.put(new byte[]{'f','m','t',' '}).putInt(20).putShort((short)17).putShort((short)1)
                .putInt(22050).putInt(11178).putShort((short)256).putShort((short)4)
                .putShort((short)2).putShort((short)505);
        wav.put(new byte[]{'f','a','c','t'}).putInt(4).putInt(505);
        wav.put(new byte[]{'d','a','t','a'}).putInt(256).putShort((short)1000);
        InternalDataSource source = null;
        try {
            source = new InternalDataSource(new ByteArrayInputStream(wav.array()), "audio/wav");
            com.arthenica.mobileffmpeg.StreamInformation stream = FFprobe
                    .getMediaInformation(source.getLocator()).getStreams().get(0);
            assertEquals("pcm_s16le", stream.getCodec());
            assertEquals("22050", String.valueOf(stream.getSampleRate()));
            AndroidPlayer player = new AndroidPlayer();
            try {
                player.setLooping(true); player.setDataSource(source.getLocator()); player.start();
                assertTrue(player.isPlaying());
            } finally { player.release(); }
        } finally {
            if (source != null) source.disconnect();
        }
    }

    @Test public void zeroLengthReadStillMakesProgress() throws Exception {
        byte[] wave = AudioGainTest.wave();
        InternalDataSource source = new InternalDataSource(new ByteArrayInputStream(wave) {
            boolean first = true;
            @Override public synchronized int read(byte[] bytes, int offset, int length) {
                if (first) { first = false; return 0; }
                return super.read(bytes, offset, length);
            }
        }, "audio/wav");
        try { assertArrayEquals(wave, Files.readAllBytes(new File(source.getLocator()).toPath())); }
        finally { source.disconnect(); }
    }

    @Test public void prefetchPreparesDurationAndFailureDoesNotReportStarted() throws Exception {
        MicroPlayer valid = new MicroPlayer(new InternalDataSource(
                new ByteArrayInputStream(AudioGainTest.wave()), "audio/wav"));
        MicroPlayer invalid = new MicroPlayer(new InternalDataSource(
                new ByteArrayInputStream(new byte[]{1, 2, 3}), "audio/wav"));
        try {
            valid.prefetch();
            assertEquals(Player.PREFETCHED, valid.getState());
            assertEquals(2_000_000L, valid.getDuration(), 50_000L);
            try { invalid.start(); fail("Bad audio must produce a guest MediaException"); }
            catch (MediaException expected) { assertEquals(Player.REALIZED, invalid.getState()); }
            valid.setLevel(0); valid.start(); valid.stop(); valid.deallocate();
            valid.start();
            assertEquals(Player.STARTED, valid.getState());
            assertEquals(0, valid.getLevel());
        } finally { valid.close(); invalid.close(); }
    }

    @Test public void finiteLoopsCompleteAndCanReplay() throws Exception {
        MicroPlayer player = new MicroPlayer(new InternalDataSource(
                new ByteArrayInputStream(AudioGainTest.wave()), "audio/wav"));
        try {
            player.setLevel(0); player.setLoopCount(2); player.start();
            long deadline = SystemClock.elapsedRealtime() + 6500;
            while (player.getState() == Player.STARTED && SystemClock.elapsedRealtime() < deadline) {
                SystemClock.sleep(50);
            }
            assertEquals(Player.PREFETCHED, player.getState());
            assertEquals("Known duration survives completion/reset", 2_000_000L, player.getDuration(), 50_000L);
            player.start();
            assertEquals(Player.STARTED, player.getState());
            assertEquals(0, player.getLevel());
        } finally { player.close(); }
    }

    @Test public void toneSequenceCanBeReplacedAfterDeallocation() throws Exception {
        TonePlayer player = new TonePlayer();
        try {
            byte version = javax.microedition.media.control.ToneControl.VERSION;
            player.setSequence(new byte[]{version, 1, 60, 16, 64, 16});
            player.setMute(true); player.prefetch();
            assertTrue(player.getDuration() > 0);
            player.start(); player.stop(); player.deallocate();
            player.setSequence(new byte[]{version, 1, 67, 32});
            player.start();
            assertEquals(Player.STARTED, player.getState());
            assertTrue(player.isMuted());
            player.close();
            player.setPan(100); // Matches existing post-close setLevel/setMute tolerance.
            assertEquals(0, player.getPan());
        } finally { player.close(); }
    }
}
