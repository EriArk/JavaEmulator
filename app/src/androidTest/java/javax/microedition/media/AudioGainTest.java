package javax.microedition.media;

import com.nokia.mid.sound.Sound;
import org.junit.Test;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import static org.junit.Assert.*;

/** Original generated fixtures; no commercial game data or global audio changes. */
public class AudioGainTest {
    static byte[] wave() {
        int rate = 22050, samples = rate * 2;
        ByteBuffer wav = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN);
        wav.put(new byte[]{'R','I','F','F'}).putInt(36 + samples * 2)
                .put(new byte[]{'W','A','V','E','f','m','t',' '}).putInt(16)
                .putShort((short)1).putShort((short)1).putInt(rate).putInt(rate * 2)
                .putShort((short)2).putShort((short)16)
                .put(new byte[]{'d','a','t','a'}).putInt(samples * 2);
        for (int i=0; i<samples; i++) wav.putShort((short)(Math.sin(i*440*2*Math.PI/rate)*8000));
        return wav.array();
    }

    private static BasePlayer backing(Sound sound) throws Exception {
        Field field = Sound.class.getDeclaredField("player"); field.setAccessible(true);
        return (BasePlayer)field.get(sound);
    }

    @Test public void nokiaGainClampsButRetainsExactReadbackAndPositiveMinimum() throws Exception {
        Sound sound = new Sound(wave(), Sound.FORMAT_WAV);
        try {
            assertEquals(255, sound.getGain());
            for (int gain : new int[]{-10,0,1,2,127,128,254,255,1000}) {
                sound.setGain(gain);
                int bounded = Math.max(0, Math.min(255,gain));
                assertEquals(bounded,sound.getGain());
                assertEquals((bounded*100+254)/255,backing(sound).getLevel());
                if(bounded>0)assertTrue(backing(sound).getLevel()>0);
            }
        } finally { sound.release(); }
    }

    @Test public void nokiaGainSurvivesReplacementReleaseAndDoesNotChangeAnotherSound() throws Exception {
        Sound sound = new Sound(wave(), Sound.FORMAT_WAV);
        Sound other = new Sound(440, 500);
        try {
            sound.setGain(64);
            BasePlayer previous=backing(sound);
            sound.init(880,500);
            assertEquals(Player.CLOSED,previous.getState());
            assertEquals(64,sound.getGain());
            assertEquals(26,backing(sound).getLevel());
            assertEquals(255,other.getGain());
            assertEquals(100,backing(other).getLevel());
            sound.release();
            sound.setGain(1);
            sound.init(wave(), Sound.FORMAT_WAV);
            assertEquals(1,sound.getGain());
            assertEquals(1,backing(sound).getLevel());
        } finally { sound.release(); other.release(); }
    }

    @Test public void nokiaLiveGainRemainsAppliedAcrossStopResumeAndReplay() throws Exception {
        Sound sound=new Sound(wave(), Sound.FORMAT_WAV);
        try {
            sound.setGain(0); sound.play(0);
            assertEquals(0,backing(sound).getLevel());
            assertEquals(Player.STARTED,backing(sound).getState());
            sound.setGain(128);
            assertEquals(51,backing(sound).getLevel());
            sound.stop(); sound.resume();
            assertEquals(51,backing(sound).getLevel());
            sound.play(1);
            assertEquals(51,backing(sound).getLevel());
        } finally { sound.release(); }
    }

    @Test public void unchangedMmapiCurveMutePanAndPlayerIsolation() {
        class Probe extends BasePlayer {
            float left=1,right=1;
            @Override public void doSetVolume(float l,float r){left=l;right=r;}
        }
        Probe p=new Probe(),other=new Probe();
        try {
            float previous=-1;
            for(int level=0;level<=100;level++) {
                assertEquals(level,p.setLevel(level));
                assertTrue(p.left>=previous); assertTrue(p.left>=0&&p.left<=1);
                assertEquals(p.left,p.right,0); previous=p.left;
            }
            p.setLevel(50); assertEquals(.150515f,p.left,.00001f);
            p.setMute(true); assertEquals(0,p.left,0); assertEquals(50,p.getLevel());
            p.setPan(-100); p.setLevel(75); assertEquals(0,p.right,0);
            p.setMute(false); assertTrue(p.left>0); assertEquals(0,p.right,0);
            p.setPan(100); assertEquals(0,p.left,0); assertTrue(p.right>0);
            assertEquals(100,other.getLevel()); assertEquals(1,other.left,0);
            p.setPan(0); p.setLevel(-1); assertEquals(0,p.left,0);
            p.setLevel(200); assertEquals(1,p.left,0);
        } finally {p.close();other.close();}
    }
}
