/* Copyright 2026 AbyssME contributors. SPDX-License-Identifier: GPL-3.0-only */
package javax.microedition.media;

import java.io.IOException;

/** Bridges only the pinned libraries already shipped in the application. */
final class NativeAudio {
    static { System.loadLibrary("abyssaudiobridge"); }
    static native void decodeAdpcm(String input, String output) throws IOException;
    static native long openMidi(int[] config) throws IOException;
    static native void closeMidi(long handle);
    static native boolean writeMidi(long handle, byte[] data, int offset, int length);
    static native int renderMidi(long handle, short[] pcm);
    private NativeAudio() {}
}
