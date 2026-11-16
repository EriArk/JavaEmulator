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
}
