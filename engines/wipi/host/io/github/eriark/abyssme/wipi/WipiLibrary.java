/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.wipi;

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

public final class WipiLibrary implements ExternalLibrary.PackageProvider {
    private static File root(Context c) { return new File(StoragePaths.library(c), "wipi/packages"); }

    @Override public void install(Context context, Uri uri) throws IOException {
        WipiPackage game;
        try (InputStream input = context.getContentResolver().openInputStream(uri)) { game = WipiPackage.read(input); }
        File root = root(context);
        File dir = new File(root, game.id);
        if (new File(dir, "game.zip").isFile()) return;
        Files.createDirectories(root.toPath());
        String title = uri.getLastPathSegment();
        if ("content".equals(uri.getScheme())) try (Cursor c = context.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) title = c.getString(0);
        }
        Properties info = new Properties();
        info.setProperty("title", title == null ? game.carrier : title.replaceFirst("(?i)\\.zip$", ""));
        info.setProperty("carrier", game.carrier);
        File tmp = Files.createTempDirectory(root.toPath(), ".import-").toFile();
        try {
            Files.write(new File(tmp, "game.zip").toPath(), game.bytes);
            store(tmp, info);
            if (!tmp.renameTo(dir) && !new File(dir, "game.zip").isFile())
                throw new IOException("Cannot install carrier package");
        } finally {
            if (tmp.exists()) {
                for (String f : new String[]{"game.zip","card.properties"}) new File(tmp,f).delete();
                tmp.delete();
            }
        }
    }
    private static Properties load(File dir) throws IOException {
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
            if (dir.getName().matches("[a-f0-9]{64}") && new File(dir,"game.zip").isFile())
                games.add(new Card(dir, load(dir), stableId(c,dir.getName())));
        }
        return games;
    }
    private static synchronized int stableId(Context c, String hash) {
        android.content.SharedPreferences ids = c.getSharedPreferences("wipi-card-ids", Context.MODE_PRIVATE);
        if (ids.contains(hash)) return ids.getInt(hash,0);
        int next = ids.getInt("next",Integer.MIN_VALUE + 1);
        ids.edit().putInt(hash,next).putInt("next",next+1).apply();
        return next;
    }

    private static final class Card extends ExternalLibrary.Item {
        private final File dir;
        Card(File dir, Properties p, int id) {
            super("wipi/"+dir.getName(), p.getProperty("title"), "SKT".equals(p.getProperty("carrier")) ? "SKVM" : "WIPI", p.getProperty("carrier"));
            this.dir=dir; setId(id);
            setLastPlayedAt(Long.parseLong(p.getProperty("last","0")));
            setPlayCount(Integer.parseInt(p.getProperty("count","0")));
            setFavorite(Boolean.parseBoolean(p.getProperty("favorite","false")));
        }
        @Override public int platformId() { return 5; }
        @Override public void launch(Activity activity, boolean settings) {
            if (!settings) {
                try {
                    Properties p=load(dir);
                    p.setProperty("last",Long.toString(System.currentTimeMillis()));
                    p.setProperty("count",Integer.toString(getPlayCount()+1));
                    store(dir,p);
                } catch(IOException e) { android.util.Log.w("WipiLibrary","Cannot save play history",e); }
            }
            activity.startActivity(new Intent(activity,WipiGameActivity.class)
                    .putExtra("installed",dir.getName()).putExtra("title",getTitle()));
        }
        @Override public void favorite() {
            try {
                Properties p=load(dir); boolean favorite=!isFavorite();
                p.setProperty("favorite",Boolean.toString(favorite)); store(dir,p); setFavorite(favorite);
            } catch(IOException e) { android.util.Log.w("WipiLibrary","Cannot save favorite",e); }
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
            File config=new File(Config.getConfigsDir(),"wipi-"+dir.getName());
            Files.createDirectories(config.toPath());
            ProfileModel profile=ProfilesManager.loadConfig(config);
            return profile!=null?profile:new ProfileModel(config);
        }
        @Override public void removePackage() throws IOException {
            Files.delete(new File(dir,"game.zip").toPath());
            File[] files=dir.listFiles();
            if (files!=null) for (File f:files) Files.delete(f.toPath());
            Files.delete(dir.toPath());
        }
    }
}
