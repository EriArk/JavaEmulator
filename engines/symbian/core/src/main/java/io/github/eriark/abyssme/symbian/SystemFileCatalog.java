package io.github.eriark.abyssme.symbian;

import android.content.Context;
import android.util.AtomicFile;
import com.github.eka2l1.emu.Emulator;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Private validated system sets. The native caller must serialize all engine operations. */
final class SystemFileCatalog {
    static final class Entry {
        final String id, model, firmware, os;
        final boolean restored;
        Entry(String id, String model, String firmware, String os) {
            this(id, model, firmware, os, false);
        }
        Entry(String id, String model, String firmware, String os, boolean restored) {
            this.id = id; this.model = model; this.firmware = firmware; this.os = os; this.restored = restored;
        }
        String displayName() { return model + (restored ? " (restored copy)" : ""); }
        String label() { return displayName() + " / " + firmware + " / " + os; }
        boolean supports(GameImport.Kind kind) {
            switch (kind) {
                case CARD: return firmware.equalsIgnoreCase("RH-29") || firmware.equalsIgnoreCase("RH-47") || firmware.equalsIgnoreCase("NEM-4");
                case SIS_LEGACY: return Arrays.asList("epocu6", "epoc6", "epoc70", "epoc7", "epoc80", "epoc81a").contains(os);
                case NGAGE2: return Arrays.asList("epoc91", "epoc93fp1", "epoc93fp2").contains(os);
                case SIS_MODERN: return Arrays.asList("epoc81b", "epoc91", "epoc93fp1", "epoc93fp2", "epoc94", "epoc95", "epoc10", "epoc100").contains(os);
                default: return false;
            }
        }
    }
    private final File base;
    SystemFileCatalog(Context context) { this(new File(context.getFilesDir(), "symbian-systems")); }
    SystemFileCatalog(File base) { this.base = base; }
    File set(String id) throws IOException {
        if (id == null || !id.matches("[0-9a-f]{64}")) throw new IOException("Invalid system set identity");
        File file = new File(base, id).getCanonicalFile();
        if (!file.getParentFile().equals(base.getCanonicalFile())) throw new IOException("Invalid system directory");
        return file;
    }
    File runtime(Entry entry) throws IOException { return new File(set(entry.id), "runtime"); }
    List<Entry> list() throws IOException {
        List<Entry> result = new ArrayList<>();
        File[] dirs = base.listFiles();
        if (dirs != null) for (File dir : dirs) {
            if (dir.getName().matches("[0-9a-f]{64}")) result.add(read(dir.getName()));
        }
        result.sort(Comparator.comparing(Entry::label).thenComparing(e -> e.id));
        return result;
    }
    Entry read(String id) throws IOException {
        File root = set(id);
        try {
            JSONObject json = readJson(new File(root, "set.json"));
            if ((json.getInt("version") != 1 && json.getInt("version") != 2) || !id.equals(json.getString("id"))) throw new IOException("Unsupported system set");
            if (!new File(root, "runtime/data/devices.yml").isFile() || !new File(root, "source/phone.rom").isFile()
                    || (!json.getString("rpkgSha256").isEmpty() && !new File(root, "source/phone.rpkg").isFile()))
                throw new IOException("System set is incomplete. Existing game data has been kept.");
            if (json.getInt("version") == 2) {
                String firmware = json.getString("firmware").toLowerCase(Locale.ROOT);
                if (firmware.contains("/") || firmware.contains("\\") || firmware.equals("..")
                        || !new File(root, "runtime/data/drives/z/" + firmware).isDirectory()
                        || !new File(root, "runtime/data/roms/" + firmware + "/SYM.ROM").isFile())
                    throw new IOException("Prepared system files are missing. Existing game data has been kept.");
            }
            return new Entry(id, json.getString("model"), json.getString("firmware"), json.getString("os"), json.has("restoredFrom"));
        } catch (JSONException error) { throw new IOException("Invalid system set metadata", error); }
    }
    File activeRoot(File legacy) throws IOException {
        File file = new File(base, "active.json");
        if (!file.exists()) return legacy;
        try { return runtime(read(readJson(file).getString("id"))); }
        catch (JSONException error) { throw new IOException("Invalid selected system set", error); }
    }
    void activate(Entry entry) throws IOException {
        try { writeJson(new File(base, "active.json"), new JSONObject().put("id", entry.id)); }
        catch (JSONException error) { throw new IOException(error); }
    }
    List<Entry> candidates(GameImport.Kind kind, String sourceHash) throws IOException {
        List<Entry> result = new ArrayList<>();
        for (Entry entry : list()) if (entry.supports(kind)) result.add(entry);
        File binding = new File(base, "bindings.json");
        if (binding.exists()) {
            String preferred = readJson(binding).optString(sourceHash);
            for (Entry entry : result) if (entry.id.equals(preferred)) return Collections.singletonList(entry);
        }
        List<Entry> restoredMatches = new ArrayList<>();
        for (Entry entry : result) if (entry.restored && sourceBindings(entry).contains(sourceHash)) restoredMatches.add(entry);
        if (!restoredMatches.isEmpty()) return restoredMatches;
        String recommended = kind == GameImport.Kind.NGAGE2 ? "RM-320" : kind == GameImport.Kind.CARD ? "RH-29" : "";
        List<Entry> preferred = new ArrayList<>();
        for (Entry entry : result) if (entry.firmware.equalsIgnoreCase(recommended)) preferred.add(entry);
        if (preferred.size() == 1) return preferred;
        File active = new File(base, "active.json");
        if (preferred.size() > 1 && active.isFile()) {
            String selected = readJson(active).optString("id");
            for (Entry entry : preferred) if (entry.id.equals(selected)) return Collections.singletonList(entry);
        }
        return result;
    }
    void bind(String sourceHash, Entry entry) throws IOException {
        if (!sourceHash.matches("[0-9a-f]{64}")) throw new IOException("Invalid game identity");
        File file = new File(base, "bindings.json");
        JSONObject json = file.exists() ? readJson(file) : new JSONObject();
        try { json.put(sourceHash, entry.id); }
        catch (JSONException error) { throw new IOException(error); }
        writeJson(file, json);
    }
    List<Entry> boundSystems() throws IOException {
        File file = new File(base, "bindings.json");
        JSONObject bindings = file.exists() ? readJson(file) : new JSONObject();
        Set<String> ids = new LinkedHashSet<>();
        for (Iterator<String> keys = bindings.keys(); keys.hasNext();) ids.add(bindings.optString(keys.next()));
        for (Entry entry : list()) if (entry.restored) ids.add(entry.id);
        List<Entry> entries = new ArrayList<>();
        for (String id : ids) {
            try { entries.add(read(id)); }
            catch (IOException error) { android.util.Log.w("AbyssSymbian", "Unavailable bound system: " + id, error); }
        }
        return entries;
    }
    List<String> sourceBindings(Entry entry) throws IOException {
        Set<String> hashes = new LinkedHashSet<>();
        File file = new File(base, "bindings.json");
        JSONObject bindings = file.exists() ? readJson(file) : new JSONObject();
        for (Iterator<String> keys = bindings.keys(); keys.hasNext();) {
            String key = keys.next();
            if (key.matches("[0-9a-f]{64}") && entry.id.equals(bindings.optString(key))) hashes.add(key);
        }
        org.json.JSONArray restored = readJson(new File(set(entry.id), "set.json")).optJSONArray("sourceBindings");
        if (restored != null) for (int i = 0; i < restored.length(); i++) {
            String hash = restored.optString(i);
            if (hash.matches("[0-9a-f]{64}")) hashes.add(hash);
        }
        return new ArrayList<>(hashes);
    }
    File newRestore() throws IOException {
        Files.createDirectories(base.toPath());
        return Files.createTempDirectory(base.toPath(), "pending-restore-").toFile();
    }
    void discardRestore(File pending) throws IOException { removePending(pending); }
    Entry commitRestore(File pending, String id) throws IOException {
        if (!pending.getParentFile().getCanonicalFile().equals(base.getCanonicalFile()) || !pending.getName().startsWith("pending-restore-"))
            throw new IOException("Invalid restore directory");
        if (set(id).exists()) throw new IOException("This restored phone already exists");
        Files.move(pending.toPath(), set(id).toPath(), StandardCopyOption.ATOMIC_MOVE);
        return read(id);
    }
    interface Installer { String[] install(File runtime, File rom, File rpkg) throws Exception; }

