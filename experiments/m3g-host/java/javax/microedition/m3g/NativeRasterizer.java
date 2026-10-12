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

    static boolean draw(int[] pixels, short[] depth, int[][] textures, float[] state, int[] flags) {
        if (!available()) return false;
        // Avoid JNI object-array lookups/local references on the common single-layer path.
        int units = flags != null && flags.length == 45 ? flags[25] : -1;
        boolean drawn = units >= 0 && units <= 1
                ? drawSingle(pixels, depth, textures != null && textures.length > 0 ? textures[0] : null, state, flags)
                : drawHalf(pixels, depth, textures, state, flags);
        if (!drawn) return false;
        draws++;
        return true;
    }

    static int textureBlend(Graphics3DPipelines.TextureBlender blender) {
        if (blender == null) return 0;
        if (blender == Graphics3DPipelines.TextureBlenders.REPLACE_RGB) return 1;
        if (blender == Graphics3DPipelines.TextureBlenders.MODULATE_RGB) return 2;
        if (blender == Graphics3DPipelines.TextureBlenders.MODULATE_RGBA) return 3;
        if (blender == Graphics3DPipelines.TextureBlenders.ALPHA_MUL) return 4;
        if (blender == Graphics3DPipelines.TextureBlenders.REPLACE_ALPHA) return 5;
        if (blender == Graphics3DPipelines.TextureBlenders.ADD_RGB) return 6;
        if (blender == Graphics3DPipelines.TextureBlenders.ADD_RGBA) return 7;
        if (blender == Graphics3DPipelines.TextureBlenders.BLEND_RGB) return 8;
        if (blender == Graphics3DPipelines.TextureBlenders.BLEND_RGBA) return 9;
        if (blender == Graphics3DPipelines.TextureBlenders.DECAL_RGBA) return 10;
        if (blender == Graphics3DPipelines.TextureBlenders.PASSTHROUGH) return 11;
        return -1;
    }

    private static native boolean drawHalf(int[] pixels, short[] depth, int[][] textures,
                                           float[] state, int[] flags);
    private static native boolean drawSingle(int[] pixels, short[] depth, int[] texture,
                                             float[] state, int[] flags);
}
