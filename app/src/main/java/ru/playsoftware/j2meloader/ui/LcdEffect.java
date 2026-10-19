package ru.playsoftware.j2meloader.ui;

import android.graphics.*;

/** Original, frame-independent LCD optical mask. No guest-frame readback or history. */
public final class LcdEffect {
    public static final String[] NAMES = {"Off", "LCD Soft", "LCD Classic"};
    private final Paint wash = new Paint(), gaps = new Paint();
    private float[] grid = new float[0];
    private float cachedWidth = -1, cachedHeight = -1;
    private int cachedSourceWidth, cachedSourceHeight;

    public static int mode(int value) { return value >= 0 && value < NAMES.length ? value : 0; }
    public static int intensity(int value) { return Math.max(0, Math.min(100, value)); }
    public static float gridStrength(float scale) {
        return Float.isFinite(scale) ? Math.max(0, Math.min(1, (scale - 2f) / 2f)) : 0;
    }

    /** Bounds are the rotated picture, not the viewport or letterbox. */
    public void draw(Canvas canvas, RectF bounds, int sourceWidth, int sourceHeight,
                     int rotation, int effect, int strength) {
        effect = mode(effect); strength = intensity(strength);
        if (effect == 0 || strength == 0 || sourceWidth <= 0 || sourceHeight <= 0
                || bounds.isEmpty()) return;
        int angle = Math.floorMod(rotation / 90, 4) * 90;
        boolean quarter = angle % 180 != 0;
        float width = quarter ? bounds.height() : bounds.width();
        float height = quarter ? bounds.width() : bounds.height();
        float sx = width / sourceWidth, sy = height / sourceHeight;
        float visibility = gridStrength(Math.min(sx, sy));
        int save = canvas.save();
        canvas.clipRect(bounds);
        canvas.translate(bounds.centerX(), bounds.centerY());
        canvas.rotate(angle);
        canvas.translate(-width / 2, -height / 2);
        wash.setColor(Color.argb(Math.round((effect == 1 ? 8 : 17) * strength / 100f),128,128,128));
        canvas.drawRect(0, 0, width, height, wash);
        if (visibility > 0) {
            prepare(width, height, sourceWidth, sourceHeight);
            gaps.setColor(Color.argb(Math.round((effect == 1 ? 48 : 96) * visibility * strength / 100f),0,0,0));
            gaps.setStrokeWidth(1);
            canvas.drawLines(grid, gaps);
        }
        canvas.restoreToCount(save);
    }

    private void prepare(float width, float height, int sourceWidth, int sourceHeight) {
        if (width == cachedWidth && height == cachedHeight && sourceWidth == cachedSourceWidth
                && sourceHeight == cachedSourceHeight) return;
        int length = (sourceWidth + sourceHeight) * 4;
        if (grid.length != length) grid = new float[length];
        int point = 0;
        // Match nearest-neighbour pixel boundaries, including fractional Fit scales.
        // A minified repeating texture aliases thin gaps into periodically dark bands.
        for (int x = 1; x <= sourceWidth; x++) {
            float edge = Math.round(x * width / sourceWidth);
            grid[point++] = edge - .5f; grid[point++] = 0;
            grid[point++] = edge - .5f; grid[point++] = height;
        }
        for (int y = 1; y <= sourceHeight; y++) {
            float edge = Math.round(y * height / sourceHeight);
            grid[point++] = 0; grid[point++] = edge - .5f;
            grid[point++] = width; grid[point++] = edge - .5f;
        }
        cachedWidth = width; cachedHeight = height;
        cachedSourceWidth = sourceWidth; cachedSourceHeight = sourceHeight;
    }
}
