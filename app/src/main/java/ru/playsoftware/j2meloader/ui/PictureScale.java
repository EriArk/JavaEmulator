/* Copyright 2026. GPL-3.0-only. */
package ru.playsoftware.j2meloader.ui;

/** Presentation geometry only; never changes the guest resolution or sampling filter. */
public final class PictureScale {
    public static final int INTEGER = 3;
    private PictureScale() { }

    /** Whole source-pixel multiples when possible; Fit downscaling instead of clipping below 1x. */
    public static float integerFit(int width, int height, float availableWidth, float availableHeight) {
        if (width <= 0 || height <= 0 || !Float.isFinite(availableWidth)
                || !Float.isFinite(availableHeight) || availableWidth <= 0 || availableHeight <= 0) return 0;
        float fit = Math.min((float)Math.floor(availableWidth) / width,
                (float)Math.floor(availableHeight) / height);
        return fit >= 1 ? (float)Math.floor(fit) : fit;
    }

    public static float center(float value, int extent, boolean integer) {
        return integer ? (float)Math.floor(value - extent / 2f) + extent / 2f : value;
    }
}
