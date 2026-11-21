/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.pocketpc;

final class GizmondoBridge {
    static { System.loadLibrary("gizmondo_jni"); }
    static native String importCard(String runtime, String archive);
    static native String start(String runtime, String guest);
    static native byte[] nativePollFrame(long session);
    static native long nativeAudioFormat(long session);
    static native short[] nativePollAudio(long session, int limit);
    static native int nativeIsRunning(long session);
    static native int nativeSendInput(long session, int kind, int a, int b);
    static native void rate(long session, int percent);
    static native void nativeRequestStop(long session);
    static native String nativeFinishGame(long session);
    private GizmondoBridge() {}
}
