/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.input;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.ui.AppPalette;

/** Small static pixel motif; the actual preview is the running game's retained frame. */
final class PresetSwatch extends Drawable {
    private final Paint paint = new Paint();
    private final Bitmap pixels = Bitmap.createBitmap(12, 6, Bitmap.Config.ARGB_8888);
    private final int width, height;
    private final String name;
    PresetSwatch(Context context, String name) {
        this.name = name;
        float density = context.getResources().getDisplayMetrics().density;
        width = Math.round(72 * density); height = Math.round(36 * density);
        int accent = AppPalette.color(context, R.color.accent);
        int bg = AppPalette.color(context, R.color.background);
        for (int y = 0; y < 6; y++) for (int x = 0; x < 12; x++)
            pixels.setPixel(x, y, x >= y && x < y + 5 ? accent : bg);
        setBounds(0, 0, width, height);
    }
    @Override public int getIntrinsicWidth() { return width; }
    @Override public int getIntrinsicHeight() { return height; }
    @Override public void draw(Canvas canvas) {
        paint.setColor(Color.WHITE); paint.setAlpha(255);
        paint.setFilterBitmap("Smooth".equals(name));
        canvas.drawBitmap(pixels, null, getBounds(), paint);
        if ("CRT".equals(name) || name.startsWith("LCD")) {
            paint.setColor(0x70000000);
            float step = getBounds().height() / 6f;
            for (float y = getBounds().top; y < getBounds().bottom; y += step)
                canvas.drawRect(getBounds().left, y, getBounds().right, y + Math.max(1, step / 5), paint);
        }
    }
    @Override public void setAlpha(int alpha) { }
    @Override public void setColorFilter(ColorFilter filter) { }
    @Override public int getOpacity() { return PixelFormat.OPAQUE; }
}
