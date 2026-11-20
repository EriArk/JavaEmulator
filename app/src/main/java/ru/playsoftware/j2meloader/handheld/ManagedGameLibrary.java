/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.handheld;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.AtomicFile;
import io.github.eriark.abyssme.storage.StoragePaths;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import ru.playsoftware.j2meloader.applist.*;
import ru.playsoftware.j2meloader.catalog.ExternalLibrary;
import ru.playsoftware.j2meloader.config.*;

/** Shared storage, artwork and card actions for file-based handheld packages. */
public abstract class ManagedGameLibrary implements ExternalLibrary.PackageProvider {
    private final String engine;
    private final Class<? extends Activity> activityClass;
    protected ManagedGameLibrary(String engine, Class<? extends Activity> activityClass) {
        this.engine = engine;
        this.activityClass = activityClass;
    }
    protected abstract String inspect(android.content.Context context, File content, String name, Properties info) throws IOException;
    protected abstract String platformName(Properties info);
    protected abstract int platformId(Properties info);
    protected abstract String variant(Properties info);
    protected abstract String filename(Properties info);
    protected abstract int firstCardId();
    protected void configureProfile(ProfileModel profile) {}

    protected void extractInstalledArt(File game, File directory) throws IOException {}

    private void backfillArt(File directory, File game) {
        File done = new File(directory, "package-art-v2");
        if (done.isFile()) return;
        try {
            extractInstalledArt(game, directory);
            done.createNewFile();
        } catch (IOException | RuntimeException e) {
            android.util.Log.w("ManagedGameLibrary", "Cannot read package artwork", e);
        }
    }

    protected void imported(Context context, String id, Properties info) throws IOException {}

