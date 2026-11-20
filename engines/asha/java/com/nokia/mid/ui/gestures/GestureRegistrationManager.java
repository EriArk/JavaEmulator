/* SPDX-License-Identifier: GPL-3.0-only */
package com.nokia.mid.ui.gestures;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.ViewConfiguration;
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
                case Canvas.KEY_FIRE: gestures = GESTURE_TAP | GESTURE_DOUBLE_TAP | GESTURE_LONG_PRESS | GESTURE_LONG_PRESS_REPEATED; break;
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
                    binding.button.update(down ? CanvasEvent.POINTER_PRESSED : CanvasEvent.POINTER_RELEASED, 0, x, y, time);
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
        public void pointer(int type, int id, int x, int y, long time) {
            if (listener == null) return;
            for (Binding binding : bindings) binding.touch.update(type, id, x, y, time);
        }
    }

    private static final class Contact {
        private static final Handler TIMER = new Handler(Looper.getMainLooper());
        final Input input;
        final GestureInteractiveZone zone;
        private int primary = -1, secondary = -1;
        private int startX, startY, lastX, lastY, secondX, secondY, secondStartX, secondStartY;
        private long startTime;
        private boolean moved, held, afterPinch;
        private int generation;
        private PinchEvent pinch;

        // Only a zone subscribing to double taps needs to defer the first tap.
        private static final int DOUBLE_TAP_MS = ViewConfiguration.getDoubleTapTimeout();
        private static final int DOUBLE_TAP_RADIUS = 12; // guest canvas pixels
        private StrokeEvent pendingTap;
        private long pendingTime;
        private boolean secondTap;

        Contact(Input input, GestureInteractiveZone zone) { this.input = input; this.zone = zone; }

        synchronized void cancel() {
            primary = secondary = -1;
            pinch = null;
            pendingTap = null;
            secondTap = false;
            generation++;
        }

        private boolean wants(int types) { return (zone.getGestures() & types) != 0; }
        private void emit(StrokeEvent event) {
            GestureListener listener = input.listener;
            if (listener != null && wants(event.getType()))
                listener.gestureAction(input.canvas, zone, event);
        }
        private void send(int type, int dx, int dy, int vx, int vy) {
            if (primary != -1 && wants(type))
                emit(new StrokeEvent(type, startX, startY, dx, dy, vx, vy));
        }
        private void boundary(int type, int x, int y) {
            if (wants(type)) emit(new StrokeEvent(type, x, y, 0, 0, 0, 0));
        }
        private void finishPending(boolean paired) {
            StrokeEvent first = pendingTap;
            if (first == null) return;
            pendingTap = null;
            boolean startSecond = secondTap;
            secondTap = false;
            if (!paired) emit(first);
            boundary(GESTURE_RECOGNITION_END, first.getStartX(), first.getStartY());
            if (startSecond) send(GESTURE_RECOGNITION_START, 0, 0, 0, 0);
        }
        private void deferTap(long time) {
            StrokeEvent tap = new StrokeEvent(GESTURE_TAP, startX, startY, 0, 0, 0, 0);
            pendingTap = tap;
            pendingTime = time;
            scheduleTapExpiry(tap, time + DOUBLE_TAP_MS, false);
        }
        private void scheduleTapExpiry(StrokeEvent tap, long due, boolean waitingForRelease) {
            TIMER.postAtTime(() -> Display.postEvent(RunnableEvent.getInstance(
                    () -> expireTap(tap, waitingForRelease))), due);
        }
        private synchronized void expireTap(StrokeEvent tap, boolean waitingForRelease) {
            if (pendingTap != tap || secondTap != waitingForRelease) return;
            if (input.canvas.isShown()) finishPending(false);
            else cancel();
        }
        private void scheduleHold(int token, long due) {
            TIMER.postAtTime(() -> Display.postEvent(RunnableEvent.getInstance(() -> hold(token))), due);
        }
        private synchronized void hold(int token) {
            if (generation != token || primary == -1 || moved || !input.canvas.isShown()) return;
            finishPending(false);
            send(held ? GESTURE_LONG_PRESS_REPEATED : GESTURE_LONG_PRESS, 0, 0, 0, 0);
            held = true;
            if (generation == token && wants(GESTURE_LONG_PRESS_REPEATED))
                scheduleHold(token, SystemClock.uptimeMillis() + zone.getLongPressTimeInterval());
        }

        private void begin(int id, int x, int y, long time) {
            if (primary != -1) {
                if (id == primary || secondary != -1) return;
                finishPending(false);
                secondary = id;
                secondStartX = secondX = x;
                secondStartY = secondY = y;
                generation++; // the first contact no longer qualifies for long press
                pinch = new PinchEvent(startX, startY, lastX, lastY, x, y, null);
                boundary(GESTURE_RECOGNITION_START, x, y);
                return;
            }
            boolean inside = zone.contains(x, y);
            secondTap = inside && pendingTap != null && time >= pendingTime
                    && time - pendingTime <= DOUBLE_TAP_MS
                    && Math.hypot(x - pendingTap.getStartX(), y - pendingTap.getStartY()) <= DOUBLE_TAP_RADIUS;
            if (!secondTap) finishPending(false);
            if (!inside) return;
            primary = id;
            startX = lastX = x;
            startY = lastY = y;
            startTime = time;
            moved = held = afterPinch = false;
            if (secondTap) scheduleTapExpiry(pendingTap, time + DOUBLE_TAP_MS, true);
            else send(GESTURE_RECOGNITION_START, 0, 0, 0, 0);
            if (wants(GESTURE_LONG_PRESS | GESTURE_LONG_PRESS_REPEATED))
                scheduleHold(generation, time + zone.getLongPressTimeInterval());
        }

        private void updatePinch(int type, int id, int x, int y, long time) {
            if (id == primary) {
                lastX = x;
                lastY = y;
            } else {
                secondX = x;
                secondY = y;
            }
            PinchEvent next = new PinchEvent(startX, startY, lastX, lastY, secondX, secondY, pinch);
            pinch = next;
            if (next.getPinchDistanceChange() != 0 || next.getPinchCenterChangeX() != 0
                    || next.getPinchCenterChangeY() != 0) emit(next);
            if (type != CanvasEvent.POINTER_RELEASED || primary == -1) return;
            int endX = id == primary ? startX : secondStartX;
            int endY = id == primary ? startY : secondStartY;
            if (id == primary) {
                primary = secondary;
                startX = secondStartX;
                startY = secondStartY;
                lastX = secondX;
                lastY = secondY;
            }
            secondary = -1;
            pinch = null;
            afterPinch = true;
            moved = false;
            startTime = time;
            boundary(GESTURE_RECOGNITION_END, endX, endY);
        }

        synchronized void update(int type, int id, int x, int y, long time) {
            if (type == CanvasEvent.POINTER_PRESSED) {
                begin(id, x, y, time);
                return;
            }
            if (id != primary && id != secondary) return;
            if (secondary != -1) {
                updatePinch(type, id, x, y, time);
                return;
            }
            int dx = x - startX, dy = y - startY;
            boolean changed = x != lastX || y != lastY;
            moved |= afterPinch ? changed : Math.max(Math.abs(dx), Math.abs(dy)) >= 5;
            if (moved && changed) {
                finishPending(false);
                send(GESTURE_DRAG, x - lastX, y - lastY, 0, 0);
                lastX = x;
                lastY = y;
            }
            if (type != CanvasEvent.POINTER_RELEASED) return;
            long duration = Math.max(1, time - startTime);
            boolean longPress = wants(GESTURE_LONG_PRESS) && duration >= zone.getLongPressTimeInterval();
            boolean quickTap = !moved && !held && !afterPinch && !longPress && duration < DOUBLE_TAP_MS;
            boolean paired = secondTap && quickTap;
            finishPending(paired);
            boolean deferred = false;
            if (paired) {
                send(GESTURE_DOUBLE_TAP, 0, 0, 0, 0);
            } else if (moved) {
                int vx = (int)(dx * 1000L / duration), vy = (int)(dy * 1000L / duration);
                send(GESTURE_DROP, dx, dy, 0, 0);
                if (!afterPinch && Math.hypot(vx, vy) >= 100) send(GESTURE_FLICK, dx, dy, vx, vy);
            } else if (!held && !afterPinch) {
                if (longPress) {
                    send(GESTURE_LONG_PRESS, 0, 0, 0, 0);
                } else if (quickTap && wants(GESTURE_DOUBLE_TAP)) {
                    deferTap(time);
                    deferred = true;
                } else {
                    send(GESTURE_TAP, 0, 0, 0, 0);
                }
            }
            if (!deferred) send(GESTURE_RECOGNITION_END, dx, dy, 0, 0);
            primary = -1;
            generation++;
        }
    }
}
