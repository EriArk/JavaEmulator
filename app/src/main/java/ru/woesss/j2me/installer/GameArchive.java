package ru.woesss.j2me.installer;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import org.apache.commons.compress.archivers.sevenz.*;

/** Bounded, selected-entry extraction. Archive paths are never used as output paths. */
final class GameArchive {
    private static final long MB = 1024L * 1024;
    private static final int MAX_ENTRIES = 10000, MAX_GAMES = 1024;
    private GameArchive() { }

    static boolean mophun(String name) { return name.toLowerCase(Locale.ROOT).endsWith(".mpn"); }
    static String leaf(String name) { return name.replace('\\', '/').substring(name.replace('\\', '/').lastIndexOf('/') + 1); }
    private static boolean supported(String name) { return mophun(name) || name.toLowerCase(Locale.ROOT).endsWith(".jar"); }
    private static long limit(String name) { return (mophun(name) ? 16 : 64) * MB; }
    private static void check() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Archive import cancelled");
    }
    private static SevenZFile seven(File source) throws IOException {
        return new SevenZFile(source, SevenZFileOptions.builder().withMaxMemoryLimitInKb(128 * 1024).build());
    }
    private static boolean zip(File source) throws IOException {
        if (source.length() > 512 * MB) throw new IOException("Game archive exceeds 512 MB");
        try (InputStream in = new FileInputStream(source)) { return in.read() == 'P' && in.read() == 'K'; }
    }
    private static final class Listing {
        final List<String> games = new ArrayList<>();
        final Set<String> paths = new HashSet<>();
        long expanded;
        void add(String name, boolean directory, long size) throws IOException {
            check();
            if (name == null || name.length() > 4096) throw new IOException("Invalid archive path");
            String path = name.replace('\\', '/');
            if (path.isEmpty() || path.startsWith("/") || path.contains(":")
                    || Arrays.asList(path.split("/")).contains("..")) throw new IOException("Unsafe archive path");
            for (int i = 0; i < path.length(); i++)
                if (Character.isISOControl(path.charAt(i))) throw new IOException("Invalid archive path");
            if (paths.size() >= MAX_ENTRIES) throw new IOException("Too many archive entries");
            if (!paths.add(path.toLowerCase(Locale.ROOT))) throw new IOException("Duplicate archive path");
            if (size < 0 || size > 1024 * MB - expanded) throw new IOException("Expanded archive exceeds 1 GB");
            expanded += size;
            if (!directory && supported(name)) {
                if (games.size() >= MAX_GAMES) throw new IOException("Too many games. Select a smaller archive.");
                games.add(name);
            }
        }
    }
    static List<String> list(File source) throws IOException {
        check(); Listing listing = new Listing();
        if (zip(source)) {
            try (ZipFile archive = new ZipFile(source)) {
                for (FileHeader entry : archive.getFileHeaders())
                    listing.add(entry.getFileName(), entry.isDirectory(), entry.getUncompressedSize());
            }
        } else {
            try (SevenZFile archive = seven(source)) {
                for (SevenZArchiveEntry entry : archive.getEntries())
                    listing.add(entry.getName(), entry.isDirectory(), entry.getSize());
            }
        }
        if (listing.games.isEmpty()) throw new IOException("Archive does not contain a JAR or MPN game");
        return listing.games;
    }
    static void extract(File source, String name, File output) throws IOException {
        // Recheck the archive after the chooser; never trust a stale entry name or size.
        if (!list(source).contains(name)) throw new IOException("The selected game is no longer in this archive");
        boolean complete = false;
        try {
            if (zip(source)) {
                try (ZipFile archive = new ZipFile(source)) {
                    FileHeader entry = archive.getFileHeader(name);
                    if (entry.isEncrypted()) throw new IOException("Password-protected games are not supported");
                    if (entry.getUncompressedSize() > limit(name)) throw new IOException("Selected game is too large");
                    try (InputStream in = archive.getInputStream(entry); OutputStream out = new FileOutputStream(output)) {
                        copy(in, out, limit(name));
                    }
                }
            } else {
                try (SevenZFile archive = seven(source)) {
                    boolean found = false;
                    SevenZArchiveEntry entry;
                    while ((entry = archive.getNextEntry()) != null) {
                        check();
                        if (!entry.isDirectory() && name.equals(entry.getName())) {
                            if (entry.getSize() > limit(name)) throw new IOException("Selected game is too large");
                            try (OutputStream out = new FileOutputStream(output)) {
                                copy(new InputStream() {
                                    @Override public int read() throws IOException { return archive.read(); }
                                    @Override public int read(byte[] b, int off, int len) throws IOException { return archive.read(b, off, len); }
                                }, out, limit(name));
                            }
                            found = true;
                            break;
                        }
                    }
                    if (!found) throw new IOException("Selected game is missing");
                }
            }
            check(); complete = true;
        } finally { if (!complete) Files.deleteIfExists(output.toPath()); }
    }
    private static void copy(InputStream in, OutputStream out, long limit) throws IOException {
        byte[] buffer = new byte[32768]; long total = 0;
        for (int n; (n = in.read(buffer)) != -1;) {
            check(); total += n;
            if (total > limit) throw new IOException("Selected game is too large");
            out.write(buffer, 0, n);
        }
    }
}
