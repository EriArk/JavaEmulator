/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.piece;

final class PieceCore {
    static { System.loadLibrary("abysspiece"); }
    static native boolean start(String file, String saves);
    static native void stop();
    static native boolean flush();
    static native void speed(int percent);
    static native void pause(boolean paused);
    static native boolean frame(int[] pixels);
    static native boolean exited();
    static native void button(int id, boolean down);
}
