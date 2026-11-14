/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.input;

/** A stable touch-only identity with independent orientation coordinates. */
public final class ExtraTouchButton {
    public static final int LIMIT = 12;
    @com.google.gson.annotations.SerializedName("id")
    public String id = java.util.UUID.randomUUID().toString();
    @com.google.gson.annotations.SerializedName("label")
    public String label;
    @com.google.gson.annotations.SerializedName("code")
    public int code = 49;
    @com.google.gson.annotations.SerializedName("opacity")
    public int opacity = 100;
    @com.google.gson.annotations.SerializedName("portrait")
    public TouchPlacement portrait = placement(.35f, .5f);
    @com.google.gson.annotations.SerializedName("landscape")
    public TouchPlacement landscape = placement(.35f, .5f);
    private static TouchPlacement placement(float x, float y) {
        TouchPlacement p = new TouchPlacement(); p.x = x; p.y = y; return p;
    }
    public TouchPlacement position(boolean wide) {
        if (wide) { if (landscape == null) landscape = placement(.35f,.5f); return landscape; }
        if (portrait == null) portrait = placement(.35f,.5f); return portrait;
    }
    public String title() {
        return label == null || label.trim().isEmpty() ? TouchMappings.glyph(code)
                : label.trim().substring(0, Math.min(8, label.trim().length()));
    }
    public boolean valid() {
        return id != null && id.matches("[a-zA-Z0-9-]{1,64}") && TouchMappings.validCode(code);
    }
}
