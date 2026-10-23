/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/** Use an app-internal, account-private directory (not shared game storage or backups). */
public final class FileContactJournal implements TemporaryContacts.Journal {
    private static final int MAX_BYTES = 512 * 1024;
    private final Path directory, file;

    public FileContactJournal(Path privateRoot, String account) throws IOException {
        if (!TemporaryContacts.validId(account)) throw new IllegalArgumentException("Invalid account");
        // Android's trusted Context directory can contain /data/user/0 aliases.
        // Resolve that supplied root once; never follow account/journal symlinks.
        Path root = privateRoot.toRealPath();
        if (!Files.isDirectory(root)) throw new IOException("Contact root is not a directory");
        directory = root.resolve(account);
        file = directory.resolve("temporary-contacts.json");
        checkPath();
        Files.createDirectories(directory);
    }

    private void checkPath() throws IOException {
        for (Path p = directory; p != null; p = p.getParent()) {
            if (Files.isSymbolicLink(p)) throw new IOException("Symlink in contact storage");
        }
        if (Files.isSymbolicLink(file)) throw new IOException("Symlink contact journal");
    }

    @Override public JsonObject load() throws IOException {
        checkPath();
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return null;
        try (FileChannel input = FileChannel.open(file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            long size = input.size();
            if (size < 2 || size > MAX_BYTES) throw new IOException("Invalid contact journal size");
            ByteBuffer buffer = ByteBuffer.allocate((int) size);
            while (buffer.hasRemaining()) if (input.read(buffer) < 0) throw new IOException("Truncated journal");
            if (input.size() != size) throw new IOException("Journal changed while reading");
            return JsonParser.parseString(new String(buffer.array(), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (RuntimeException e) { throw new IOException("Invalid contact journal", e); }
    }

    @Override public void save(JsonObject snapshot) throws IOException {
        byte[] bytes = snapshot.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) throw new IOException("Contact journal is full");
        checkPath();
        Path temp = Files.createTempFile(directory, "contacts-", ".tmp");
        try {
            try (FileChannel output = FileChannel.open(temp, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) output.write(buffer);
                output.force(true);
            }
            checkPath();
            // No non-atomic fallback: a failed journal write must stop provider mutations.
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
    }
}
