package ru.playsoftware.j2meloader.catalog;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.*;
import ru.playsoftware.j2meloader.applist.AppItem;
import ru.playsoftware.j2meloader.R;

/** Portable data only: never restore databases, DEX files, or filesystem permissions. */
public final class LibraryArchive {
    public static final String MANIFEST = "abyssme-backup.json";
    private static final long MAX_BYTES = 2L * 1024 * 1024 * 1024;
    private static final int MAX_FILES = 50000;
    private final AtomicBoolean cancelled;
    private long bytes;
    private int files;

    public LibraryArchive(AtomicBoolean cancelled) { this.cancelled = cancelled; }

    interface Destination { OutputStream open() throws IOException; }

    /** Do all source validation before opening (and potentially truncating) a provider document. */
    int export(File root, List<AppItem> games, File cache, Destination destination) throws IOException {
        File staged = File.createTempFile("library-export-", ".zip", cache);
        try {
            int count;
            try (OutputStream output = new FileOutputStream(staged)) {
                count = write(root, games, output);
            }
            check();
            try (InputStream input = new FileInputStream(staged);
                 OutputStream output = destination.open()) {
                if (output == null) throw LibraryTransferFailure.of(R.string.library_transfer_storage_failure,"Cannot write backup");
                byte[] buffer = new byte[65536];
                int n;
                while ((n = input.read(buffer)) != -1) {
                    check();
                    output.write(buffer, 0, n);
                }
                output.flush();
            }
            return count;
        } finally {
            if (!staged.delete()) staged.deleteOnExit();
        }
    }

