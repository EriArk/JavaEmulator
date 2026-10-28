package javax.microedition.media;

import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import static org.junit.Assert.*;

public class AdpcmPrecisionTest {
    private static byte[] ima(boolean stereo) {
        int channels = stereo ? 2 : 1, block = channels * 8;
        ByteBuffer b = ByteBuffer.allocate(60 + block).order(ByteOrder.LITTLE_ENDIAN);
        b.put(new byte[]{'R','I','F','F'}).putInt(b.capacity()-8).put(new byte[]{'W','A','V','E','f','m','t',' '});
        b.putInt(20).putShort((short)17).putShort((short)channels).putInt(22050).putInt(19600*channels)
                .putShort((short)block).putShort((short)4).putShort((short)2).putShort((short)9);
        b.put(new byte[]{'f','a','c','t'}).putInt(4).putInt(9).put(new byte[]{'d','a','t','a'}).putInt(block);
        b.putShort((short)1000).putShort((short)0);
        if(stereo) b.putShort((short)-1000).putShort((short)0);
        b.putInt(0x11111111);
        if(stereo) b.putInt(0x99999999);
        return b.array();
    }

    private static byte[] decode(byte[] wave) throws Exception {
        InternalDataSource source = new InternalDataSource(new ByteArrayInputStream(wave),"audio/wav");
        try { return Files.readAllBytes(new File(source.getLocator()).toPath()); }
        finally { source.disconnect(); }
    }

    @Test public void monoImaKeepsLowBitsAndRate() throws Exception {
        ByteBuffer b=ByteBuffer.wrap(decode(ima(false))).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(22050,b.getInt(24)); assertEquals(16,b.getShort(34));
        assertEquals(18,b.getInt(40));
        // The pinned FFmpeg decoder uses ((2 * nibble + 1) * step) >> 3:
        // nibble 1 with step 7 produces 2, not the separately rounded shift/add 1.
        for(int i=0;i<9;i++) assertEquals(1000+i*2,b.getShort(44+i*2));
    }

    @Test public void stereoImaInterleavesWithoutMixingChannels() throws Exception {
        ByteBuffer b=ByteBuffer.wrap(decode(ima(true))).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(2,b.getShort(22)); assertEquals(36,b.getInt(40));
        for(int i=0;i<9;i++) {
            assertEquals(1000+i*2,b.getShort(44+i*4));
            assertEquals(-1000-i*2,b.getShort(46+i*4));
        }
    }

    @Test public void yamahaAdpcmAlsoKeepsSixteenBitStereoSamples() throws Exception {
        ByteBuffer b=ByteBuffer.allocate(48).order(ByteOrder.LITTLE_ENDIAN);
        b.put(new byte[]{'R','I','F','F'}).putInt(40).put(new byte[]{'W','A','V','E','f','m','t',' '});
        b.putInt(16).putShort((short)0x20).putShort((short)2).putInt(16000).putInt(16000)
                .putShort((short)2).putShort((short)4).put(new byte[]{'d','a','t','a'}).putInt(4)
                .putInt(0x91919191);
        ByteBuffer pcm=ByteBuffer.wrap(decode(b.array())).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(16,pcm.getShort(34)); assertEquals(16000,pcm.getInt(24));
        assertEquals(16,pcm.getInt(40));
        for(int i=0;i<4;i++) {
            assertEquals(47*(i+1),pcm.getShort(44+i*4));
            assertEquals(-47*(i+1),pcm.getShort(46+i*4));
        }
    }

    @Test public void invalidInputLeavesNoConvertedFile() throws Exception {
        File input=AudioPipelineTest.fixture(new byte[]{1,2,3},".wav");
        File output=new File(input.getPath()+".out.wav");
        try {
            try { NativeAudio.decodeAdpcm(input.getPath(),output.getPath()); fail(); }
            catch(IOException expected) { assertFalse(output.exists()); }
        } finally { input.delete(); output.delete(); }
    }
}
