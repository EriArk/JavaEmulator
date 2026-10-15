package io.github.eriark.abyssme.symbian;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import java.io.IOException;
import java.util.*;
import ru.playsoftware.j2meloader.applist.AppItem;
import ru.playsoftware.j2meloader.catalog.ExternalLibrary;

/** Main-process adapter: index and presentation preferences only, never native engine calls. */
public final class SymbianLauncherLibrary implements ExternalLibrary.Provider {
    @Override public List<AppItem> read(Context context) throws IOException {
        List<AppItem> result = new ArrayList<>();
        SharedPreferences preferences = context.getSharedPreferences("symbian-library-presentation", Context.MODE_PRIVATE);
        for (SymbianLibraryIndex.Entry entry : new SymbianLibraryIndex(context).read())
            result.add(new Item(entry, preferences));
        return result;
    }
    private static synchronized int stableId(SharedPreferences preferences, String key) {
        int id = preferences.getInt(key + ".id", 0);
        if (id < -1) return id;
        int next = preferences.getInt("nextId", -2);
        if (next >= -1 || next == Integer.MIN_VALUE) throw new IllegalStateException("Symbian library IDs exhausted");
        if (!preferences.edit().putInt(key + ".id", next).putInt("nextId", next - 1).commit())
            throw new IllegalStateException("Cannot save library identity");
        return next;
    }
    private static final class Item extends ExternalLibrary.Item {
        private final SymbianLibraryIndex.Entry entry;
        private final SharedPreferences preferences;
        Item(SymbianLibraryIndex.Entry entry, SharedPreferences preferences) {
            super(entry.key(), entry.title, entry.platform, entry.phone);
            this.entry = entry; this.preferences = preferences;
            setId(stableId(preferences, entry.key()));
            setFavorite(preferences.getBoolean(entry.key() + ".favorite", false));
            setLastPlayedAt(preferences.getLong(entry.key() + ".lastPlayed", 0));
            setPlayCount(preferences.getInt(entry.key() + ".playCount", 0));
        }
        @Override public String getImagePathExt() { return entry.icon.isFile() ? entry.icon.getAbsolutePath() : null; }
        @Override public boolean supportsUninstallReview() { return true; }
        @Override public void uninstall(Activity activity) {
            activity.startActivity(new Intent(activity,SymbianActivity.class)
                    .putExtra(SymbianLibraryIndex.SYSTEM_EXTRA,entry.system)
                    .putExtra(SymbianLibraryIndex.UID_EXTRA,entry.uid)
                    .putExtra(SymbianLibraryIndex.REMOVE_EXTRA,true));
        }
        @Override public String getCoverPathExt() { return entry.cover.isFile() ? entry.cover.getAbsolutePath() : null; }
        @Override public void favorite() {
            setFavorite(!isFavorite());
            preferences.edit().putBoolean(entry.key() + ".favorite", isFavorite()).apply();
        }
        @Override public void launch(Activity activity, boolean settings) {
            Intent intent = new Intent(activity, SymbianActivity.class).putExtra(SymbianLibraryIndex.SYSTEM_EXTRA, entry.system);
            if (!settings) {
                intent.putExtra(SymbianLibraryIndex.UID_EXTRA, entry.uid);
                setLastPlayedAt(System.currentTimeMillis()); setPlayCount(getPlayCount() + 1);
                preferences.edit().putLong(entry.key() + ".lastPlayed", getLastPlayedAt())
                        .putInt(entry.key() + ".playCount", getPlayCount()).apply();
            }
            activity.startActivity(intent);
        }
    }
}
