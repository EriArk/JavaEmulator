package javax.microedition.media.tone;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import org.junit.Test;
import static org.junit.Assert.*;

/** Decode generated SMF with the independent JDK MIDI parser. */
public class ToneSequenceTest {
    @Test public void defaultQuarterNoteHasExpectedDuration() throws Exception {
        ToneSequence tone = compile(-2,1,60,16);
        assertEquals(500000, tone.getDuration());
        assertEquals(500000, read(tone).getMicrosecondLength());
        assertEquals(60, ((ShortMessage) notes(tone).get(0).getMessage()).getData1());
    }
    @Test public void tempoAndResolutionPreserveMusicalDuration() throws Exception {
        ToneSequence tone = compile(-2,1,-3,60,-4,96,60,24);
        assertEquals(250000, read(tone).getMicrosecondLength());
    }
    @Test public void silenceAdvancesNextNoteAndTrailingDuration() throws Exception {
        ToneSequence tone = compile(-2,1,-1,16,60,16,-1,16);
        assertEquals(64, notes(tone).get(0).getTick());
        assertEquals(1500000, read(tone).getMicrosecondLength());
    }
    @Test public void repeatProducesSeparateNotes() throws Exception {
        ToneSequence tone = compile(-2,1,-9,3,64,8);
        assertEquals(3, notes(tone).size());
        assertEquals(750000, read(tone).getMicrosecondLength());
    }
    @Test public void volumeChangesVelocity() throws Exception {
        ToneSequence tone = compile(-2,1,-8,50,60,8,-8,100,62,8);
        assertEquals(63, ((ShortMessage) notes(tone).get(0).getMessage()).getData2());
        assertEquals(127, ((ShortMessage) notes(tone).get(1).getMessage()).getData2());
    }
    @Test public void blocksDoNotPlayUntilReferencedAndCanCompose() throws Exception {
        ToneSequence tone = compile(-2,1,-5,0,60,8,-6,0,-5,1,-7,0,-7,0,-6,1,-7,1);
        assertEquals(2, notes(tone).size());
        assertEquals(500000, read(tone).getMicrosecondLength());
    }
    @Test public void repeatedProcessingIsIdempotent() {
        ToneSequence tone = compile(-2,1,60,16);
        byte[] first = tone.getByteArray();
        tone.process();
        assertArrayEquals(first, tone.getByteArray());
    }
    @Test public void inputAndOutputAreDefensivelyCopied() {
        byte[] source = {-2,1,60,16};
        ToneSequence tone = new ToneSequence(source);
        source[0] = 0;
        tone.process();
        byte[] midi = tone.getByteArray();
        midi[0] = 0;
        assertEquals('M', tone.getByteArray()[0]);
    }
    @Test public void independentCompilersDoNotShareVolumeOrTiming() throws Exception {
        ToneSequence quiet = compile(-2,1,-8,0,60,16);
        ToneSequence loud = compile(-2,1,60,8);
        assertEquals(127, ((ShortMessage) notes(loud).get(0).getMessage()).getData2());
        assertEquals(500000, quiet.getDuration());
    }
    @Test public void invalidHeadersAndDurationsAreRejected() {
        invalid(); invalid(-2); invalid(-2,2,60,16); invalid(-2,1,60);
        invalid(-2,1,60,0); invalid(-2,1,-3,4,60,16); invalid(-2,1,-4,0,60,16);
        invalid(-2,1,-8,101,60,16); invalid(-2,1,-9,1,60,16);
    }
    @Test public void malformedBlocksAndForwardReferencesAreRejected() {
        invalid(-2,1,-7,0); invalid(-2,1,-5,0,60,16,-6,1,60,16);
        invalid(-2,1,-5,0,-7,0,-6,0,-7,0);
        invalid(-2,1,-5,0,-6,0,-7,0);
        invalid(-2,1,-5,0,60,16,-6,0);
    }
    @Test public void singleToneUsesMillisecondsAndClampsVolume() throws Exception {
        Sequence seq = MidiSystem.getSequence(new ByteArrayInputStream(ToneSequence.singleTone(69, 1234, 200)));
        assertEquals(1234000, seq.getMicrosecondLength());
    }
    @Test public void longSingleToneDoesNotOverflowMidiDelta() throws Exception {
        Sequence seq = MidiSystem.getSequence(new ByteArrayInputStream(ToneSequence.singleTone(69, Integer.MAX_VALUE, 100)));
        assertEquals(Integer.MAX_VALUE * 1000L, seq.getMicrosecondLength());
    }
    @Test public void excessiveBlockExpansionIsBounded() {
        List<Integer> bytes = new ArrayList<>();
        for (int n : new int[]{-2,1,-5,0,60,1,-6,0}) bytes.add(n);
        for (int i = 1; i < 22; i++) for (int n : new int[]{-5,i,-7,i-1,-7,i-1,-6,i}) bytes.add(n);
        bytes.add(-7); bytes.add(21);
        int[] input = new int[bytes.size()];
        for (int i = 0; i < input.length; i++) input[i] = bytes.get(i);
        invalid(input);
    }
    private ToneSequence compile(int... bytes) {
        byte[] input = new byte[bytes.length];
        for (int i = 0; i < input.length; i++) input[i] = (byte) bytes[i];
        ToneSequence tone = new ToneSequence(input);
        tone.process();
        return tone;
    }
    private void invalid(int... bytes) {
        try { compile(bytes); fail("Invalid tone accepted"); }
        catch (IllegalArgumentException expected) { }
    }
    private Sequence read(ToneSequence tone) throws Exception { return MidiSystem.getSequence(tone.getStream()); }
    private List<MidiEvent> notes(ToneSequence tone) throws Exception {
        List<MidiEvent> notes = new ArrayList<>();
        javax.sound.midi.Track track = read(tone).getTracks()[0];
        for (int i = 0; i < track.size(); i++) {
            MidiEvent event = track.get(i);
            if (event.getMessage() instanceof ShortMessage
                    && ((ShortMessage) event.getMessage()).getCommand() == ShortMessage.NOTE_ON) notes.add(event);
        }
        return notes;
    }
}
