/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.pokitto;

final class PokittoCore {
    static { System.loadLibrary("abysspokitto"); }
    static synchronized native boolean start(String file, String saves);
    static synchronized native void stop();
    static native void speed(int percent);
    static native void pause(boolean paused);
    static native boolean frame(int[] pixels);
    static native boolean exited();
    static native void button(int id, boolean down);
}
