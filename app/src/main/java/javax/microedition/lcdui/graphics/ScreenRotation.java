package javax.microedition.lcdui.graphics;

/** Clockwise presentation rotation; game coordinates and key mappings stay unchanged. */
public final class ScreenRotation {
    private ScreenRotation() { }

    public static int normalize(int degrees) {
        return degrees % 90 == 0 ? Math.floorMod(degrees, 360) : 0;
    }

    public static boolean swapsAxes(int degrees) {
        return normalize(degrees) % 180 != 0;
    }

    // Inverse transform, shared by texture coordinates and direct touch input.
    public static float sourceX(float x, float y, int degrees) {
        switch (normalize(degrees)) {
            case 90: return y;
            case 180: return 1 - x;
            case 270: return 1 - y;
            default: return x;
        }
    }

    public static float sourceY(float x, float y, int degrees) {
        switch (normalize(degrees)) {
            case 90: return 1 - x;
            case 180: return 1 - y;
            case 270: return x;
            default: return y;
        }
    }
}
