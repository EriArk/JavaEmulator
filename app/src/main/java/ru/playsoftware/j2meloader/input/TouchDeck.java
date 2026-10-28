package ru.playsoftware.j2meloader.input;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.SparseIntArray;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import ru.playsoftware.j2meloader.config.ProfileModel;
import ru.playsoftware.j2meloader.R;

/** Touch-only MIDP controls. Physical controller mappings remain independent. */
public final class TouchDeck {
    public interface Sink {
        void press(int key);
        void release(int key);
        void repeat(int key);
        void feedback();
        void invalidate();
    }
    public static final int PHONE = 0, GAMEPAD = 1, LEGACY = 2;
    public static final int THEME_APP = 0, THEME_DARK = 1, THEME_LIGHT = 2;
    private static final Typeface KEY_FONT = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private static final String[] LETTERS = {"+", "", "ABC", "DEF", "GHI", "JKL", "MNO", "PQRS", "TUV", "WXYZ"};
    private static final int NUMBERS = 10000;
    public static final class Key {
        public final RectF bounds;
        public final String label;
        public final int[] codes;
        Key(RectF bounds, String label, int... codes) {
            this.bounds = bounds; this.label = label; this.codes = codes;
        }
    }
    private final ProfileModel settings;
    private final float density;
    private final Sink sink;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ArrayList<Key> keys = new ArrayList<>();
    private final Map<Integer, Key> pointers = new HashMap<>();
    private final SparseIntArray held = new SparseIntArray();
    private final RectF viewport = new RectF();
    private final RectF[] groups = {new RectF(), new RectF()};
    private final RectF[] lanes = {new RectF(), new RectF()};
    private final RectF[] originalGroups = {new RectF(), new RectF()};
    private float width, height;
    private boolean numbers;
    private boolean enabled = true;

