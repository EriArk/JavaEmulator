package ru.playsoftware.j2meloader.applist;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;
import java.util.HashMap;
import java.util.Map;

/** Isolate remembered library navigation from fixture catalogs. */
public final class LibraryPreferencesSnapshot implements AutoCloseable {
    private final SharedPreferences preferences;
    private final Map<String, ?> before;
    public LibraryPreferencesSnapshot(Context context) {
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        before = new HashMap<>(preferences.getAll());
        SharedPreferences.Editor edit = preferences.edit();
        for (String key : before.keySet()) if (owned(key)) edit.remove(key);
        edit.commit();
    }
    private boolean owned(String key) { return key.startsWith("library") || key.equals("pref_library_view_mode"); }
    @Override public void close() {
        SharedPreferences.Editor edit = preferences.edit();
        for (String key : preferences.getAll().keySet()) if (owned(key)) edit.remove(key);
        for (Map.Entry<String, ?> entry : before.entrySet()) if (owned(entry.getKey())) {
            Object value = entry.getValue();
            if (value instanceof String) edit.putString(entry.getKey(), (String) value);
            else if (value instanceof Integer) edit.putInt(entry.getKey(), (Integer) value);
            else if (value instanceof Boolean) edit.putBoolean(entry.getKey(), (Boolean) value);
            else if (value instanceof Long) edit.putLong(entry.getKey(), (Long) value);
        }
        edit.commit();
    }
}
