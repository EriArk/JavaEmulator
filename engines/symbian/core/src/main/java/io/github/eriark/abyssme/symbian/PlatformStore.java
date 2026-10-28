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
        if (!platform.isPlatform()) throw new SystemFailure(SystemFailure.Code.IMPORT_SELECT_THE_N_GAGE_2_0_PLATFORM_INSTALLER_NOT);
        if (platform.file.length() > LIMIT) throw new SystemFailure(SystemFailure.Code.IMPORT_PLATFORM_INSTALLER_EXCEEDS_256_MB);
        Files.createDirectories(root.toPath());
        File target = new File(root, platform.hash + ".sis");
        if (!Files.isRegularFile(target.toPath(), LinkOption.NOFOLLOW_LINKS) || !platform.hash.equals(SystemFileCatalog.hash(target))) {
            File temporary = File.createTempFile("platform-", ".pending", root);
            try {
                try (InputStream in = new FileInputStream(platform.file); FileOutputStream out = new FileOutputStream(temporary)) {
                    byte[] buffer = new byte[65536]; long size = 0;
                    for (int n; (n = in.read(buffer)) != -1;) {
                        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Import cancelled");
                        size += n; if (size > LIMIT) throw new SystemFailure(SystemFailure.Code.IMPORT_PLATFORM_INSTALLER_EXCEEDS_256_MB);
                        out.write(buffer, 0, n);
                    }
                    out.getFD().sync();
                }
                if (!platform.hash.equals(SystemFileCatalog.hash(temporary))) throw new SystemFailure(SystemFailure.Code.IMPORT_PLATFORM_INSTALLER_CHANGED_WHILE_COPYING);
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
        if (!hash.matches("[0-9a-f]{64}")) throw new SystemFailure(SystemFailure.Code.IMPORT_SAVED_PLATFORM_INDEX_IS_DAMAGED_ADD_THE_PLATFORM_AGAIN);
        File file = new File(root, hash + ".sis");
        if (!Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS) || file.length() > LIMIT)
            throw new SystemFailure(SystemFailure.Code.IMPORT_SAVED_PLATFORM_IS_MISSING_ADD_THE_PLATFORM_AGAIN);
        GameImport result = GameImport.inspect(file);
        if (!result.hash.equals(hash) || !result.isPlatform()) {
            result.close(); throw new SystemFailure(SystemFailure.Code.IMPORT_SAVED_PLATFORM_CHANGED_ADD_THE_PLATFORM_AGAIN);
        }
        return result;
    }
}
