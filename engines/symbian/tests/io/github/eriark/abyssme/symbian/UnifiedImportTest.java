package io.github.eriark.abyssme.symbian;

import android.content.Context;
import android.net.Uri;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.zip.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class UnifiedImportTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private File root() throws IOException { return Files.createTempDirectory(context.getCacheDir().toPath(), "unified-import-").toFile(); }
    private byte[] sis(int uid) { return ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN).putInt(0x10201a7a).putInt(0).putInt(uid).array(); }
    private File zip(File root, String[] names, byte[][] data) throws IOException {
        File file = File.createTempFile("input-", ".zip", root);
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(file))) {
            for (int i = 0; i < names.length; i++) { out.putNextEntry(new ZipEntry(names[i])); out.write(data[i]); out.closeEntry(); }
        }
        return file;
    }
    private ImportKind.Kind zipKind(File root, String name, byte[] data) throws Exception {
        return ImportKind.inspect(zip(root, new String[]{name}, new byte[][]{data}), "selected.zip");
    }
    private File seven(String... names) throws Exception {
        File file = new File(root(), "input.7z");
        try (org.apache.commons.compress.archivers.sevenz.SevenZOutputFile out =
                     new org.apache.commons.compress.archivers.sevenz.SevenZOutputFile(file)) {
            for (String name : names) {
                org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry entry =
                        new org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry();
                entry.setName(name); entry.setSize(3);
                out.putArchiveEntry(entry); out.write(new byte[]{1, 2, 3}); out.closeArchiveEntry();
            }
        }
        return file;
    }
    @Test public void sevenRoutesJavaMophunNativeAndSystemsWithoutNativeDecoder() throws Exception {
        assertEquals(ImportKind.Kind.EXISTING, ImportKind.inspect(seven("java/game.JAR", "mophun/game.MPN"), null));
        assertEquals(ImportKind.Kind.EXISTING, ImportKind.inspect(seven("game.mpn"), null));
        assertEquals(ImportKind.Kind.CONTAINER, ImportKind.inspect(seven("game.sisx"), null));
        assertEquals(ImportKind.Kind.CONTAINER, ImportKind.inspect(seven("System/Apps/Snakes/Snakes.app"), null));
        assertEquals(ImportKind.Kind.SYSTEM, ImportKind.inspect(seven("phone/system.rpkg"), null));
        assertEquals(ImportKind.Kind.SYSTEM, ImportKind.inspect(seven("data/roms/test/sym.rom", "data/drives/c/game.sis"), null));
    }
    @Test public void sevenRejectsAmbiguousTypesAndUnsafePaths() throws Exception {
        for (String[] names : new String[][]{{"game.mpn", "game.sisx"}, {"phone.rom", "game.jar"}, {"../game.mpn"}}) {
            try { ImportKind.inspect(seven(names), null); fail(); } catch (IOException expected) { }
        }
    }
    @Test public void archiveContentsRouteJavaGamesSystemsAndPlatformSeparately() throws Exception {
        File root = root(); byte[] dummy = {1, 2, 3};
        assertEquals(ImportKind.Kind.EXISTING, zipKind(root, "META-INF/MANIFEST.MF", dummy));
        assertEquals(ImportKind.Kind.EXISTING,ImportKind.inspect(zip(root,new String[]{"META-INF/MANIFEST.MF","data/a.zip","data/b.zip"},new byte[][]{dummy,dummy,dummy}),"game.jar"));
        assertEquals(ImportKind.Kind.EXISTING, zipKind(root, "collection/game.jar", dummy));
        assertEquals(ImportKind.Kind.EXISTING, zipKind(root, "game.mpn", dummy));
        assertEquals(ImportKind.Kind.SYSTEM, zipKind(root, "N95/system.rom", dummy));
        assertEquals(ImportKind.Kind.SYSTEM, zipKind(root, "N95/system.rpkg", dummy));
        assertEquals(ImportKind.Kind.GAME, zipKind(root, "System/Apps/Snakes/Snakes.app", dummy));
        assertEquals(ImportKind.Kind.GAME, zipKind(root, "game.sisx", sis(123)));
        assertEquals(ImportKind.Kind.PLATFORM, zipKind(root, "N-Gage/platform.sis", sis(0x20003b78)));
        File prepared = zip(root, new String[]{"phone/data/roms/rm-320/sym.rom", "phone/data/drives/c/game.sis"},
                new byte[][]{dummy, sis(123)});
        assertEquals(ImportKind.Kind.SYSTEM, ImportKind.inspect(prepared, null));
        assertEquals(ImportKind.Kind.BATCH,ImportKind.inspect(zip(root,new String[]{"one.sis","two.sisx"},new byte[][]{sis(123),sis(456)}),"games.zip"));
    }
    @Test public void directPackagesAreRecognizedByContentAndExistingTypesRetainTheirImporter() throws Exception {
        File root = root(), file = new File(root, "payload");
        Files.write(file.toPath(), sis(123));
        assertEquals(ImportKind.Kind.GAME, ImportKind.inspect(file, null));
        Files.write(file.toPath(), sis(0x20003b78));
        assertEquals(ImportKind.Kind.PLATFORM, ImportKind.inspect(file, "unknown.bin"));
        Files.write(file.toPath(), "Content-Type: multipart/mixed; boundary=test\r\nContent-Description: Ngage-Install-File\r\n".getBytes("US-ASCII"));
        assertEquals(ImportKind.Kind.GAME, ImportKind.inspect(file, "unknown.bin"));
        for (String name : new String[]{"game.JAD", "game.MPN", "game.kjx"})
            assertEquals(ImportKind.Kind.EXISTING, ImportKind.inspect(file, name));
        Files.write(file.toPath(), new byte[]{0x37, 0x7a, (byte)0xbc, (byte)0xaf, 0x27, 0x1c});
        try {ImportKind.inspect(file,"phones.7z");fail("Truncated 7z must not be treated as firmware");}catch(IOException expected){}
    }
    @Test public void mixedAmbiguousAndUnsafeArchivesFailBeforeAnyInstall() throws Exception {
        File root = root();
        String[][] cases = {{"system.rom", "game.sis"}, {"game.jar", "game.sis"}, {"../game.sis"}};
        for (String[] names : cases) {
            byte[][] data = new byte[names.length][]; java.util.Arrays.fill(data, sis(123));
            File input = zip(root, names, data);
            try { ImportKind.inspect(input, input.getName()); fail(java.util.Arrays.toString(names)); } catch (IOException expected) { }
        }
    }
    @Test public void savedPlatformSurvivesSourceRemovalAndDetectsTampering() throws Exception {
        File root = root(), source = new File(root, "platform.sis"), saved = new File(root, "saved");
        PlatformStore store = new PlatformStore(saved);
        assertNull(store.open());
        Files.write(source.toPath(), sis(123));
        try (GameImport wrong = GameImport.inspect(source)) {
            try { store.save(wrong); fail(); } catch (IOException expected) { }
        }
        assertNull(store.open());
        Files.write(source.toPath(), sis(0x20003b78));
        try (GameImport platform = GameImport.inspect(source)) { store.save(platform); }
        Files.delete(source.toPath()); File cached;
        try (GameImport restored = new PlatformStore(saved).open()) { assertTrue(restored.isPlatform()); cached = restored.file; }
        assertTrue(cached.isFile());
        Files.write(cached.toPath(), sis(123));
        try { store.open(); fail(); } catch (IOException expected) { }
        Files.write(new File(saved, "selected").toPath(), "../../outside".getBytes("US-ASCII"));
        try { store.open(); fail(); } catch (IOException expected) { }
    }
    @Test public void platformPointerRecoversBackupAndRejectsSymlinkPayloads() throws Exception {
        File root = root(), source = new File(root, "platform.sis"), saved = new File(root, "saved");
        PlatformStore store = new PlatformStore(saved);
        Files.write(source.toPath(), sis(0x20003b78)); File cached;
        try (GameImport platform = GameImport.inspect(source)) { store.save(platform); }
        Files.move(new File(saved, "selected").toPath(), new File(saved, "selected.bak").toPath());
        try (GameImport restored = store.open()) { assertNotNull(restored); cached = restored.file; }
        Files.delete(cached.toPath()); Files.createSymbolicLink(cached.toPath(), source.toPath());
        try { store.open(); fail(); } catch (IOException expected) { }
        try (GameImport platform = GameImport.inspect(source)) { store.save(platform); }
        assertFalse(Files.isSymbolicLink(cached.toPath()));
        try (GameImport restored = store.open()) { assertTrue(restored.isPlatform()); }
    }
    @Test public void transferClaimsOnlyAppOwnedRegularFilesAndCancellationCleansInput() throws Exception {
        File root = root(), input = new File(root, "game.sis"); Files.write(input.toPath(), sis(123));
        File staged = SymbianFiles.stage(context, Uri.fromFile(input));
        try {
            assertEquals(staged.getCanonicalFile(), SymbianFiles.claimStaged(context, staged.getName()));
            for (String token : new String[]{"../" + staged.getName(), "input-../other", "input-..\\other", input.getAbsolutePath()}) {
                try { SymbianFiles.claimStaged(context, token); fail(token); } catch (IOException expected) { }
            }
            File link = new File(staged.getParentFile(), "input-link");
            Files.createSymbolicLink(link.toPath(), input.toPath());
            try {
                try { SymbianFiles.claimStaged(context, link.getName()); fail(); } catch (IOException expected) { }
            } finally { Files.delete(link.toPath()); }
            int before = staged.getParentFile().list().length;
            Thread.currentThread().interrupt();
            try { SymbianFiles.stage(context, Uri.fromFile(input)); fail(); } catch (InterruptedIOException expected) { }
            finally { Thread.interrupted(); }
            assertEquals(before, staged.getParentFile().list().length);
        } finally { Files.delete(staged.toPath()); }
        assertTrue(input.isFile());
    }
}
