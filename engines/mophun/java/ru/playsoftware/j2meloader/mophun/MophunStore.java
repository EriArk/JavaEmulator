package ru.playsoftware.j2meloader.mophun;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Locale;
import org.json.JSONObject;
import ru.playsoftware.j2meloader.applist.AppItem;
import ru.playsoftware.j2meloader.appsdb.AppDatabase;
import ru.playsoftware.j2meloader.catalog.AdditionalGames;
import ru.playsoftware.j2meloader.catalog.LibraryImporter;
import ru.playsoftware.j2meloader.util.FileUtils;

public final class MophunStore {
    public static final String EXECUTABLE = "game.mpn";

    public static String name(Context context, Uri uri) {
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = context.getContentResolver().query(uri,
                    new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) return cursor.getString(0);
            } catch (RuntimeException ignored) { }
        }
        String path = uri.getLastPathSegment();
        return path == null ? "Mophun game.mpn" : new File(path).getName();
    }

    public static int model(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("[t300]") || lower.contains("[t310]")) return 0;
        if (lower.contains("[t226]") || lower.contains("[t230]")) return 5;
        return 2;
    }

    public static File directory(File root, String id) throws IOException {
        if (id == null || !id.matches("mophun-[0-9a-f]{64}")) throw new IOException("Invalid game identifier");
        File directory = new File(new File(root, "converted"), id);
        if (Files.isSymbolicLink(directory.toPath())) throw new IOException("Linked game directory is not supported");
        return directory;
    }

    public static JSONObject metadata(File directory) throws IOException {
        File file = new File(directory, AdditionalGames.MANIFEST);
        if (file.length() > 32768 || Files.isSymbolicLink(file.toPath())) throw new IOException("Invalid game metadata");
        try {
            JSONObject meta = new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            if (!"mophun".equals(meta.getString("engine")) || meta.getInt("version") != 1
                    || !directory.getName().equals("mophun-" + meta.getString("sha256")))
                throw new IOException("Unrecognized game metadata");
            return meta;
        } catch (org.json.JSONException error) { throw new IOException("Invalid game metadata", error); }
    }

    public static AppItem read(File directory) throws IOException {
        JSONObject meta = metadata(directory);
        File game = new File(directory, EXECUTABLE);
        if (!game.isFile() || Files.isSymbolicLink(game.toPath())) throw new IOException("Mophun executable is missing");
        AppItem item = new AppItem(directory.getName(), meta.optString("title", "Mophun game"), "Unknown", "");
        item.setSourceHash(meta.optString("sha256"));
        item.setSourceKey("mophun:" + item.getSourceHash());
        item.setSourceUri(meta.optString("sourceUri", null));
        if (new File(directory, "icon.png").isFile()) item.setImagePathExt("icon.png");
        if (new File(directory, "cover.png").isFile()) item.setCoverPathExt("cover.png");
        return item;
    }

    public static AppItem install(Context context, File root, Uri uri) throws Exception {
        String name = name(context, uri);
        File stagingRoot = new File(root, ".mophun-import");
        if (!stagingRoot.isDirectory() && !stagingRoot.mkdirs()) throw new IOException("Cannot create import directory");
        File stage = Files.createTempDirectory(stagingRoot.toPath(), "stage-").toFile();
        try {
            File game = new File(stage, EXECUTABLE);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = context.getContentResolver().openInputStream(uri);
                 FileOutputStream output = new FileOutputStream(game)) {
                if (input == null) throw new IOException("Cannot open game");
                byte[] buffer = new byte[8192];
                long size = 0;
                for (int read; (read = input.read(buffer)) != -1;) {
                    size += read;
                    if (size > 16 * 1024 * 1024) throw new IOException("Mophun executable exceeds 16 MB");
                    output.write(buffer, 0, read);
                    digest.update(buffer, 0, read);
                }
                output.getFD().sync();
            }
            MophunSession.executable(game);
            StringBuilder hash = new StringBuilder();
            for (byte value : digest.digest()) hash.append(String.format(Locale.ROOT, "%02x", value & 255));
            String title = name.replaceFirst("(?i)\\.mpn$", "").replace('_', ' ').trim();
            JSONObject meta = new JSONObject().put("version", 1).put("engine", "mophun")
                    .put("sha256", hash.toString()).put("title", title).put("originalName", name)
                    .put("model", MophunCompatibility.model(hash.toString(), model(name))).put("sourceUri", uri.toString());
            Files.write(new File(stage, AdditionalGames.MANIFEST).toPath(), meta.toString(2).getBytes(StandardCharsets.UTF_8));
            File destination = directory(root, "mophun-" + hash);
            synchronized (LibraryImporter.class) {
                try (AppDatabase db = AppDatabase.open(context, root.getAbsolutePath())) {
                    AppItem existing = db.appItemDao().getByPath(destination.getName());
                    if (existing != null) {
                        read(destination);
                        return existing;
                    }
                    if (destination.exists()) {
                        AppItem recovered = read(destination);
                        recovered.setId((int) db.appItemDao().insertImported(recovered));
                        return recovered;
                    }
                    if (!destination.getParentFile().isDirectory() && !destination.getParentFile().mkdirs())
                        throw new IOException("Cannot create game directory");
                    if (!stage.renameTo(destination)) throw new IOException("Cannot publish imported game");
                    try {
                        AppItem item = read(destination);
                        item.setId((int) db.appItemDao().insertImported(item));
                        return item;
                    } catch (Exception failure) {
                        FileUtils.deleteDirectory(destination);
                        throw failure;
                    }
                }
            }
        } finally {
            if (stage.exists()) FileUtils.deleteDirectory(stage);
        }
    }
}
