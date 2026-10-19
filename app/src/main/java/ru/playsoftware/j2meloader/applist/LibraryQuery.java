package ru.playsoftware.j2meloader.applist;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import ru.playsoftware.j2meloader.catalog.AdditionalGames;
import ru.playsoftware.j2meloader.catalog.ExternalLibrary;

/** Shared collection rules. Platform inspection runs on the adapter's filter worker. */
public final class LibraryQuery {
    public static final String[] PLATFORMS = {"All platforms", "Java ME", "Mophun", "Symbian", "N-Gage"};
    private LibraryQuery() { }
    public static int platform(AppItem item) {
        if (item instanceof ExternalLibrary.Item) {
            String label = item.getAuthor();
            return label != null && label.toLowerCase(Locale.ROOT).contains("n-gage") ? 4 : 3;
        }
        return AdditionalGames.isManaged(new File(item.getPathExt())) ? 2 : 1;
    }
    public static List<AppItem> select(List<AppItem> items, int category, int platform, String query) {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        List<AppItem> result = new ArrayList<>();
        for (AppItem item : items) {
            if (category == AppsListAdapter.CATEGORY_RECENT && item.getLastPlayedAt() <= 0) continue;
            if (category == AppsListAdapter.CATEGORY_FAVORITES && !item.isFavorite()) continue;
            if (platform != 0 && platform(item) != platform) continue;
            String title = item.getTitle() == null ? "" : item.getTitle();
            String author = item.getAuthor() == null ? "" : item.getAuthor();
            if (!needle.isEmpty() && !title.toLowerCase(Locale.ROOT).contains(needle)
                    && !author.toLowerCase(Locale.ROOT).contains(needle)) continue;
            result.add(item);
        }
        if (category == AppsListAdapter.CATEGORY_RECENT)
            result.sort(Comparator.comparingLong(AppItem::getLastPlayedAt).reversed()
                    .thenComparing(AppItem::getPath));
        return result;
    }
    public static AppItem lastPlayed(List<AppItem> items) {
        AppItem latest = null;
        for (AppItem item : items) if (item.getLastPlayedAt() > 0
                && (latest == null || item.getLastPlayedAt() > latest.getLastPlayedAt())) latest = item;
        return latest;
    }
}
