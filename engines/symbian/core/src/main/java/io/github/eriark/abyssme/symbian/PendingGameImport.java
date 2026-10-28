package io.github.eriark.abyssme.symbian;

import android.util.AtomicFile;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

/** One durable, app-owned input. Guest drives and user source files are never cleaned here. */
final class PendingGameImport {
    enum Step { READY, INSTALLING, PLATFORM, COMPONENT, GUEST, COMPLETE }
    static final class Entry {
        final String name, hash, system, owner;
        final GameImport.Kind kind;
        final Step step;
        Entry(String name, String hash, GameImport.Kind kind, String system, Step step) {
            this(name,hash,kind,system,step,"");
        }
        Entry(String name, String hash, GameImport.Kind kind, String system, Step step, String owner) {
            this.name = name; this.hash = hash; this.kind = kind; this.system = system; this.step = step; this.owner=owner;
        }
    }
    private final File root;
    PendingGameImport(File root) { this.root = root; }
    private File current() { return new File(root, "current"); }
    File payload() { return new File(current(), "payload"); }
    boolean exists() { return current().exists(); }

    Entry read() throws IOException {
        if (!exists()) return null;
        AtomicFile metadata = new AtomicFile(new File(current(), "import.json"));
        try (InputStream input = metadata.openRead()) {
            byte[] bytes = new byte[8193];
            int size = 0, count;
            while (size < bytes.length && (count = input.read(bytes, size, bytes.length - size)) != -1) size += count;
            if (size > 8192) throw new SystemFailure(SystemFailure.Code.IMPORT_IMPORT_RECORD_IS_TOO_LARGE);
            JSONObject json = new JSONObject(new String(bytes, 0, size, StandardCharsets.UTF_8));
            String hash = json.getString("hash"), system = json.getString("system");
            if (json.getInt("version") != 1 || !hash.matches("[0-9a-f]{64}")
                    || (!system.isEmpty() && !system.matches("[0-9a-f]{64}")))
                throw new SystemFailure(SystemFailure.Code.IMPORT_INVALID_IMPORT_IDENTITY);
            String owner=json.optString("owner","");
            if(!owner.isEmpty() && !owner.matches("[0-9a-f-]{36}:[0-9]{1,3}")) throw new SystemFailure(SystemFailure.Code.IMPORT_INVALID_QUEUE_OWNER);
            Entry entry = new Entry(json.getString("name"), hash, GameImport.Kind.valueOf(json.getString("kind")),
                    system, Step.valueOf(json.getString("step")),owner);
            if (!Files.isRegularFile(payload().toPath(), LinkOption.NOFOLLOW_LINKS))
                throw new SystemFailure(SystemFailure.Code.IMPORT_THE_PENDING_GAME_FILE_IS_MISSING);
            return entry;
        } catch (RuntimeException | org.json.JSONException error) {
            throw new SystemFailure(SystemFailure.Code.IMPORT_CANNOT_READ_PENDING_IMPORT, error);
        }
    }

    void begin(GameImport game, String name) throws IOException {
        begin(game,name,"");
    }
    void begin(GameImport game, String name, String owner) throws IOException {
        if (exists()) throw new SystemFailure(SystemFailure.Code.IMPORT_FINISH_OR_REMOVE_THE_PENDING_IMPORT_FIRST);
        Files.createDirectories(root.toPath());
        if (game.file.length() > 1024L * 1024 * 1024 || root.getUsableSpace() < game.file.length() + 32L * 1024 * 1024)
            throw new SystemFailure(SystemFailure.Code.IMPORT_NOT_ENOUGH_SPACE_TO_RETAIN_THE_GAME_IMPORT);
        File staging = Files.createTempDirectory(root.toPath(), "staging-").toFile();
        try {
            File target = new File(staging, "payload");
            try (InputStream input = new FileInputStream(game.file); FileOutputStream output = new FileOutputStream(target)) {
                byte[] buffer = new byte[65536]; long size = 0;
                for (int count; (count = input.read(buffer)) != -1;) {
                    size += count;
                    if (size > 1024L * 1024 * 1024) throw new SystemFailure(SystemFailure.Code.IMPORT_GAME_EXCEEDS_1_GB);
                    output.write(buffer, 0, count);
                }
                output.getFD().sync();
            }
            if (!game.hash.equals(SystemFileCatalog.hash(target))) throw new SystemFailure(SystemFailure.Code.IMPORT_GAME_CHANGED_WHILE_RETAINING_IMPORT);
            String label = name == null ? "Game" : name.replaceAll("[\\p{Cntrl}]", " ").trim();
            if (label.isEmpty()) label = "Game";
            if (label.length() > 160) label = label.substring(0, 160);
            write(staging, new Entry(label, game.hash, game.kind, "", Step.READY,owner));
            Files.move(staging.toPath(), current().toPath());
        } finally { remove(staging); }
    }

    Entry update(Entry entry, String system, Step step) throws IOException {
        Entry actual = read();
        if (actual == null || !actual.hash.equals(entry.hash)) throw new SystemFailure(SystemFailure.Code.IMPORT_PENDING_IMPORT_CHANGED);
        Entry updated = new Entry(entry.name, entry.hash, entry.kind, system, step,entry.owner);
        write(current(), updated);
        return updated;
    }
    void verify(Entry entry) throws IOException {
        if (!entry.hash.equals(SystemFileCatalog.hash(payload()))) throw new SystemFailure(SystemFailure.Code.IMPORT_THE_PENDING_GAME_FILE_HAS_CHANGED);
    }
    void discard() throws IOException {
        if (!exists()) return;
        File discarded = new File(root, "staging-discard-" + java.util.UUID.randomUUID());
        Files.move(current().toPath(), discarded.toPath());
        remove(discarded);
    }
    void recover() throws IOException {
        File[] files = root.listFiles();
        if (files != null) for (File file : files) if (file.getName().startsWith("staging-")) remove(file);
        Entry entry = read();
        if (entry != null && entry.step == Step.COMPLETE && entry.owner.isEmpty()) discard();
    }
    private static void write(File folder, Entry entry) throws IOException {
        AtomicFile file = new AtomicFile(new File(folder, "import.json"));
        FileOutputStream stream = null;
        try {
            JSONObject json = new JSONObject().put("version", 1).put("name", entry.name).put("hash", entry.hash)
                    .put("kind", entry.kind.name()).put("system", entry.system).put("step", entry.step.name()).put("owner",entry.owner);
            stream = file.startWrite();
            stream.write(json.toString().getBytes(StandardCharsets.UTF_8));
            file.finishWrite(stream);
        } catch (Exception error) {
            if (stream != null) file.failWrite(stream);
            throw new SystemFailure(SystemFailure.Code.IMPORT_CANNOT_SAVE_IMPORT_PROGRESS, error);
        }
    }
    private void remove(File target) throws IOException {
        Path path = target.toPath().toAbsolutePath().normalize();
        if (!path.getParent().equals(root.toPath().toAbsolutePath().normalize())) throw new SystemFailure(SystemFailure.Code.IMPORT_INVALID_IMPORT_CLEANUP_PATH);
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return;
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file); return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult postVisitDirectory(Path dir, IOException error) throws IOException {
                if (error != null) throw error;
                Files.delete(dir); return FileVisitResult.CONTINUE;
            }
        });
    }
}
