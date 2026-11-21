/* SPDX-License-Identifier: GPL-3.0-only */
package com.pockethle.app;

/** JNI entry points exported by the pinned PocketHLE Android core. */
public final class NativeBridge {
    static { System.loadLibrary("pockethle_jni"); }
    public static native String importCab(String library, String source);
    public static native String importExe(String library, String source);
    public static native String importZip(String library, String source);
    public static native long nativeStartGame(String library, String id);
    public static native byte[] nativePollFrame(long session);
    public static native long nativeAudioFormat(long session);
    public static native short[] nativePollAudio(long session, int limit);
    public static native int nativeIsRunning(long session);
    public static native int nativeSendInput(long session, int kind, int a, int b);
    public static native void nativeRate(long session, int percent);
    public static native void nativeRequestStop(long session);
    public static native String nativeFinishGame(long session);
    private NativeBridge() {}
}
