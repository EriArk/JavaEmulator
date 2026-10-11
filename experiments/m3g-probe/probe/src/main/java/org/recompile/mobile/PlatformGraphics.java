package org.recompile.mobile;

import android.graphics.Bitmap;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

public final class PlatformGraphics extends Graphics {
    private final int[] pixels;
    private int color;
    public PlatformGraphics(int width, int height) {
        super(new Image(Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)));
        pixels = new int[width * height];
    }
    public int[] getFrameBuffer() { return pixels; }
    public int getColor() { return color; }
    public void setColor(int color) { this.color = color; }
    public void drawTriangle(int x1, int y1, int x2, int y2, int x3, int y3) {
        throw new UnsupportedOperationException("Debug wireframe is not part of this probe");
    }
}