    public synchronized void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        cancel(); this.enabled = enabled;
        layout(width, height);
    }
    public synchronized boolean isEnabled() { return enabled; }
    private android.content.Context themeContext;
    private int themeMode = -1;
    private String paletteStyle;
    private int surface = 0xff10181c, button = 0xff29363e, accent = 0xffffc15a,
            secondaryAccent = 0xff71d3d5, line = 0xff6c818c, text = 0xffedf1ee,
            muted = 0xffb0c0c5, onAccent = 0xff151b1e;
    private int keyHighlight = 0xff50606a, keyShadow = 0xff080c0f;

    public synchronized void setTheme(android.content.Context context) {
        themeContext = context;
        themeMode = -1;
        updateTheme();
    }

    private void updateTheme() {
        if (themeContext == null) return;
        int mode = TouchAppearance.mode(themeContext, settings);
        String style = ru.playsoftware.j2meloader.ui.AppPalette.selected(themeContext);
        if (mode == themeMode && java.util.Objects.equals(style, paletteStyle)) return;
        themeMode = mode;
        paletteStyle = style;
        android.content.Context palette = TouchAppearance.palette(themeContext, settings);
        surface = ru.playsoftware.j2meloader.ui.AppPalette.color(palette, R.color.touch_surface);
        button = ru.playsoftware.j2meloader.ui.AppPalette.color(palette, R.color.touch_button);
        accent = ru.playsoftware.j2meloader.ui.AppPalette.color(palette, R.color.accent);
        secondaryAccent = ru.playsoftware.j2meloader.ui.AppPalette.color(palette, R.color.phone_cyan);
        line = ru.playsoftware.j2meloader.ui.AppPalette.color(palette, R.color.touch_line);
        text = ru.playsoftware.j2meloader.ui.AppPalette.color(palette, R.color.text_primary);
        muted = ru.playsoftware.j2meloader.ui.AppPalette.color(palette, R.color.text_secondary);
        onAccent = ru.playsoftware.j2meloader.ui.AppPalette.color(palette, R.color.on_accent);
        keyHighlight = ru.playsoftware.j2meloader.ui.AppPalette.color(palette, R.color.touch_key_highlight);
        keyShadow = ru.playsoftware.j2meloader.ui.AppPalette.color(palette, R.color.touch_key_shadow);
    }
    private final Runnable repeat = new Runnable() {
        @Override public void run() {
            synchronized (TouchDeck.this) {
                for (int i = 0; i < held.size(); i++) sink.repeat(held.keyAt(i));
                if (held.size() > 0) handler.postDelayed(this, 90);
            }
        }
    };

    public TouchDeck(ProfileModel settings, float density, Sink sink) {
        this.settings = settings; this.density = density; this.sink = sink;
    }
    public synchronized ArrayList<Key> getKeys() { return new ArrayList<>(keys); }
    public RectF gameArea(float w, float h) {
        if (overlay()) return new RectF(0, 0, w, h);
        return separateGameArea(w, h);
    }
    private boolean overlay() { return style() == PHONE && settings.touchKeypadOverlay; }
    private int opacity() { return TouchAppearance.opacity(themeContext, settings.touchOpacity); }
    private RectF separateGameArea(float w, float h) {
        if (!enabled) return new RectF(0, 0, w, h);
        float unit = unit(w, h);
        if (w > h) return new RectF(3 * unit + dp(12), dp(8), w - 3 * unit - dp(12), h - dp(8));
        float rows = style() == PHONE ? 6 : 5;
        return new RectF(dp(8), dp(8), w - dp(8), Math.max(dp(96),
                h - rows * unit - dp(12) - dp(32) * Math.max(0,Math.min(1,settings.touchPortraitReach))));
    }
    private int style() { return settings.touchLayout == null ? PHONE : settings.touchLayout; }
    private float unit(float w, float h) {
        float requested = dp(48 + Math.max(0, Math.min(2, settings.touchSize)) * 6);
        return Math.min(requested, Math.min((w - dp(24)) / (w > h ? 7 : 6),
                (h - dp(56)) / (w > h ? 5 : 8)));
    }
    private float dp(float value) { return value * density; }
    public synchronized void layout(float w, float h) {
        cancel();
        width = w; height = h; keys.clear(); viewport.set(separateGameArea(w, h));
        if (!enabled) { sink.invalidate(); return; }
        float u = unit(w, h);
        boolean landscape = w > h;
        if (!landscape && style() == PHONE) {
            float x = dp(8), y = viewport.bottom + dp(8), col = (w - dp(16)) / 3;
            float fourth = (w - dp(16)) / 4;
            key(x, y, fourth, u, "L", -6);
            key(x + fourth, y, fourth, u, "\u2191", -1);
            key(x + fourth * 2, y, fourth, u, "\u2193", -2);
            key(x + fourth * 3, y, fourth, u, "R", -7);
            key(x, y + u, col, u, "\u2190", -3);
            key(x + col, y + u, col, u, "OK", -5);
            key(x + col * 2, y + u, col, u, "\u2192", -4);
            digits(x, y + 2 * u, col, u);
        } else {
            float reach = landscape ? settings.touchLandscapeReach : settings.touchPortraitReach;
            float y = viewport.bottom + dp(8);
            if (landscape) y = dp(48) + Math.max(0, h - dp(52) - 5 * u) * (1 - Math.max(0, Math.min(1, reach)));
            float left = dp(4), right = w - 3 * u - dp(4);
            key(left, y, 1.5f * u, u, "L", -6);
            key(left + 1.5f * u, y, 1.5f * u, u, "R", -7);
            dpad(left, y + u, u);
            if (style() == PHONE || numbers) {
                digits(right, y + u, u, u);
            } else {
                key(right + u, y + u, u, u, "1", 49);
                key(right, y + 2 * u, u, u, "*", 42);
                key(right + 2 * u, y + 2 * u, u, u, "5", 53);
                key(right + u, y + 3 * u, u, u, "0", 48);
            }
            if (style() == GAMEPAD) key(right, y, 3 * u, u, numbers ? "Actions" : "123", NUMBERS);
        }
        applyPlacements();
        sink.invalidate();
    }

    public int groupCount() { return width <= height && style() == PHONE ? 1 : 2; }
    public RectF groupBounds(int group) { return new RectF(groups[group]); }
    public String placementId(int group) { return (width > height ? "landscape" : "portrait") + ":" + style() + ":" + group
            + (overlay() ? ":overlay" : ""); }
    public TouchPlacement placement(int group) {
        if (settings.touchPlacements == null) settings.touchPlacements = new HashMap<>();
        String id = placementId(group);
        TouchPlacement value = settings.touchPlacements.get(id);
        if (value == null) {
            value = new TouchPlacement();
            RectF lane = lanes[group], bounds = originalGroups[group];
            value.x = lane.width() > bounds.width() ? (bounds.left - lane.left) / (lane.width() - bounds.width()) : .5f;
            value.y = lane.height() > bounds.height() ? (bounds.top - lane.top) / (lane.height() - bounds.height()) : .5f;
            settings.touchPlacements.put(id, value);
        }
        return value;
    }
    public void moveGroup(int group, float dx, float dy) {
        TouchPlacement placement = placement(group);
        RectF lane = lanes[group], bounds = groups[group];
        if (lane.width() - bounds.width() > 1) placement.x = TouchPlacement.bounded(
                placement.x() + dx / (lane.width() - bounds.width()), 0, 1, .5f);
        if (lane.height() - bounds.height() > 1) placement.y = TouchPlacement.bounded(
                placement.y() + dy / (lane.height() - bounds.height()), 0, 1, .5f);
        layout(width, height);
    }
    private int group(Key key) { return groupCount() == 1 || key.bounds.centerX() < width / 2 ? 0 : 1; }
    private void applyPlacements() {
        Map<Key, Integer> membership = new HashMap<>();
        for (Key key : keys) membership.put(key, group(key));
        for (int i = 0; i < 2; i++) { groups[i].setEmpty(); originalGroups[i].setEmpty(); }
        for (Key key : keys) originalGroups[group(key)].union(key.bounds);
        for (int i = 0; i < groupCount(); i++) {
            RectF base = originalGroups[i], lane = lanes[i];
            if (width > height) lane.set(i == 0 ? dp(4) : viewport.right + dp(4), dp(48),
                    i == 0 ? viewport.left - dp(4) : width - dp(4), height - dp(4));
            else lane.set(i == 0 ? dp(4) : width / 2 + dp(4), viewport.bottom + dp(8),
                    groupCount() == 1 || i == 1 ? width - dp(4) : width / 2 - dp(4), height - dp(4));
            if (overlay()) lane.set(i == 0 ? dp(4) : width / 2 + dp(4), dp(48),
                    groupCount() == 1 || i == 1 ? width - dp(4) : width / 2 - dp(4), height - dp(4));
            TouchPlacement placement = settings.touchPlacements == null ? null : settings.touchPlacements.get(placementId(i));
            if (placement == null || base.isEmpty()) { groups[i].set(base); continue; }
            float scale = Math.min(placement.scale(), Math.min(lane.width() / base.width(), lane.height() / base.height()));
            scale = Math.max(.1f, scale);
            float left = lane.left + Math.max(0, lane.width() - base.width() * scale) * placement.x();
            float top = lane.top + Math.max(0, lane.height() - base.height() * scale) * placement.y();
            // Group membership must be determined from the original coordinates.
            for (Key key : keys) if (membership.get(key) == i) {
                RectF b = key.bounds;
                b.set(left + (b.left - base.left) * scale, top + (b.top - base.top) * scale,
                        left + (b.right - base.left) * scale, top + (b.bottom - base.top) * scale);
            }
            groups[i].set(left, top, left + base.width() * scale, top + base.height() * scale);
        }
    }
    private void dpad(float x, float y, float u) {
        String[] labels = {"\u2196", "\u2191", "\u2197", "\u2190", "OK", "\u2192", "\u2199", "\u2193", "\u2198"};
        int[][] codes = {{-1,-3},{-1},{-1,-4},{-3},{-5},{-4},{-2,-3},{-2},{-2,-4}};
        for (int i = 0; i < 9; i++) key(x + i % 3 * u, y + i / 3 * u, u, u, labels[i], codes[i]);
    }
    private void digits(float x, float y, float w, float h) {
        String[] labels = {"1","2","3","4","5","6","7","8","9","*","0","#"};
        for (int i = 0; i < 12; i++) key(x + i % 3 * w, y + i / 3 * h, w, h, labels[i], labels[i].charAt(0));
    }
    private void key(float x, float y, float w, float h, String label, int... codes) {
        keys.add(new Key(new RectF(x, y, x+w, y+h), label, codes));
    }
    private Key at(float x, float y) {
        for (Key key : keys) if (key.bounds.contains(x,y)) return key;
        return null;
    }
    public synchronized boolean down(int pointer, float x, float y) {
        Key key = at(x,y);
        if (key == null) return false;
        boolean fresh = !pointers.containsValue(key);
        if (key.codes[0] == NUMBERS) {
            numbers = !numbers; layout(width,height);
            pointers.put(pointer, null);
        } else {
            pointers.put(pointer,key); syncHeld();
        }
        if (fresh && settings.vkFeedback) sink.feedback();
        sink.invalidate(); return true;
    }
    public synchronized boolean move(int pointer, float x, float y) {
        if (!pointers.containsKey(pointer)) return false;
        Key old = pointers.get(pointer), next = at(x,y);
        if (next != null && next.codes[0] == NUMBERS) next = null;
        if (old != next) {
            boolean fresh = next != null && !pointers.containsValue(next);
            pointers.put(pointer,next); syncHeld(); sink.invalidate();
            if (fresh && settings.vkFeedback) sink.feedback();
        }
        return true;
    }
    public synchronized boolean up(int pointer) {
        if (!pointers.containsKey(pointer)) return false;
        pointers.remove(pointer); syncHeld(); sink.invalidate(); return true;
    }
    private void syncHeld() {
        SparseIntArray desired = new SparseIntArray();
        for (Key key : pointers.values()) if (key != null) {
            for (int code : key.codes) desired.put(code,desired.get(code)+1);
        }
        boolean wasEmpty = held.size() == 0;
        for (int i=held.size()-1;i>=0;i--) {
            int code = held.keyAt(i);
            if (desired.indexOfKey(code) < 0) { held.delete(code); sink.release(code); }
        }
        for (int i=0;i<desired.size();i++) {
            int code=desired.keyAt(i);
            if (held.indexOfKey(code) < 0) sink.press(code);
            held.put(code,desired.valueAt(i));
        }
        if (held.size() == 0) handler.removeCallbacks(repeat);
        else if (wasEmpty) handler.postDelayed(repeat,400);
    }
    public synchronized void cancel() {
        handler.removeCallbacks(repeat);
        for (int i=0;i<held.size();i++) sink.release(held.keyAt(i));
        held.clear(); pointers.clear(); sink.invalidate();
    }
    public synchronized void draw(Canvas canvas) {
        if (!enabled) return;
        updateTheme();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(surface);
        if (!overlay() && width > height) {
            canvas.drawRect(0,0,viewport.left,height,paint);
            canvas.drawRect(viewport.right,0,width,height,paint);
        } else if (!overlay()) canvas.drawRect(0,viewport.bottom,width,height,paint);
        // Composite the complete keys once, including action faces, borders and labels.
        int alpha = opacity() * 255 / 100;
        int layer = alpha == 255 ? canvas.save() : canvas.saveLayerAlpha(0, 0, width, height, alpha);
        boolean cross = style() == GAMEPAD || width > height;
        if (cross) drawDpadBase(canvas);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(KEY_FONT);
        for (Key key : keys) {
            RectF rect = new RectF(key.bounds); rect.inset(dp(3),dp(3));
            float labelScale = Math.min(1, key.bounds.height() / dp(48));
            boolean pressed = pointers.containsValue(key);
            boolean dpad = cross && isDpad(key);
            if (dpad && key.codes.length == 1) pressed = held.get(key.codes[0]) > 0;
            boolean action = style() == GAMEPAD && !numbers && key.codes[0] > 0 && key.codes[0] != NUMBERS;
            int opacity = 100;
            paint.setColor(pressed ? accent : button);
            paint.setAlpha(opacity * 255 / 100);
            if (action) {
                paint.setColor(keyShadow); canvas.drawOval(rect, paint);
                RectF face = new RectF(rect); face.bottom -= dp(2);
                if (pressed) face.offset(0, dp(1.5f));
                paint.setColor(pressed ? accent : button); canvas.drawOval(face, paint);
                if (!pressed && !"contrast".equals(paletteStyle)) {
                    paint.setColor(keyHighlight); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(1));
                    canvas.drawArc(face.left + dp(2), face.top + dp(2), face.right - dp(2), face.bottom - dp(2),
                            210, 120, false, paint); paint.setStyle(Paint.Style.FILL);
                }
            }
            else if (!dpad) drawPhoneKey(canvas, rect, pressed, opacity);
            else if (pressed) {
                paint.setAlpha(210);
                canvas.drawRoundRect(rect,dp(6),dp(6),paint);
            }
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(1));
            paint.setColor(pressed ? accent : action ? (key.codes[0] == 53 ? accent : secondaryAccent) : line);
            if (action) canvas.drawOval(rect,paint);
            else if (!dpad) canvas.drawRoundRect(rect,dp(6),dp(6),paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(pressed ? onAccent : text);
            paint.setTextSize(dp(dpad && key.codes.length > 1 ? 13 : key.label.length() > 2 ? 14 : 20) * labelScale);
            if (dpad && key.codes.length > 1 && !pressed) paint.setColor(muted);
            boolean digit = (style() == PHONE || numbers) && key.codes.length == 1 && key.codes[0] >= 48 && key.codes[0] <= 57;
            if (digit) paint.setTextSize(dp(23) * labelScale);
            canvas.drawText(key.label,rect.centerX(),rect.centerY()-(paint.ascent()+paint.descent())/2-(digit ? dp(5) * labelScale : 0),paint);
            if (digit) {
                paint.setTextSize(dp(9) * labelScale);
                paint.setColor(pressed ? onAccent : muted);
                canvas.drawText(LETTERS[key.codes[0]-48],rect.centerX(),rect.centerY()+dp(13) * labelScale,paint);
                if (key.codes[0] == 53) {
                    canvas.drawCircle(rect.left + dp(8), rect.bottom - dp(5), dp(1), paint);
                    canvas.drawCircle(rect.right - dp(8), rect.bottom - dp(5), dp(1), paint);
                }
            }
        }
        canvas.restoreToCount(layer);
    }

    private void drawPhoneKey(Canvas canvas, RectF face, boolean pressed, int opacity) {
        if ("contrast".equals(paletteStyle)) {
            paint.setColor(pressed ? accent : button); paint.setAlpha(opacity * 255 / 100);
            canvas.drawRoundRect(face, dp(2), dp(2), paint);
            return;
        }
        paint.setColor(keyShadow);
        paint.setAlpha(opacity * 255 / 100);
        canvas.drawRoundRect(face, dp(6), dp(6), paint);
        face.bottom -= dp(2);
        if (pressed) face.offset(0, dp(1.5f));
        paint.setColor(pressed ? accent : button);
        paint.setAlpha(opacity * 255 / 100);
        canvas.drawRoundRect(face, dp(6), dp(6), paint);
        if (!pressed) {
            RectF sheen = new RectF(face.left + dp(1), face.top + dp(1), face.right - dp(1),
                    face.top + face.height() * .42f);
            paint.setColor(keyHighlight); paint.setAlpha(opacity * 60 / 100);
            canvas.drawRoundRect(sheen, dp(5), dp(5), paint);
            paint.setColor(keyHighlight);
            paint.setAlpha(opacity * 255 / 100);
            paint.setStrokeWidth(dp(1));
            canvas.drawLine(face.left + dp(6), face.top + dp(1.5f),
                    face.right - dp(6), face.top + dp(1.5f), paint);
        }
    }

    private boolean isDpad(Key key) {
        return key.codes[0] >= -5 && key.codes[0] <= -1;
    }

    private void drawDpadBase(Canvas canvas) {
        Key center = null;
        for (Key key : keys) if (key.codes.length == 1 && key.codes[0] == -5) center = key;
        if (center == null) return;
        RectF c = center.bounds;
        float u = c.width(), inset = dp(3);
        Path vertical = new Path(), horizontal = new Path();
        vertical.addRoundRect(new RectF(c.left + inset, c.top - u + inset,
                c.right - inset, c.bottom + u - inset), dp(7), dp(7), Path.Direction.CW);
        horizontal.addRoundRect(new RectF(c.left - u + inset, c.top + inset,
                c.right + u - inset, c.bottom - inset), dp(7), dp(7), Path.Direction.CW);
        vertical.op(horizontal, Path.Op.UNION);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(button);
        paint.setAlpha(255);
        canvas.drawPath(vertical, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.5f));
        paint.setColor(line);
        canvas.drawPath(vertical, paint);
        paint.setStyle(Paint.Style.FILL);
    }
}
