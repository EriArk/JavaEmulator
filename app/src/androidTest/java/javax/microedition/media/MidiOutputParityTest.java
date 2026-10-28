package javax.microedition.media;

import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.audiofx.Visualizer;
import android.os.Bundle;
import android.os.SystemClock;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Assume;
import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.Arrays;
import static org.junit.Assert.*;

/** Compares MMAPI against Android's own MIDI backend without altering authored MIDI dynamics. */
public class MidiOutputParityTest {
    @Test public void quietAndLoudMidiMatchAndroidAndConcurrentEffectsStayIndependent() throws Exception {
        withAudio(() -> {
            int quiet = compare(midi(32), "generated CC7=32", true);
            int loud = compare(midi(127), "generated CC7=127", false);
            assertTrue("Authored channel volume must remain effective", loud > quiet + 500);
        });
    }

    @Test public void optionalPrivateMidiMatchesAndroid() throws Exception {
        String path = InstrumentationRegistry.getArguments().getString("audioMidi");
        Assume.assumeTrue("Pass audioMidi for an authorized local game asset", path != null);
        byte[] bytes = Files.readAllBytes(new File(path).toPath());
        assertEquals('M', bytes[0]); assertEquals('T', bytes[1]);
        withAudio(() -> compare(bytes, "private MIDI", false));
    }

    private static byte[] midi(int volume) {
        return new byte[]{'M','T','h','d',0,0,0,6,0,0,0,1,0,96,
                'M','T','r','k',0,0,0,20,
                0,(byte)0xc0,19, 0,(byte)0xb0,7,(byte)volume,
                0,(byte)0x90,60,100, (byte)0x98,0,(byte)0x80,60,0,
                0,(byte)0xff,0x2f,0};
    }

    private static int compare(byte[] midi, String label, boolean concurrency) throws Exception {
        File file = AudioPipelineTest.fixture(midi, ".mid");
        MediaPlayer raw = new MediaPlayer();
        MicroPlayer wrapped = null, effect = null;
        Visualizer rawMeter = null, wrappedMeter = null;
        try {
            raw.setDataSource(file.getPath()); raw.prepare(); raw.start();
            rawMeter = meter(raw);
            int androidRms = rms(rawMeter);
            raw.pause(); rawMeter.release(); rawMeter = null;
            wrapped = new MicroPlayer(new InternalDataSource(new ByteArrayInputStream(midi), "audio/midi"));
            wrapped.start();
            Field field = MicroPlayer.class.getDeclaredField("player"); field.setAccessible(true);
            wrappedMeter = meter((MediaPlayer) field.get(wrapped));
            int mmapiRms = rms(wrappedMeter);
            report(label + " Android=" + androidRms + " MMAPI=" + mmapiRms + " mB");
            assertTrue("MIDI must produce measurable output", androidRms > -9000);
            assertEquals("MMAPI must not attenuate this MIDI beyond Android", androidRms, mmapiRms, 350);
            if (concurrency) {
                effect = new MicroPlayer(new InternalDataSource(new ByteArrayInputStream(AudioGainTest.wave()), "audio/wav"));
                effect.setLoopCount(-1); effect.setLevel(0); effect.start();
                assertEquals("Silent effect must not mute music", mmapiRms, rms(wrappedMeter), 350);
                effect.setLevel(100);
                assertEquals("Another session must not change music gain", mmapiRms, rms(wrappedMeter), 350);
                wrapped.setMute(true);
                assertTrue("Muted music stays silent with an audible effect", rms(wrappedMeter) <= -9000);
                wrapped.stop(); wrapped.start();
                assertTrue("Resume retains mute", rms(wrappedMeter) <= -9000);
                wrapped.setMute(false);
                assertEquals("Unmute restores original music gain", mmapiRms, rms(wrappedMeter), 350);
            }
            return mmapiRms;
        } finally {
            if (rawMeter != null) rawMeter.release();
            if (wrappedMeter != null) wrappedMeter.release();
            raw.release();
            if (wrapped != null) wrapped.close();
            if (effect != null) effect.close();
            file.delete();
        }
    }

    private static Visualizer meter(MediaPlayer player) {
        Visualizer meter = new Visualizer(player.getAudioSessionId());
        assertEquals(0, meter.setMeasurementMode(Visualizer.MEASUREMENT_MODE_PEAK_RMS));
        assertEquals(0, meter.setEnabled(true));
        return meter;
    }

    static int rms(Visualizer meter) {
        SystemClock.sleep(600);
        int[] samples = new int[9];
        for (int i = 0; i < samples.length; i++) {
            Visualizer.MeasurementPeakRms value = new Visualizer.MeasurementPeakRms();
            assertEquals(0, meter.getMeasurementPeakRms(value));
            samples[i] = value.mRms;
            SystemClock.sleep(100);
        }
        Arrays.sort(samples);
        return samples[samples.length / 2];
    }

    interface Check { void run() throws Exception; }
    static void withAudio(Check check) throws Exception {
        android.app.UiAutomation ui = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        ui.adoptShellPermissionIdentity("android.permission.RECORD_AUDIO", "android.permission.MODIFY_AUDIO_SETTINGS");
        AudioManager audio = (AudioManager) InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getSystemService(android.content.Context.AUDIO_SERVICE);
        boolean muted = audio.isStreamMute(AudioManager.STREAM_MUSIC);
        if (muted) audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0);
        int volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        try {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, 2, 0);
            check.run();
        } finally {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0);
            if (muted) audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0);
            ui.dropShellPermissionIdentity();
        }
    }

    static void report(String message) {
        Bundle report = new Bundle(); report.putString("stream", message + "\n");
        InstrumentationRegistry.getInstrumentation().sendStatus(0, report);
    }
}
