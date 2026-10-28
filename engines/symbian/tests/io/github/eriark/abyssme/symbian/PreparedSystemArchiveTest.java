package io.github.eriark.abyssme.symbian;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import com.github.eka2l1.emu.Emulator;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import static org.junit.Assert.*;
import static org.junit.Assume.*;

public final class PreparedSystemArchiveTest {
    private Context context() { return InstrumentationRegistry.getInstrumentation().getTargetContext(); }
    private File folder() throws Exception { return Files.createTempDirectory(context().getCacheDir().toPath(), "prepared-test-").toFile(); }
    private Map<String, byte[]> files(String prefix) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        for (String phone : new String[]{"rh-29", "rm-320"}) {
            byte[] rom = new byte[512]; Arrays.fill(rom, (byte)phone.length());
            files.put(prefix + "data/roms/" + phone + "/SYM.ROM", rom);
            files.put(prefix + "data/drives/z/" + phone + "/System/Versions/sw.txt", phone.getBytes(StandardCharsets.UTF_8));
        }
        files.put(prefix + "data/drives/c/private/save.dat", new byte[]{99});
        files.put(prefix + "data/drives/e/installed.app", new byte[]{99});
        files.put(prefix + "data/devices.yml", new byte[]{99});
        files.put(prefix + "config.yml", new byte[]{99});
        return files;
    }
    private File archive(File root, String name, Map<String, byte[]> files, boolean seven) throws Exception {
        File target = new File(root, name);
        if (seven) {
            try (SevenZOutputFile out = new SevenZOutputFile(target)) {
                for (Map.Entry<String, byte[]> item : files.entrySet()) {
                    SevenZArchiveEntry e = new SevenZArchiveEntry(); e.setName(item.getKey()); e.setSize(item.getValue().length);
                    out.putArchiveEntry(e); out.write(item.getValue()); out.closeArchiveEntry();
                }
            }
        } else {
            try (ZipArchiveOutputStream out = new ZipArchiveOutputStream(target)) {
                for (Map.Entry<String, byte[]> item : files.entrySet()) {
                    out.putArchiveEntry(new ZipArchiveEntry(item.getKey())); out.write(item.getValue()); out.closeArchiveEntry();
                }
            }
        }
        return target;
    }
    private String[] install(File runtime, File rom, File z, String hint) throws Exception {
        assertTrue(rom.isFile()); assertTrue(new File(z, "system/versions/sw.txt").isFile());
        assertTrue(new File(runtime, "data").mkdir());
        File zTarget = new File(runtime, "data/drives/z/" + hint);
        assertTrue(zTarget.getParentFile().mkdirs()); Files.move(z.toPath(), zTarget.toPath());
        File romTarget = new File(runtime, "data/roms/" + hint + "/SYM.ROM");
        assertTrue(romTarget.getParentFile().mkdirs()); Files.copy(rom.toPath(), romTarget.toPath());
        Files.write(new File(runtime, "data/devices.yml").toPath(), new byte[]{1});
        return new String[]{hint.toUpperCase(Locale.ROOT), "Phone " + hint, hint.equals("rh-29") ? "epoc6" : "epoc93fp1"};
    }
    @Test public void zipAndSevenZReuseContentAndKeepSavesBindingsAndSelection() throws Exception {
        File root = folder(); File base = new File(root, "catalog"); SystemFileCatalog catalog = new SystemFileCatalog(base);
        File zip = archive(root, "renamed.bin", files(""), false);
        SystemFileCatalog.ImportResult first = catalog.addArchive(zip, t -> { }, this::install);
        assertEquals(2, first.entries.size()); assertEquals(0, first.reused); assertTrue(first.failures.toString(), first.failures.isEmpty());
        SystemFileCatalog.Entry selected = first.entries.get(0);
        catalog.activate(selected); String hash = SystemFileCatalog.hash(zip); catalog.bind(hash, selected);
        File save = new File(catalog.runtime(selected), "keep.sav"); Files.write(save.toPath(), new byte[]{1, 2, 3});
        File seven = archive(root, "other-name.bin", files("wrapper/"), true);
        SystemFileCatalog.ImportResult second = catalog.addArchive(seven, t -> { }, (a,b,c,d) -> { throw new AssertionError("Reinstalled existing system"); });
        assertEquals(2, second.reused); assertEquals(2, catalog.list().size());
        assertEquals(catalog.runtime(selected), catalog.activeRoot(root));
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(save.toPath()));
        assertEquals(selected.id, catalog.candidates(GameImport.Kind.CARD, hash).get(0).id);
        for (SystemFileCatalog.Entry entry : catalog.list()) {
            assertFalse(new File(catalog.runtime(entry), "data/drives/c/private/save.dat").exists());
            assertFalse(new File(catalog.runtime(entry), "config.yml").exists());
        }
        assertEquals(0, base.listFiles(f -> f.getName().startsWith("pending-")).length);
    }
    @Test public void failedPhoneDoesNotUndoSuccessfulPhonesOrPublishPartialSet() throws Exception {
        File root = folder(); File base = new File(root, "catalog"); SystemFileCatalog catalog = new SystemFileCatalog(base);
        SystemFileCatalog.ImportResult result = catalog.addArchive(archive(root, "phones.zip", files(""), false), t -> { },
                (runtime, rom, z, hint) -> { if (hint.equals("rm-320")) throw new IOException("bad metadata"); return install(runtime, rom, z, hint); });
        assertEquals(1, result.entries.size()); assertEquals(1, result.failures.size());
        assertTrue(result.summary().contains("rm-320: bad metadata")); assertEquals(1, catalog.list().size());
        assertEquals(0, base.listFiles(f -> f.getName().startsWith("pending-")).length);
    }
    @Test public void unsafePathsAndCaseCollisionsAreRejected() throws Exception {
        File root = folder();
        for (String path : new String[]{"../escape", "C:/escape", "/escape", "data/roms/rh-29/sym.rom",
                "data/roms/rh-29/SYM.ROM/child"}) {
            Map<String, byte[]> entries = files(""); entries.put(path, new byte[]{1});
            SystemFileCatalog catalog = new SystemFileCatalog(new File(root, "catalog"));
            try { catalog.addArchive(archive(root, "unsafe.zip", entries, false), t -> { }, this::install); fail(path); }
            catch (IOException expected) { }
            assertTrue(catalog.list().isEmpty());
            assertEquals(0, new File(root, "catalog").list().length);
        }
    }
    @Test public void cancelledInstallationStopsCollectionAndKeepsPreviouslyInstalledPhones() throws Exception {
        File root = folder();
        try {
            File base = new File(root, "catalog"); SystemFileCatalog catalog = new SystemFileCatalog(base);
            Map<String, byte[]> entries = files("");
            entries.put("data/roms/zz-last/SYM.ROM", new byte[512]);
            entries.put("data/drives/z/zz-last/System/Versions/sw.txt", new byte[]{3});
            List<String> attempted = new ArrayList<>();
            InterruptedIOException cancellation = new InterruptedIOException("cancelled");
            try {
                catalog.addArchive(archive(root, "cancel-install.zip", entries, false), t -> { }, (runtime, rom, z, hint) -> {
                    attempted.add(hint);
                    if (hint.equals("rm-320")) throw cancellation;
                    return install(runtime, rom, z, hint);
                });
                fail("Cancellation must escape the collection loop");
            } catch (InterruptedIOException expected) { assertSame(cancellation, expected); }
            assertEquals(Arrays.asList("rh-29", "rm-320"), attempted);
            assertEquals(1, catalog.list().size());
            assertEquals("RH-29", catalog.list().get(0).firmware);
            assertEquals(0, base.listFiles(f -> f.getName().startsWith("pending-")).length);
        } finally { PhoneBackupTest.remove(root); }
    }
    @Test public void incompletePhoneIsReportedWithoutBlockingCompletePairs() throws Exception {
        File root = folder(); Map<String, byte[]> entries = files("");
        entries.put("data/drives/z/temp/leftover", new byte[]{1});
        SystemFileCatalog catalog = new SystemFileCatalog(new File(root, "catalog"));
        SystemFileCatalog.ImportResult result = catalog.addArchive(archive(root, "partial.zip", entries, false), t -> { }, this::install);
        assertEquals(2, result.entries.size()); assertEquals(Collections.singletonList("temp: ROM missing"), result.failures);
        assertEquals(SystemFailure.Code.ROM_MISSING, ((SystemFailure)result.failureDetails.get(0).error).code);
    }
    @Test public void interruptedExtractionRemovesStagingWithoutPublishingPhones() throws Exception {
        File root = folder(); File base = new File(root, "catalog"); SystemFileCatalog catalog = new SystemFileCatalog(base);
        for (boolean seven : new boolean[]{false, true}) {
            try {
                catalog.addArchive(archive(root, seven ? "cancel.7z" : "cancel.zip", files(""), seven), t -> {
                    if (t.startsWith("Reading")) throw new InterruptedIOException("cancelled");
                }, this::install);
                fail();
            } catch (InterruptedIOException expected) { }
            assertTrue(catalog.list().isEmpty()); assertEquals(0, base.list().length);
        }
    }
    @Test public void corruptZipCrcIsRejectedBeforeAnyPhoneIsInstalled() throws Exception {
        File root = folder(); File archive = new File(root, "corrupt.zip");
        try (ZipArchiveOutputStream out = new ZipArchiveOutputStream(archive)) {
            for (Map.Entry<String, byte[]> item : files("").entrySet()) {
                ZipArchiveEntry entry = new ZipArchiveEntry(item.getKey());
                entry.setMethod(0); entry.setSize(item.getValue().length);
                java.util.zip.CRC32 crc = new java.util.zip.CRC32(); crc.update(item.getValue()); entry.setCrc(crc.getValue());
                out.putArchiveEntry(entry); out.write(item.getValue()); out.closeArchiveEntry();
            }
        }
        long offset;
        try (org.apache.commons.compress.archivers.zip.ZipFile zip = new org.apache.commons.compress.archivers.zip.ZipFile(archive)) {
            offset = zip.getEntry("data/roms/rh-29/SYM.ROM").getDataOffset();
        }
        try (RandomAccessFile file = new RandomAccessFile(archive, "rw")) { file.seek(offset); file.write(99); }
        SystemFileCatalog catalog = new SystemFileCatalog(new File(root, "catalog"));
        try { catalog.addArchive(archive, t -> { }, this::install); fail(); }
        catch (IOException expected) { assertTrue(expected.getMessage(), expected.getMessage().contains("Damaged")); }
        assertTrue(catalog.list().isEmpty()); assertEquals(0, new File(root, "catalog").list().length);
    }
    @Test public void changedZDriveDoesNotMergeWithAnExistingEnvironment() throws Exception {
        File root = folder(); SystemFileCatalog catalog = new SystemFileCatalog(new File(root, "catalog"));
        Map<String, byte[]> entries = files("");
        catalog.addArchive(archive(root, "first.zip", entries, false), t -> { }, this::install);
        entries.put("data/drives/z/rh-29/System/Versions/sw.txt", new byte[]{5});
        SystemFileCatalog.ImportResult result = catalog.addArchive(archive(root, "changed.7z", entries, true), t -> { }, this::install);
        assertEquals(3, catalog.list().size()); assertEquals(1, result.reused);
        SystemFileCatalog.Entry chosen = result.entries.get(0); catalog.activate(chosen);
        assertEquals("RH-29", chosen.firmware);
        List<SystemFileCatalog.Entry> matches = catalog.candidates(GameImport.Kind.CARD, "unbound");
        assertEquals(1, matches.size()); assertEquals(chosen.id, matches.get(0).id);
    }
    @Test public void zipSymlinksAreRejectedEvenInIgnoredDrives() throws Exception {
        File root = folder(); File archive = new File(root, "link.zip");
        try (ZipArchiveOutputStream out = new ZipArchiveOutputStream(archive)) {
            ZipArchiveEntry e = new ZipArchiveEntry("data/drives/c/link"); e.setUnixMode(0120777);
            out.putArchiveEntry(e); out.write("../../outside".getBytes(StandardCharsets.UTF_8)); out.closeArchiveEntry();
        }
        try { PreparedSystemArchive.extract(archive, new File(root, "out"), t -> { }); fail(); }
        catch (IOException expected) { assertTrue(expected.getMessage().contains("Links")); }
    }
    @Test public void legacyZipStillUsesRomRpkgImporter() throws Exception {
        File root = folder(); Map<String, byte[]> entries = new LinkedHashMap<>(); entries.put("phone.rom", new byte[512]);
        assertTrue(PreparedSystemArchive.extract(archive(root, "legacy.zip", entries, false), new File(root, "out"), t -> { }).isEmpty());
    }
    @Test public void malformedProductMetadataReturnsAnErrorWithoutCrashingNativeHost() throws Exception {
        File root = folder(); File runtime = new File(root, "runtime"); assertTrue(runtime.mkdir());
        File z = new File(root, "z"), versions = new File(z, "resource/versions"); assertTrue(versions.mkdirs());
        Files.write(new File(versions, "product.txt").toPath(), "Manufacturer=Nokia\n".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(versions, "sw.txt").toPath(), "broken".getBytes(StandardCharsets.UTF_8));
        File rom = new File(root, "phone.rom"); Files.write(rom.toPath(), new byte[512]);
        try {
            SymbianFiles.prepareAssets(context(), runtime); Emulator.prepare(runtime.getAbsolutePath());
            try { Emulator.installPreparedDevice(rom.getAbsolutePath(), z.getAbsolutePath(), "rh-29"); fail(); }
            catch (IOException expected) { assertTrue(expected.getMessage().contains("identify")); }
            assertEquals(0, Emulator.getDevices().length);
        } finally { Emulator.stopSession(); }
    }
    @Test public void unknownPlatformVersionIsNotSilentlyRegisteredAsSymbian94() throws Exception {
        File root = folder(); File runtime = new File(root, "runtime"); assertTrue(runtime.mkdir());
        File z = new File(root, "z"), versions = new File(z, "resource/versions"); assertTrue(versions.mkdirs());
        Files.write(new File(versions, "product.txt").toPath(), "Manufacturer=Nokia\nProduct=RM-320\nModel=N95\n".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(versions, "platform.txt").toPath(), "SymbianOSMajorVersion=99\nSymbianOSMinorVersion=99\n".getBytes(StandardCharsets.UTF_8));
        File rom = new File(root, "phone.rom"); Files.write(rom.toPath(), new byte[512]);
        try {
            SymbianFiles.prepareAssets(context(), runtime); Emulator.prepare(runtime.getAbsolutePath());
            try { Emulator.installPreparedDevice(rom.getAbsolutePath(), z.getAbsolutePath(), "rm-320"); fail(); }
            catch (IOException expected) { assertTrue(expected.getMessage(), expected.getMessage().contains("Symbian version")); }
            assertEquals(0, Emulator.getDevices().length);
        } finally { Emulator.stopSession(); }
    }
    @Test public void archiveFolderAliasAndEmptyDisplayNameDoNotRejectIdentifiedPhone() throws Exception {
        File root = folder(); File runtime = new File(root, "runtime"); assertTrue(runtime.mkdir());
        File z = new File(root, "z"), versions = new File(z, "resource/versions"); assertTrue(versions.mkdirs());
        Files.write(new File(versions, "sw.txt").toPath(), "v1\\n2026\\nRM-320\\nNokia".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(versions, "model.txt").toPath(), new byte[]{(byte)0xff, (byte)0xfe});
        Files.write(new File(versions, "platform.txt").toPath(), "SymbianOSMajorVersion=9\nSymbianOSMinorVersion=1\n".getBytes(StandardCharsets.UTF_8));
        File rom = new File(root, "phone.rom"); Files.write(rom.toPath(), new byte[512]);
        try {
            SymbianFiles.prepareAssets(context(), runtime); Emulator.prepare(runtime.getAbsolutePath());
            Emulator.installPreparedDevice(rom.getAbsolutePath(), z.getAbsolutePath(), "friendly-folder-label");
            assertArrayEquals(new String[]{"RM-320", "RM-320", "epoc91"}, Emulator.getDeviceInfo());
        } finally { Emulator.stopSession(); }
    }
    @Test public void importProvidedCollection() throws Exception {
        assumeTrue("Private collection opt-in", "true".equals(InstrumentationRegistry.getArguments().getString("collection")));
        File archive = new File(context().getExternalFilesDir(null), "symbian-input/collection.7z"); assertTrue(archive.isFile());
        SystemFileCatalog catalog = new SystemFileCatalog(context());
        catalog.recoverInterruptedImports();
        File previous = catalog.activeRoot(SymbianFiles.root(context()));
        File bindings = new File(context().getFilesDir(), "symbian-systems/bindings.json");
        String bindingHash = bindings.isFile() ? SystemFileCatalog.hash(bindings) : "";
        Map<String, String> before = new TreeMap<>();
        for (SystemFileCatalog.Entry e : catalog.list()) {
            File drives = new File(catalog.runtime(e), "data/drives");
            File[] children = drives.listFiles();
            if (children != null) for (File child : children) if (!child.getName().equals("z"))
                try (java.util.stream.Stream<java.nio.file.Path> paths = Files.walk(child.toPath())) {
                    for (java.nio.file.Path p : (Iterable<java.nio.file.Path>)paths.filter(Files::isRegularFile)::iterator)
                        before.put(p.toString(), SystemFileCatalog.hash(p.toFile()));
                }
        }
        Emulator.stopSession();
        try {
            SystemFileCatalog.ImportResult result = catalog.addArchive(context(), archive, t -> {
                try { Files.write(new File(context().getExternalFilesDir(null), "collection-progress.txt").toPath(), t.getBytes(StandardCharsets.UTF_8)); }
                catch (IOException e) { throw new java.io.UncheckedIOException(e); }
            });
            assertNotNull(result);
            StringBuilder report = new StringBuilder(result.summary()).append('\n');
            for (SystemFileCatalog.Entry entry : result.entries) report.append(entry.id).append(' ').append(entry.label()).append('\n');
            assertFalse(result.summary(), result.entries.isEmpty());
            assertEquals(previous, catalog.activeRoot(SymbianFiles.root(context())));
            assertEquals(bindingHash, bindings.isFile() ? SystemFileCatalog.hash(bindings) : "");
            for (Map.Entry<String, String> e : before.entrySet()) assertEquals(e.getKey(), e.getValue(), SystemFileCatalog.hash(new File(e.getKey())));
            report.append("Existing writable files verified: ").append(before.size()).append('\n');
            Files.write(new File(context().getExternalFilesDir(null), "collection-result.txt").toPath(), report.toString().getBytes(StandardCharsets.UTF_8));
        } finally { Emulator.stopSession(); }
    }
}
