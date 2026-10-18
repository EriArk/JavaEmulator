package ru.playsoftware.j2meloader.input;

/** Normalized placement within a control lane, independent of device resolution. */
public final class TouchPlacement {
    @com.google.gson.annotations.SerializedName("scale")
    public float scale = 1;
    @com.google.gson.annotations.SerializedName("x")
    public float x = .5f;
    @com.google.gson.annotations.SerializedName("y")
    public float y = 1;

    public static float bounded(float value, float min, float max, float fallback) {
        return Float.isNaN(value) || Float.isInfinite(value) ? fallback : Math.max(min, Math.min(max, value));
    }
    public float scale() { return bounded(scale, .7f, 1.2f, 1); }
    public float x() { return bounded(x, 0, 1, .5f); }
    public float y() { return bounded(y, 0, 1, 1); }
}
