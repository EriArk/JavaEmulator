package ru.playsoftware.j2meloader.applist;

import android.content.SharedPreferences;
import ru.playsoftware.j2meloader.BuildConfig;

/** Versioned library preferences. Never rewrites game identities or engine settings. */
final class LibraryNavigation {
    private final SharedPreferences prefs;
    LibraryNavigation(SharedPreferences prefs) {
        this.prefs = prefs;
        if (!prefs.getBoolean("libraryTabsMigrated", false)) {
            int platform = platform(prefs.getInt("libraryPlatform", 1));
            SharedPreferences.Editor edit = prefs.edit();
            for (int p = 1; p <= 4; p++) {
                edit.putInt(key(p, "view"), bounded(prefs.getInt("pref_library_view_mode", 0), 2));
                edit.putInt(key(p, "category"), bounded(prefs.getInt("libraryCategory", 0), 2));
            }
            edit.putString(key(platform, "selection"), prefs.getString("librarySelection", null));
            edit.putInt("libraryPlatform", platform).putBoolean("libraryTabsMigrated", true).apply();
        }
    }
    static int platform(int value) { return value >= 1 && value < LibraryQuery.PLATFORMS.length ? value : 1; }
    static boolean available(int id) {
        return id == 1 || id == 2 || ((id == 3 || id == 4) && BuildConfig.SYMBIAN_EXPERIMENT)
                || (id == 5 && BuildConfig.WIPI_PROBE) || ((id == 6 || id == 7) && BuildConfig.DINGOO_PROBE)
                || (id == 8 && BuildConfig.PIECE_PROBE) || (id == 9 && BuildConfig.GP32_PROBE)
                || (id == 10 && BuildConfig.SUPERVISION_PROBE)
                || (id == 11 && BuildConfig.MEGADUCK_PROBE)
                || (id == 12 && BuildConfig.CYBIKO_PROBE)
                || (id == 13 && BuildConfig.FLASH_PROBE);
    }

    static int next(int value, int direction) {
        int id = platform(value);
        int step = direction < 0 ? -1 : 1;
        do {
            id = 1 + Math.floorMod(id - 1 + step, LibraryQuery.PLATFORMS.length - 1);
        } while (!available(id));
        return id;
    }
    private static int bounded(int value, int max) { return Math.max(0, Math.min(max, value)); }
    private static String key(int platform, String field) { return "libraryTab." + platform + "." + field; }
    int active() { return platform(prefs.getInt("libraryPlatform", 1)); }
    int view(int p) { return bounded(prefs.getInt(key(p, "view"), 0), 2); }
    int category(int p) { return bounded(prefs.getInt(key(p, "category"), 0), 2); }
    int generation(int p) { return bounded(prefs.getInt(key(p, "generation"), 0), 2); }
    String query(int p) { return prefs.getString(key(p, "query"), ""); }
    String selection(int p) { return prefs.getString(key(p, "selection"), null); }
    void save(int p, int view, int category, int generation, String query, String selection) {
        prefs.edit().putInt("libraryPlatform", p).putInt(key(p, "view"), view)
                .putInt(key(p, "category"), category).putInt(key(p, "generation"), generation)
                .putString(key(p, "query"), query).putString(key(p, "selection"), selection).apply();
    }
}
