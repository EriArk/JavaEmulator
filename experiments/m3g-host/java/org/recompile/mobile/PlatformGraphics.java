package org.recompile.mobile;

import android.graphics.Bitmap;
import javax.microedition.lcdui.Graphics;

/** Imports only when needed; the bound clip is the only region committed to MIDP. */
public final class PlatformGraphics {
    public static boolean lazyRead = true;
    public static long imports, exports, skippedImports;
    private final Bitmap bitmap;
    private final int[] pixels;
    private final int stride, x, y, width, height;
    private boolean ready;

    public PlatformGraphics(Graphics target) { this(target, null); }

    public PlatformGraphics(Graphics target, int[] reuse) {
        bitmap = target.getBitmap();
        stride = bitmap.getWidth();
        int size = stride * bitmap.getHeight();
        pixels = reuse != null && reuse.length == size ? reuse : new int[size];
        x = Math.max(0, Math.min(stride, target.getClipX() + target.getTranslateX()));
        y = Math.max(0, Math.min(bitmap.getHeight(), target.getClipY() + target.getTranslateY()));
        width = Math.max(0, Math.min(stride, target.getClipX() + target.getTranslateX() + target.getClipWidth()) - x);
        height = Math.max(0, Math.min(bitmap.getHeight(), target.getClipY() + target.getTranslateY() + target.getClipHeight()) - y);
        if (!lazyRead) prepare(false);
    }

    public int[] pixels() { return pixels; }

    public void prepareClear(int left, int top, int right, int bottom) {
        prepare(left <= x && top <= y && right >= x + width && bottom >= y + height);
    }

    public void prepare(boolean discard) {
        if (ready) return;
        if (width > 0 && height > 0 && !discard) {
            BitmapTransfer.read(bitmap, pixels, stride, x, y, width, height);
            imports++;
        } else skippedImports++;
        ready = true;
    }

    public void flush() {
        if (!ready || width == 0 || height == 0) return;
        bitmap.setPixels(pixels, y * stride + x, stride, x, y, width, height);
        exports++;
    }
    public int getColor() { throw new UnsupportedOperationException("Debug wireframe is not supported"); }
    public void setColor(int color) { throw new UnsupportedOperationException("Debug wireframe is not supported"); }
    public void drawTriangle(int a, int b, int c, int d, int e, int f) {
        throw new UnsupportedOperationException("Debug wireframe is not supported");
    }
}
