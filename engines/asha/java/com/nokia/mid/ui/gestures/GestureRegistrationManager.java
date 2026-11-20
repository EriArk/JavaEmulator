/* SPDX-License-Identifier: GPL-3.0-only */
package com.nokia.mid.ui.gestures;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.event.CanvasEvent;
import javax.microedition.lcdui.event.RunnableEvent;
import static com.nokia.mid.ui.gestures.GestureInteractiveZone.*;

public final class GestureRegistrationManager {
    public static final int MAX_SUPPORTED_GESTURE_INTERACTIVE_ZONES = 99;

    private static synchronized Input input(Object container) {
        if (!(container instanceof Canvas)) throw new IllegalArgumentException("Expected a Canvas");
        Canvas canvas = (Canvas)container;
        if (canvas.gestureInput == null) canvas.gestureInput = new Input(canvas);
        return (Input)canvas.gestureInput;
    }

    public static void setListener(Object container, GestureListener listener) {
        Input input = input(container);
        input.cancel();
        input.listener = listener;
    }

    public static synchronized boolean register(Object container, GestureInteractiveZone zone) {
        if (zone == null) throw new IllegalArgumentException("Missing zone");
        Input input = input(container);
        for (Binding binding : input.bindings) if (binding.touch.zone == zone) return false;
        if (input.bindings.size() >= MAX_SUPPORTED_GESTURE_INTERACTIVE_ZONES) return false;
        input.bindings.add(new Binding(input, zone));
        return true;
    }

    public static void unregister(Object container, GestureInteractiveZone zone) {
        Input input = input(container);
        for (Binding binding : input.bindings) {
            if (binding.touch.zone == zone && input.bindings.remove(binding)) binding.cancel();
        }
    }

    public static void unregisterAll(Object container) {
        Input input = input(container);
        for (Binding binding : input.bindings) unregister(container, binding.touch.zone);
    }

    private static final class Binding {
        final Contact touch, button;
        Binding(Input input, GestureInteractiveZone zone) {
            touch = new Contact(input, zone);
            button = new Contact(input, zone);
        }
        void cancel() { touch.cancel(); button.cancel(); }
    }

    private static final class Input implements Canvas.GestureInput {
        final Canvas canvas;
        final CopyOnWriteArrayList<Binding> bindings = new CopyOnWriteArrayList<>();
        volatile GestureListener listener;
        Input(Canvas canvas) { this.canvas = canvas; }
        public void cancel() { for (Binding binding : bindings) binding.cancel(); }
        public boolean acceptsKey(int key) {
            if (listener == null) return false;
            int gestures;
            switch (key) {
                case Canvas.KEY_FIRE: gestures = GESTURE_TAP | GESTURE_LONG_PRESS | GESTURE_LONG_PRESS_REPEATED; break;
                case Canvas.KEY_LEFT: case Canvas.KEY_RIGHT: case Canvas.KEY_UP: case Canvas.KEY_DOWN:
                    gestures = GESTURE_FLICK; break;
                default: return false;
            }
            int x = canvas.getWidth() / 2, y = canvas.getHeight() / 2;
            for (Binding binding : bindings)
                if (binding.button.zone.contains(x, y) && binding.button.wants(gestures)) return true;
            return false;
        }
        public void key(int key, boolean down, long time) {
            if (listener == null) return;
            int x = canvas.getWidth() / 2, y = canvas.getHeight() / 2;
            if (key == Canvas.KEY_FIRE) {
                for (Binding binding : bindings)
                    binding.button.update(down ? CanvasEvent.POINTER_PRESSED : CanvasEvent.POINTER_RELEASED, x, y, time);
            } else if (down) {
                int vx = key == Canvas.KEY_LEFT ? -500 : key == Canvas.KEY_RIGHT ? 500 : 0;
                int vy = key == Canvas.KEY_UP ? -500 : key == Canvas.KEY_DOWN ? 500 : 0;
                for (Binding binding : bindings) {
                    GestureListener current = listener;
                    Contact contact = binding.button;
                    if (current != null && contact.zone.contains(x, y) && contact.wants(GESTURE_FLICK))
                        current.gestureAction(canvas, contact.zone, new StrokeEvent(GESTURE_FLICK, x, y, 0, 0, vx, vy));
                }
            }
        }
        public void pointer(int type, int x, int y, long time) {
            if (listener == null) return;
            for (Binding binding : bindings) binding.touch.update(type, x, y, time);
        }
    }

    private static final class Contact {
        private static final Handler TIMER = new Handler(Looper.getMainLooper());
        final Input input;
        final GestureInteractiveZone zone;
        private int startX, startY, lastX, lastY;
        private long startTime;
        private boolean active, moved, held;
        private int generation;

        Contact(Input input, GestureInteractiveZone zone) { this.input = input; this.zone = zone; }

        synchronized void cancel() { active = false; generation++; }

        private boolean wants(int types) { return (zone.getGestures() & types) != 0; }
        private void send(int type, int dx, int dy, int vx, int vy) {
            GestureListener listener = input.listener;
            if (active && listener != null && wants(type))
                listener.gestureAction(input.canvas, zone, new StrokeEvent(type, startX, startY, dx, dy, vx, vy));
        }
        private void scheduleHold(int token, long due) {
            TIMER.postAtTime(() -> Display.postEvent(RunnableEvent.getInstance(() -> hold(token))), due);
        }
        private synchronized void hold(int token) {
            if (generation != token || !active || moved || !input.canvas.isShown()) return;
            send(held ? GESTURE_LONG_PRESS_REPEATED : GESTURE_LONG_PRESS, 0, 0, 0, 0);
            held = true;
            if (active && wants(GESTURE_LONG_PRESS_REPEATED))
                scheduleHold(token, SystemClock.uptimeMillis() + zone.getLongPressTimeInterval());
        }

        synchronized void update(int type, int x, int y, long time) {
            if (type == CanvasEvent.POINTER_PRESSED) {
                cancel();
                if (!zone.contains(x, y)) return;
                active = true;
                moved = held = false;
                startX = lastX = x;
                startY = lastY = y;
                startTime = time;
                send(GESTURE_RECOGNITION_START, 0, 0, 0, 0);
                if (wants(GESTURE_LONG_PRESS | GESTURE_LONG_PRESS_REPEATED))
                    scheduleHold(generation, time + zone.getLongPressTimeInterval());
                return;
            }
            if (!active) return;
            int dx = x - startX, dy = y - startY;
            moved |= Math.max(Math.abs(dx), Math.abs(dy)) >= 5;
            if (moved && (x != lastX || y != lastY)) {
                send(GESTURE_DRAG, x - lastX, y - lastY, 0, 0);
                lastX = x;
                lastY = y;
            }
            if (type != CanvasEvent.POINTER_RELEASED) return;
            if (moved) {
                long duration = Math.max(1, time - startTime);
                int vx = (int)(dx * 1000L / duration), vy = (int)(dy * 1000L / duration);
                send(GESTURE_DROP, dx, dy, 0, 0);
                if (Math.hypot(vx, vy) >= 100) send(GESTURE_FLICK, dx, dy, vx, vy);
            } else if (!held) {
                boolean longPress = wants(GESTURE_LONG_PRESS) && time - startTime >= zone.getLongPressTimeInterval();
                send(longPress ? GESTURE_LONG_PRESS : GESTURE_TAP, 0, 0, 0, 0);
            }
            send(GESTURE_RECOGNITION_END, dx, dy, 0, 0);
            cancel();
        }
    }
}
