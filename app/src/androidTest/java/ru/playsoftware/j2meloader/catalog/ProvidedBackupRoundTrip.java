// SPDX-License-Identifier: GPL-3.0-only
package ru.playsoftware.j2meloader.catalog;

import android.content.Context;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import ru.playsoftware.j2meloader.applist.AppItem;
import ru.playsoftware.j2meloader.appsdb.AppDatabase;
import ru.playsoftware.j2meloader.config.ProfileModel;
import ru.playsoftware.j2meloader.config.ProfilesManager;
import static org.junit.Assert.*;

/** Shared opt-in fixture helper. Caller owns all directories and must stop the game first. */
public final class ProvidedBackupRoundTrip {
    public static final class Restored {
        public final File root;
        public final String id;
        public final int saveFiles;
        Restored(File root, String id, int saveFiles) {
            this.root = root; this.id = id; this.saveFiles = saveFiles;
        }
    }

    public static Restored restore(Context context, File source, AppItem game, File work,
                                   File report, boolean requireSaves) throws Exception {
        assertTrue(work.mkdirs());
        String id = game.getPath();
        // Copy just this fixture, excluding any unrelated library/shared filesystem data.
        File snapshot = new File(work, "source");
        for (String dir : new String[]{"converted", "configs", "data"})
            copy(new File(source, dir + "/" + id), new File(snapshot, dir + "/" + id));
        Map<String, String> saves = hashes(new File(snapshot, "data/" + id));
        if (requireSaves) assertFalse("Game must generate real save files before backup", saves.isEmpty());
        AtomicBoolean cancelled = new AtomicBoolean();
        LibraryArchive archive = new LibraryArchive(cancelled);
        File backup = new File(work, "game.abyssme");
        assertEquals(1, archive.export(snapshot, Collections.singletonList(game), work,
                () -> new FileOutputStream(backup)));
        File stage;
        try (FileInputStream input = new FileInputStream(backup)) { stage = archive.unpack(input, work); }
        File destination = new File(work, "restored");
        assertTrue(destination.mkdirs());
        try (AppDatabase db = AppDatabase.open(context, destination.getPath())) {
            LibraryImporter.Catalog catalog = new LibraryImporter.Catalog() {
                public AppItem bySource(String key) { return db.appItemDao().getBySourceKey(key); }
                public AppItem byPath(String path) { return db.appItemDao().getByPath(path); }
                public AppItem byTitle(String title, String vendor) { return db.appItemDao().get(title, vendor); }
                public void insert(AppItem item) { db.appItemDao().insertImported(item); }
            };
            LibraryImporter importer = new LibraryImporter(context, destination, catalog, cancelled);
            LibraryImporter.Preview preview = importer.scanBackup(stage);
            assertEquals(1, preview.entries.size());
            LibraryImporter.Entry entry = preview.entries.get(0);
            assertNull(entry.problem);
            assertEquals(!saves.isEmpty(), entry.hasSaves);
            assertFalse("Never export compiled Java code", new File(stage, "converted/" + id + "/converted.dex").exists());
            importer.importEntry(entry);
            AppItem restored = catalog.bySource(entry.sourceKey);
            assertNotNull(restored);
            String restoredId = restored.getPath();
            assertEquals(game.getTitle(), restored.getTitle());
            assertEquals(game.isFavorite(), restored.isFavorite());
            assertEquals(game.getPlayCount(), restored.getPlayCount());
            assertEquals(game.getLastPlayedAt(), restored.getLastPlayedAt());
            File restoredData = new File(destination, "data/" + restoredId);
            assertEquals("Restored save paths and bytes", saves, hashes(restoredData));
            String payload = new File(snapshot, "converted/" + id + "/res.jar").exists() ? "res.jar" : "game.mpn";
            assertEquals(SourceIdentity.sha256(new File(snapshot, "converted/" + id + "/" + payload)),
                    SourceIdentity.sha256(new File(destination, "converted/" + restoredId + "/" + payload)));
            if (payload.equals("res.jar")) assertTrue(new File(destination, "converted/" + restoredId + "/converted.dex").isFile());
            ProfileModel original = ProfilesManager.loadConfig(new File(snapshot, "configs/" + id));
            if (original != null) {
                ProfileModel profile = ProfilesManager.loadConfig(new File(destination, "configs/" + restoredId));
                assertNotNull(profile);
                assertEquals(original.screenWidth, profile.screenWidth);
                assertEquals(original.screenHeight, profile.screenHeight);
                assertEquals(original.screenRotation, profile.screenRotation);
            }
            LibraryImporter.Entry duplicate = importer.scanBackup(stage).entries.get(0);
            assertNotNull("Duplicate restore must be rejected", duplicate.problem);
            try { importer.importEntry(duplicate); fail("Duplicate restore must not publish"); }
            catch (java.io.IOException expected) { }
            assertEquals("Duplicate restore must retain current saves", saves, hashes(restoredData));
            assertEquals("Export must not change the source saves", saves, hashes(new File(source, "data/" + id)));
            org.json.JSONObject result = new org.json.JSONObject();
            result.put("saveFiles", saves.size());
            result.put("saveHashesBeforeAndAfterRestore", new org.json.JSONObject(saves));
            result.put("archiveSha256", SourceIdentity.sha256(backup));
            result.put("duplicateRestoreRejected", true);
            result.put("scope", "Byte preservation before relaunch; screenshots must establish any game progress");
            Files.write(report.toPath(), result.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return new Restored(destination, restoredId, saves.size());
        } finally { LibraryArchive.remove(stage); }
    }

    public static Map<String, String> hashes(File directory) throws Exception {
        Map<String, String> result = new TreeMap<>();
        if (!directory.exists()) return result;
        try (java.util.stream.Stream<Path> paths = Files.walk(directory.toPath())) {
            for (Path path : (Iterable<Path>) paths.filter(Files::isRegularFile)::iterator)
                result.put(directory.toPath().relativize(path).toString(), SourceIdentity.sha256(path.toFile()));
        }
        return result;
    }

    public static void copy(File source, File target) throws Exception {
        if (!source.exists()) return;
        try (java.util.stream.Stream<Path> paths = Files.walk(source.toPath())) {
            for (Path path : (Iterable<Path>) paths::iterator) {
                assertFalse("Fixture cannot contain links", Files.isSymbolicLink(path));
                Path to = target.toPath().resolve(source.toPath().relativize(path));
                if (Files.isDirectory(path)) Files.createDirectories(to);
                else { Files.createDirectories(to.getParent()); Files.copy(path, to); }
            }
        }
    }
}
