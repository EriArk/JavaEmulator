package javax.microedition.media;

import android.media.audiofx.Visualizer;
import org.junit.Test;
import static org.junit.Assert.*;

public class DirectMidiTest {
    private static Visualizer meter(MidiPlayer player) {
        Visualizer v=new Visualizer(player.audioSessionId());
        assertEquals(0,v.setMeasurementMode(Visualizer.MEASUREMENT_MODE_PEAK_RMS));
        assertEquals(0,v.setEnabled(true)); return v;
    }
    private static void note(MidiPlayer p,int channel) {
        p.setProgram(channel,-1,19);
        p.shortMidiEvent(0x90|channel,60,110);
    }
    private static int rms(Visualizer v) {
        // Android keeps the last measurements for 2000 ms when a silent track
        // stops producing effect buffers. Do not mistake that history for audio.
        android.os.SystemClock.sleep(2300);
        return MidiOutputParityTest.rms(v);
    }
    private static int measured(String label,MidiPlayer p,Visualizer v) throws Exception {
        int rms=rms(v);
        java.lang.reflect.Field field=MidiPlayer.class.getDeclaredField("session"); field.setAccessible(true);
        Object session=field.get(p);
        field=MidiSession.class.getDeclaredField("track"); field.setAccessible(true);
        android.media.AudioTrack track=(android.media.AudioTrack)field.get(session);
        field=MidiSession.class.getDeclaredField("failed"); field.setAccessible(true);
        boolean failed=field.getBoolean(session);
        field=MidiSession.class.getDeclaredField("leftGain"); field.setAccessible(true);
        float gain=field.getFloat(session);
        byte[] wave=new byte[v.getCaptureSize()]; v.getWaveForm(wave);
        int peak=0; for(byte b:wave)peak=Math.max(peak,Math.abs((b&255)-128));
        MidiOutputParityTest.report(label+" rms="+rms+" waveformPeak="+peak+" gain="+gain
                +" failed="+failed+" head="+track.getPlaybackHeadPosition()+" play="+track.getPlayState());
        return rms;
    }
    @Test public void channelReadbackBoundsAndLongSlices() throws Exception {
        MidiPlayer p=new MidiPlayer();
        try {
            try { p.setChannelVolume(0,100); fail(); } catch(IllegalStateException expected) { }
            p.setMute(true); p.prefetch();
            assertEquals(3,p.longMidiEvent(new byte[]{0,(byte)0xb3,7,42,0},1,3));
            assertEquals(42,p.getChannelVolume(3)); assertEquals(100,p.getLevel());
            p.setLevel(10); assertEquals(42,p.getChannelVolume(3));
            p.setChannelVolume(3,0); assertEquals(0,p.getChannelVolume(3));
            assertEquals(0,p.longMidiEvent(new byte[0],0,0));
            try { p.longMidiEvent(new byte[4],1,Integer.MAX_VALUE); fail(); } catch(IllegalArgumentException expected) { }
            try { p.setChannelVolume(16,80); fail(); } catch(IllegalArgumentException expected) { }
            try { p.setProgram(0,16384,0); fail(); } catch(IllegalArgumentException expected) { }
            p.shortMidiEvent(0xc0,10,999); // Unused byte is not transmitted.
            p.shortMidiEvent(0xf8,999,999);
            p.close();
            try { p.shortMidiEvent(0x90,60,80); fail(); } catch(IllegalStateException expected) { }
        } finally { p.close(); }
    }

