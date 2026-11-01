// SPDX-License-Identifier: GPL-3.0-only
package io.github.eriark.abyssme.symbian;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import com.github.eka2l1.emu.Emulator;
import org.junit.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import static org.junit.Assert.*;

/** Opt-in native validation with the owner's installed, private Snakes phone. */
public final class Eka2l1DeviceTest {
    @Test public void installedPhoneSnapshotRetainsGamesAndOriginalBytes() throws Exception {
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("ekaDevice")));
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        SystemFileCatalog catalog=new SystemFileCatalog(context);
        SymbianLibraryIndex.Entry game=new SymbianLibraryIndex(context).read().stream()
                .filter(entry -> entry.title.equals("Snakes")).findFirst().orElse(null);
        assertNotNull("Provision the private Snakes fixture",game);
        SystemFileCatalog.Entry phone=catalog.read(game.system);
        File runtime=catalog.runtime(phone), root=Files.createTempDirectory(context.getCacheDir().toPath(),"eka-device-test-").toFile();
        File source=new File(root,"source"), destination=new File(root,"destination");
        Map<String,String> original=hashes(runtime);
        try {
            Emulator.stopSession();
            try(java.util.stream.Stream<Path> files=Files.walk(new File(runtime,"data").toPath())) {
                for(Path file:(Iterable<Path>)files::iterator) {
                    File target=new File(source,"data/"+new File(runtime,"data").toPath().relativize(file));
                    if(Files.isDirectory(file))Files.createDirectories(target.toPath());
                    else { Files.createDirectories(target.getParentFile().toPath()); Files.copy(file,target.toPath()); }
                }
            }
            Files.copy(new File(runtime,"config.yml").toPath(),new File(source,"config.yml").toPath());
            try(Eka2l1Transfer transfer=new Eka2l1Transfer(root,text -> {})) {
                transfer.readDirectory(source);
                SystemFileCatalog.Entry imported=transfer.install(destination,transfer.phones.get(0),(candidate,metadata) -> {
                    SymbianFiles.prepareAssets(context,candidate);
                    try {
                        Emulator.prepare(candidate.getAbsolutePath());
                        assertArrayEquals(new String[]{phone.firmware,phone.model,phone.os},Emulator.getDeviceInfo());
                        assertTrue(Arrays.asList(Emulator.getLibraryApps()).contains("Snakes"));
                    } finally { Emulator.stopSession(); }
                });
                File copied=new SystemFileCatalog(new File(destination,"symbian-systems")).runtime(imported);
                try {
                    Emulator.prepare(copied.getAbsolutePath());
                    String[] apps=Emulator.getLibraryApps(); assertTrue(Arrays.asList(apps).contains(Long.toString(game.uid)));
                    SymbianLibraryIndex index=new SymbianLibraryIndex(new File(destination,"symbian-library"));
                    index.publish(imported,apps,false);
                    assertTrue(index.read().stream().anyMatch(entry -> entry.uid==game.uid && entry.title.equals("Snakes")));
                } finally { Emulator.stopSession(); }
                assertEquals(original,hashes(runtime));
            }
            if("true".equals(InstrumentationRegistry.getArguments().getString("ekaExportFixture"))) {
                File zip=new File(context.getExternalFilesDir(null),"eka-transfer-device-fixture.zip");
                try(ZipOutputStream output=new ZipOutputStream(new FileOutputStream(zip)); java.util.stream.Stream<Path> files=Files.walk(source.toPath())) {
                    for(Path file:(Iterable<Path>)files.filter(Files::isRegularFile)::iterator) {
                        output.putNextEntry(new ZipEntry("EKA2L1/"+source.toPath().relativize(file)));
                        Files.copy(file,output); output.closeEntry();
                    }
                }
            }
        } finally { Emulator.stopSession(); PhoneBackupTest.remove(root); }
    }
    private static Map<String,String> hashes(File root) throws IOException {
        Map<String,String> result=new TreeMap<>();
        try(java.util.stream.Stream<Path> files=Files.walk(root.toPath())) {
            for(Path file:(Iterable<Path>)files.filter(Files::isRegularFile)::iterator)
                result.put(root.toPath().relativize(file).toString(),SystemFileCatalog.hash(file.toFile()));
        }
        return result;
    }
}
