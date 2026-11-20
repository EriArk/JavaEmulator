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
    protected abstract String inspect(File content, String name, Properties info) throws IOException;
    protected abstract String platformName(Properties info);
    protected abstract int platformId(Properties info);
    protected abstract String variant(Properties info);
    protected abstract String filename(Properties info);
    protected abstract int firstCardId();
    protected void configureProfile(ProfileModel profile) {}

    protected File root(Context c) { return new File(StoragePaths.library(c), engine + "/packages"); }
    @Override public void install(Context context, Uri uri) throws IOException {
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
            String id = inspect(content, name, info);
            File dir = new File(root, id);
            File game = new File(dir, filename(info));
            if (game.isFile()) return;
            int dot = name.lastIndexOf('.');
            info.setProperty("title", dot > 0 ? name.substring(0,dot) : name);
            Files.move(content.toPath(), new File(tmp, filename(info)).toPath());
            store(tmp, info);
            if (!tmp.renameTo(dir) && !game.isFile()) throw new IOException("Cannot install game");
        } finally {
            File[] left = tmp.listFiles();
            if (left != null) for (File file : left) file.delete();
            tmp.delete();
        }
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
                if (new File(dir, filename(info)).isFile()) games.add(new Card(dir, info, stableId(c,dir.getName())));
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
            this.dir=dir; setId(id);
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
                    .putExtra("installed",dir.getName()).putExtra("title",getTitle()));
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
        private String artwork(String name) { File f=new File(dir,name); return f.isFile()?f.getPath():null; }
        @Override public String getImagePathExt() { return artwork(GameArtwork.USER_ICON); }
        @Override public String getCoverPathExt() { return artwork(GameArtwork.USER_COVER); }
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
            Files.delete(new File(dir,filename(load(dir))).toPath());
            File[] files=dir.listFiles();
            if (files!=null) for (File f:files) Files.delete(f.toPath());
            Files.delete(dir.toPath());
        }
    }
}
