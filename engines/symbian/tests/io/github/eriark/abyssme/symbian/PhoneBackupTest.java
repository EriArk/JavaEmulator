package io.github.eriark.abyssme.symbian;

import androidx.test.platform.app.InstrumentationRegistry;
import com.github.eka2l1.emu.Emulator;
import org.json.*;
import org.junit.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import static org.junit.Assert.*;
import ru.playsoftware.j2meloader.config.ProfileModel;
import ru.playsoftware.j2meloader.config.ProfilesManager;

public final class PhoneBackupTest {
    private File root; private SystemFileCatalog catalog; private PhoneBackup backup;
    private static final PreparedSystemArchive.Progress SILENT=text -> {};
    private static final PhoneBackup.Validator VALID=(runtime,metadata) -> {};
    @Before public void setup() throws Exception {
        root=Files.createTempDirectory(InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir().toPath(),"phone-backup-test-").toFile();
        catalog=new SystemFileCatalog(new File(root,"symbian-systems")); backup=new PhoneBackup(root);
    }
    @After public void cleanup() throws Exception { remove(root); }
    static void remove(File root) throws IOException {
        if(root==null || !root.exists()) return;
        try(java.util.stream.Stream<Path> paths=Files.walk(root.toPath())) {
            for(Path path:(Iterable<Path>)paths.sorted(Comparator.reverseOrder())::iterator) Files.delete(path);
        }
    }
    private void write(File file,String text) throws IOException {
        Files.createDirectories(file.getParentFile().toPath()); Files.write(file.toPath(),text.getBytes(StandardCharsets.UTF_8));
    }
    private SystemFileCatalog.Entry phone() throws Exception {
        File rom=new File(root,"phone.rom"); write(rom,"test-rom");
        SystemFileCatalog.Entry entry=catalog.add(Collections.singletonList(rom),(runtime,r,p) -> {
            write(new File(runtime,"data/devices.yml"),"registration");
            write(new File(runtime,"data/drives/rh-29/c/system/data/save.dat"),"save-progress");
            write(new File(runtime,"data/drives/rh-29/e/system/apps/game/game.app"),"game-content");
            write(new File(runtime,"data/roms/rh-29/SYM.ROM"),"test-rom");
            Files.createDirectories(new File(runtime,"data/drives/rh-29/d/empty").toPath());
            return new String[]{"RH-29","Test phone","epoc6"};
        });
        File profileDir=new File(PhoneBackup.profiles(root,entry.id),"123"); Files.createDirectories(profileDir.toPath());
        ProfileModel profile=new ProfileModel(profileDir); profile.screenRotation=180; profile.touchLayout=1;
        android.util.SparseIntArray mapping=new android.util.SparseIntArray(); mapping.put(android.view.KeyEvent.KEYCODE_BUTTON_X,'9');
        profile.setActiveKeyMappings(mapping); assertTrue(ProfilesManager.saveConfig(profile));
        catalog.bind(SystemFileCatalog.digest("game".getBytes(StandardCharsets.UTF_8)),entry);
        return entry;
    }
    private File archive(SystemFileCatalog.Entry entry) throws Exception {
        File zip=new File(root,UUID.randomUUID()+".zip");
        backup.exportPhone(entry,zip,new String[]{"data-storage: data","registration"},SILENT); return zip;
    }
    private File art(SystemFileCatalog.Entry entry, long uid, boolean icon) {
        return new File(SymbianLibraryIndex.artworkRoot(root,entry.id),uid+(icon ? "/user-icon.png" : "/user-cover.png"));
    }
    private void image(File file, int color) throws Exception {
        Files.createDirectories(file.getParentFile().toPath());
        Files.write(file.toPath(),SymbianArtworkTest.image(color));
    }
    @Test public void artworkRoundTripRebindsToNewPhoneAndLauncherWithoutChangingOriginal() throws Exception {
        SystemFileCatalog.Entry original=phone();
        byte[] icon=SymbianArtworkTest.image(0xff334455), cover=SymbianArtworkTest.image(0xff669977);
        image(art(original,123,true),0xff334455); image(art(original,123,false),0xff669977);
        image(art(original,0xffffffffL,false),0xffcc7799);
        File zip=archive(original);
        assertEquals(2,PhoneBackup.inspect(zip).manifest.getInt("version"));
        SystemFileCatalog.Entry restored=backup.restore(zip,VALID,SILENT);
        assertTrue(art(restored,123,true).getPath().contains("symbian-systems/"+restored.id+"/artwork"));
        assertArrayEquals(icon,Files.readAllBytes(art(restored,123,true).toPath()));
        assertArrayEquals(cover,Files.readAllBytes(art(restored,123,false).toPath()));
        assertArrayEquals(Files.readAllBytes(art(original,0xffffffffL,false).toPath()),Files.readAllBytes(art(restored,0xffffffffL,false).toPath()));
        android.content.Context base=InstrumentationRegistry.getInstrumentation().getTargetContext();
        android.content.Context context=new android.content.ContextWrapper(base) {
            @Override public File getFilesDir() { return root; }
            @Override public android.content.SharedPreferences getSharedPreferences(String name,int mode) {
                return super.getSharedPreferences(root.getName()+name,mode);
            }
        };
        SymbianLibraryIndex index=new SymbianLibraryIndex(context);
        String[] apps={"123","Game","Symbian"};
        index.publish(original,apps,false); index.publish(restored,apps,false);
        for(ru.playsoftware.j2meloader.applist.AppItem item:new SymbianLauncherLibrary().read(context)) {
            assertArrayEquals(icon,Files.readAllBytes(new File(item.getImagePathExt()).toPath()));
            assertArrayEquals(cover,Files.readAllBytes(new File(item.getCoverPathExt()).toPath()));
        }
        Files.delete(art(restored,123,true).toPath());
        image(art(restored,123,false),0xffaabbcc);
        SystemFileCatalog.Entry again=backup.restore(archive(restored),VALID,SILENT);
        assertFalse(art(again,123,true).exists());
        assertArrayEquals(SymbianArtworkTest.image(0xffaabbcc),Files.readAllBytes(art(again,123,false).toPath()));
        assertArrayEquals(icon,Files.readAllBytes(art(original,123,true).toPath()));
        assertArrayEquals(cover,Files.readAllBytes(art(original,123,false).toPath()));
        context.getSharedPreferences("symbian-library-presentation",0).edit().clear().commit();
    }
    @Test public void versionOneWithoutArtworkStillRestoresAndCanAcquireArtwork() throws Exception {
        SystemFileCatalog.Entry original=phone();
        Map<String,byte[]> entries=contents(archive(original));
        JSONObject manifest=new JSONObject(new String(entries.get(PhoneBackup.MANIFEST),StandardCharsets.UTF_8));
        JSONArray oldFiles=new JSONArray(), rows=manifest.getJSONArray("files");
        for(int i=0;i<rows.length();i++) if(!rows.getJSONObject(i).getString("path").equals("artwork")) oldFiles.put(rows.get(i));
        manifest.put("version",1).put("files",oldFiles); entries.remove("artwork/");
        entries.put(PhoneBackup.MANIFEST,manifest.toString().getBytes(StandardCharsets.UTF_8));
        SystemFileCatalog.Entry restored=backup.restore(repack(entries),VALID,SILENT);
        assertTrue(SymbianLibraryIndex.artworkRoot(root,restored.id).isDirectory());
        image(art(restored,123,false),0xff123456);
        SystemFileCatalog.Entry again=backup.restore(archive(restored),VALID,SILENT);
        assertArrayEquals(SymbianArtworkTest.image(0xff123456),Files.readAllBytes(art(again,123,false).toPath()));
        assertFalse(art(original,123,false).exists());
    }
    private void changeInventory(Map<String,byte[]> entries,String from,String to,byte[] content) throws Exception {
        JSONObject manifest=new JSONObject(new String(entries.get(PhoneBackup.MANIFEST),StandardCharsets.UTF_8));
        JSONArray rows=manifest.getJSONArray("files");
        for(int i=0;i<rows.length();i++) {
            JSONObject row=rows.getJSONObject(i);
            if(row.getString("path").equals(from)) {
                manifest.put("bytes",manifest.getLong("bytes")-row.getLong("size")+content.length);
                row.put("path",to).put("size",content.length).put("sha256",SystemFileCatalog.digest(content));
            }
        }
        entries.remove(from); entries.put(to,content);
        entries.put(PhoneBackup.MANIFEST,manifest.toString().getBytes(StandardCharsets.UTF_8));
    }
    @Test public void artworkIntegrityImageAndPathChecksRejectWithoutPublishing() throws Exception {
        SystemFileCatalog.Entry original=phone(); image(art(original,123,false),0xff112233);
        File zip=archive(original); String path="artwork/123/user-cover.png";
        byte[] bytes=Files.readAllBytes(art(original,123,false).toPath());
        Map<String,byte[]> entries=contents(zip); byte[] corrupt=bytes.clone(); corrupt[corrupt.length-1]^=1;
        entries.put(path,corrupt); rejected(repack(entries));
        entries=contents(zip); changeInventory(entries,path,path,new byte[]{1,2,3}); rejected(repack(entries));
        entries=contents(zip); changeInventory(entries,path,path,new byte[0]); rejected(repack(entries));
        for(String unsafe:new String[]{"artwork/0/user-cover.png","artwork/4294967296/user-cover.png",
                "artwork/123/other.png","artwork/123/../user-cover.png","artwork/123/nested/user-cover.png"}) {
            entries=contents(zip); changeInventory(entries,path,unsafe,bytes); rejected(repack(entries));
        }
        try { backup.restore(zip,(r,m) -> {throw new IOException("validation failed");},SILENT); fail(); }
        catch(IOException expected) { }
        assertEquals(1,catalog.list().size());
        assertEquals(0,new File(root,"symbian-systems").listFiles(f -> f.getName().startsWith("pending-")).length);
        assertArrayEquals(bytes,Files.readAllBytes(art(original,123,false).toPath()));
    }
    @Test public void oversizedAndLinkedArtworkCannotBeExported() throws Exception {
        SystemFileCatalog.Entry original=phone(); File cover=art(original,123,false);
        image(cover,0xff112233);
        try(RandomAccessFile file=new RandomAccessFile(cover,"rw")) { file.setLength((16L<<20)+1); }
        try { archive(original); fail(); } catch(IOException expected) { }
        Files.delete(cover.toPath()); File external=new File(root,"external.png"); image(external,0xff445566);
        Files.createSymbolicLink(cover.toPath(),external.toPath());
        try { archive(original); fail(); } catch(IOException expected) { }
        assertTrue(external.isFile()); Files.delete(cover.toPath());
    }
    @Test public void roundTripRetainsDrivesProfilesAndBindingsWithoutOverwritingOriginal() throws Exception {
        SystemFileCatalog.Entry original=phone(); catalog.activate(original);
        File zip=archive(original); assertEquals(ImportKind.Kind.BACKUP,ImportKind.inspect(zip,"renamed.bin"));
        assertTrue(PhoneBackup.inspect(zip).description().contains("not replaced"));
        SystemFileCatalog.Entry restored=backup.restore(zip,VALID,SILENT);
        assertNotEquals(original.id,restored.id); assertTrue(restored.restored);
        assertEquals(catalog.runtime(original),catalog.activeRoot(root));
        assertEquals(catalog.sourceBindings(original),catalog.sourceBindings(restored));
        assertTrue(catalog.boundSystems().stream().anyMatch(e -> e.id.equals(restored.id)));
        assertArrayEquals(Files.readAllBytes(new File(catalog.runtime(original),"data/drives/rh-29/c/system/data/save.dat").toPath()),
                Files.readAllBytes(new File(catalog.runtime(restored),"data/drives/rh-29/c/system/data/save.dat").toPath()));
        assertTrue(new File(catalog.runtime(restored),"data/drives/rh-29/d/empty").isDirectory());
        assertTrue(new File(PhoneBackup.profiles(root,restored.id),"123/config.json").isFile());
        ProfileModel profile=ProfilesManager.loadConfig(new File(PhoneBackup.profiles(root,restored.id),"123"));
        assertNotNull(profile); assertEquals(180,profile.screenRotation); assertEquals(Integer.valueOf(1),profile.touchLayout);
        assertEquals('9',profile.getActiveKeyMappings().get(android.view.KeyEvent.KEYCODE_BUTTON_X));
        write(new File(catalog.runtime(restored),"data/drives/rh-29/c/system/data/save.dat"),"changed copy");
        assertEquals("save-progress",new String(Files.readAllBytes(new File(catalog.runtime(original),"data/drives/rh-29/c/system/data/save.dat").toPath()),StandardCharsets.UTF_8));
        File again=archive(restored); SystemFileCatalog.Entry second=backup.restore(again,VALID,SILENT);
        assertNotEquals(restored.id,second.id); assertEquals(3,catalog.list().size());
    }
    private Map<String,byte[]> contents(File archive) throws Exception {
        Map<String,byte[]> result=new LinkedHashMap<>();
        try(java.util.zip.ZipFile zip=new java.util.zip.ZipFile(archive)) {
            for(Enumeration<? extends ZipEntry> e=zip.entries();e.hasMoreElements();) {
                ZipEntry item=e.nextElement(); try(InputStream in=zip.getInputStream(item); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                    byte[] buffer=new byte[8192]; for(int n;(n=in.read(buffer))!=-1;)out.write(buffer,0,n); result.put(item.getName(),out.toByteArray());
                }
            }
        }
        return result;
    }
    private File repack(Map<String,byte[]> contents) throws Exception {
        File file=new File(root,UUID.randomUUID()+".zip");
        try(ZipOutputStream zip=new ZipOutputStream(new FileOutputStream(file))) {
            for(Map.Entry<String,byte[]> item:contents.entrySet()) {zip.putNextEntry(new ZipEntry(item.getKey()));zip.write(item.getValue());zip.closeEntry();}
        }
        return file;
    }
    private void rejected(File file) throws Exception {
        int before=catalog.list().size();
        try { backup.restore(file,VALID,SILENT); fail("Invalid backup accepted"); } catch(IOException expected) {}
        assertEquals(before,catalog.list().size());
        File[] pending=new File(root,"symbian-systems").listFiles(f -> f.getName().startsWith("pending-")); assertEquals(0,pending.length);
    }
    @Test public void alteredBytesMissingFilesAndUnsupportedVersionsAreRejected() throws Exception {
        File original=archive(phone());
        Map<String,byte[]> entries=contents(original);
        entries.put("runtime/data/drives/rh-29/c/system/data/save.dat","bad-progress!".getBytes(StandardCharsets.UTF_8)); rejected(repack(entries));
        entries=contents(original); entries.remove("source/phone.rom"); rejected(repack(entries));
        entries=contents(original); JSONObject manifest=new JSONObject(new String(entries.get(PhoneBackup.MANIFEST),StandardCharsets.UTF_8));
        manifest.put("version",99); entries.put(PhoneBackup.MANIFEST,manifest.toString().getBytes(StandardCharsets.UTF_8)); rejected(repack(entries));
    }
    @Test public void unsafeUnlistedAndCaseCollidingEntriesAreRejected() throws Exception {
        File original=archive(phone());
        for(String path:new String[]{"../escaped","runtime/scripts/execute.lua","source/PHONE.rom","/absolute","runtime/data/../escape"}) {
            Map<String,byte[]> entries=contents(original); entries.put(path,new byte[]{1}); rejected(repack(entries));
        }
        assertFalse(new File(root,"escaped").exists());
    }
    @Test public void cancellationValidationFailureAndLowSpaceDoNotPublishPartialPhones() throws Exception {
        File original=archive(phone());
        for(int mode=0;mode<3;mode++) {
            final int scenario=mode;
            try {
                backup.restore(original,(r,m) -> {if(scenario==1)throw new IOException("Native validation failed");},
                        text -> {if(scenario==0 && text.startsWith("Restoring"))throw new InterruptedIOException("cancel");},
                        f -> scenario==2 ? 0 : f.getUsableSpace()); fail();
            } catch(IOException expected) {}
            assertEquals(1,catalog.list().size());
            assertEquals(0,new File(root,"symbian-systems").listFiles(f -> f.getName().startsWith("pending-")).length);
        }
    }
    @Test public void sourceLinksAndModifiedDuringCopyAreRejected() throws Exception {
        SystemFileCatalog.Entry entry=phone(); File outside=new File(root,"outside"); write(outside,"keep");
        File link=new File(catalog.runtime(entry),"data/drives/rh-29/c/link"); Files.createSymbolicLink(link.toPath(),outside.toPath());
        try {archive(entry);fail();}catch(IOException expected){} Files.delete(link.toPath());
        File save=new File(catalog.runtime(entry),"data/drives/rh-29/c/system/data/save.dat");
        File zip=new File(root,"changed.zip"); boolean[] changed={false};
        try {
            backup.exportPhone(entry,zip,new String[]{"config","devices"},text -> {
                if(!changed[0] && text.startsWith("Backing up")) {changed[0]=true;write(save,"different");}
            }); fail();
        } catch(IOException expected){}
        assertFalse(zip.exists()); assertEquals("keep",new String(Files.readAllBytes(outside.toPath()),StandardCharsets.UTF_8));
    }
    @Test public void interruptedStagingIsRecoveredWithoutDeletingExistingPhones() throws Exception {
        SystemFileCatalog.Entry entry=phone(); File partial=catalog.newRestore(); write(new File(partial,"runtime/partial"),"incomplete");
        catalog.recoverInterruptedImports(); assertFalse(partial.exists()); assertEquals(entry.id,catalog.list().get(0).id);
    }
    @Test public void portableNativeSettingsExcludeHostPathsAndNetworkSecrets() throws Exception {
        write(new File(root,"config.yml"),"data-storage: /outside\nimei: 123456789012345\nbtnet-password: secret\nenable-gdb-stub: true\nbkg-path: /secret\n");
        write(new File(root,"data/devices.yml"),"RH-29:\n  model: Test phone\n  manufacturer: Nokia\n  platver: epoc6\n  machine-uid: 1\n  isolated-drives: true\n");
        String[] clean=Emulator.portablePhoneSettings(root.getAbsolutePath());
        assertFalse(clean[0].contains("secret")); assertFalse(clean[0].contains("outside")); assertTrue(clean[0].contains("123456789012345"));
        assertTrue(clean[0].contains("enable-gdb-stub: false")); assertTrue(clean[1].contains("isolated-drives: true"));
        write(new File(root,"config.yml"),"audio-master-volume: not-a-number\n");
        try {Emulator.portablePhoneSettings(root.getAbsolutePath());fail();}catch(IOException expected){}
        write(new File(root,"config.yml"),"language: 1\n");
        write(new File(root,"data/devices.yml"),"../escape:\n  model: bad\n");
        try {Emulator.portablePhoneSettings(root.getAbsolutePath());fail();}catch(IOException expected){}
    }
    @Test public void neverBootedPhoneExportsDefaultsWithoutCreatingSourceConfig() throws Exception {
        SystemFileCatalog.Entry entry=phone();
        File runtime=catalog.runtime(entry);
        write(new File(runtime,"data/devices.yml"),"RH-29:\n  model: Test phone\n  manufacturer: Nokia\n  platver: epoc6\n  machine-uid: 1\n  isolated-drives: true\n");
        File config=new File(runtime,"config.yml"); assertFalse(config.exists());
        String[] settings=Emulator.portablePhoneSettings(runtime.getAbsolutePath());
        assertTrue(settings[0].contains("data-storage: data"));
        File zip=new File(root,"never-booted.zip"); backup.exportPhone(entry,zip,settings,SILENT);
        SystemFileCatalog.Entry restored=backup.restore(zip,VALID,SILENT);
        assertTrue(new File(catalog.runtime(restored),"config.yml").isFile());
        assertFalse("Backup must not initialize the source phone",config.exists());
    }
}
