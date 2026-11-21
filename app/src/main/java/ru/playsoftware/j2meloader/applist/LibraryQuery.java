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
    public static final String[] PLATFORMS = {"All platforms", "Java ME", "Mophun", "Symbian", "N-Gage", "WIPI / SKVM", "Dingoo A320", "Gemei A330", "Aquaplus P/ECE", "GamePark GP32", "Watara Supervision", "Mega Duck", "Cybiko", "Flash Lite", "Gamate", "GameKing", "Palm OS", "MediaTek MRE", "Palm webOS", "Firefox OS", "KaiOS", "Atari Portfolio"};
    private LibraryQuery() { }
    public static int platform(AppItem item) {
        if (item instanceof ExternalLibrary.Item) {
            return ((ExternalLibrary.Item) item).platformId();
        }
        int indexed=ru.playsoftware.j2meloader.catalog.HandheldFormat.indexedPlatform(item.getPath());
        if(indexed!=0)return indexed;
        return AdditionalGames.isManaged(new File(item.getPathExt())) ? 2 : 1;
    }
    public static List<AppItem> select(List<AppItem> items, int category, int platform, String query) {
        return select(items, category, platform, 0, query);
    }
    public static int generation(AppItem item) {
        if (platform(item) != 4) return 0;
        return item.getAuthor().contains("2.0") ? 2 : 1;
    }
    public static boolean hasBothNgageGenerations(List<AppItem> items) {
        boolean classic = false, modern = false;
        for (AppItem item : items) {
            int generation = generation(item);
            classic |= generation == 1;
            modern |= generation == 2;
        }
        return classic && modern;
    }
    public static List<AppItem> select(List<AppItem> items, int category, int platform, int generation, String query) {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        List<AppItem> result = new ArrayList<>();
        for (AppItem item : items) {
            if (category == AppsListAdapter.CATEGORY_RECENT && item.getLastPlayedAt() <= 0) continue;
            if (category == AppsListAdapter.CATEGORY_FAVORITES && !item.isFavorite()) continue;
            if (platform != 0 && platform(item) != platform) continue;
            if (platform == 4 && generation != 0 && generation(item) != generation) continue;
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
