package ru.playsoftware.j2meloader.applist;

import android.content.SharedPreferences;

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
    static int platform(int value) { return value >= 1 && value <= 4 ? value : 1; }
    static int next(int value, int direction) { return 1 + Math.floorMod(platform(value) - 1 + direction, 4); }
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
