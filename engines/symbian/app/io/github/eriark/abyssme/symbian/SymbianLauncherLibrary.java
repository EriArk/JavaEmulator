package io.github.eriark.abyssme.symbian;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import java.io.IOException;
import java.io.File;
import java.util.*;
import ru.playsoftware.j2meloader.applist.AppItem;
import ru.playsoftware.j2meloader.applist.GameArtwork;
import ru.playsoftware.j2meloader.catalog.ExternalLibrary;

/** Main-process adapter: index and presentation preferences only, never native engine calls. */
public final class SymbianLauncherLibrary implements ExternalLibrary.Provider {
    @Override public List<AppItem> read(Context context) throws IOException {
        List<AppItem> result = new ArrayList<>();
        SharedPreferences preferences = context.getSharedPreferences("symbian-library-presentation", Context.MODE_PRIVATE);
        for (SymbianLibraryIndex.Entry entry : new SymbianLibraryIndex(context).read())
            result.add(new Item(entry, preferences, context.getFilesDir()));
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
        private final File files;
        Item(SymbianLibraryIndex.Entry entry, SharedPreferences preferences, File files) {
            super(entry.key(), entry.title, entry.platform, entry.phone);
            this.entry = entry; this.preferences = preferences;
            this.files = files;
            setId(stableId(preferences, entry.key()));
            setFavorite(preferences.getBoolean(entry.key() + ".favorite", false));
            setLastPlayedAt(preferences.getLong(entry.key() + ".lastPlayed", 0));
            setPlayCount(preferences.getInt(entry.key() + ".playCount", 0));
        }
        @Override public File getArtworkDirectory() {
            return new File(SymbianLibraryIndex.artworkRoot(files, entry.system), Long.toString(entry.uid));
        }
        private String artwork(boolean icon) {
            File custom = new File(getArtworkDirectory(), icon ? GameArtwork.USER_ICON : GameArtwork.USER_COVER);
            File automatic = icon ? entry.icon : entry.cover;
            return custom.isFile() ? custom.getAbsolutePath() : automatic.isFile() ? automatic.getAbsolutePath() : null;
        }
        @Override public String getImagePathExt() { return artwork(true); }
        @Override public boolean supportsUninstallReview() { return true; }
        @Override public void uninstall(Activity activity) {
            activity.startActivity(new Intent(activity,SymbianActivity.class)
                    .putExtra(SymbianLibraryIndex.SYSTEM_EXTRA,entry.system)
                    .putExtra(SymbianLibraryIndex.UID_EXTRA,entry.uid)
                    .putExtra(SymbianLibraryIndex.REMOVE_EXTRA,true));
        }
        @Override public String getCoverPathExt() { return artwork(false); }
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
