package javax.microedition.media;

import org.junit.Test;
import static org.junit.Assert.*;

public class MidiChannelStateTest {
    @Test public void shortMessagesHaveTheirRealLength() {
        assertEquals(3, MidiChannelState.messageSize(0x90));
        assertEquals(2, MidiChannelState.messageSize(0xC1));
        assertEquals(2, MidiChannelState.messageSize(0xD9));
        assertEquals(2, MidiChannelState.messageSize(0xF1));
        assertEquals(3, MidiChannelState.messageSize(0xF2));
        assertEquals(1, MidiChannelState.messageSize(0xF8));
        for (int status : new int[]{-1, 0x7f, 0xf0, 0xf7, 0xf4, 0xf5, 256}) {
            try { MidiChannelState.messageSize(status); fail("Invalid MIDI status accepted"); }
            catch (IllegalArgumentException expected) { }
        }
    }
    @Test public void runningStatusAndRealtimeSurviveSplitBuffers() {
        MidiChannelState state = new MidiChannelState();
        assertEquals(-1, state.volume(2));
        state.accept(new byte[]{0, (byte)0xb2,7}, 1, 2);
        state.accept(new byte[]{(byte)0xf8,32,7,64},0,4);
        assertEquals(64, state.volume(2));
        assertEquals(-1, state.volume(1));
    }
    @Test public void sysexDoesNotInventChannelVolumeAndResetInvalidatesReadback() {
        MidiChannelState state = new MidiChannelState();
        state.accept(new byte[]{(byte)0xb0,7,100},0,3);
        assertEquals(100,state.volume(0));
        state.accept(new byte[]{(byte)0xf0,7,55,(byte)0xf7},0,4);
        assertEquals(-1,state.volume(0));
        state.accept(new byte[]{(byte)0xb1,7,80,(byte)0xff},0,4);
        assertEquals(-1,state.volume(1));
    }
    @Test public void programAndPressureDoNotConsumeTheNextStatus() {
        MidiChannelState state = new MidiChannelState();
        state.accept(new byte[]{(byte)0xc0,19,(byte)0xd0,50,(byte)0xb0,7,20,11,70},0,9);
        assertEquals(20,state.volume(0));
    }
}
