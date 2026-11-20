/* SPDX-License-Identifier: GPL-3.0-only */
package com.parkjeongseop.wipi;

import android.content.Context;

/** JNI names exported by the pinned Android adapter. One native session per process. */
public final class WipiNative {
    private WipiNative() {}
    private static boolean ready;
    public static synchronized void initialize(Context context) {
        if (ready) return;
        System.loadLibrary("wipi_android");
        nativeInit(context.getApplicationContext());
        ready = true;
    }
    private static native void nativeInit(Context context);
    public static native boolean nativeStart(byte[] data, String filename, String dataDir, String soundfont);
    public static native boolean nativeGetFrame(int[] pixels);
    public static native String nativeGetError(int[] kind);
    public static native boolean nativePollExit();
    public static native void nativeKeyDown(String key);
    public static native void nativeKeyUp(String key);
    public static native void nativeSetPaused(boolean paused);
    public static native void nativeStop();
}
