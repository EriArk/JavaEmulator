package javax.microedition.media;

import android.media.audiofx.Visualizer;
import android.os.SystemClock;
import androidx.test.platform.app.InstrumentationRegistry;
import com.nokia.mid.sound.Sound;
import org.junit.Test;
import java.lang.reflect.Field;
import java.util.Arrays;
import static org.junit.Assert.*;

/** Session-only Android output measurement, not a microphone/listening test. */
public class AudioOutputGainTest {
    @Test public void nokiaGainReachesTheAndroidOutputAndMuteSurvivesResume() throws Exception {
        android.app.UiAutomation ui=InstrumentationRegistry.getInstrumentation().getUiAutomation();
        ui.adoptShellPermissionIdentity("android.permission.RECORD_AUDIO", "android.permission.MODIFY_AUDIO_SETTINGS");
        android.media.AudioManager audio=(android.media.AudioManager)InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getSystemService(android.content.Context.AUDIO_SERVICE);
        boolean wasMuted=audio.isStreamMute(android.media.AudioManager.STREAM_MUSIC);
        if(wasMuted)audio.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC,android.media.AudioManager.ADJUST_UNMUTE,0);
        int originalVolume=audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC);
        Sound sound=null;
        Visualizer visualizer=null;
        try {
            audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC,1,0);
            sound=new Sound(AudioGainTest.wave(),Sound.FORMAT_WAV);
            sound.play(0);
            Field field=Sound.class.getDeclaredField("player");field.setAccessible(true);
            MicroPlayer micro=(MicroPlayer)field.get(sound);
            field=MicroPlayer.class.getDeclaredField("player");field.setAccessible(true);
            AndroidPlayer player=(AndroidPlayer)field.get(micro);
            visualizer=new Visualizer(player.getAudioSessionId());
            assertEquals(Visualizer.SUCCESS,visualizer.setMeasurementMode(Visualizer.MEASUREMENT_MODE_PEAK_RMS));
            assertEquals(Visualizer.SUCCESS,visualizer.setEnabled(true));
            int full=rms(visualizer);
            sound.setGain(128); int half=rms(visualizer);
            sound.setGain(0); int muted=rms(visualizer);
            sound.stop(); sound.resume(); int resumedMuted=rms(visualizer);
            sound.setGain(255); int restored=rms(visualizer);
            android.util.Log.i("AudioOutputGainTest","session RMS millibels full="+full+" half="+half
                    +" muted="+muted+" resumedMuted="+resumedMuted+" restored="+restored);
            android.os.Bundle report=new android.os.Bundle();
            report.putString("stream","Session RMS millibels: "+full+", "+half+", "+muted+", "+resumedMuted+", "+restored
                    +"; Android music volume="+audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                    +" muted="+audio.isStreamMute(android.media.AudioManager.STREAM_MUSIC)+"\n");
            InstrumentationRegistry.getInstrumentation().sendStatus(0,report);
            // The deliberately quiet system volume attenuates this measurement too.
            // Assert separation from the -9600 mB silence floor, not absolute loudness.
            assertTrue("Generated wave must produce measurable audio: "+full,full>-9000);
            assertTrue("Gain change must attenuate actual output",half<full-800);
            assertTrue("Positive gain remains measurable",half>-9500);
            assertTrue("Zero gain is silent",muted<=-9000);
            assertTrue("Resume stays muted",resumedMuted<=-9000);
            assertEquals("Full gain restored",full,restored,150);
        } finally {
            if(visualizer!=null)visualizer.release();
            if(sound!=null)sound.release();
            audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC,originalVolume,0);
            if(wasMuted)audio.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC,android.media.AudioManager.ADJUST_MUTE,0);
            ui.dropShellPermissionIdentity();
        }
    }

    private static int rms(Visualizer visualizer) {
        SystemClock.sleep(700);
        int[] samples=new int[7];
        for(int i=0;i<samples.length;i++) {
            Visualizer.MeasurementPeakRms measurement=new Visualizer.MeasurementPeakRms();
            assertEquals(Visualizer.SUCCESS,visualizer.getMeasurementPeakRms(measurement));
            samples[i]=measurement.mRms;
            SystemClock.sleep(100);
        }
        Arrays.sort(samples);return samples[samples.length/2];
    }
}
