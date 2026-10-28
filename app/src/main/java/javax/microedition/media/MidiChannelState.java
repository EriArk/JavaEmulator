/* Copyright 2026 AbyssME contributors. SPDX-License-Identifier: GPL-3.0-only */
package javax.microedition.media;

import java.util.Arrays;

/** Readback follows the same byte stream as the synth, including running status. */
final class MidiChannelState {
    private final int[] volumes = new int[16];
    private int status, position, first;
    private boolean sysex;

    MidiChannelState() { reset(); }
    void reset() { Arrays.fill(volumes, -1); status = position = 0; sysex = false; }
    int volume(int channel) { return volumes[channel]; }

    static int messageSize(int status) {
        if (status < 0x80 || status > 0xFF || status == 0xF0 || status == 0xF7
                || status == 0xF4 || status == 0xF5 || status == 0xF9 || status == 0xFD) {
            throw new IllegalArgumentException("Invalid short MIDI status");
        }
        if (status >= 0xF8 || status == 0xF6) return 1;
        if (status == 0xF1 || status == 0xF3 || (status & 0xE0) == 0xC0) return 2;
        return 3;
    }

    void accept(byte[] data, int offset, int length) {
        for (int i = offset; i < offset + length; i++) {
            int b = data[i] & 255;
            if (b >= 0xF8) { if (b == 0xFF) reset(); continue; }
            if (b >= 0x80) {
                position = 0;
                if (b == 0xF0) {
                    sysex = true; status = 0;
                    Arrays.fill(volumes, -1); // A vendor reset can change all controllers.
                } else if (b == 0xF7) { sysex = false; status = 0; }
                else { sysex = false; status = b; }
                continue;
            }
            if (sysex || status == 0) continue;
            if (position++ == 0) first = b;
            int size;
            try { size = messageSize(status); }
            catch (IllegalArgumentException e) { status = position = 0; continue; }
            if (position == size - 1) {
                if ((status & 0xF0) == 0xB0 && first == 7) volumes[status & 15] = b;
                position = 0;
                if (status >= 0xF0) status = 0;
            }
        }
    }
}