    static final class ImportResult {
        final List<Entry> entries = new ArrayList<>();
        final List<String> failures = new ArrayList<>();
        int reused;
        String summary() {
            return "Added: " + (entries.size() - reused) + " / Already available: " + reused
                    + (failures.isEmpty() ? "" : "\nNot imported:\n" + String.join("\n", failures));
        }
    }
    interface PreparedInstaller { String[] install(File runtime, File rom, File z, String hint) throws Exception; }
    ImportResult addArchive(Context context, File archive, PreparedSystemArchive.Progress progress) throws Exception {
        return addArchive(archive, progress, (runtime, rom, z, hint) -> {
            SymbianFiles.prepareAssets(context, runtime);
            try {
                Emulator.prepare(runtime.getAbsolutePath());
                Emulator.installPreparedDevice(rom.getAbsolutePath(), z.getAbsolutePath(), hint);
                return Emulator.getDeviceInfo();
            } finally { Emulator.stopSession(); }
        });
    }
    ImportResult addArchive(File archive, PreparedSystemArchive.Progress progress, PreparedInstaller installer) throws Exception {
        if (!base.isDirectory() && !base.mkdirs()) throw new IOException("Cannot create system catalog");
        File staging = Files.createTempDirectory(base.toPath(), "pending-collection-").toFile();
        try {
            ImportResult result = new ImportResult();
            List<PreparedSystemArchive.Dump> dumps = PreparedSystemArchive.extract(archive, staging, progress, result.failures);
            if (dumps.isEmpty()) return null;
            for (int i = 0; i < dumps.size(); i++) {
                PreparedSystemArchive.Dump dump = dumps.get(i);
                progress.update("Preparing system " + (i + 1) + "/" + dumps.size() + ": " + dump.hint);
                String id = digest(("rom=" + dump.romHash + "\nz=" + dump.zHash).getBytes(StandardCharsets.UTF_8));
                if (set(id).exists()) { result.entries.add(read(id)); result.reused++; continue; }
                File pending = Files.createTempDirectory(base.toPath(), "pending-").toFile();
                try {
                    File source = new File(pending, "source"), runtime = new File(pending, "runtime");
                    if (!source.mkdir() || !runtime.mkdir()) throw new IOException("Cannot create system environment");
                    File rom = new File(source, "phone.rom"), z = new File(runtime, "incoming-z");
                    Files.move(dump.rom.toPath(), rom.toPath());
                    Files.move(dump.z.toPath(), z.toPath());
                    String[] info = installer.install(runtime, rom, z, dump.hint);
                    if (info == null || info.length != 3 || Arrays.stream(info).anyMatch(s -> s == null || s.isEmpty())
                            || !new File(runtime, "data/devices.yml").isFile()) throw new IOException("System could not be identified");
                    JSONObject metadata = new JSONObject().put("version", 2).put("id", id)
                            .put("firmware", info[0]).put("model", info[1]).put("os", info[2])
                            .put("romSha256", dump.romHash).put("rpkgSha256", "").put("zSha256", dump.zHash);
                    writeJson(new File(pending, "set.json"), metadata);
                    Files.move(pending.toPath(), set(id).toPath(), StandardCopyOption.ATOMIC_MOVE);
                    result.entries.add(read(id));
                } catch (Exception error) {
                    result.failures.add(dump.hint + ": " + error.getMessage());
                } finally { removePending(pending); }
            }
            return result;
        } finally { removePending(staging); }
    }

