package io.github.eriark.abyssme.symbian;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.*;

/** Inspect package content before selecting a Symbian environment. */
final class GameImport implements AutoCloseable {
    enum Kind { CARD, NGAGE2, SIS_LEGACY, SIS_MODERN }
    final File file;
    final Kind kind;
    final String hash;
    private final boolean extracted;
    private GameImport(File file, Kind kind, boolean extracted) throws IOException {
        this.file = file; this.kind = kind; this.extracted = extracted;
        hash = SystemFileCatalog.hash(file);
    }
    static boolean isZip(File file) throws IOException {
        try (InputStream input = new FileInputStream(file)) {
            return input.read() == 'P' && input.read() == 'K' && input.read() == 3 && input.read() == 4;
        }
    }
    static GameImport inspect(File source) throws IOException {
        if (source.length() > 1024L * 1024 * 1024) throw new IOException("Game input exceeds 1 GB");
        if(CompressedGameArchive.accepts(source)) {
            File zip=CompressedGameArchive.toZip(source,message->{}); boolean retained=false;
            try {
                GameImport result=inspect(zip);
                if(!result.extracted) {GameImport owned=new GameImport(zip,result.kind,true);retained=true;return owned;}
                return result;
            } finally {if(!retained)Files.deleteIfExists(zip.toPath());}
        }
        if (!isZip(source)) return new GameImport(source, packageKind(source), false);
        try (ZipFile zip = new ZipFile(source)) {
            List<ZipEntry> packages = new ArrayList<>();
            Set<String> cardDirectories = new HashSet<>();
            Set<String> cardRoots = new HashSet<>();
            Set<String> fileNames = new HashSet<>();
            int count = 0;
            long expandedSize = 0;
            for (Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements();) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Import cancelled");
                ZipEntry entry = entries.nextElement();
                if (++count > 20000) throw new IOException("Too many entries in game archive");
                String path = entry.getName().replace('\\', '/');
                if (path.startsWith("/") || path.contains(":") || Arrays.asList(path.split("/")).contains(".."))
                    throw new IOException("Unsafe path in game archive");
                if (entry.isDirectory()) continue;
                String name = path.toLowerCase(Locale.ROOT);
                if (!fileNames.add(name)) throw new IOException("Duplicate path in game archive");
                if (entry.getSize() < 0 || entry.getSize() > 1024L * 1024 * 1024 - expandedSize)
                    throw new IOException("Expanded game exceeds 1 GB");
                expandedSize += entry.getSize();
                if (name.matches("(?:.*/)?system/apps/[^/]+/[^/]+\\.app")) {
                    cardDirectories.add(name.substring(0, name.lastIndexOf('/') + 1));
                    cardRoots.add(name.substring(0, name.lastIndexOf("system/apps/")));
                }
                if (name.endsWith(".sis") || name.endsWith(".sisx") || name.endsWith(".n-gage")) packages.add(entry);
            }
            // Card games may include a SIS beside their APP as part of the card image.
            // Do not treat an unrelated installer outside those directories as a dependency.
            if (cardRoots.size() == 1) {
                boolean embeddedOnly = true;
                for (ZipEntry entry : packages) {
                    String name = entry.getName().replace('\\', '/').toLowerCase(Locale.ROOT);
                    embeddedOnly &= !name.endsWith(".n-gage")
                            && cardDirectories.contains(name.substring(0, name.lastIndexOf('/') + 1));
                }
                if (embeddedOnly) return new GameImport(source, Kind.CARD, false);
            }
            if (!cardRoots.isEmpty() || packages.size() != 1) throw new IOException("Select an archive containing one game or installer");
            File file = File.createTempFile("game-", ".package", source.getParentFile());
            boolean complete = false;
            try {
                ZipEntry entry = packages.get(0);
                CRC32 crc = new CRC32();
                long size = 0;
                try (InputStream input = zip.getInputStream(entry); FileOutputStream output = new FileOutputStream(file)) {
                    byte[] buffer = new byte[65536];
                    for (int n; (n = input.read(buffer)) != -1;) {
                        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Import cancelled");
                        size += n;
                        if (size > 1024L * 1024 * 1024) throw new IOException("Expanded game exceeds 1 GB");
                        crc.update(buffer, 0, n); output.write(buffer, 0, n);
                    }
                    if (size != entry.getSize() || crc.getValue() != entry.getCrc()) throw new IOException("Damaged game archive");
                    output.getFD().sync();
                }
                GameImport result = new GameImport(file, packageKind(file), true);
                complete = true;
                return result;
            } finally { if (!complete) Files.deleteIfExists(file.toPath()); }
        }
    }
    private static Kind packageKind(File file) throws IOException {
        byte[] header = new byte[512];
        int count;
        try (InputStream input = new FileInputStream(file)) { count = input.read(header); }
        if (count >= 16) {
            ByteBuffer bytes = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
            if (bytes.getInt(0) == 0x10201a7a) return Kind.SIS_MODERN;
            if (bytes.getInt(4) == 0x1000006d || bytes.getInt(4) == 0x10003a12) return Kind.SIS_LEGACY;
            String text = new String(header, 0, count, StandardCharsets.US_ASCII).toLowerCase(Locale.ROOT);
            if (text.startsWith("content-type: multipart/mixed;") && text.contains("content-description: ngage-install-file"))
                return Kind.NGAGE2;
        }
        throw new IOException("Not a supported Symbian package. Select a SIS/SISX, N-Gage package or game ZIP.");
    }
    boolean isPlatform() throws IOException {
        if (kind != Kind.SIS_MODERN) return false;
        try (DataInputStream input = new DataInputStream(new FileInputStream(file))) {
            byte[] header = new byte[12]; input.readFully(header);
            // The platform distribution's package UID differs from its launcher UID.
            return ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).getInt(8) == 0x20003b78;
        }
    }
    @Override public void close() throws IOException { if (extracted) Files.deleteIfExists(file.toPath()); }
}
