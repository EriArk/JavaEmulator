/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.input;

/** Axis selection from device metadata, independent of Android for regression tests. */
public final class RightStickAxes {
    // Stable Android MotionEvent axis IDs. Never use trigger/brake/gas axes as a stick.
    public static final int Z = 11, RX = 12, RY = 13, RZ = 14;
    public interface Ranges { Range get(int axis); }
    public static final class Range {
        final float min, max, flat;
        public Range(float min, float max, float flat) {
            this.min = min; this.max = max; this.flat = flat;
        }
        boolean centered() { return Float.isFinite(min) && Float.isFinite(max) && min < 0 && max > 0; }
        public float value(float raw) {
            if (!centered() || !Float.isFinite(raw) || Math.abs(raw) <= Math.max(0, flat)) return 0;
            return Math.max(-1, Math.min(1, raw / (raw < 0 ? -min : max)));
        }
    }
    public final int x, y;
    private RightStickAxes(int x, int y) { this.x = x; this.y = y; }
    private static final RightStickAxes STANDARD = new RightStickAxes(Z, RZ);
    private static final RightStickAxes ALTERNATE = new RightStickAxes(RX, RY);
    private static final RightStickAxes NONE = new RightStickAxes(-1, -1);
    public static RightStickAxes select(Ranges ranges) {
        if (pair(ranges, Z, RZ)) return STANDARD;
        if (pair(ranges, RX, RY)) return ALTERNATE;
        return NONE;
    }
    private static boolean pair(Ranges ranges, int x, int y) {
        Range a = ranges.get(x), b = ranges.get(y);
        return a != null && b != null && a.centered() && b.centered();
    }
}
