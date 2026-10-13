/* Copyright 2026 AbyssME contributors. SPDX-License-Identifier: Apache-2.0 */
package javax.microedition.media.tone;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import static javax.microedition.media.control.ToneControl.*;

/** Compiles the public MMAPI tone grammar to standard MIDI, without Android dependencies. */
public final class ToneSequence {
    private final byte[] input;
    private byte[] midi;
    private long duration;
    private int offset, tempo, resolution, volume, events;
    private long ticks, pending;
    private List<Event>[] blocks;
    private ByteArrayOutputStream track;
    private static final int MAX_EVENTS = 1000000;

    public ToneSequence(byte[] sequence) {
        if (sequence == null) throw new IllegalArgumentException("Null tone sequence");
        input = sequence.clone();
    }

    @SuppressWarnings("unchecked")
    public void process() {
        offset = 0; tempo = 30; resolution = 64; volume = 100; events = 0;
        ticks = pending = duration = 0; midi = null;
        blocks = (List<Event>[]) new List<?>[128];
        if (next() != VERSION || next() != 1) throw invalid();
        if (peek() == TEMPO) { next(); tempo = range(next(), 5, 127); }
        if (peek() == RESOLUTION) { next(); resolution = range(next(), 1, 127); }
        while (peek() == BLOCK_START) {
            next();
            int id = range(next(), 0, 127);
            if (blocks[id] != null) throw invalid();
            List<Event> body = parse(true);
            if (next() != BLOCK_END || next() != id) throw invalid();
            blocks[id] = body;
        }
        List<Event> body = parse(false);
        track = new ByteArrayOutputStream();
        int micros = 60000000 / (tempo * 4);
        bytes(0, 0xff, 0x51, 3, micros >>> 16, micros >>> 8, micros);
        bytes(0, 0xc0, 0);
        emit(body);
        variable(pending);
        bytes(0xff, 0x2f, 0);
        midi = file(track.toByteArray(), resolution);
        duration = ticks * micros / resolution;
    }

    private List<Event> parse(boolean block) {
        List<Event> result = new ArrayList<>();
        while (offset < input.length && (!block || peek() != BLOCK_END)) {
            int op = next(), value = next();
            if (op >= SILENCE) {
                range(value, 1, 127);
                result.add(new Event(op, value, 1, null));
            } else if (op == SET_VOLUME) {
                result.add(new Event(op, range(value, 0, 100), 1, null));
            } else if (op == PLAY_BLOCK) {
                range(value, 0, 127);
                if (blocks[value] == null) throw invalid();
                result.add(new Event(op, 0, 1, blocks[value]));
            } else if (op == REPEAT) {
                range(value, 2, 127);
                int note = range(next(), SILENCE, 127);
                int length = range(next(), 1, 127);
                result.add(new Event(note, length, value, null));
            } else throw invalid();
        }
        if (result.isEmpty()) throw invalid();
        return result;
    }

    private void emit(List<Event> sequence) {
        for (Event event : sequence) {
            if (++events > MAX_EVENTS) throw new IllegalArgumentException("Tone expansion limit exceeded");
            if (event.block != null) { emit(event.block); continue; }
            if (event.note == SET_VOLUME) { volume = event.length; continue; }
            for (int repeat = 0; repeat < event.repeat; repeat++) {
                if (++events > MAX_EVENTS) throw new IllegalArgumentException("Tone expansion limit exceeded");
                long length = event.length * 4L;
                ticks += length;
                if (event.note == SILENCE) { pending += length; continue; }
                variable(pending); pending = 0;
                bytes(0x90, event.note, volume * 127 / 100);
                variable(length);
                bytes(0x80, event.note, 0);
            }
        }
    }

    private void variable(long value) {
        if (value < 0) throw invalid();
        // MIDI deltas have four bytes; empty sequencer events carry longer rests.
        while (value > 0x0fffffffL) {
            variable(0x0fffffffL);
            bytes(0xff, 0x7f, 0);
            value -= 0x0fffffffL;
        }
        int groups = 0;
        long buffer = value & 127;
        while ((value >>= 7) > 0) { buffer = (buffer << 8) | (value & 127) | 128; groups++; }
        for (; groups >= 0; groups--, buffer >>= 8) track.write((int) buffer & 255);
    }

    private void bytes(int... bytes) { for (int value : bytes) track.write(value); }
    private int peek() { return offset < input.length ? input[offset] : -128; }
    private int next() { if (offset == input.length) throw invalid(); return input[offset++]; }
    private static int range(int value, int min, int max) {
        if (value < min || value > max) throw invalid();
        return value;
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid tone sequence"); }

    private static byte[] file(byte[] track, int division) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(0x4d546864); out.writeInt(6);
            out.writeShort(0); out.writeShort(1); out.writeShort(division);
            out.writeInt(0x4d54726b); out.writeInt(track.length); out.write(track);
            return bytes.toByteArray();
        } catch (IOException impossible) { throw new AssertionError(impossible); }
    }

    public byte[] getByteArray() { if (midi == null) throw new IllegalStateException("Sequence not processed"); return midi.clone(); }
    public ByteArrayInputStream getStream() { return new ByteArrayInputStream(getByteArray()); }
    public long getDuration() { return duration; }

    static byte[] singleTone(int note, int milliseconds, int volume) {
        range(note, 0, 127);
        if (milliseconds <= 0) throw invalid();
        ToneSequence compiler = new ToneSequence(new byte[0]);
        compiler.track = new ByteArrayOutputStream();
        compiler.bytes(0, 0xff, 0x51, 3, 7, 0xa1, 0x20, 0, 0x90, note,
                Math.max(0, Math.min(100, volume)) * 127 / 100);
        compiler.variable(milliseconds * 2L);
        compiler.bytes(0x80, note, 0, 0, 0xff, 0x2f, 0);
        return file(compiler.track.toByteArray(), 1000);
    }

    private static final class Event {
        final int note, length, repeat;
        final List<Event> block;
        Event(int note, int length, int repeat, List<Event> block) {
            this.note = note; this.length = length; this.repeat = repeat; this.block = block;
        }
    }
}
