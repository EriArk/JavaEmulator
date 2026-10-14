package com.github.eka2l1.emu;

import android.util.Size;

/** No physical-camera permission or access in the N-Gage prototype. */
public final class EmulatorCamera {
    public static int getCameraCount() { return 0; }
    public static int initializeCamera(int index) { return -1; }
    public static void releaseCamera(int handle) { }
    public static void destroyCamera(int handle) { }
    public static int[] getSupportedImageOutputFormats() { return new int[0]; }
    public static int getFlashMode(int handle) { return 0; }
    public static boolean setFlashMode(int handle, int mode) { return false; }
    public static boolean isCameraFacingFront(int handle) { return false; }
    public static Size[] getOutputImageSizes(int handle) { return new Size[0]; }
    public static boolean captureImage(int handle, int resolution, int format) { return false; }
    public static boolean receiveViewfinderFeed(int handle, int width, int height, int format) { return false; }
    public static void stopViewfinderFeed(int handle) { }
}
