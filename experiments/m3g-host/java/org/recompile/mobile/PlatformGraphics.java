package org.recompile.mobile;

import android.graphics.Bitmap;
import javax.microedition.lcdui.Graphics;

/** Copies the shared MIDP bitmap at bind/release so 2D and 3D retain paint order. */
public final class PlatformGraphics {
    public PlatformGraphics(Graphics target) { }
    public static int[] read(Graphics target, int[] reuse) {
        Bitmap bitmap = target.getBitmap();
        int width = bitmap.getWidth(), height = bitmap.getHeight();
        int[] pixels = reuse != null && reuse.length == width * height ? reuse : new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        return pixels;
    }
    public static void flush(Graphics target, int[] pixels) {
        Bitmap bitmap = target.getBitmap();
        bitmap.setPixels(pixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
    }
    public int getColor() { throw new UnsupportedOperationException("Debug wireframe is not supported"); }
    public void setColor(int color) { throw new UnsupportedOperationException("Debug wireframe is not supported"); }
    public void drawTriangle(int a, int b, int c, int d, int e, int f) {
        throw new UnsupportedOperationException("Debug wireframe is not supported");
    }
}