    protected File root(Context c) { return new File(StoragePaths.library(c), engine + "/packages"); }
    @Override public ExternalLibrary.Item install(Context context, Uri uri) throws IOException {
        String name = uri.getLastPathSegment();
        if ("content".equals(uri.getScheme())) try (Cursor c = context.getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) name = c.getString(0);
        }
        if (name == null) name = "Game";
        File root = root(context);
        Files.createDirectories(root.toPath());
        File tmp = Files.createTempDirectory(root.toPath(), ".import-").toFile();
        try {
            File content = new File(tmp,"import");
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                if (in == null) throw new IOException("Could not open game file");
                Files.copy(in, content.toPath());
            }
            Properties info = new Properties();
            if (name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
                try { GameArtwork.extractArchive(content, tmp, null, false); }
                catch (IOException | IllegalArgumentException e) { android.util.Log.d("ManagedGameLibrary", "Archive artwork unavailable", e); }
            }
            String id = inspect(context, content, name, info);
            String source = ru.playsoftware.j2meloader.catalog.SourceIdentity.key(uri);
            info.setProperty("source",source);
            File dir = new File(root, id);
            File game = new File(dir, filename(info));
            if (game.isFile()) {
                Properties existing = load(dir);
                existing.setProperty("source",source);
                store(dir,existing);
                for (String art : new String[]{"cover.png", "icon.png"}) {
                    File found = new File(tmp, art);
                    if (found.isFile()) Files.move(found.toPath(), new File(dir, art).toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                imported(context, id, existing);
                return new Card(dir,existing,stableId(context,id));
            }
            int dot = name.lastIndexOf('.');
            if (!info.containsKey("title")) info.setProperty("title", dot > 0 ? name.substring(0,dot) : name);
            Files.move(content.toPath(), new File(tmp, filename(info)).toPath());
            store(tmp, info);
            if (!tmp.renameTo(dir) && !game.isFile()) throw new IOException("Cannot install game");
            imported(context, id, info);
            backfillArt(dir, game);
            return new Card(dir,load(dir),stableId(context,id));
        } finally {
            if (tmp.exists()) removeTree(tmp);
        }
    }
    private static void removeTree(File file) throws IOException {
        if (!Files.isSymbolicLink(file.toPath()) && file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) throw new IOException("Cannot read package directory");
            for (File child : children) removeTree(child);
        }
        Files.delete(file.toPath());
    }
    protected static Properties load(File dir) throws IOException {
        Properties p = new Properties();
        try (InputStream in = new AtomicFile(new File(dir,"card.properties")).openRead()) { p.load(in); }
        return p;
    }
    private static void store(File dir, Properties p) throws IOException {
        AtomicFile file = new AtomicFile(new File(dir,"card.properties"));
        FileOutputStream out = file.startWrite();
        try { p.store(out,null); file.finishWrite(out); }
        catch (IOException e) { file.failWrite(out); throw e; }
    }
    @Override public List<AppItem> read(Context c) throws IOException {
        List<AppItem> games = new ArrayList<>();
        File[] dirs = root(c).listFiles();
        if (dirs != null) for (File dir : dirs) {
            if (!dir.getName().matches("[a-f0-9]{64}")) continue;
            try {
                Properties info = load(dir);
                File game = new File(dir, filename(info));
                if (game.isFile()) {
                    backfillArt(dir, game);
                    games.add(new Card(dir, info, stableId(c,dir.getName())));
                }
            } catch (IOException | IllegalArgumentException e) {
                android.util.Log.w("ManagedGameLibrary","Cannot read card " + dir.getName(),e);
            }
        }
        return games;
    }
    private int stableId(Context c, String hash) {
        synchronized (ManagedGameLibrary.class) {
            android.content.SharedPreferences ids = c.getSharedPreferences(engine + "-card-ids", Context.MODE_PRIVATE);
            if (ids.contains(hash)) return ids.getInt(hash,0);
            int next = ids.getInt("next",firstCardId());
            ids.edit().putInt(hash,next).putInt("next",next+1).apply();
            return next;
        }
    }

    private final class Card extends ExternalLibrary.Item {
        private final File dir;
        private final int platform;
        Card(File dir, Properties p, int id) {
            super(engine + "/" + dir.getName(), p.getProperty("title"), platformName(p), variant(p));
            platform = ManagedGameLibrary.this.platformId(p);
            this.dir=dir; setId(id); setSourceKey(p.getProperty("source"));
            setLastPlayedAt(Long.parseLong(p.getProperty("last","0")));
            setPlayCount(Integer.parseInt(p.getProperty("count","0")));
            setFavorite(Boolean.parseBoolean(p.getProperty("favorite","false")));
        }
        @Override public int platformId() { return platform; }
        @Override public void launch(Activity activity, boolean settings) {
            if (!settings) {
                try {
                    Properties p=load(dir);
                    p.setProperty("last",Long.toString(System.currentTimeMillis()));
                    p.setProperty("count",Integer.toString(Integer.parseInt(p.getProperty("count","0")) + 1));
                    store(dir,p);
                } catch(IOException e) { android.util.Log.w("ManagedGameLibrary","Cannot save play history",e); }
            }
            activity.startActivity(new Intent(activity,activityClass)
                    .putExtra("artworkEngine", engine).putExtra("installed",dir.getName()).putExtra("title",getTitle()));
        }
        @Override public void favorite() {
            try {
                Properties p=load(dir); boolean favorite=!isFavorite();
                p.setProperty("favorite",Boolean.toString(favorite)); store(dir,p); setFavorite(favorite);
            } catch(IOException e) { android.util.Log.w("ManagedGameLibrary","Cannot save favorite",e); }
        }
        @Override public void rename(String title) throws IOException {
            Properties p=load(dir); p.setProperty("title",title); store(dir,p); setTitle(title);
        }
        @Override public File getArtworkDirectory() { return dir; }
        private String artwork(String... names) {
            for (String name : names) {
                File f = new File(dir, name);
                if (f.isFile()) return f.getPath();
            }
            return null;
        }
        @Override public String getImagePathExt() { return artwork(GameArtwork.USER_ICON, "icon.png", "cover.png", "preview.png"); }
        @Override public String getCoverPathExt() { return artwork(GameArtwork.USER_COVER, "cover.png", "preview.png"); }
        @Override public boolean supportsGameSettings() { return true; }
        @Override public ProfileModel loadGameSettings() throws IOException {
            File config=new File(Config.getConfigsDir(),engine + "-" + dir.getName());
            Files.createDirectories(config.toPath());
            ProfileModel profile=ProfilesManager.loadConfig(config);
            if (profile == null) profile = new ProfileModel(config);
            configureProfile(profile);
            return profile;
        }
        @Override public void removePackage() throws IOException {
            removeTree(dir);
        }
    }
}
