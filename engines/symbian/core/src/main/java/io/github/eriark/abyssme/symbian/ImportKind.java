package io.github.eriark.abyssme.symbian;

import java.io.*;
import java.util.*;
import java.util.zip.*;
import org.apache.commons.compress.archivers.sevenz.*;

/** Routing is not installation validation; native importers still check their full input. */
final class ImportKind {
    enum Kind { EXISTING, GAME, SYSTEM, PLATFORM, BACKUP, BATCH, CONTAINER }
    static Kind inspect(File source, String name) throws IOException {
        String lower = (name == null ? source.getName() : name).toLowerCase(Locale.ROOT);
        if (CompressedGameArchive.isSeven(source)) return inspectSeven(source);
        if (CompressedGameArchive.accepts(source)) return Kind.CONTAINER;
        if (GameImport.isZip(source)) {
            boolean system = false, prepared = false, game = false, existing = false;
            int count = 0;
            try (ZipFile zip = new ZipFile(source)) {
                if (zip.getEntry(PhoneBackup.MANIFEST) != null) { PhoneBackup.inspect(source); return Kind.BACKUP; }
                for (Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements();) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Import cancelled");
                    ZipEntry entry = entries.nextElement();
                    if (++count > 250000) throw new IOException("Too many archive entries");
                    String path = entry.getName().replace('\\', '/').toLowerCase(Locale.ROOT);
                    if (path.startsWith("/") || path.contains(":") || path.indexOf('\0') >= 0
                            || Arrays.asList(path.split("/")).contains("..")) throw new IOException("Unsafe archive path");
                    if (entry.isDirectory()) continue;
                    prepared |= path.matches("(?:.*/)?data/roms/[^/]+/sym\\.rom");
                    system |= path.endsWith(".rom") || path.endsWith(".rpkg");
                    game |= path.endsWith(".sis") || path.endsWith(".sisx") || path.endsWith(".n-gage")
                            || path.matches("(?:.*/)?system/apps/[^/]+/[^/]+\\.app");
                    existing |= path.endsWith(".jar") || path.endsWith(".jad") || path.endsWith(".mpn") || path.endsWith(".kjx")
                            || path.equals("meta-inf/manifest.mf");
                }
            }
            if (prepared) return Kind.SYSTEM; // Writable files inside prepared dumps are ignored by the system importer.
            if ((system && (game || existing)) || (game && existing))
                throw new IOException("This archive mixes game types or system files. Select one game or system archive.");
            if (system) return Kind.SYSTEM;
            if (!existing && ArchiveBatch.multiple(source)) return Kind.BATCH;
            if (!game) return Kind.EXISTING;
        } else if (PreparedSystemArchive.isArchive(source) || lower.endsWith(".rom") || lower.endsWith(".rpkg")) {
            return Kind.SYSTEM;
        } else if (lower.endsWith(".jar") || lower.endsWith(".jad") || lower.endsWith(".mpn") || lower.endsWith(".kjx")) {
            return Kind.EXISTING;
        }
        try (GameImport game = GameImport.inspect(source)) { return game.isPlatform() ? Kind.PLATFORM : Kind.GAME; }
    }

    private static Kind inspectSeven(File source) throws IOException {
        boolean system = false, prepared = false, game = false, existing = false;
        int count = 0;
        try (SevenZFile archive = new SevenZFile(source,
                SevenZFileOptions.builder().withMaxMemoryLimitInKb(128 * 1024).build())) {
            for (SevenZArchiveEntry entry : archive.getEntries()) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Import cancelled");
                if (++count > 250000) throw new IOException("Too many archive entries");
                if (entry.getName() == null) throw new IOException("Invalid archive path");
                String path = entry.getName().replace('\\', '/').toLowerCase(Locale.ROOT);
                if (path.startsWith("/") || path.contains(":") || path.indexOf('\0') >= 0
                        || Arrays.asList(path.split("/")).contains("..")) throw new IOException("Unsafe archive path");
                if (entry.isDirectory()) continue;
                prepared |= path.matches("(?:.*/)?data/roms/[^/]+/sym\\.rom");
                system |= path.endsWith(".rom") || path.endsWith(".rpkg");
                game |= path.endsWith(".sis") || path.endsWith(".sisx") || path.endsWith(".n-gage")
                        || path.matches("(?:.*/)?system/apps/[^/]+/[^/]+\\.app");
                existing |= path.endsWith(".jar") || path.endsWith(".jad") || path.endsWith(".mpn") || path.endsWith(".kjx");
            }
        }
        if (prepared) return Kind.SYSTEM;
        if ((system && (game || existing)) || (game && existing))
            throw new IOException("This archive mixes game types or system files. Select one game or system archive.");
        if (system) return Kind.SYSTEM;
        return existing ? Kind.EXISTING : Kind.CONTAINER;
    }
}
