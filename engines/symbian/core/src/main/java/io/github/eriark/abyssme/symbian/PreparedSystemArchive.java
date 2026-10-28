package io.github.eriark.abyssme.symbian;

import com.github.eka2l1.emu.Emulator;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.sevenz.SevenZFileOptions;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.CRC32;

/** Reads prepared phone collections, never their writable drives or host configuration. */
final class PreparedSystemArchive {
    static final long MAX_INPUT = 4L * 1024 * 1024 * 1024;
    private static final long MAX_EXPANDED = 8L * 1024 * 1024 * 1024;
    interface Progress {
        void update(String text) throws IOException;
        default void update(SystemProgress step, Object... arguments) throws IOException {
            update(step.fallback(arguments));
        }
    }
    static final class Dump {
        final String hint;
        final File rom, z;
        String romHash, zHash;
        Dump(String hint, File root) {
            this.hint = hint; rom = new File(root, "phone.rom"); z = new File(root, "z");
        }
    }
    private static final class Item {
        final String path;
        final long size, crc;
        final boolean directory;
        String phone, relative;
        Item(String path, long size, long crc, boolean directory) throws IOException {
            this.path = normalize(path); this.size = size; this.crc = crc; this.directory = directory;
            if (size < 0 || size > 1024L * 1024 * 1024) throw new SystemFailure(SystemFailure.Code.IMPORT_INVALID_SYSTEM_ARCHIVE_ENTRY_SIZE);
        }
    }
    static boolean isArchive(File file) throws IOException {
        return GameImport.isZip(file) || isSevenZ(file);
    }
    private static boolean isSevenZ(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            byte[] magic = new byte[6];
            return in.read(magic) == 6 && Arrays.equals(magic, new byte[]{0x37, 0x7a, (byte)0xbc, (byte)0xaf, 0x27, 0x1c});
        }
    }
    private static String normalize(String raw) throws IOException {
        String name = raw.replace('\\', '/').toLowerCase(Locale.ROOT);
        if (name.length() > 1024 || name.startsWith("/") || name.contains(":") || name.indexOf('\0') >= 0)
            throw new SystemFailure(SystemFailure.Code.IMPORT_UNSAFE_PATH_IN_SYSTEM_ARCHIVE);
        for (int i = 0; i < name.length(); i++) if (name.charAt(i) < 32) throw new SystemFailure(SystemFailure.Code.IMPORT_INVALID_ARCHIVE_PATH_CHARACTERS);
        if (name.endsWith("/")) name = name.substring(0, name.length() - 1);
        String[] parts = name.split("/", -1);
        if (parts.length > 64) throw new SystemFailure(SystemFailure.Code.IMPORT_SYSTEM_ARCHIVE_PATHS_ARE_TOO_DEEP);
        for (String part : parts) if (part.isEmpty() || part.equals(".") || part.equals(".."))
            throw new SystemFailure(SystemFailure.Code.IMPORT_UNSAFE_PATH_IN_SYSTEM_ARCHIVE);
        return name;
    }
    /** Empty means a legacy ROM/RPKG archive; malformed prepared collections fail explicitly. */
    static List<Dump> extract(File archive, File destination, Progress progress) throws IOException {
        return extract(archive, destination, progress, new ArrayList<>());
    }
    static List<Dump> extract(File archive, File destination, Progress progress, List<String> skipped) throws IOException {
        return extractReported(archive, destination, progress,
                (source, failure) -> skipped.add(source + ": " + failure.getMessage()));
    }
    static List<Dump> extractReported(File archive, File destination, Progress progress,
            java.util.function.BiConsumer<String, SystemFailure> skipped) throws IOException {
        if (archive.length() > MAX_INPUT) throw new SystemFailure(SystemFailure.Code.IMPORT_SYSTEM_ARCHIVE_EXCEEDS_4_GB);
        progress.update(SystemProgress.INSPECT_SYSTEM);
        boolean seven = isSevenZ(archive);
        List<Item> items = new ArrayList<>();
        try (SevenZFile sz = seven ? openSeven(archive) : null;
             ZipFile zip = seven ? null : new ZipFile(archive)) {
            long expanded = 0;
            if (seven) {
                for (SevenZArchiveEntry entry : sz.getEntries()) {
                    int mode = entry.getHasWindowsAttributes() ? (entry.getWindowsAttributes() >>> 16) & 0170000 : 0;
                    if (entry.isAntiItem() || (entry.getHasWindowsAttributes() && (entry.getWindowsAttributes() & 0x400) != 0)
                            || (mode != 0 && mode != 0100000 && mode != 0040000))
                        throw new SystemFailure(SystemFailure.Code.IMPORT_LINKS_AND_DELETION_ENTRIES_ARE_NOT_SUPPORTED_IN_SYSTEM);
                    items.add(new Item(entry.getName(), entry.getSize(), entry.getHasCrc() ? entry.getCrcValue() : -1, entry.isDirectory()));
                    if (items.size() > 250000) throw new SystemFailure(SystemFailure.Code.IMPORT_TOO_MANY_FILES_IN_SYSTEM_ARCHIVE);
                }
            } else {
                Enumeration<ZipArchiveEntry> entries = zip.getEntries();
                while (entries.hasMoreElements()) {
                    ZipArchiveEntry entry = entries.nextElement();
                    int type = entry.getUnixMode() & 0170000;
                    if (entry.isUnixSymlink() || (type != 0 && type != 0100000 && type != 0040000)
                            || (entry.getExternalAttributes() & 0x400) != 0)
                        throw new SystemFailure(SystemFailure.Code.IMPORT_LINKS_ARE_NOT_SUPPORTED_IN_SYSTEM_ARCHIVES);
                    items.add(new Item(entry.getName(), entry.getSize(), entry.getCrc(), entry.isDirectory()));
                    if (items.size() > 250000) throw new SystemFailure(SystemFailure.Code.IMPORT_TOO_MANY_FILES_IN_SYSTEM_ARCHIVE);
                }
            }
            Map<String, Boolean> paths = new HashMap<>();
            Set<String> phones = new TreeSet<>(), roms = new HashSet<>(), disks = new HashSet<>();
            long selected = 0, retainedRoms = 0;
            for (Item item : items) {
                if (paths.put(item.path, item.directory) != null) throw new SystemFailure(SystemFailure.Code.ARCHIVE_PATH_COLLISION, item.path);
                expanded += item.size;
                if (expanded > MAX_EXPANDED) throw new SystemFailure(SystemFailure.Code.IMPORT_EXPANDED_SYSTEM_ARCHIVE_EXCEEDS_8_GB);
                String[] parts = item.path.split("/");
                for (int i = 0; i + 3 < parts.length; i++) {
                    if (!parts[i].equals("data")) continue;
                    if (parts[i + 1].equals("roms") && i + 4 == parts.length && parts[i + 3].equals("sym.rom")) {
                        item.phone = String.join("/", Arrays.copyOfRange(parts, 0, i + 1)) + "/" + parts[i + 2];
                        item.relative = "";
                        if (!item.directory) roms.add(item.phone);
                        break;
                    }
                    if (parts[i + 1].equals("drives") && parts[i + 2].equals("z") && i + 4 < parts.length) {
                        item.phone = String.join("/", Arrays.copyOfRange(parts, 0, i + 1)) + "/" + parts[i + 3];
                        item.relative = String.join("/", Arrays.copyOfRange(parts, i + 4, parts.length));
                        if (!item.directory) disks.add(item.phone);
                        break;
                    }
                }
                if (item.phone != null && !item.directory) {
                    if ((item.relative.contains("/versions/") && item.size > 1024 * 1024)
                            || (item.relative.endsWith("/hal.dll") && item.size > 32L * 1024 * 1024))
                        throw new SystemFailure(SystemFailure.Code.IMPORT_OVERSIZED_PHONE_METADATA);
                    phones.add(item.phone); selected += item.size;
                    if (item.relative.isEmpty()) retainedRoms += item.size;
                }
            }
            for (Item item : items) {
                for (int slash = item.path.lastIndexOf('/'); slash > 0; slash = item.path.lastIndexOf('/', slash - 1))
                    if (Boolean.FALSE.equals(paths.get(item.path.substring(0, slash)))) throw new SystemFailure(SystemFailure.Code.IMPORT_CONFLICTING_ARCHIVE_PATHS);
            }
            if (phones.isEmpty()) return Collections.emptyList();
            if (phones.size() > 64) throw new SystemFailure(SystemFailure.Code.IMPORT_TOO_MANY_PHONES_IN_SYSTEM_ARCHIVE);
            Set<String> incomplete = new HashSet<>(phones);
            phones.retainAll(roms); phones.retainAll(disks);
            incomplete.removeAll(phones);
            for (String phone : new TreeSet<>(incomplete)) skipped.accept(phone.substring(phone.lastIndexOf('/') + 1),
                    new SystemFailure(roms.contains(phone) ? SystemFailure.Code.Z_DRIVE_MISSING : SystemFailure.Code.ROM_MISSING));
            if (phones.isEmpty()) throw new SystemFailure(SystemFailure.Code.IMPORT_NO_COMPLETE_ROM_AND_Z_DRIVE_PAIR_FOUND);
            if (!destination.isDirectory() && !destination.mkdirs()) throw new SystemFailure(SystemFailure.Code.IMPORT_CANNOT_CREATE_ARCHIVE_STAGING_DIRECTORY);
            if (destination.getUsableSpace() < selected + retainedRoms + 256L * 1024 * 1024)
                throw new SystemFailure(SystemFailure.Code.IMPORT_NOT_ENOUGH_FREE_SPACE_FOR_SYSTEM_FILES);
            Map<String, Dump> dumps = new TreeMap<>();
            Map<String, TreeMap<String, String>> hashes = new HashMap<>();
            for (String phone : phones) {
                File root = new File(destination, Integer.toString(dumps.size()));
                if (!root.mkdir()) throw new SystemFailure(SystemFailure.Code.IMPORT_CANNOT_STAGE_PHONE);
                dumps.put(phone, new Dump(phone.substring(phone.lastIndexOf('/') + 1), root));
                hashes.put(phone, new TreeMap<>());
            }
            if (seven) {
                Map<String, Item> expected = new HashMap<>();
                for (Item item : items) expected.put(item.path, item);
                Set<String> seen = new HashSet<>();
                Emulator.extractSystemArchive(archive.getAbsolutePath(), new Emulator.ArchivePlan() {
                    long last;
                    @Override public String destination(String name, long size, boolean directory) throws IOException {
                        String path = normalize(name);
                        Item item = expected.get(path);
                        if (item == null || item.size != size || item.directory != directory || !seen.add(path))
                            throw new SystemFailure(SystemFailure.Code.IMPORT_SYSTEM_ARCHIVE_CHANGED_DURING_EXTRACTION);
                        if (directory || item.phone == null || !phones.contains(item.phone)) return "";
                        Dump dump = dumps.get(item.phone);
                        return (item.relative.isEmpty() ? dump.rom : new File(dump.z, item.relative)).getAbsolutePath();
                    }
                    @Override public void progress(long completed, long total) throws IOException {
                        long now = System.nanoTime();
                        if (now - last > 250000000L) {
                            progress.update(SystemProgress.READ_SYSTEM, completed * 100 / Math.max(1, total)); last = now;
                        }
                    }
                });
                if (seen.size() != items.size()) throw new SystemFailure(SystemFailure.Code.IMPORT_INCOMPLETE_SYSTEM_ARCHIVE);
            }
            long done = 0, lastUpdate = 0;
            byte[] buffer = new byte[65536];
            Enumeration<ZipArchiveEntry> zipEntries = seven ? null : zip.getEntries();
            for (Item item : items) {
                ZipArchiveEntry ze = seven ? null : zipEntries.nextElement();
                if (item.directory || item.phone == null || !phones.contains(item.phone)) continue;
                Dump dump = dumps.get(item.phone);
                File target = item.relative.isEmpty() ? dump.rom : new File(dump.z, item.relative);
                if (!target.getParentFile().isDirectory() && !target.getParentFile().mkdirs()) throw new SystemFailure(SystemFailure.Code.IMPORT_CANNOT_CREATE_Z_DRIVE_DIRECTORY);
                CRC32 crc = new CRC32();
                java.security.MessageDigest digest = SystemFileCatalog.sha256();
                long written = 0;
                // Native libarchive handles BCJ2; verify decoded bytes against the preflight CRCs.
                try (InputStream input = seven ? new BufferedInputStream(new FileInputStream(target)) : zip.getInputStream(ze);
                     OutputStream out = seven ? null : new BufferedOutputStream(new FileOutputStream(target))) {
                    for (int n; (n = input.read(buffer)) != -1;) {
                        written += n; done += n;
                        if (written > item.size) throw new SystemFailure(SystemFailure.Code.IMPORT_SYSTEM_ARCHIVE_SIZE_MISMATCH);
                        crc.update(buffer, 0, n); digest.update(buffer, 0, n); if (out != null) out.write(buffer, 0, n);
                        long now = System.nanoTime();
                        if (now - lastUpdate > 250000000L) {
                            progress.update(seven ? SystemProgress.CHECK_SYSTEM : SystemProgress.READ_SYSTEM, done * 100 / Math.max(1, selected)); lastUpdate = now;
                        }
                    }
                }
                if (written != item.size || (item.crc != -1 && crc.getValue() != item.crc)) throw new SystemFailure(SystemFailure.Code.IMPORT_DAMAGED_SYSTEM_ARCHIVE_ENTRY);
                if (item.relative.isEmpty()) {
                    if (written < 256) throw new SystemFailure(SystemFailure.Code.IMPORT_INCOMPLETE_PHONE_ROM);
                    dump.romHash = SystemFileCatalog.hex(digest.digest());
                } else hashes.get(item.phone).put(item.relative, written + ":" + SystemFileCatalog.hex(digest.digest()));
            }
            for (Map.Entry<String, Dump> entry : dumps.entrySet()) {
                StringBuilder manifest = new StringBuilder();
                hashes.get(entry.getKey()).forEach((path, hash) -> manifest.append(path).append('\0').append(hash).append('\n'));
                entry.getValue().zHash = SystemFileCatalog.digest(manifest.toString().getBytes(StandardCharsets.UTF_8));
            }
            return new ArrayList<>(dumps.values());
        }
    }
    private static SevenZFile openSeven(File file) throws IOException {
        return new SevenZFile(file, SevenZFileOptions.builder().withMaxMemoryLimitInKb(128 * 1024).build());
    }
}
