/* SPDX-License-Identifier: GPL-3.0-or-later
 * Native acceleration of FreeJ2ME-Plus's software raster pipeline.
 * See experiments/m3g-host/native/README.md for provenance and Java fallback.
 */
package javax.microedition.m3g;

final class NativeRasterizer {
    static boolean enabled = true;
    static long draws;
    private static final boolean available;
    static {
        boolean loaded;
        try { System.loadLibrary("abyssm3graster"); loaded = true; }
        catch (UnsatisfiedLinkError missing) { loaded = false; }
        available = loaded;
    }

    static boolean available() { return enabled && available; }

    static boolean draw(int[] pixels, short[] depth, int[] texture, float[] state, int[] flags) {
        if (!available() || !drawHalf(pixels, depth, texture, state, flags)) return false;
        draws++;
        return true;
    }

    private static native boolean drawHalf(int[] pixels, short[] depth, int[] texture,
                                           float[] state, int[] flags);
}
