/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.pocketpc;

import android.content.Context;
import com.pockethle.app.NativeBridge;
import io.github.eriark.abyssme.storage.StoragePaths;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import org.json.*;
import ru.playsoftware.j2meloader.catalog.PocketPcFormat;
import ru.playsoftware.j2meloader.catalog.SourceIdentity;
import ru.playsoftware.j2meloader.handheld.ManagedGameLibrary;

public class PocketPcLibrary extends ManagedGameLibrary {
    private final String engine;
    public PocketPcLibrary() { this("pocketpc", PocketPcActivity.class); }
    PocketPcLibrary(String engine, Class<? extends android.app.Activity> activity) {
        super(engine, activity);
        this.engine = engine;
    }

    static List<String> programs(File directory) throws IOException {
        Path base = directory.toPath(), extracted = base.resolve("extracted");
        List<String> result = new ArrayList<>();
        try (Stream<Path> files = Files.walk(extracted)) {
            for (Iterator<Path> it = files.iterator(); it.hasNext();) {
                Path p = it.next();
                if (!p.toString().toLowerCase(Locale.ROOT).endsWith(".exe") || !Files.isRegularFile(p)) continue;
                try (InputStream in = new BufferedInputStream(Files.newInputStream(p))) {
                    if (PocketPcFormat.armExecutable(in)) result.add(base.relativize(p).toString());
                }
            }
        }
        result.sort(String.CASE_INSENSITIVE_ORDER);
        return result;
    }

    String importSource(File runtime, File input, String ext) throws IOException {
        if (ext.equals(".cab")) return NativeBridge.importCab(runtime.getPath(), input.getPath());
        if (ext.equals(".zip")) return NativeBridge.importZip(runtime.getPath(), input.getPath());
        return NativeBridge.importExe(runtime.getPath(), input.getPath());
    }

    @Override protected String inspect(Context c, File content, String name, Properties info) throws IOException {
        String lower = name.toLowerCase(Locale.ROOT);
        String ext = lower.endsWith(".cab") ? ".cab" : lower.endsWith(".zip") ? ".zip" : ".exe";
        File dir = content.getParentFile();
        File input = new File(new File(dir, "source"), new File(name.replace('\\','/')).getName());
        Files.createDirectory(input.getParentFile().toPath());
        Files.move(content.toPath(), input.toPath());
        File runtime = new File(dir, "installed");
        try {
            JSONObject entry = new JSONObject(importSource(runtime, input, ext));
            if (entry.has("error")) throw new IOException(entry.getString("error"));
            String id = entry.getString("id");
            if (!id.matches("[a-zA-Z0-9_-]+")) throw new IOException("Invalid Pocket PC installation ID");
            File game = new File(runtime, "games/" + id);
            List<String> choices = programs(game);
            if (choices.isEmpty()) throw new IOException("Package needs a native Windows CE ARM executable");
            info.setProperty("guest", id);
            String title = entry.getString("display_name");
            if (ext.equals(".zip") && "pocketpc".equals(engine)) {
                for (String executable : choices) {
                    String basename = new File(executable).getName();
                    if (basename.substring(0,basename.length()-4).equalsIgnoreCase(title)) {
                        title=input.getName().substring(0,input.getName().length()-4);
                        break;
                    }
                }
            }
            info.setProperty("title", title);
            info.setProperty("file", "game" + ext);
            String iconName = entry.optString("icon", "icon.png");
            File icon = new File(game, new File(iconName).getName());
            if (icon.isFile()) Files.copy(icon.toPath(), new File(dir, "icon.png").toPath(), StandardCopyOption.REPLACE_EXISTING);
            return SourceIdentity.sha256(input);
        } catch (JSONException | LinkageError e) { throw new IOException("Pocket PC import failed", e); }
        finally { Files.move(input.toPath(), content.toPath()); Files.delete(input.getParentFile().toPath()); }
    }

    File runtime(Context c, String hash) { return new File(StoragePaths.library(c), engine + "/data/" + hash); }

    @Override protected void imported(Context c, String hash, Properties info) throws IOException {
        File destination = runtime(c, hash);
        File pending = new File(new File(root(c), hash), "installed");
        if (destination.isDirectory()) {
            if (pending.exists()) removeTree(pending);
            return;
        }
        Files.createDirectories(destination.getParentFile().toPath());
        try { Files.move(pending.toPath(), destination.toPath()); }
        catch (IOException race) { if (!destination.isDirectory()) throw race; }
    }
    String guest(Context c, String hash) throws IOException {
        Properties info = load(new File(root(c),hash));
        String id = info.getProperty("guest","");
        if (!id.matches("[a-zA-Z0-9_-]+")) throw new IOException("Invalid Pocket PC installation");
        return id;
    }
    @Override protected String filename(Properties info) { String file=info.getProperty("file","game.cab"); return file.equals("game.zip") || file.equals("game.exe") ? file : "game.cab"; }
    @Override protected String variant(Properties info) { return "Windows CE ARM"; }
    @Override protected String platformName(Properties info) { return "Pocket PC"; }
    @Override protected int platformId(Properties info) { return 22; }
    @Override protected int firstCardId() { return -1190000000; }
}
