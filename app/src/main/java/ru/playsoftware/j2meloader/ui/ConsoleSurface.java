package ru.playsoftware.j2meloader.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import androidx.core.graphics.ColorUtils;
import ru.playsoftware.j2meloader.R;

/** Shallow molded surfaces. No bitmap layers, blur, frame loop or guest-render effects. */
public final class ConsoleSurface extends Drawable {
    public enum Kind { CHASSIS, SCREEN, KEY, TAB, CARD, ROW, PANEL }
    private final Kind kind;
    private final float dp;
    private final boolean dark, contrast;
    private final int surface, accent, tint;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF face = new RectF();
    private LinearGradient fill;
    private boolean selected, focused, pressed, enabled = true;
    private int opacity = 255;

    public ConsoleSurface(Context context, Kind kind) { this(context, kind, -1); }
    public ConsoleSurface(Context context, Kind kind, int platform) {
        this.kind = kind;
        dp = context.getResources().getDisplayMetrics().density;
        int background = AppPalette.color(context, R.color.background);
        dark = ColorUtils.calculateLuminance(background) < .25;
        contrast = "contrast".equals(AppPalette.selected(context));
        boolean classic = "classic".equals(AppPalette.selected(context));
        surface = classic ? (dark ? 0xff25282b : 0xffd4d9dc)
                : AppPalette.color(context, R.color.phone_panel_alt);
        accent = AppPalette.color(context, R.color.accent);
        int[] hues = {0xffb78a37, 0xff5b926b, 0xff548ca6, 0xffad667a};
        tint = platform >= 0 && platform < hues.length ? hues[platform] : surface;
    }
    private int blend(int a, int b, float amount) { return ColorUtils.blendARGB(a, b, amount); }
    private boolean key() { return kind == Kind.KEY || kind == Kind.TAB; }
    @Override public boolean isStateful() { return key() || kind == Kind.CARD || kind == Kind.ROW; }
    @Override protected boolean onStateChange(int[] states) {
        boolean s = false, f = false, p = false, e = false;
        for (int state : states) {
            s |= state == android.R.attr.state_selected;
            f |= state == android.R.attr.state_focused;
            p |= state == android.R.attr.state_pressed;
            e |= state == android.R.attr.state_enabled;
        }
        if (s == selected && f == focused && p == pressed && e == enabled) return false;
        selected = s; focused = f; pressed = p; enabled = e;
        rebuild(); invalidateSelf(); return true;
    }
    @Override protected void onBoundsChange(Rect bounds) { rebuild(); }
    private void rebuild() {
        Rect b = getBounds();
        float inset = key() ? 3 * dp : (kind == Kind.CARD ? dp : 0);
        face.set(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset);
        if (key()) { face.bottom -= 2 * dp; if (pressed || selected) face.top += dp; }
        int base = surface;
        if (kind == Kind.SCREEN) base = dark ? 0xff0e1113 : 0xfff2f5f6;
        if (kind == Kind.CARD || kind == Kind.ROW) base = dark ? 0xff171b1e : 0xffe8edef;
        if (kind == Kind.TAB) base = blend(surface, tint, selected ? .50f : .22f);
        if (kind == Kind.KEY && selected) base = blend(base, accent, .18f);
        if ((kind == Kind.CARD || kind == Kind.ROW) && (focused || pressed)) base = blend(base, accent, .13f);
        if (pressed) base = blend(base, Color.BLACK, .15f);
        if (contrast) base = dark ? Color.BLACK : Color.WHITE;
        int top = contrast ? base : blend(base, Color.WHITE, key() ? .08f : .025f);
        int bottom = contrast ? base : blend(base, Color.BLACK, key() ? .14f : .06f);
        fill = new LinearGradient(0, face.top, 0, Math.max(face.top + 1, face.bottom),
                top, bottom, Shader.TileMode.CLAMP);
    }
    private void solid(int color, Paint.Style style, float stroke) {
        paint.setShader(null); paint.setColor(color); paint.setStyle(style); paint.setStrokeWidth(stroke);
        paint.setAlpha(Math.round(Color.alpha(color) * opacity / 255f * (enabled ? 1 : .45f)));
    }
    @Override public void draw(Canvas canvas) {
        if (face.isEmpty()) return;
        float radius = (kind == Kind.CHASSIS || kind == Kind.SCREEN || kind == Kind.PANEL ? 12 : 8) * dp;
        if (key() && !contrast) {
            solid(dark ? 0xff090b0d : 0xff8e989e, Paint.Style.FILL, 0);
            canvas.save(); canvas.translate(0, 2 * dp);
            canvas.drawRoundRect(face, radius, radius, paint); canvas.restore();
        }
        paint.setStyle(Paint.Style.FILL); paint.setShader(fill);
        paint.setAlpha(Math.round(opacity * (enabled ? 1 : .45f)));
        if (kind != Kind.ROW || focused || pressed)
            canvas.drawRoundRect(face, radius, radius, paint);
        if (kind == Kind.SCREEN && !contrast) {
            // Concentric contact edges imply a recessed well without an offscreen shadow.
            for (int i = 0; i < 3; i++) {
                solid(dark ? 0xff050708 : 0xffaab4ba, Paint.Style.STROKE, dp);
                paint.setAlpha(Math.round(opacity * (3 - i) / 4f));
                float d = (i + .5f) * dp;
                canvas.drawRoundRect(face.left+d, face.top+d, face.right-d, face.bottom-d,
                        Math.max(dp, radius-d), Math.max(dp, radius-d), paint);
            }
        } else if (kind == Kind.ROW && !focused && !pressed) {
            solid(dark ? 0xff30363a : 0xffc1c9cc, Paint.Style.STROKE, dp);
            canvas.drawLine(face.left + 10*dp, face.bottom-dp, face.right-10*dp, face.bottom-dp, paint);
        } else {
            int line = focused || pressed ? accent : contrast ? (dark ? Color.WHITE : Color.BLACK)
                    : blend(surface, dark ? Color.WHITE : Color.BLACK, kind == Kind.CARD ? .08f : .20f);
            solid(line, Paint.Style.STROKE, (focused || pressed || contrast ? 2 : .75f) * dp);
            float d = dp;
            canvas.drawRoundRect(face.left+d, face.top+d, face.right-d, face.bottom-d, radius, radius, paint);
            if (!contrast && kind != Kind.CARD && kind != Kind.ROW) {
                solid(dark ? 0x387d888e : 0xb0ffffff, Paint.Style.STROKE, dp);
                canvas.drawLine(face.left+radius, face.top+dp, face.right-radius, face.top+dp, paint);
            }
        }
        if (selected && key()) {
            solid(accent, Paint.Style.FILL, 0);
            float half = Math.min(16 * dp, face.width()/5);
            canvas.drawRoundRect(face.centerX()-half, face.bottom-4*dp,
                    face.centerX()+half, face.bottom-2*dp, dp, dp, paint);
        }
        paint.setShader(null);
    }
    @Override public void getOutline(Outline outline) { outline.setRoundRect(getBounds(), 8 * dp); }
    @Override public void setAlpha(int alpha) { opacity = alpha; invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
