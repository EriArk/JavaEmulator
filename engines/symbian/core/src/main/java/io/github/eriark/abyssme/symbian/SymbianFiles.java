package io.github.eriark.abyssme.symbian;

import android.content.Context;
import android.net.Uri;
import android.database.Cursor;
import android.provider.OpenableColumns;
import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class SymbianFiles {
    private SymbianFiles() { }
    public static File root(Context context) { return new File(context.getFilesDir(), "symbian"); }
    public static void prepareAssets(Context context) throws IOException {
        prepareAssets(context, root(context));
    }
    public static void prepareAssets(Context context, File root) throws IOException {
        for (String name : new String[]{"patch", "resources", "compat"}) {
            copyAssets(context, name, new File(root, name));
        }
        Files.createDirectories(new File(root, "scripts").toPath());
        // Regional dumps can lack Latin text faces. Use the Android device's font
        // as an engine fallback, never replace fonts inside the guest's ROM/drives.
        File latin = new File("/system/fonts/Roboto-Regular.ttf");
        if (latin.isFile()) retainFallbackFont(latin, new File(root, "data/fonts/abyss-android-regular.ttf"));
    }
    static void retainFallbackFont(File source, File target) throws IOException {
        if (target.isFile()) return;
        File folder = target.getParentFile();
        if (!folder.isDirectory() && !folder.mkdirs()) throw new SystemFailure(SystemFailure.Code.IMPORT_CANNOT_CREATE_FONT_DIRECTORY);
        File pending = File.createTempFile("font-", ".pending", folder);
        try {
            try (InputStream input = new FileInputStream(source); FileOutputStream output = new FileOutputStream(pending)) {
                byte[] buffer = new byte[65536];
                long total = 0;
                for (int n; (n = input.read(buffer)) != -1;) {
                    total += n;
                    if (total > 32L * 1024 * 1024) throw new SystemFailure(SystemFailure.Code.IMPORT_FALLBACK_FONT_EXCEEDS_32_MB);
                    output.write(buffer, 0, n);
                }
                if (total == 0) throw new SystemFailure(SystemFailure.Code.IMPORT_EMPTY_FALLBACK_FONT);
                output.getFD().sync();
            }
            Files.move(pending.toPath(), target.toPath());
        } finally { Files.deleteIfExists(pending.toPath()); }
    }
    private static void copyAssets(Context context, String asset, File target) throws IOException {
        String[] entries = context.getAssets().list(asset);
        if (entries != null && entries.length > 0) {
            if (!target.isDirectory() && !target.mkdirs()) throw new SystemFailure(SystemFailure.Code.IMPORT_CANNOT_CREATE_ENGINE_DIRECTORY);
            for (String child : entries) copyAssets(context, asset + "/" + child, new File(target, child));
        } else if (!target.exists()) {
            try (InputStream input = context.getAssets().open(asset)) {
                Files.copy(input, target.toPath());
            }
        }
    }
    public static String name(Context context, Uri uri) {
        try (Cursor c = context.getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (RuntimeException ignored) { }
        return uri.getLastPathSegment() == null ? "input" : new File(uri.getLastPathSegment()).getName();
    }
    public static File stage(Context context, Uri uri) throws IOException {
        return stage(context, uri, 1024L * 1024 * 1024, text -> { });
    }
    static File claimStaged(Context context, String token) throws IOException {
        File folder = new File(root(context), "imports").getCanonicalFile();
        if (token == null || !token.startsWith("input-") || !token.equals(new File(token).getName()) || token.contains("\\"))
            throw new SystemFailure(SystemFailure.Code.IMPORT_INVALID_IMPORT_TRANSFER);
        File file = new File(folder, token);
        if (!file.getCanonicalFile().getParentFile().equals(folder)
                || !Files.isRegularFile(file.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS))
            throw new SystemFailure(SystemFailure.Code.IMPORT_SELECTED_IMPORT_IS_NO_LONGER_AVAILABLE_SELECT_THE_FILE);
        return file;
    }
    static File stage(Context context, Uri uri, long limit, PreparedSystemArchive.Progress progress) throws IOException {
        String name = name(context, uri);
        if (name == null || !name.equals(new File(name).getName()) || name.contains("\\"))
            throw new SystemFailure(SystemFailure.Code.IMPORT_INVALID_INPUT_FILENAME);
        File folder = new File(root(context), "imports");
        if (!folder.isDirectory() && !folder.mkdirs()) throw new SystemFailure(SystemFailure.Code.IMPORT_CANNOT_CREATE_IMPORT_DIRECTORY);
        File target = File.createTempFile("input-", "-" + name, folder);
        boolean complete = false;
        try (InputStream input = context.getContentResolver().openInputStream(uri);
             FileOutputStream output = new FileOutputStream(target)) {
            if (input == null) throw new SystemFailure(SystemFailure.Code.IMPORT_CANNOT_OPEN_INPUT);
            byte[] buffer = new byte[65536];
            long total = 0;
            long lastUpdate = 0;
            for (int n; (n = input.read(buffer)) != -1;) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Import cancelled");
                total += n;
                if (total > limit) throw new SystemFailure(SystemFailure.Code.INPUT_GB_LIMIT, limit / (1024L * 1024 * 1024));
                output.write(buffer, 0, n);
                long now = System.nanoTime();
                if (now - lastUpdate > 250000000L) {
                    progress.update(SystemProgress.READ_INPUT, total / (1024 * 1024)); lastUpdate = now;
                }
            }
            output.getFD().sync();
            complete = true;
            return target;
        } finally { if (!complete) Files.deleteIfExists(target.toPath()); }
    }

    /** Extract only system inputs; never merge an archive's config or writable drives. */
    public static List<File> extractPhoneInputs(File archive, File folder) throws IOException {
        // Use the bounded native decoder for solid/BCJ2 7z, then the same
        // ROM/RPKG validation as ZIP. Never merge the archive's writable drives.
        if (CompressedGameArchive.isSeven(archive)) {
            File decoded = CompressedGameArchive.toZip(archive, text -> {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Import cancelled");
            });
            try { return extractPhoneInputs(decoded, folder); }
            finally { Files.deleteIfExists(decoded.toPath()); }
        }
        List<File> files = new ArrayList<>();
        boolean complete = false;
        try (ZipFile zip = new ZipFile(archive)) {
            int entries = 0;
            long total = 0;
            boolean rom = false, rpkg = false;
            java.util.Map<String, Boolean> paths = new java.util.HashMap<>();
            java.util.Enumeration<? extends ZipEntry> iterator = zip.entries();
            while (iterator.hasMoreElements()) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Import cancelled");
                ZipEntry entry = iterator.nextElement();
                if (++entries > 20000) throw new SystemFailure(SystemFailure.Code.IMPORT_TOO_MANY_ENTRIES_IN_PHONE_ARCHIVE);
                String name = ArchiveBatch.safe(entry.getName());
                String key = name.toLowerCase(Locale.ROOT).replaceAll("/+$", "");
                if (paths.put(key, entry.isDirectory()) != null) throw new SystemFailure(SystemFailure.Code.IMPORT_DUPLICATE_ARCHIVE_PATH);
                if (entry.isDirectory()) continue;
                String lower = name.toLowerCase(Locale.ROOT);
                boolean isRom = lower.endsWith(".rom"), isRpkg = lower.endsWith(".rpkg");
                if (lower.endsWith(".sis") || lower.endsWith(".sisx") || lower.endsWith(".jar") || lower.endsWith(".mpn")
                        || lower.endsWith(".jad") || lower.endsWith(".kjx")
                        || lower.endsWith(".n-gage"))
                    throw new SystemFailure(SystemFailure.Code.IMPORT_THIS_ARCHIVE_MIXES_GAMES_AND_PHONE_FILES_SELECT_A);
                if (!isRom && !isRpkg) continue;
                if ((isRom && rom) || (isRpkg && rpkg)) throw new SystemFailure(SystemFailure.Code.IMPORT_SELECT_AN_ARCHIVE_CONTAINING_ONE_PHONE);
                rom |= isRom; rpkg |= isRpkg;
                if (entry.getSize() < 1 || entry.getSize() > 1024L * 1024 * 1024 - total)
                    throw new SystemFailure(SystemFailure.Code.IMPORT_INVALID_PHONE_FILE_SIZE_MAXIMUM_1_GB_EXPANDED);
                if (folder.getUsableSpace() < entry.getSize() + 64L * 1024 * 1024)
                    throw new SystemFailure(SystemFailure.Code.IMPORT_NOT_ENOUGH_SPACE_TO_UNPACK_PHONE_FILES_FREE_STORAGE);
                File target = File.createTempFile("phone-", isRom ? ".rom" : ".rpkg", folder);
                files.add(target);
                try (InputStream input = zip.getInputStream(entry); FileOutputStream output = new FileOutputStream(target)) {
                    byte[] buffer = new byte[65536];
                    java.util.zip.CRC32 crc = new java.util.zip.CRC32();
                    long size = 0;
                    for (int n; (n = input.read(buffer)) != -1;) {
                        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Import cancelled");
                        total += n;
                        if (total > 1024L * 1024 * 1024) throw new SystemFailure(SystemFailure.Code.IMPORT_EXPANDED_PHONE_FILES_EXCEED_1_GB);
                        size += n;
                        crc.update(buffer, 0, n);
                        output.write(buffer, 0, n);
                    }
                    if (size == 0 || size != entry.getSize() || crc.getValue() != entry.getCrc())
                        throw new SystemFailure(SystemFailure.Code.IMPORT_DAMAGED_SYSTEM_FILE_IN_PHONE_ARCHIVE);
                    output.getFD().sync();
                }
            }
            for (String path : paths.keySet())
                for (int slash = path.lastIndexOf('/'); slash > 0; slash = path.lastIndexOf('/', slash - 1))
                    if (Boolean.FALSE.equals(paths.get(path.substring(0, slash)))) throw new SystemFailure(SystemFailure.Code.IMPORT_CONFLICTING_ARCHIVE_PATHS);
            if (!rom) throw new SystemFailure(SystemFailure.Code.IMPORT_NO_PHONE_ROM_FOUND_IN_THIS_ARCHIVE);
            complete = true;
            return files;
        } finally {
            if (!complete) for (File file : files) Files.deleteIfExists(file.toPath());
        }
    }

    public static void stageCardPackage(File root, File drive, File source, String name) throws IOException {
        if (name == null || name.isEmpty() || !name.equals(new File(name).getName()) || name.contains("\\"))
            throw new SystemFailure(SystemFailure.Code.IMPORT_INVALID_PACKAGE_FILENAME);
        File folder = new File(drive, "n-gage").getCanonicalFile();
        if (!folder.toPath().startsWith(root.getCanonicalFile().toPath()))
            throw new SystemFailure(SystemFailure.Code.IMPORT_THE_MEMORY_CARD_IS_OUTSIDE_THIS_ENVIRONMENT);
        if (!folder.isDirectory() && !folder.mkdirs()) throw new SystemFailure(SystemFailure.Code.IMPORT_CANNOT_OPEN_MEMORY_CARD);
        File destination = new File(folder, name);
        if (destination.exists()) throw new SystemFailure(SystemFailure.Code.IMPORT_THIS_PACKAGE_IS_ALREADY_ON_THE_MEMORY_CARD);
        File pending = File.createTempFile("package-", ".pending", folder);
        try {
            try (InputStream input = new FileInputStream(source);
                 FileOutputStream output = new FileOutputStream(pending)) {
                byte[] buffer = new byte[65536];
                for (int n; (n = input.read(buffer)) != -1;) output.write(buffer, 0, n);
                output.getFD().sync();
            }
            Files.move(pending.toPath(), destination.toPath());
        } finally { Files.deleteIfExists(pending.toPath()); }
    }
}
