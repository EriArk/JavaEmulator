package org.recompile.mobile;

import java.io.InputStream;
import javax.microedition.util.ContextHolder;

/** Android host services for the pinned renderer, not its desktop frontend. */
public final class Mobile {
    public static final int LOG_DEBUG = 0, LOG_WARNING = 1, LOG_ERROR = 2;
    public static int m3gAntiAliasingMode = 1, m3gPerspectiveCorrectionMode = 1;
    public static int m3gMipmapMode = 1, m3gBilinearFilterMode = 1, m3gDitheringMode = 1;
    public static int m3gPerspCorrSubFactor = 15;
    public static boolean m3gDisableFog, M3GRenderUntexturedPolygons, M3GRenderWireframe, halfResM3GRaster;
    public static void log(int level, String message) {
        if (level != LOG_DEBUG) android.util.Log.w("M3GHost", message);
    }
    public static InputStream getMIDletResourceAsStream(String name) {
        return ContextHolder.getResourceAsStream(null, name);
    }
}
