package io.github.eriark.abyssme.symbian;

import android.graphics.RectF;
import android.util.SparseArray;

/** Owns guest fingers independently of the virtual keys. Coordinates are surface pixels. */
final class SymbianTouch {
    static final int DOWN = 0, MOVE = 1, UP = 2;
    static final int MAX_POINTERS = 8;
    interface Sink { void touch(int x, int y, int pressure, int action, int pointer); }
    private static final class Finger {
        final int slot;
        final RectF bounds;
        int x, y;
        Finger(int slot, RectF bounds) { this.slot = slot; this.bounds = new RectF(bounds); }
    }
    private final SparseArray<Finger> fingers = new SparseArray<>();
    private final Sink sink;
    SymbianTouch(Sink sink) { this.sink = sink; }

    boolean down(int id, float x, float y, float pressure, RectF bounds) {
        if (fingers.get(id) != null || fingers.size() == MAX_POINTERS || bounds == null
                || !bounds.contains(x, y)) return false;
        boolean[] used = new boolean[MAX_POINTERS];
        for (int i = 0; i < fingers.size(); i++) used[fingers.valueAt(i).slot] = true;
        int slot = 0;
        while (used[slot]) slot++;
        Finger finger = new Finger(slot, bounds);
        fingers.put(id, finger);
        send(finger, x, y, pressure, DOWN);
        return true;
    }
    void move(int id, float x, float y, float pressure) {
        Finger finger = fingers.get(id);
        if (finger != null) send(finger, x, y, pressure, MOVE);
    }
    void up(int id) {
        Finger finger = fingers.get(id);
        if (finger == null) return;
        sink.touch(finger.x, finger.y, 0, UP, finger.slot);
        fingers.remove(id);
    }
    void cancel() {
        while (fingers.size() > 0) up(fingers.keyAt(0));
    }
    private void send(Finger finger, float x, float y, float pressure, int action) {
        int px = Math.max((int) Math.ceil(finger.bounds.left), Math.min((int) Math.ceil(finger.bounds.right) - 1, (int) x));
        int py = Math.max((int) Math.ceil(finger.bounds.top), Math.min((int) Math.ceil(finger.bounds.bottom) - 1, (int) y));
        if (action == MOVE && px == finger.x && py == finger.y) return;
        finger.x = px; finger.y = py;
        sink.touch(px, py, Math.max(1, Math.min(100, Math.round(pressure * 100))), action, finger.slot);
    }
}
