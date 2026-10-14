package io.github.eriark.abyssme.symbian;

import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** A user-supplied installer, never guest drives or downloaded firmware. */
final class PlatformStore {
    private static final long LIMIT = 256L * 1024 * 1024;
    private final File root;
    PlatformStore(File root) { this.root = root; }
    void save(GameImport platform) throws IOException {
        if (!platform.isPlatform()) throw new IOException("Select the N-Gage 2.0 platform installer, not a game SIS");
        if (platform.file.length() > LIMIT) throw new IOException("Platform installer exceeds 256 MB");
        Files.createDirectories(root.toPath());
        File target = new File(root, platform.hash + ".sis");
        if (!Files.isRegularFile(target.toPath(), LinkOption.NOFOLLOW_LINKS) || !platform.hash.equals(SystemFileCatalog.hash(target))) {
            File temporary = File.createTempFile("platform-", ".pending", root);
            try {
                try (InputStream in = new FileInputStream(platform.file); FileOutputStream out = new FileOutputStream(temporary)) {
                    byte[] buffer = new byte[65536]; long size = 0;
                    for (int n; (n = in.read(buffer)) != -1;) {
                        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Import cancelled");
                        size += n; if (size > LIMIT) throw new IOException("Platform installer exceeds 256 MB");
                        out.write(buffer, 0, n);
                    }
                    out.getFD().sync();
                }
                if (!platform.hash.equals(SystemFileCatalog.hash(temporary))) throw new IOException("Platform installer changed while copying");
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally { Files.deleteIfExists(temporary.toPath()); }
        }
        AtomicFile pointer = new AtomicFile(new File(root, "selected")); FileOutputStream out = null;
        try {
            out = pointer.startWrite(); out.write(platform.hash.getBytes(StandardCharsets.US_ASCII)); pointer.finishWrite(out);
        } catch (IOException error) { if (out != null) pointer.failWrite(out); throw error; }
    }
    GameImport open() throws IOException {
        File selected = new File(root, "selected");
        if (!selected.exists() && !new File(root, "selected.bak").exists()) return null;
        String hash;
        try (InputStream in = new AtomicFile(selected).openRead()) {
            byte[] value = new byte[65]; int size = 0, n;
            while (size < value.length && (n = in.read(value, size, value.length - size)) != -1) size += n;
            hash = new String(value, 0, size, StandardCharsets.US_ASCII);
        }
        if (!hash.matches("[0-9a-f]{64}")) throw new IOException("Saved platform index is damaged. Add the platform again.");
        File file = new File(root, hash + ".sis");
        if (!Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS) || file.length() > LIMIT)
            throw new IOException("Saved platform is missing. Add the platform again.");
        GameImport result = GameImport.inspect(file);
        if (!result.hash.equals(hash) || !result.isPlatform()) {
            result.close(); throw new IOException("Saved platform changed. Add the platform again.");
        }
        return result;
    }
}
