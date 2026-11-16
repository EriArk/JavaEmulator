/* SPDX-License-Identifier: GPL-3.0-only */
package wolphun.vm;

import android.os.SystemClock;
import org.junit.Test;
import static org.junit.Assert.*;

public class MophunPcmTest {
    private VSound sound() {
        byte[] header=new byte[40];header[0]='V';header[1]='M';header[2]='G';header[3]='P';
        return new VSound(new Pip2(new Vmgp(header),65536,4096));
    }
    private int sample(VSound sound) {
        int p=sound.vm.heapBase;
        sound.vm.write32(p+8,1);sound.vm.write32(p+12,0);sound.vm.write32(p+16,8192);
        sound.vm.write32(p+20,44100);sound.vm.write8(p+24,16);sound.vm.write8(p+25,1);sound.vm.write32(p+32,16384);
        for(int i=0;i<8192;i++) {short v=(short)(Math.sin(i*2*Math.PI*440/44100)*5000);sound.vm.write8(p+36+i*2,v&255);sound.vm.write8(p+37+i*2,(v>>8)&255);}
        int h=sound.getHandle(p);assertTrue(h>0);return h;
    }
    @Test public void mixPauseAndSpeedKeepVoiceState() {
        VSound sound=sound();int h=sample(sound);VSound.Voice voice=new VSound.Voice();voice.snd=sound.sounds.get(h);voice.step=1;voice.playing=true;voice.loop=-1;sound.voices.put(h,voice);
        int[] l=new int[100],r=new int[100];sound.renderFrames(l,r,100);assertEquals(100,voice.pos,0);assertTrue(java.util.Arrays.stream(l).anyMatch(x->x!=0));
        sound.pause(true);sound.renderFrames(l,r,100);assertEquals(100,voice.pos,0);assertEquals(0,java.util.Arrays.stream(l).sum());
        sound.speed(200);sound.renderFrames(l,r,100);assertEquals(100,voice.pos,0);sound.pause(false);sound.renderFrames(l,r,100);assertEquals(300,voice.pos,0);
        sound.speed(50);sound.renderFrames(l,r,100);assertEquals(350,voice.pos,0);sound.dispose();
    }
    @Test public void audioTrackStartsPausedResumesAndReleasesWorker() {
        VSound sound=sound();
        try {
            sound.pause(true);assertEquals(0,sound.init());assertNotEquals(android.media.AudioTrack.PLAYSTATE_PLAYING,sound.track.getPlayState());
            int h=sample(sound);sound.ctrl(h,1,0xffff);SystemClock.sleep(120);assertEquals(0,sound.voices.get(h).pos,0);
            sound.pause(false);SystemClock.sleep(180);assertTrue(sound.voices.get(h).pos>0);sound.pause(true);
            double stopped;synchronized(sound.voices){stopped=sound.voices.get(h).pos;}
            SystemClock.sleep(120);assertEquals(stopped,sound.voices.get(h).pos,0);assertNull(sound.error());
            Thread worker=sound.mixThread;sound.dispose();assertFalse(worker.isAlive());assertFalse(sound.running());assertNull(sound.track);
        }finally{sound.dispose();}
    }
    @Test public void repeatedInitDisposeDoesNotLeakMixers() {
        VSound sound=sound();
        for(int i=0;i<3;i++){
            try {assertEquals(0,sound.init());Thread worker=sound.mixThread;assertEquals(0,sound.init());assertSame(worker,sound.mixThread);sound.dispose();assertFalse(worker.isAlive());assertNull(sound.error());}
            finally {sound.dispose();}
        }
    }
    @Test public void beepSequencePreservesFrequencyDurationVolumeAndSilence() {
        // Original 440 Hz/100 ms tone followed by 50 ms of silence.
        BeepSequence sequence=new BeepSequence(new byte[]{(byte)184,1,100,0,(byte)255,0,0,50,0,(byte)255},false);
        int rises=0,previous=0,peak=0;
        for(int i=0;i<4410;i++) {int sample=sequence.next(1);if(previous<=0&&sample>0)rises++;previous=sample;peak=Math.max(peak,Math.abs(sample));}
        assertEquals(44,rises);assertTrue(peak>16000);
        for(int i=0;i<2205;i++)assertEquals(0,sequence.next(1));
        assertEquals(0,sequence.next(1));assertTrue(sequence.finished());
        BeepSequence quiet=new BeepSequence(new byte[]{(byte)184,1,100,0,64},false);
        for(int i=0;i<4410;i++)assertTrue(Math.abs(quiet.next(1))<=4112);
    }
    @Test public void beepLoopAndZeroDurationDoNotHang() {
        BeepSequence empty=new BeepSequence(new byte[]{1,0,0,0,(byte)255},true);
        assertEquals(0,empty.next(1));assertTrue(empty.finished());
        BeepSequence sequence=new BeepSequence(new byte[]{1,0,0,0,0,(byte)184,1,10,0,(byte)255},true);
        long energy=0;for(int i=0;i<10000;i++)energy+=Math.abs(sequence.next(4));
        assertTrue(energy>10000);assertFalse(sequence.finished());
        try {new BeepSequence(new byte[4],false);fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void beepResourcesStopAndFollowHostPause() {
        VSound sound=sound();Sdk sdk=new Sdk(sound.vm,new wolphun.Screen(101,80,1));sound=sdk.vsound;
        try {
            sound.pause(true);int p=sound.vm.heapBase;
            byte[] data={(byte)184,1,100,0,(byte)255};for(int i=0;i<data.length;i++)sound.vm.write8(p+i,data[i]&255);
            assertEquals(1,sdk.playResource(p,5,0x100));
            int[] l=new int[100],r=new int[100];sound.renderFrames(l,r,100);assertEquals(0,java.util.Arrays.stream(l).sum());
            sound.pause(false);sound.renderFrames(l,r,100);assertTrue(java.util.Arrays.stream(l).anyMatch(x->x!=0));
            sdk.playResource(0,0,0x400);sound.renderFrames(l,r,100);assertTrue(java.util.Arrays.stream(l).allMatch(x->x==0));
            sound.beep(440,100);sound.beep(0,0);sound.renderFrames(l,r,100);assertTrue(java.util.Arrays.stream(l).allMatch(x->x==0));
            assertEquals(0,sdk.playResource(p,4,0));assertNull(sound.error());
        } finally {sound.dispose();}
    }
}
