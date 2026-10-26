package ru.playsoftware.j2meloader.input;

/** Shared placement policy in dp, independent of the input device's screen shape. */
public final class MenuPlacement {
    public enum Position { CENTER, SIDE, BOTTOM }

    private MenuPlacement() { }

    public static Position resolve(float width, float height, boolean controller,
                                   boolean confirmation, boolean preview) {
        if (confirmation) return Position.CENTER;
        int panelWidth = preview ? 300 : 380;
        // Reserve a useful part of the viewport for the game, not just a narrow strip.
        if (width >= height * 1.4f && width >= panelWidth + 240) return Position.SIDE;
        if (!controller && height >= width * 1.4f) return Position.BOTTOM;
        return Position.CENTER;
    }
}
