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
        if (!folder.isDirectory() && !folder.mkdirs()) throw new IOException("Cannot create font directory");
        File pending = File.createTempFile("font-", ".pending", folder);
        try {
            try (InputStream input = new FileInputStream(source); FileOutputStream output = new FileOutputStream(pending)) {
                byte[] buffer = new byte[65536];
                long total = 0;
                for (int n; (n = input.read(buffer)) != -1;) {
                    total += n;
                    if (total > 32L * 1024 * 1024) throw new IOException("Fallback font exceeds 32 MB");
                    output.write(buffer, 0, n);
                }
                if (total == 0) throw new IOException("Empty fallback font");
                output.getFD().sync();
            }
            Files.move(pending.toPath(), target.toPath());
        } finally { Files.deleteIfExists(pending.toPath()); }
    }
    private static void copyAssets(Context context, String asset, File target) throws IOException {
        String[] entries = context.getAssets().list(asset);
        if (entries != null && entries.length > 0) {
            if (!target.isDirectory() && !target.mkdirs()) throw new IOException("Cannot create engine directory");
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
            throw new IOException("Invalid import transfer");
        File file = new File(folder, token);
        if (!file.getCanonicalFile().getParentFile().equals(folder)
                || !Files.isRegularFile(file.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Selected import is no longer available. Select the file again.");
        return file;
    }
    static File stage(Context context, Uri uri, long limit, PreparedSystemArchive.Progress progress) throws IOException {
        String name = name(context, uri);
        if (name == null || !name.equals(new File(name).getName()) || name.contains("\\"))
            throw new IOException("Invalid input filename");
        File folder = new File(root(context), "imports");
        if (!folder.isDirectory() && !folder.mkdirs()) throw new IOException("Cannot create import directory");
        File target = File.createTempFile("input-", "-" + name, folder);
        boolean complete = false;
        try (InputStream input = context.getContentResolver().openInputStream(uri);
             FileOutputStream output = new FileOutputStream(target)) {
            if (input == null) throw new IOException("Cannot open input");
            byte[] buffer = new byte[65536];
            long total = 0;
            long lastUpdate = 0;
            for (int n; (n = input.read(buffer)) != -1;) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Import cancelled");
                total += n;
                if (total > limit) throw new IOException("Input exceeds " + (limit / (1024L * 1024 * 1024)) + " GB");
                output.write(buffer, 0, n);
                long now = System.nanoTime();
                if (now - lastUpdate > 250000000L) {
                    progress.update("Reading selected file: " + (total / (1024 * 1024)) + " MB"); lastUpdate = now;
                }
            }
            output.getFD().sync();
            complete = true;
            return target;
        } finally { if (!complete) Files.deleteIfExists(target.toPath()); }
    }

    /** Extract only system inputs; never merge an archive's config or writable drives. */
    public static List<File> extractPhoneInputs(File archive, File folder) throws IOException {
        List<File> files = new ArrayList<>();
        boolean complete = false;
        try (ZipFile zip = new ZipFile(archive)) {
            int entries = 0;
            long total = 0;
            boolean rom = false, rpkg = false;
            java.util.Enumeration<? extends ZipEntry> iterator = zip.entries();
            while (iterator.hasMoreElements()) {
                ZipEntry entry = iterator.nextElement();
                if (++entries > 20000) throw new IOException("Too many entries in phone archive");
                String name = entry.getName().replace('\\', '/');
                if (name.startsWith("/") || name.contains(":") || java.util.Arrays.asList(name.split("/")).contains(".."))
                    throw new IOException("Unsafe path in phone archive");
                if (entry.isDirectory()) continue;
                String lower = name.toLowerCase(Locale.ROOT);
                boolean isRom = lower.endsWith(".rom"), isRpkg = lower.endsWith(".rpkg");
                if (!isRom && !isRpkg) continue;
                if ((isRom && rom) || (isRpkg && rpkg)) throw new IOException("Select an archive containing one phone");
                rom |= isRom; rpkg |= isRpkg;
                File target = File.createTempFile("phone-", isRom ? ".rom" : ".rpkg", folder);
                files.add(target);
                try (InputStream input = zip.getInputStream(entry); FileOutputStream output = new FileOutputStream(target)) {
                    byte[] buffer = new byte[65536];
                    java.util.zip.CRC32 crc = new java.util.zip.CRC32();
                    long size = 0;
                    for (int n; (n = input.read(buffer)) != -1;) {
                        total += n;
                        if (total > 1024L * 1024 * 1024) throw new IOException("Expanded phone files exceed 1 GB");
                        size += n;
                        crc.update(buffer, 0, n);
                        output.write(buffer, 0, n);
                    }
                    if (size == 0 || size != entry.getSize() || crc.getValue() != entry.getCrc())
                        throw new IOException("Damaged system file in phone archive");
                    output.getFD().sync();
                }
            }
            if (!rom) throw new IOException("No phone ROM found in this archive");
            complete = true;
            return files;
        } finally {
            if (!complete) for (File file : files) Files.deleteIfExists(file.toPath());
        }
    }

    public static void stageCardPackage(File root, File drive, File source, String name) throws IOException {
        if (name == null || name.isEmpty() || !name.equals(new File(name).getName()) || name.contains("\\"))
            throw new IOException("Invalid package filename");
        File folder = new File(drive, "n-gage").getCanonicalFile();
        if (!folder.toPath().startsWith(root.getCanonicalFile().toPath()))
            throw new IOException("The memory card is outside this environment");
        if (!folder.isDirectory() && !folder.mkdirs()) throw new IOException("Cannot open memory card");
        File destination = new File(folder, name);
        if (destination.exists()) throw new IOException("This package is already on the memory card");
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