    public int write(File root, List<AppItem> games, OutputStream output) throws IOException {
        bytes = 0; files = 0;
        JsonObject manifest = new JsonObject(), hashes = new JsonObject(), catalog = new JsonObject();
        manifest.addProperty("version", 1);
        manifest.addProperty("id", UUID.randomUUID().toString());
        manifest.add("files", hashes); manifest.add("games", catalog);
        int count = 0;
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(output))) {
            for (AppItem game : games) {
                check();
                String id = game.getPath();
                safeName(id);
                File app = new File(root, "converted/" + id);
                if (!new File(app, "res.jar").isFile() && !new File(app, AdditionalGames.MANIFEST).isFile())
                    throw LibraryTransferFailure.of(R.string.library_transfer_launch_once,"Launch " + game.getTitle() + " once before backing up the library",game.getTitle());
                JsonObject info = new JsonObject();
                info.addProperty("title", game.getTitle());
                info.addProperty("favorite", game.isFavorite());
                info.addProperty("playCount", game.getPlayCount());
                info.addProperty("lastPlayedAt", game.getLastPlayedAt());
                catalog.add(id, info);
                for (String dir : new String[]{"converted", "data", "configs"})
                    pack(root, new File(root, dir + "/" + id), zip, hashes, 0);
                count++;
            }
            if (count == 0) throw LibraryTransferFailure.of(R.string.library_transfer_no_games,"No installed games to back up");
            pack(root, new File(root, "fs"), zip, hashes, 0);
            ru.playsoftware.j2meloader.config.SharedProfiles.read(root); // Validate before publishing the archive.
            pack(root, new File(root, ru.playsoftware.j2meloader.config.SharedProfiles.FILE), zip, hashes, 0);
            ru.playsoftware.j2meloader.config.SharedProfiles.GRAPHICS.read(root);
            pack(root, new File(root, ru.playsoftware.j2meloader.config.SharedProfiles.GRAPHICS_FILE), zip, hashes, 0);
            byte[] metadata = new Gson().toJson(manifest).getBytes(StandardCharsets.UTF_8);
            if (metadata.length > 16 * 1024 * 1024 || ++files > MAX_FILES) throw LibraryTransferFailure.of(R.string.library_transfer_import_limit,"Backup metadata too large");
            limit(metadata.length);
            zip.putNextEntry(new ZipEntry(MANIFEST));
            zip.write(metadata);
            zip.closeEntry();
        }
        return count;
    }

    private void pack(File root, File file, ZipOutputStream zip, JsonObject hashes, int depth) throws IOException {
        check();
        if (!file.exists()) return;
        if (depth > 24 || Files.isSymbolicLink(file.toPath())) throw LibraryTransferFailure.of(R.string.library_transfer_unsafe_path,"Unsupported backup path");
        safeName(file.getName());
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) throw LibraryTransferFailure.of(R.string.library_transfer_source_access,"Cannot read " + file.getName());
            for (File child : children) pack(root, child, zip, hashes, depth + 1);
            return;
        }
        String path = root.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/');
        if (path.startsWith("converted/") && !Arrays.asList("res.jar", "converted.dex.conf",
                AdditionalGames.MANIFEST, "game.mpn", SourceHints.FILE).contains(file.getName())
                && !Arrays.asList(ru.playsoftware.j2meloader.applist.GameArtwork.FILES).contains(file.getName())) return;
        if (++files > MAX_FILES) throw LibraryTransferFailure.of(R.string.library_transfer_import_limit,"Too many backup files");
        long length = file.length(), modified = file.lastModified(), written = 0;
        java.security.MessageDigest digest = digest();
        zip.putNextEntry(new ZipEntry(path));
        byte[] portable = path.startsWith("configs/") && file.getName().equals("config.json")
                ? ru.playsoftware.j2meloader.config.SharedProfiles.portableConfig(file) : null;
        if(portable!=null)length=portable.length;
        try (InputStream input = portable==null ? new FileInputStream(file) : new ByteArrayInputStream(portable)) {
            byte[] buffer = new byte[65536]; int n;
            while ((n = input.read(buffer)) != -1) {
                check(); limit(n); written += n; digest.update(buffer, 0, n); zip.write(buffer, 0, n);
            }
        }
        zip.closeEntry();
        if (length != written || portable==null && length != file.length() || modified != file.lastModified())
            throw LibraryTransferFailure.of(R.string.library_transfer_backup_changed,"Game data changed during backup. Exit the game and retry.");
        hashes.addProperty(path, hex(digest.digest()));
    }

    /** Extraction is private staging; nothing is installed until all hashes have been checked. */
    public File unpack(InputStream input, File cache) throws IOException {
        bytes = 0; files = 0;
        File stage = Files.createTempDirectory(cache.toPath(), "library-backup-").toFile();
        boolean complete = false;
        Map<String, String> hashes = new HashMap<>();
        Set<String> names = new HashSet<>();
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(input))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                check();
                String path = entry.getName();
                String[] parts = path.split("/", -1);
                if (parts.length > 26) throw LibraryTransferFailure.of(R.string.library_transfer_import_limit,"Backup folders are too deep");
                for (String part : parts) safeName(part);
                if (!names.add(path) || ++files > MAX_FILES) throw LibraryTransferFailure.of(R.string.library_transfer_source_invalid,"Duplicate or excessive backup entries");
                if (!path.equals(MANIFEST) && !path.equals(ru.playsoftware.j2meloader.config.SharedProfiles.FILE) && !path.equals(ru.playsoftware.j2meloader.config.SharedProfiles.GRAPHICS_FILE) && !(parts.length >= 3 && Arrays.asList("converted", "data", "configs").contains(parts[0]))
                        && !(parts.length >= 2 && parts[0].equals("fs")))
                    throw LibraryTransferFailure.of(R.string.library_transfer_source_invalid,"Unexpected backup file: " + path);
                if (entry.isDirectory()) throw LibraryTransferFailure.of(R.string.library_transfer_source_invalid,"Unexpected directory entry");
                File target = new File(stage, path);
                if (!target.getParentFile().isDirectory() && !target.getParentFile().mkdirs()) throw LibraryTransferFailure.of(R.string.library_transfer_storage_failure,"Cannot unpack backup");
                java.security.MessageDigest digest = digest();
                try (FileOutputStream out = new FileOutputStream(target)) {
                    byte[] buffer = new byte[65536]; int n; long size = 0;
                    while ((n = zip.read(buffer)) != -1) {
                        check(); limit(n); size += n;
                        if (path.equals(MANIFEST) && size > 16 * 1024 * 1024) throw LibraryTransferFailure.of(R.string.library_transfer_import_limit,"Backup metadata too large");
                        digest.update(buffer, 0, n); out.write(buffer, 0, n);
                    }
                }
                if (!path.equals(MANIFEST)) hashes.put(path, hex(digest.digest()));
            }
            JsonObject manifest = readManifest(stage);
            JsonObject expected = manifest.getAsJsonObject("files");
            if (expected.size() != hashes.size()) throw LibraryTransferFailure.of(R.string.library_transfer_source_invalid,"Backup is incomplete");
            for (Map.Entry<String, String> file : hashes.entrySet())
                if (!expected.has(file.getKey()) || !file.getValue().equals(expected.get(file.getKey()).getAsString()))
                    throw LibraryTransferFailure.of(R.string.library_transfer_source_invalid,"Backup checksum mismatch: " + file.getKey());
            complete = true;
            return stage;
        } catch (RuntimeException e) {
            if (e instanceof CancellationException) throw e;
            throw LibraryTransferFailure.caused(R.string.library_transfer_source_invalid,"Invalid AbyssME backup", e);
        } finally { if (!complete) remove(stage); }
    }

    static JsonObject readManifest(File stage) throws IOException {
        try (Reader reader = new FileReader(new File(stage, MANIFEST))) {
            JsonObject manifest = JsonParser.parseReader(reader).getAsJsonObject();
            if (manifest.get("version").getAsInt() != 1) throw LibraryTransferFailure.of(R.string.library_transfer_backup_version,"Unsupported backup version");
            UUID.fromString(manifest.get("id").getAsString());
            return manifest;
        } catch (RuntimeException e) { throw LibraryTransferFailure.caused(R.string.library_transfer_source_invalid,"Invalid backup metadata", e); }
    }

    static void safeName(String name) throws IOException {
        if (name == null || name.isEmpty() || name.equals(".") || name.equals("..") || name.contains("/")
                || name.contains("\\") || name.contains(":") || name.indexOf('\0') >= 0) throw LibraryTransferFailure.of(R.string.library_transfer_unsafe_path,"Unsafe backup path");
    }

    static void remove(File file) throws IOException {
        if (Files.isSymbolicLink(file.toPath())) throw LibraryTransferFailure.of(R.string.library_transfer_unsafe_path,"Unexpected link in backup staging");
        File[] children = file.listFiles();
        if (children != null) for (File child : children) remove(child);
        if (file.exists() && !file.delete()) throw LibraryTransferFailure.of(R.string.library_transfer_cleanup,"Cannot remove backup staging");
    }
    private void check() { if (cancelled.get() || Thread.currentThread().isInterrupted()) throw new CancellationException("Cancelled"); }
    private void limit(int n) throws IOException { bytes += n; if (bytes > MAX_BYTES) throw LibraryTransferFailure.of(R.string.library_transfer_import_limit,"Backup exceeds 2 GiB"); }
    private static java.security.MessageDigest digest() { try { return java.security.MessageDigest.getInstance("SHA-256"); } catch (Exception e) { throw new AssertionError(e); } }
    private static String hex(byte[] bytes) { StringBuilder out = new StringBuilder(); for (byte b : bytes) out.append(String.format(Locale.ROOT, "%02x", b & 255)); return out.toString(); }
}