    Entry add(Context context, List<File> inputs) throws Exception {
        return add(inputs, (runtime, rom, rpkg) -> {
            SymbianFiles.prepareAssets(context, runtime);
            try {
                Emulator.prepare(runtime.getAbsolutePath());
                if (Emulator.doesRomNeedRPKG(rom.getAbsolutePath()) && rpkg == null)
                    throw new IOException("This phone also needs its .rpkg file. Select the .rom and .rpkg from the same phone dump together, or add their ZIP archive. Existing phones and saves are unchanged.");
                int code = Emulator.installDevice(rpkg == null ? "" : rpkg.getAbsolutePath(), rom.getAbsolutePath(), true, true);
                if (code != 0) throw new IOException("System installation failed (" + code + ")");
                Emulator.prepare(runtime.getAbsolutePath());
                return Emulator.getDeviceInfo();
            } finally { Emulator.stopSession(); }
        });
    }
    Entry add(List<File> inputs, Installer installer) throws Exception {
        if (!base.isDirectory() && !base.mkdirs()) throw new IOException("Cannot create system catalog");
        File pending = Files.createTempDirectory(base.toPath(), "pending-").toFile();
        try {
            List<File> files = inputs;
            if (inputs.size() == 1 && PreparedSystemArchive.isArchive(inputs.get(0)))
                files = SymbianFiles.extractPhoneInputs(inputs.get(0), pending);
            File rom = null, rpkg = null;
            for (File file : files) {
                String name = file.getName().toLowerCase(Locale.ROOT);
                if (name.endsWith(".rom") && rom == null) rom = file;
                else if (name.endsWith(".rpkg") && rpkg == null) rpkg = file;
                else throw new IOException("Select one phone ZIP/7z archive, or its .rom and matching .rpkg together.");
            }
            if (rom == null) throw new IOException("No phone .rom file found. Add an EKA2L1 phone dump, not a game or N-Gage installer.");
            String romHash = hash(rom), rpkgHash = rpkg == null ? "" : hash(rpkg);
            String id = digest(("rom=" + romHash + "\nrpkg=" + rpkgHash).getBytes(StandardCharsets.UTF_8));
            if (set(id).exists()) return read(id);
            File source = new File(pending, "source");
            if (!source.mkdir()) throw new IOException("Cannot retain system inputs");
            File retainedRom = new File(source, "phone.rom"), retainedRpkg = rpkg == null ? null : new File(source, "phone.rpkg");
            copy(rom, retainedRom);
            if (rpkg != null) copy(rpkg, retainedRpkg);
            if (files != inputs) for (File file : files) Files.deleteIfExists(file.toPath());
            if (!hash(retainedRom).equals(romHash) || (rpkg != null && !hash(retainedRpkg).equals(rpkgHash)))
                throw new IOException("System input changed while importing");
            File runtime = new File(pending, "runtime");
            if (!runtime.mkdir()) throw new IOException("Cannot create system environment");
            String[] info = installer.install(runtime, retainedRom, retainedRpkg);
            if (info == null || info.length != 3 || Arrays.stream(info).anyMatch(s -> s == null || s.isEmpty()))
                throw new IOException("The system files could not be identified");
            if (!new File(runtime, "data/devices.yml").isFile()) throw new IOException("System installation is incomplete");
            JSONObject metadata = new JSONObject().put("version", 1).put("id", id)
                    .put("firmware", info[0]).put("model", info[1]).put("os", info[2])
                    .put("romSha256", romHash).put("rpkgSha256", rpkgHash);
            writeJson(new File(pending, "set.json"), metadata);
            // Runtime stores relative data paths; promotion never merges another phone's saves.
            Files.move(pending.toPath(), set(id).toPath(), StandardCopyOption.ATOMIC_MOVE);
            return read(id);
        } finally { removePending(pending); }
    }
    void recoverInterruptedImports() throws IOException {
        File[] dirs = base.listFiles();
        if (dirs != null) for (File dir : dirs) if (dir.getName().startsWith("pending-")) removePending(dir);
    }
    private void removePending(File dir) throws IOException {
        if (!dir.getParentFile().getCanonicalFile().equals(base.getCanonicalFile()) || !dir.getName().startsWith("pending-"))
            throw new IOException("Invalid temporary system directory");
        if (!Files.exists(dir.toPath(), LinkOption.NOFOLLOW_LINKS)) return;
        Files.walkFileTree(dir.toPath(), new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                Files.delete(path); return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult postVisitDirectory(Path path, IOException error) throws IOException {
                if (error != null) throw error;
                Files.delete(path); return FileVisitResult.CONTINUE;
            }
        });
    }
    static String hash(File file) throws IOException {
        MessageDigest digest = sha256();
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[65536];
            for (int n; (n = input.read(buffer)) != -1;) digest.update(buffer, 0, n);
        }
        return hex(digest.digest());
    }
    static String digest(byte[] bytes) { return hex(sha256().digest(bytes)); }
    static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException error) { throw new AssertionError(error); }
    }
    static String hex(byte[] bytes) {
        char[] text = new char[bytes.length * 2];
        char[] digits = "0123456789abcdef".toCharArray();
        for (int i = 0; i < bytes.length; i++) {
            text[i * 2] = digits[(bytes[i] & 255) >>> 4]; text[i * 2 + 1] = digits[bytes[i] & 15];
        }
        return new String(text);
    }
    private static void copy(File from, File to) throws IOException {
        try (InputStream input = new FileInputStream(from); FileOutputStream output = new FileOutputStream(to)) {
            byte[] buffer = new byte[65536];
            for (int n; (n = input.read(buffer)) != -1;) output.write(buffer, 0, n);
            output.getFD().sync();
        }
    }
    private static JSONObject readJson(File file) throws IOException {
        AtomicFile atomic = new AtomicFile(file);
        try { return new JSONObject(new String(atomic.readFully(), StandardCharsets.UTF_8)); }
        catch (JSONException error) { throw new IOException("Invalid system catalog data", error); }
    }
    private static void writeJson(File file, JSONObject json) throws IOException {
        AtomicFile atomic = new AtomicFile(file);
        FileOutputStream output = atomic.startWrite();
        try {
            output.write(json.toString().getBytes(StandardCharsets.UTF_8));
            atomic.finishWrite(output);
        } catch (IOException | RuntimeException error) { atomic.failWrite(output); throw error; }
    }
}
