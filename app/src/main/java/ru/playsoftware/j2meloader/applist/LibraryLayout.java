package ru.playsoftware.j2meloader.applist;

/** Library geometry in dp, shared by span selection and card binding. */
public final class LibraryLayout {
    public static final String COVER_PREFERENCE = "library_cover_format";
    public static final int AUTO = 0, WIDE = 1, PORTRAIT = 2;
    public static final String[] COVER_LABELS = {"Automatic", "Wide", "Portrait"};
    public final int columns, artWidth, artHeight, format;

    private LibraryLayout(int columns, int width, int height, int format) {
        this.columns = columns; artWidth = width; artHeight = height; this.format = format;
    }

    public static int format(int value) { return value >= AUTO && value <= PORTRAIT ? value : AUTO; }

    public static boolean compact(float width, float height, float fontScale) {
        return width < 600 || height < 480 || width / Math.max(1, height) < 1.45f
                || width < 800 * Math.max(1, fontScale);
    }

    public static LibraryLayout calculate(int mode, int preference, int width, int height, float fontScale) {
        width = Math.max(1, width); height = Math.max(1, height);
        float font = Math.max(1, fontScale);
        int chosen = format(preference);
        if (chosen == AUTO) chosen = height >= 300 && width < height * 1.55f ? PORTRAIT : WIDE;
        int target = Math.round((mode == AppsListAdapter.MODE_GALLERY
                ? (chosen == PORTRAIT ? 140 : 220) : 144) * Math.min(font, 1.5f));
        int columns = mode == AppsListAdapter.MODE_LIST ? 1 : Math.max(1, width / target);
        int cellArt = Math.max(1, width / columns - 22);
        int maxArt = Math.max(32, height - Math.round(30 + 44 * font));
        if (mode != AppsListAdapter.MODE_GALLERY)
            return new LibraryLayout(columns, cellArt, Math.min(116, maxArt), chosen);
        float ratio = chosen == PORTRAIT ? 2f / 3f : 16f / 9f;
        int artHeight = Math.max(1, Math.min(Math.min(maxArt, chosen == PORTRAIT ? 300 : 180),
                Math.round(cellArt / ratio)));
        return new LibraryLayout(columns, Math.min(cellArt, Math.round(artHeight * ratio)), artHeight, chosen);
    }
}
