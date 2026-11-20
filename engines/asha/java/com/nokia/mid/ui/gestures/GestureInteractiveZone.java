/* SPDX-License-Identifier: GPL-3.0-only */
package com.nokia.mid.ui.gestures;

/** Nokia gesture identities are bit flags. A new zone covers its whole container. */
public class GestureInteractiveZone {
    public static final int GESTURE_TAP = 1, GESTURE_LONG_PRESS = 2, GESTURE_DRAG = 4,
            GESTURE_DROP = 8, GESTURE_FLICK = 16, GESTURE_LONG_PRESS_REPEATED = 32,
            GESTURE_PINCH = 64, GESTURE_DOUBLE_TAP = 128, GESTURE_RECOGNITION_START = 0x4000,
            GESTURE_RECOGNITION_END = 0x8000, GESTURE_ALL = 0xc0ff;
    private static final int SUPPORTED = GESTURE_ALL & ~(GESTURE_PINCH | GESTURE_DOUBLE_TAP);
    private int mask, interval, x, y, width, height;
    private boolean bounded;

    public GestureInteractiveZone(int gestures) { this(gestures, 500); }
    public GestureInteractiveZone(int gestures, int timeInterval) {
        setGestures(gestures);
        setLongPressTimeInterval(timeInterval);
    }
    public static boolean isSupported(int gestures) {
        return gestures != 0 && (gestures & ~SUPPORTED) == 0;
    }
    public void setGestures(int gestures) {
        if (gestures == 0 || (gestures & ~GESTURE_ALL) != 0) throw new IllegalArgumentException("gestures");
        mask = gestures;
    }
    public void setLongPressTimeInterval(int milliseconds) {
        if (milliseconds <= 0) throw new IllegalArgumentException("interval");
        interval = milliseconds;
    }
    public void setRectangle(int x, int y, int width, int height) {
        if (width < 0 || height < 0) throw new IllegalArgumentException("size");
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        bounded = true;
    }
    boolean contains(int px, int py) {
        return !bounded || (px >= x && py >= y && px < (long)x + width && py < (long)y + height);
    }
    public int getGestures() { return mask; }
    public int getLongPressTimeInterval() { return interval; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
}