    @Test public void independentSynthsDoNotShareNotesOrControllers() throws Exception {
        int[] config=new int[2]; long a=NativeAudio.openMidi(config), b=0;
        try {
            b=NativeAudio.openMidi(new int[2]);
            short[] pcm=new short[config[1]*2];
            byte[] notes={(byte)0xc0,19,(byte)0x90,60,110};
            assertTrue(NativeAudio.writeMidi(a,notes,0,notes.length));
            long energyA=0, energyB=0;
            for(int i=0;i<24;i++) {
                assertTrue(NativeAudio.renderMidi(a,pcm)>0);
                for(short s:pcm)energyA+=Math.abs((int)s);
                assertTrue(NativeAudio.renderMidi(b,pcm)>0);
                for(short s:pcm)energyB+=Math.abs((int)s);
            }
            assertTrue(energyA>10000); assertEquals(0,energyB);
            NativeAudio.closeMidi(a); a=0;
            assertTrue(NativeAudio.writeMidi(b,notes,0,notes.length));
            long after=0;
            for(int i=0;i<24;i++){NativeAudio.renderMidi(b,pcm);for(short s:pcm)after+=Math.abs((int)s);}
            assertTrue("Closing another synth cannot stop this one",after>10000);
        } finally { if(a!=0)NativeAudio.closeMidi(a); if(b!=0)NativeAudio.closeMidi(b); }
    }

    @Test public void masterAndChannelGainMuteCloseAndReplayAreIndependent() throws Exception {
        MidiOutputParityTest.withAudio(() -> {
            MidiPlayer music=new MidiPlayer(), effect=new MidiPlayer();
            Visualizer m=null,e=null;
            try {
                music.start(); effect.start();
                assertNotEquals(music.audioSessionId(),effect.audioSessionId());
                m=meter(music); e=meter(effect);
                music.setChannelVolume(0,127); effect.setChannelVolume(0,127);
                note(music,0); note(effect,0);
                int baseline=measured("music baseline",music,m);
                assertTrue("Direct MIDI must be audible",baseline>-9000);
                effect.setMute(true);
                assertTrue("Effect mute must be silent",measured("effect muted",effect,e)<=-9000);
                assertEquals(baseline,rms(m),350);
                effect.setMute(false);
                int effectLevel=rms(e);
                assertTrue(effectLevel>-9000);
                music.setChannelVolume(0,32);
                int quiet=rms(m);
                assertTrue("CC7 must affect only its channel",quiet<baseline-500);
                assertEquals("The other player's same MIDI channel retains its level",
                        effectLevel,rms(e),350);
                music.setChannelVolume(0,127); music.setLevel(50);
                int lowMaster=rms(m);
                assertTrue(lowMaster<baseline-500);
                assertEquals(127,music.getChannelVolume(0));
                music.setLevel(100);
                assertEquals(baseline,rms(m),350);
                effect.close();
                assertEquals(baseline,rms(m),350);
                music.stop(); assertTrue(rms(m)<=-9000);
                music.setMute(true); music.start(); note(music,0);
                assertTrue(rms(m)<=-9000);
                music.setMute(false);
                assertTrue(rms(m)>-9000);
                MidiOutputParityTest.report("Direct MIDI RMS full="+baseline+" CC7=32="+quiet+" master50="+lowMaster);
            } finally { if(m!=null)m.release();if(e!=null)e.release();music.close();effect.close(); }
        });
    }

    @Test public void repeatedDeallocateAndCloseReleaseAudioResources() throws Exception {
        for(int i=0;i<12;i++) {
            MidiPlayer p=new MidiPlayer();
            try {
                p.setLevel(0); p.start(); note(p,9);
                p.stop(); p.deallocate(); assertEquals(Player.REALIZED,p.getState());
                p.start(); assertEquals(0,p.getLevel()); note(p,0);
            } finally { p.close(); p.close(); }
        }
        Thread.sleep(80);
        assertFalse(Thread.getAllStackTraces().keySet().stream().anyMatch(t -> t.getName().equals("DirectMidiAudio") && t.isAlive()));
    }

    @Test public void prefetchedDirectNotesStopWithoutStartingATimeline() throws Exception {
        MidiOutputParityTest.withAudio(() -> {
            MidiPlayer p=new MidiPlayer(); Visualizer v=null;
            try {
                p.prefetch(); v=meter(p); note(p,0);
                assertEquals(Player.PREFETCHED,p.getState());
                assertTrue(measured("prefetched audible",p,v)>-9000);
                p.stop(); p.setLevel(80);
                assertTrue("Changing volume must not resume stopped sound",measured("prefetched stopped",p,v)<=-9000);
                note(p,0); assertTrue(rms(v)>-9000);
            } finally { if(v!=null)v.release(); p.close(); }
        });
    }
}
