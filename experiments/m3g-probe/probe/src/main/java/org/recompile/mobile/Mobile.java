package org.recompile.mobile;

import java.io.InputStream;

/** Minimal probe host, not FreeJ2ME's frontend or AbyssME's runtime adapter. */
public final class Mobile {
    public static final int LOG_DEBUG = 0, LOG_WARNING = 1, LOG_ERROR = 2;
    public static int m3gAntiAliasingMode = 1, m3gPerspectiveCorrectionMode = 1;
    public static int m3gMipmapMode = 1, m3gBilinearFilterMode = 1, m3gDitheringMode = 1;
    public static int m3gPerspCorrSubFactor = 15;
    public static boolean m3gDisableFog, M3GRenderUntexturedPolygons, M3GRenderWireframe, halfResM3GRaster;
    public static void log(int level, String message) {
        if (level != LOG_DEBUG) android.util.Log.w("M3GProbe", message);
    }
    public static InputStream getMIDletResourceAsStream(String name) {
        return Mobile.class.getResourceAsStream(name.startsWith("/") ? name : "/" + name);
    }
}
