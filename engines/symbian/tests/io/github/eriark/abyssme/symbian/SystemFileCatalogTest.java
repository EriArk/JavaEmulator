package io.github.eriark.abyssme.symbian;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.*;
import static org.junit.Assert.*;

public final class SystemFileCatalogTest {
    private File folder() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        return Files.createTempDirectory(context.getCacheDir().toPath(), "catalog-test-").toFile();
    }
    private String[] installed(File runtime, String firmware, String os) throws Exception {
        File data = new File(runtime, "data");
        assertTrue(data.mkdirs());
        Files.write(new File(data, "devices.yml").toPath(), new byte[]{1});
        return new String[]{firmware, "Test phone", os};
    }
    private File input(File root, String name, byte... bytes) throws Exception {
        File file = new File(root, name); Files.write(file.toPath(), bytes); return file;
    }
    private File zip(File root, String name, String[] paths, byte[]... contents) throws Exception {
        File file = new File(root, name);
        try (ZipOutputStream output = new ZipOutputStream(new FileOutputStream(file))) {
            for (int i = 0; i < paths.length; i++) {
                output.putNextEntry(new ZipEntry(paths[i])); output.write(contents[i]); output.closeEntry();
            }
        }
        return file;
    }
    @Test public void duplicateSystemContentPreservesSavesWithoutReinstall() throws Exception {
        File root = folder();
        SystemFileCatalog catalog = new SystemFileCatalog(new File(root, "systems"));
        File raw = input(root, "phone.rom", (byte)1, (byte)2);
        int[] calls = {0};
        SystemFileCatalog.Installer installer = (runtime, rom, rpkg) -> {
            calls[0]++; return installed(runtime, "RH-29", "epoc6");
        };
        SystemFileCatalog.Entry entry = catalog.add(Collections.singletonList(raw), installer);
        File save = new File(catalog.runtime(entry), "save.dat");
        Files.write(save.toPath(), new byte[]{7, 8, 9});
        File archive = zip(root, "renamed.zip", new String[]{"nested/SYM.ROM", "data/save.dat"},
                new byte[]{1, 2}, new byte[]{99});
        SystemFileCatalog.Entry duplicate = catalog.add(Collections.singletonList(archive), installer);
        assertEquals(entry.id, duplicate.id);
        assertEquals(1, calls[0]);
        assertEquals(1, catalog.list().size());
        assertArrayEquals(new byte[]{7, 8, 9}, Files.readAllBytes(save.toPath()));
        assertFalse(new File(catalog.runtime(entry), "data/save.dat").exists());
        assertEquals(0, new File(root, "systems").listFiles(f -> f.getName().startsWith("pending-")).length);
    }
    @Test public void changedFirmwareIsIsolatedAndBindingsPersist() throws Exception {
        File root = folder(); File base = new File(root, "systems");
        SystemFileCatalog catalog = new SystemFileCatalog(base);
        File rom = input(root, "phone.rom", (byte)1);
        SystemFileCatalog.Installer installer = (runtime, r, p) -> installed(runtime, "RM-320", "epoc93fp1");
        SystemFileCatalog.Entry first = catalog.add(Collections.singletonList(rom), installer);
        Files.write(rom.toPath(), new byte[]{2});
        SystemFileCatalog.Entry second = catalog.add(Collections.singletonList(rom), installer);
        assertNotEquals(first.id, second.id);
        assertNotEquals(catalog.runtime(first), catalog.runtime(second));
        String game = SystemFileCatalog.hash(rom);
        assertEquals(2, catalog.candidates(GameImport.Kind.NGAGE2, game).size());
        catalog.bind(game, second); catalog.activate(second);
        catalog = new SystemFileCatalog(base);
        assertEquals(second.id, catalog.candidates(GameImport.Kind.NGAGE2, game).get(0).id);
        assertEquals(1, catalog.candidates(GameImport.Kind.NGAGE2, game).size());
        assertTrue(catalog.candidates(GameImport.Kind.CARD, game).isEmpty());
        assertEquals(catalog.runtime(second), catalog.activeRoot(root));
    }
    @Test public void failedAndInterruptedImportsNeverPublishASetOrDeleteOutsideFiles() throws Exception {
        File root = folder(); File base = new File(root, "systems");
        SystemFileCatalog catalog = new SystemFileCatalog(base);
        File rom = input(root, "phone.rom", (byte)1);
        try {
            catalog.add(Collections.singletonList(rom), (runtime, r, p) -> { throw new IOException("interrupted"); });
            fail();
        } catch (IOException expected) { }
        assertTrue(catalog.list().isEmpty());
        try {
            catalog.add(Collections.singletonList(rom), (runtime, r, p) -> new String[]{"RH-29", "Phone", "epoc6"});
            fail("Incomplete install must not be committed");
        } catch (IOException expected) { }
        assertEquals(0, base.list().length);
        File outside = input(root, "keep", (byte)9);
        File partial = new File(base, "pending-test"); assertTrue(partial.mkdir());
        Files.createSymbolicLink(new File(partial, "link").toPath(), outside.toPath());
        catalog.recoverInterruptedImports();
        assertTrue(outside.isFile()); assertFalse(partial.exists());
    }
    @Test public void pathIdentityAndMissingFilesFailWithoutFallback() throws Exception {
        File root = folder();
        SystemFileCatalog catalog = new SystemFileCatalog(new File(root, "systems"));
        try { catalog.read("../escape"); fail(); } catch (IOException expected) { }
        File rom = input(root, "phone.rom", (byte)1);
        SystemFileCatalog.Entry entry = catalog.add(Collections.singletonList(rom),
                (runtime, r, p) -> installed(runtime, "RH-29", "epoc6"));
        catalog.activate(entry);
        Files.delete(new File(catalog.runtime(entry), "data/devices.yml").toPath());
        try { catalog.activeRoot(root); fail("Do not silently launch a different environment"); }
        catch (IOException expected) { }
    }
    @Test public void packageContentWinsOverExtensionAndZipWrapper() throws Exception {
        File root = folder();
        byte[] modern = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN).putInt(0x10201a7a).array();
        File renamed = input(root, "not-a-sis.bin", modern);
        try (GameImport game = GameImport.inspect(renamed)) { assertEquals(GameImport.Kind.SIS_MODERN, game.kind); }
        File archive = zip(root, "platform.zip", new String[]{"folder/platform.sis", "readme.txt"}, modern, new byte[]{1});
        String hash = SystemFileCatalog.hash(renamed);
        File extracted;
        try (GameImport game = GameImport.inspect(archive)) {
            extracted = game.file; assertEquals(hash, game.hash); assertEquals(GameImport.Kind.SIS_MODERN, game.kind);
        }
        assertFalse(extracted.exists()); assertTrue(archive.exists());
        byte[] legacy = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN).putInt(0x1234).putInt(0x10003a12).array();
        try (GameImport game = GameImport.inspect(input(root, "legacy.bin", legacy))) { assertEquals(GameImport.Kind.SIS_LEGACY, game.kind); }
        byte[] mime = "Content-Type: multipart/mixed; boundary=foo\r\nContent-Description: Ngage-Install-File\r\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        try (GameImport game = GameImport.inspect(input(root, "ngage.bin", mime))) { assertEquals(GameImport.Kind.NGAGE2, game.kind); }
    }
    @Test public void mixedUnsafeAndFakeGameArchivesAreRejected() throws Exception {
        File root = folder(); byte[] modern = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN).putInt(0x10201a7a).array();
        for (String[] paths : new String[][]{{"first.sis", "second.sis"}, {"first.sis", "../escape"},
                {"first.sis", "C:/escape"}, {"system/apps/game/game.app", "first.sis"},
                {"system/apps/game/game.app", "system/apps/unrelated/other.sis"},
                {"game.sis", "GAME.SIS"},
                {"one/system/apps/game/game.app", "two/system/apps/game/game.app"},
                {"system/apps/game/game.app", "system/apps/game/other.n-gage"}}) {
            File archive = zip(root, "games.zip", paths, modern, modern);
            try (GameImport ignored = GameImport.inspect(archive)) { fail(Arrays.toString(paths)); }
            catch (IOException expected) { }
        }
        File fake = zip(root, "fake.zip", new String[]{"game.sis"}, new byte[]{0, 1, 2});
        try (GameImport ignored = GameImport.inspect(fake)) { fail(); } catch (IOException expected) { }
        assertEquals(0, root.listFiles(f -> f.getName().startsWith("game-")).length);
        File card = zip(root, "card.zip", new String[]{"system/apps/game/game.app"}, new byte[]{1});
        try (GameImport game = GameImport.inspect(card)) { assertEquals(GameImport.Kind.CARD, game.kind); }
    }
    @Test public void cardIncludesItsEmbeddedInstallerWithoutBecomingAmbiguous() throws Exception {
        File root = folder();
        File archive = zip(root, "snakes-shape.zip", new String[]{
                "Bundle/System/Apps/6r45/6r45.app", "Bundle/System/Apps/6r45_1/6r45_1.app",
                "Bundle/System/Apps/6r45_1/6r45.sis"}, new byte[]{1}, new byte[]{2}, new byte[]{3});
        try (GameImport game = GameImport.inspect(archive)) {
            assertEquals(GameImport.Kind.CARD, game.kind);
            assertEquals(archive, game.file);
        }
        assertTrue(archive.isFile());
    }
}
