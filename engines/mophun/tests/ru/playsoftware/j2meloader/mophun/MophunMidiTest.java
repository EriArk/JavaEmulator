package ru.playsoftware.j2meloader.mophun;

import android.os.SystemClock;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.nio.file.Files;
import java.util.function.BooleanSupplier;
import org.junit.Test;
import static org.junit.Assert.*;

public class MophunMidiTest {
    // Original test fixture: a middle-C note lasting four quarter notes.
    private static final byte[] NOTE = {
        'M','T','h','d',0,0,0,6,0,0,0,1,0,96,
        'M','T','r','k',0,0,0,13,
        0,(byte)0x90,60,100,(byte)0x83,0,(byte)0x80,60,0,0,(byte)0xff,0x2f,0
    };

    private static void until(BooleanSupplier condition) {
        long end = SystemClock.elapsedRealtime() + 5000;
        while (!condition.getAsBoolean() && SystemClock.elapsedRealtime() < end) SystemClock.sleep(10);
        assertTrue("MIDI player did not reach expected state",condition.getAsBoolean());
    }

    private File cache() throws Exception {
        return Files.createTempDirectory(InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getCacheDir().toPath(),"midi-test-").toFile();
    }

    @Test public void loopCanPauseResumeStopAndRelease() throws Exception {
        File folder = cache();
        try (MophunMidi midi = new MophunMidi(folder)) {
            midi.play(NOTE,true);
            until(() -> midi.playing() || midi.error() != null);
            assertNull(midi.error());
            midi.pause(true);
            until(() -> !midi.playing());
            int starts = midi.starts();
            SystemClock.sleep(250);
            assertEquals(starts,midi.starts());
            midi.pause(false);
            until(midi::playing);
            assertTrue(midi.starts() > starts);
            midi.stop();
            until(() -> !midi.playing());
            assertNull(midi.error());
        }
        assertEquals("Temporary MIDI files must be removed",0,folder.list().length);
    }

    @Test public void preparingWhilePausedCannotStartInBackground() throws Exception {
        try (MophunMidi midi = new MophunMidi(cache())) {
            midi.pause(true);
            midi.play(NOTE,true);
            SystemClock.sleep(600);
            assertNull(midi.error());
            assertEquals(0,midi.starts());
            midi.pause(false);
            until(() -> midi.playing() || midi.error() != null);
            assertNull(midi.error());
            assertTrue(midi.playing());
        }
    }

    @Test public void rapidReplacementAndCloseDoNotLeavePlaybackRunning() throws Exception {
        File folder = cache();
        MophunMidi midi = new MophunMidi(folder);
        try {
            for (int i = 0; i < 10; i++) { midi.play(NOTE,true); midi.stop(); }
            midi.play(NOTE,true);
        } finally { midi.close(); }
        assertFalse(midi.playing());
        assertNull(midi.error());
        assertEquals(0,folder.list().length);
        midi.close();
    }
}
