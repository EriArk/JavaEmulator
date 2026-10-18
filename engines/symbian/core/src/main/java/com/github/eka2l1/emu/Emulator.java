package com.github.eka2l1.emu;

import android.view.Surface;

/** AbyssME host implementation of the pinned EKA2L1 JNI callback contract. */
public final class Emulator {
    static { System.loadLibrary("abysssymbian"); }
    public interface Host {
        void exited();
        void question(String text, String yes, String no);
        void input(String text, int maxLength);
        void closeInput();
    }
    public static volatile Host host;
    public static volatile String lastGuestExit;
    public static void guestExited(String reason) { lastGuestExit = reason; exitInstance(); }
    public static ClassLoader getAppClassLoader() { return Emulator.class.getClassLoader(); }
    public static void exitInstance() { Host h = host; if (h != null) h.exited(); }
    public static void showQuestionDialog(String text, String yes, String no) {
        Host h = host;
        if (h != null) h.question(text, yes, no);
        else submitQuestionDialogResponse(0);
    }
    public static void showInputDialog(String text, int maxLength) {
        Host h = host;
        if (h != null) h.input(text, maxLength);
        else submitInput("");
    }
    public static void closeInputDialog() { Host h = host; if (h != null) h.closeInput(); }
    public static boolean vibrate(int duration) { return false; }
    public static void stopVibrate() { }
    public static void prepareNgageMetadata(String path, String backups) {
        io.github.eriark.abyssme.symbian.NgageMetadata.prepare(new java.io.File(path), new java.io.File(backups));
    }

    // The host stages permitted input files internally; the guest never receives SAF URIs.
    public static int openContentUri(String uri, String mode) { return -1; }
    public static String[] listContentUriDir(String uri) { return new String[0]; }
    public static int contentUriCreateDirectory(String uri, String name) { return -1; }
    public static int contentUriCreateFile(String uri, String name) { return -1; }
    public static int contentUriCopyFile(String from, String to) { return -1; }
    public static int contentUriRemoveFile(String uri) { return -1; }
    public static int contentUriMoveFile(String from, String source, String target) { return -1; }
    public static int contentUriRenameFileTo(String uri, String name) { return -1; }
    public static String contentUriGetFileInfo(String uri) { return null; }
    public static boolean contentUriFileExists(String uri) { return false; }
    public static boolean isExternalStoragePreservedLegacy() { return false; }

    public static native void prepare(String directory);
    public static native String[] portablePhoneSettings(String directory) throws java.io.IOException;
    public static native boolean startSession();
    public static native void stopSession();
    public static native void pauseSession(boolean paused);
    public static native void setFrameRateLimit(int fps);
    public static native int getFrameRateLimit();
    public static native void setEmulationSpeed(int percent);
    public static native int getEmulationSpeed();
    public static native String[] getDevices();
    public static native String[] getDeviceInfo();
    public static native String[] getDeviceFirmwareCodes();
    public static native int getCurrentDevice();
    public static native void setCurrentDevice(int index, boolean temporary);
    public static native int installDevice(String rpkg, String rom, boolean dump, boolean isolate);
    public static native void installPreparedDevice(String rom, String z, String firmwareHint) throws java.io.IOException;
    public interface ArchivePlan {
        String destination(String path, long size, boolean directory) throws java.io.IOException;
        void progress(long completed, long total) throws java.io.IOException;
    }
    public static native void extractSystemArchive(String archive, ArchivePlan plan) throws java.io.IOException;
    public interface ArchiveSink {
        void begin(String path,long size,boolean directory) throws java.io.IOException;
        void write(byte[] bytes,int length) throws java.io.IOException;
        void end() throws java.io.IOException;
    }
    public static native void decodeGameArchive(String archive,ArchiveSink sink) throws java.io.IOException;
    public static native boolean doesRomNeedRPKG(String rom);
    public static native String[] getApps();
    public static native int installApp(String path);
    public static native int[] installedPackageVersion(long uid);
    public static native String[] inspectRemoval(long appUid, String runtime) throws java.io.IOException;
    public static native void uninstallGame(long appUid, long packageUid, int index, String runtime) throws java.io.IOException;
    public static native boolean removalComplete(String[] plan, String runtime) throws java.io.IOException;
    public static native int installNGageGame(String path);
    public static native String driveE();
    public static native String environmentReport();
    public static native String[] getCatalogApps();
    public static native String[] getLibraryApps();
    public static native android.graphics.Bitmap[] getAppIcon(long uid);
    public static native int[] getNgageArtwork(long uid, boolean cover);
    public static native void launchApp(int uid);
    public static native boolean launchGame(int uid);
    public static native void surfaceChanged(Surface surface, int width, int height);
    public static native void surfaceDestroyed();
    public static native boolean saveScreenshotTo(String path);
    public static native void pressKey(int key, int state);
    public static native void touchScreen(int x, int y, int z, int action, int pointer);
    public static native int[] touchBounds();
    public static native void setScreenParams(int background, int scale, int type, int gravity,
                                            String image, float opacity, boolean keepAspect);
    public static native void submitInput(String text);
    public static native void submitQuestionDialogResponse(int value);
}
