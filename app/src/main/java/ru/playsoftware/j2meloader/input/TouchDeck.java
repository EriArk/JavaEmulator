package ru.playsoftware.j2meloader.input;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.SparseIntArray;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import ru.playsoftware.j2meloader.config.ProfileModel;

/** Touch-only MIDP controls. Physical controller mappings remain independent. */
public final class TouchDeck {
    public interface Sink {
        void press(int key);
        void release(int key);
        void repeat(int key);
        void invalidate();
    }
    public static final int PHONE = 0, GAMEPAD = 1, LEGACY = 2;
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
    private float width, height;
    private boolean numbers;
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
        float unit = unit(w, h);
        if (w > h) return new RectF(3 * unit + dp(12), dp(8), w - 3 * unit - dp(12), h - dp(8));
        float rows = style() == PHONE ? 6 : 5;
        return new RectF(dp(8), dp(48), w - dp(8), Math.max(dp(96),
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
        width = w; height = h; keys.clear(); viewport.set(gameArea(w, h));
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
        sink.invalidate();
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
        if (key.codes[0] == NUMBERS) {
            numbers = !numbers; layout(width,height);
            pointers.put(pointer, null);
        } else {
            pointers.put(pointer,key); syncHeld();
        }
        sink.invalidate(); return true;
    }
    public synchronized boolean move(int pointer, float x, float y) {
        if (!pointers.containsKey(pointer)) return false;
        Key old = pointers.get(pointer), next = at(x,y);
        if (next != null && next.codes[0] == NUMBERS) next = null;
        if (old != next) {
            pointers.put(pointer,next); syncHeld(); sink.invalidate();
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
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xff10181c);
        if (width > height) {
            canvas.drawRect(0,0,viewport.left,height,paint);
            canvas.drawRect(viewport.right,0,width,height,paint);
        } else canvas.drawRect(0,viewport.bottom,width,height,paint);
        if (style() == GAMEPAD) drawDpadBase(canvas);
        paint.setTextAlign(Paint.Align.CENTER);
        for (Key key : keys) {
            RectF rect = new RectF(key.bounds); rect.inset(dp(3),dp(3));
            boolean pressed = pointers.containsValue(key);
            boolean dpad = style() == GAMEPAD && isDpad(key);
            if (dpad && key.codes.length == 1) pressed = held.get(key.codes[0]) > 0;
            boolean action = style() == GAMEPAD && !numbers && key.codes[0] > 0 && key.codes[0] != NUMBERS;
            int opacity = Math.max(80,Math.min(100,settings.touchOpacity));
            paint.setColor(pressed ? 0xffffc15a : 0xff29363e);
            paint.setAlpha(opacity * 255 / 100);
            if (action) canvas.drawOval(rect,paint);
            else if (!dpad) canvas.drawRoundRect(rect,dp(6),dp(6),paint);
            else if (pressed) {
                paint.setAlpha(210);
                canvas.drawRoundRect(rect,dp(6),dp(6),paint);
            }
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(1));
            paint.setColor(pressed ? 0xffffdb92 : action ? (key.codes[0] == 53 ? 0xffffc15a : 0xff71d3d5) : 0xff52616a);
            if (action) canvas.drawOval(rect,paint);
            else if (!dpad) canvas.drawRoundRect(rect,dp(6),dp(6),paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(pressed ? 0xff151b1e : 0xffedf1ee);
            paint.setTextSize(dp(dpad && key.codes.length > 1 ? 13 : key.label.length() > 2 ? 14 : 20));
            if (dpad && key.codes.length > 1 && !pressed) paint.setColor(0xff8b9da7);
            boolean digit = style() == PHONE && key.codes.length == 1 && key.codes[0] >= 48 && key.codes[0] <= 57;
            canvas.drawText(key.label,rect.centerX(),rect.centerY()-(paint.ascent()+paint.descent())/2-(digit ? dp(5) : 0),paint);
            if (digit) {
                String[] letters = {"+", "", "ABC", "DEF", "GHI", "JKL", "MNO", "PQRS", "TUV", "WXYZ"};
                paint.setTextSize(dp(9));
                paint.setColor(pressed ? 0xff394039 : 0xffb0c0c5);
                canvas.drawText(letters[key.codes[0]-48],rect.centerX(),rect.centerY()+dp(14),paint);
            }
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
        paint.setColor(0xff26343c);
        paint.setAlpha(Math.max(80, Math.min(100, settings.touchOpacity)) * 255 / 100);
        canvas.drawPath(vertical, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.5f));
        paint.setColor(0xff6c818c);
        canvas.drawPath(vertical, paint);
        paint.setStyle(Paint.Style.FILL);
    }
}
