package io.github.eriark.abyssme.symbian;

import androidx.test.platform.app.InstrumentationRegistry;
import org.apache.commons.compress.archivers.sevenz.*;
import org.junit.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import static org.junit.Assert.*;

public class RawPhoneArchiveTest {
    private File root, output;
    @Before public void setup() throws Exception {
        root = Files.createTempDirectory(InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir().toPath(),"raw-phone-test-").toFile();
        output = new File(root,"out"); assertTrue(output.mkdir());
    }
    @After public void cleanup() throws Exception { PhoneBackupTest.remove(root); }
    private File archive(boolean seven, String... names) throws Exception {
        File archive = File.createTempFile("phone-", ".bin", root);
        if(seven) {
            try(SevenZOutputFile out = new SevenZOutputFile(archive)) {
                for(String name:names) {
                    SevenZArchiveEntry entry = new SevenZArchiveEntry(); entry.setName(name); entry.setSize(3);
                    out.putArchiveEntry(entry); out.write(new byte[]{1,2,3}); out.closeArchiveEntry();
                }
            }
        } else {
            try(ZipOutputStream out = new ZipOutputStream(new FileOutputStream(archive))) {
                for(String name:names) {out.putNextEntry(new ZipEntry(name));out.write(new byte[]{1,2,3});out.closeEntry();}
            }
        }
        return archive;
    }
    @Test public void zipAndSevenNestedMixedCasePairInstallAndDeduplicateWithoutTouchingSaves() throws Exception {
        SystemFileCatalog catalog = new SystemFileCatalog(new File(root,"systems"));
        int[] calls = {0};
        SystemFileCatalog.Installer installer = (runtime,rom,rpkg) -> {
            calls[0]++; assertNotNull(rpkg);
            assertArrayEquals(new byte[]{1,2,3}, Files.readAllBytes(rom.toPath()));
            assertArrayEquals(new byte[]{1,2,3}, Files.readAllBytes(rpkg.toPath()));
            File data = new File(runtime,"data"); assertTrue(data.mkdirs());
            Files.write(new File(data,"devices.yml").toPath(),new byte[]{1});
            return new String[]{"RM-84","Test phone","epoc81a"};
        };
        SystemFileCatalog.Entry first = catalog.add(Collections.singletonList(archive(false,"nested/N70.ROM","other/N70.RpKg")),installer);
        File save = new File(catalog.runtime(first),"save.dat"); Files.write(save.toPath(),new byte[]{42});
        SystemFileCatalog.Entry second = catalog.add(Collections.singletonList(archive(true,"phone/SYM.rom","dump/files.RPKG")),installer);
        assertEquals(first.id, second.id); assertEquals(1,calls[0]);
        assertArrayEquals(new byte[]{42},Files.readAllBytes(save.toPath()));
        assertEquals(0,root.listFiles(f->f.getName().startsWith("decoded-")).length);
    }
    @Test public void missingRomMultiplePhonesUnsafeAndMixedArchivesLeaveNoExtractedFiles() throws Exception {
        for(boolean seven:new boolean[]{false,true}) for(String[] names:new String[][]{
                {"phone.rpkg"},{"one.rom","two.ROM"},{"a.rom","a.rpkg","b.rpkg"},
                {"../phone.rom"},{"phone.rom","game.sisx"},{"phone.rom","PHONE.ROM"},
                {"folder","folder/phone.rom"}}) {
            File file = archive(seven,names);
            try {SymbianFiles.extractPhoneInputs(file,output);fail(Arrays.toString(names));} catch(IOException expected) { }
            assertEquals(0,output.list().length);
            assertEquals(0,root.listFiles(f->f.getName().startsWith("decoded-")).length);
            assertTrue(file.exists());
        }
    }
    @Test public void cancellationAndDamagedSevenAreCleanFailures() throws Exception {
        File file = archive(true,"phone.rom","phone.rpkg");
        Thread.currentThread().interrupt();
        try {SymbianFiles.extractPhoneInputs(file,output);fail();} catch(IOException expected) { }
        finally {Thread.interrupted();}
        assertEquals(0,output.list().length);
        try(RandomAccessFile damaged = new RandomAccessFile(file,"rw")){damaged.setLength(12);}
        try {SymbianFiles.extractPhoneInputs(file,output);fail();} catch(IOException expected) { }
        assertEquals(0,output.list().length);
        assertEquals(0,root.listFiles(f->f.getName().startsWith("decoded-")).length);
    }
}
