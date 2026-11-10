// SPDX-License-Identifier: GPL-3.0-only
package io.github.eriark.abyssme.symbian;

import androidx.test.platform.app.InstrumentationRegistry;
import com.github.eka2l1.emu.Emulator;
import org.junit.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import static org.junit.Assert.*;

public final class Eka2l1TransferTest {
    private File root, source, destination;
    private static final PreparedSystemArchive.Progress SILENT=text -> {};
    private static final PhoneBackup.Validator VALID=(runtime,metadata) -> {};
    @Before public void setup() throws Exception {
        root=Files.createTempDirectory(InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir().toPath(),"eka-test-").toFile();
        source=new File(root,"original"); destination=new File(root,"destination");
    }
    @After public void cleanup() throws Exception { PhoneBackupTest.remove(root); }
    static String registration(String firmware,boolean isolated) {
        return firmware+":\n  model: Test phone\n  manufacturer: Nokia\n  platver: epoc6\n  machine-uid: 270562329\n  isolated-drives: "+isolated+"\n";
    }
    static void write(File root,String path,String value) throws IOException {
        File target=new File(root,path); Files.createDirectories(target.getParentFile().toPath());
        Files.write(target.toPath(),value.getBytes(StandardCharsets.UTF_8));
    }
    static String read(File root,String path) throws IOException { return new String(Files.readAllBytes(new File(root,path).toPath()),StandardCharsets.UTF_8); }
    private void fixture(boolean isolated) throws IOException {
        write(source,"data/devices.yml",registration("RH-29",isolated));
        write(source,"data/roms/rh-29/SYM.ROM","fixture-rom");
        write(source,"data/drives/z/rh-29/System/Boot.bin","fixture-z");
        String drives="data/drives/"+(isolated?"rh-29/":"");
        write(source,drives+"c/System/Data/save.dat","original-save");
        write(source,drives+"e/System/Apps/Game/Game.app","game");
        write(source,drives+"c/System/install/registry.dat","installed-component");
        Files.createDirectories(new File(source,drives+"d/empty").toPath());
        write(source,"data/j2me/registry.db","guest-java");
        write(source,"scripts/unsafe.lua","not imported");
        write(source,"config.yml","imei: '123456789012345'\nmmc-id: 'card-id'\nlanguage: 1\ndata-storage: /private/source\nenable-upnp: true\nfriends: [secret]\n");
    }
    private SystemFileCatalog catalog() { return new SystemFileCatalog(new File(destination,"symbian-systems")); }
    @Test public void sharedDrivesKeepGamesRegistrySavesAndGuestIdentityWithoutSourceWrites() throws Exception {
        fixture(false); String before=read(source,"data/devices.yml");
        try(Eka2l1Transfer transfer=new Eka2l1Transfer(root,SILENT)) {
            transfer.readDirectory(source); assertEquals(1,transfer.phones.size());
            assertTrue(transfer.size(transfer.phones.get(0))>0);
            SystemFileCatalog.Entry added=transfer.install(destination,transfer.phones.get(0),VALID);
            File runtime=catalog().runtime(added);
            assertEquals("original-save",read(runtime,"data/drives/rh-29/c/System/Data/save.dat"));
            assertEquals("game",read(runtime,"data/drives/rh-29/e/System/Apps/Game/Game.app"));
            assertEquals("installed-component",read(runtime,"data/drives/rh-29/c/System/install/registry.dat"));
            assertEquals("guest-java",read(runtime,"data/j2me/registry.db"));
            assertTrue(new File(runtime,"data/drives/rh-29/d/empty").isDirectory());
            String config=read(runtime,"config.yml");
            assertTrue(config.contains("123456789012345")); assertTrue(config.contains("card-id"));
            assertFalse(config.contains("/private/source")); assertFalse(config.contains("secret"));
            assertTrue(config.contains("enable-upnp: false"));
            assertFalse(new File(runtime,"scripts/unsafe.lua").exists());
            assertTrue(read(runtime,"data/devices.yml").contains("isolated-drives: true"));
            assertEquals(before,read(source,"data/devices.yml"));
            assertEquals("original-save",read(source,"data/drives/c/System/Data/save.dat"));
            assertTrue(added.restored); assertEquals(1,catalog().boundSystems().size());
        }
    }
    @Test public void isolatedPhonesDoNotMixWritableDrives() throws Exception {
        fixture(true);
        write(source,"data/devices.yml",registration("RH-29",true)+registration("RM-320",true));
        write(source,"data/roms/rm-320/SYM.ROM","second-rom");
        write(source,"data/drives/rm-320/c/other.dat","other-phone");
        try(Eka2l1Transfer transfer=new Eka2l1Transfer(root,SILENT)) {
            transfer.readDirectory(source); assertEquals(2,transfer.phones.size());
            SystemFileCatalog.Entry phone=transfer.install(destination,transfer.phones.get(0),VALID);
            assertFalse(new File(catalog().runtime(phone),"data/drives/rm-320").exists());
            assertFalse(new File(catalog().runtime(phone),"data/roms/rm-320").exists());
            assertEquals("other-phone",read(source,"data/drives/rm-320/c/other.dat"));
        }
    }
    @Test public void repeatedSnapshotReusesCopyWithoutOverwritingNewerSave() throws Exception {
        fixture(false); String id;
        try(Eka2l1Transfer transfer=new Eka2l1Transfer(root,SILENT)) {
            transfer.readDirectory(source); SystemFileCatalog.Entry phone=transfer.install(destination,transfer.phones.get(0),VALID); id=phone.id;
            write(catalog().runtime(phone),"data/drives/rh-29/c/System/Data/save.dat","newer-destination-save");
        }
        try(Eka2l1Transfer transfer=new Eka2l1Transfer(root,SILENT)) {
            transfer.readDirectory(source);
            SystemFileCatalog.Entry same=transfer.install(destination,transfer.phones.get(0),(r,m)->fail("Do not reboot an already imported snapshot"));
            assertEquals(id,same.id); assertEquals(1,catalog().list().size());
            assertEquals("newer-destination-save",read(catalog().runtime(same),"data/drives/rh-29/c/System/Data/save.dat"));
        }
    }
    @Test public void validatorFailureRollsBackWholePhone() throws Exception {
        fixture(false);
        try(Eka2l1Transfer transfer=new Eka2l1Transfer(root,SILENT)) {
            transfer.readDirectory(source);
            try { transfer.install(destination,transfer.phones.get(0),(r,m)->{throw new IOException("fixture-validation");}); fail(); }
            catch(IOException expected) { assertEquals("fixture-validation",expected.getMessage()); }
            assertTrue(catalog().list().isEmpty());
            assertEquals(0,Objects.requireNonNull(new File(destination,"symbian-systems").list()).length);
            assertEquals("original-save",read(source,"data/drives/c/System/Data/save.dat"));
        }
    }
    @Test public void cancellationBeforeCommitDiscardsPendingCopy() throws Exception {
        fixture(false);
        try(Eka2l1Transfer transfer=new Eka2l1Transfer(root,new PreparedSystemArchive.Progress() {
            public void update(String text) { }
            public void update(SystemProgress step,Object... args) throws IOException {
                if(step==SystemProgress.ADD_PHONE) throw new InterruptedIOException("cancel");
                step.fallback(args); // All typed progress calls must match their format arguments.
            }
        })) {
            transfer.readDirectory(source);
            try { transfer.install(destination,transfer.phones.get(0),VALID); fail(); } catch(InterruptedIOException expected) { }
            assertTrue(catalog().list().isEmpty());
        }
    }
    @Test public void missingRomCannotCommit() throws Exception {
        fixture(false); Files.delete(new File(source,"data/roms/rh-29/SYM.ROM").toPath());
        try(Eka2l1Transfer transfer=new Eka2l1Transfer(root,SILENT)) {
            transfer.readDirectory(source);
            try { transfer.install(destination,transfer.phones.get(0),VALID); fail(); }
            catch(SystemFailure expected) { assertEquals(SystemFailure.Code.ROM_MISSING,expected.code); }
            assertTrue(catalog().list().isEmpty());
        }
    }
    @Test public void dataOnlyFolderUsesDefaults() throws Exception {
        fixture(false);
        try(Eka2l1Transfer transfer=new Eka2l1Transfer(root,SILENT)) {
            transfer.readDirectory(new File(source,"data"));
            SystemFileCatalog.Entry phone=transfer.install(destination,transfer.phones.get(0),VALID);
            assertTrue(read(catalog().runtime(phone),"config.yml").contains("data-storage: data"));
        }
    }
    @Test public void retainedZipKeepsOriginalIdentityAndNewerSaves() throws Exception {
        fixture(false); File archive=new File(root,"retained.zip"), manifest=new File(root,"snapshot.json");
        // No explicit directory entries: adding them during recovery would change the copy ID.
        try(ZipOutputStream out=new ZipOutputStream(new FileOutputStream(archive)); java.util.stream.Stream<Path> files=Files.walk(source.toPath())) {
            for(Path file:(Iterable<Path>)files.filter(Files::isRegularFile)::iterator) {
                out.putNextEntry(new ZipEntry(source.toPath().relativize(file).toString())); Files.copy(file,out); out.closeEntry();
            }
        }
        Eka2l1Transfer transfer=new Eka2l1Transfer(root,SILENT);
        try {
            transfer.readZip(archive); transfer.retain(manifest);
            SystemFileCatalog.Entry first=transfer.install(destination,transfer.phones.get(0),VALID);
            write(catalog().runtime(first),"data/drives/rh-29/c/System/Data/save.dat","newer");
            Eka2l1Transfer restored=Eka2l1Transfer.reopen(manifest,SILENT);
            SystemFileCatalog.Entry second=restored.install(destination,restored.phones.get(0),(r,m)->fail("Do not validate committed copies again"));
            assertEquals(first.id,second.id);
            assertEquals("newer",read(catalog().runtime(second),"data/drives/rh-29/c/System/Data/save.dat"));
        } finally { transfer.close(); }
    }
    @Test public void retainedSnapshotRejectsChangedDataBeforeInstalling() throws Exception {
        fixture(false); File manifest=new File(root,"snapshot.json");
        try(Eka2l1Transfer transfer=new Eka2l1Transfer(root,SILENT)) {
            transfer.readDirectory(source); transfer.retain(manifest);
            write(transfer.stage,"data/drives/c/System/Data/save.dat","modified-save");
            try { Eka2l1Transfer.reopen(manifest,SILENT); fail(); } catch(IOException expected) { }
            assertTrue(catalog().list().isEmpty());
        }
    }
    @Test public void retainedSnapshotRejectsLinkedPayload() throws Exception {
        fixture(false); File manifest=new File(root,"snapshot.json");
        try(Eka2l1Transfer transfer=new Eka2l1Transfer(root,SILENT)) {
            transfer.readDirectory(source); transfer.retain(manifest);
            Path save=new File(transfer.stage,"data/drives/c/System/Data/save.dat").toPath();
            Files.delete(save); Files.createSymbolicLink(save,new File(source,"data/drives/c/System/Data/save.dat").toPath());
            try { Eka2l1Transfer.reopen(manifest,SILENT); fail(); } catch(IOException expected) { }
            finally { Files.delete(save); }
            assertEquals("original-save",read(source,"data/drives/c/System/Data/save.dat"));
        }
    }
    @Test public void twoPhoneQueueRetriesOnlyFailedCopy() throws Exception {
        fixture(true);
        write(source,"data/devices.yml",registration("RH-29",true)+registration("RM-320",true));
        write(source,"data/roms/rm-320/SYM.ROM","second-rom");
        write(source,"data/drives/rm-320/c/save.dat","second-save");
        try(Eka2l1Transfer transfer=new Eka2l1Transfer(root,SILENT)) {
            transfer.readDirectory(source);
            PhoneTransferQueue queue=new PhoneTransferQueue(new File(root,"queue.bin"));
            for(Eka2l1Transfer.Phone phone:transfer.phones) {
                PhoneTransferQueue.Item item=new PhoneTransferQueue.Item(phone.firmware,phone.label(),transfer.size(phone));
                item.selected=true; queue.items.add(item);
            }
            boolean[] failSecond={true}; int[] copies={0};
            PhoneTransferQueue.Backend backend=new PhoneTransferQueue.Backend() {
                public String copy(PhoneTransferQueue.Item item) throws Exception {
                    copies[0]++;
                    for(Eka2l1Transfer.Phone phone:transfer.phones) if(phone.firmware.equals(item.firmware))
                        return transfer.install(destination,phone,(r,m)-> {
                            if(phone.firmware.equals("RM-320") && failSecond[0]) throw new IOException("fixture failure");
                            VALID.validate(r,m);
                        }).id;
                    throw new AssertionError();
                }
                public void publish(String id) { }
                public String message(Exception error) { return error.getMessage(); }
            };
            queue.run(backend,()->false); assertEquals(1,queue.completed()); assertEquals(1,catalog().list().size());
            SystemFileCatalog.Entry first=catalog().list().get(0);
            write(catalog().runtime(first),"data/drives/rh-29/c/System/Data/save.dat","newer");
            failSecond[0]=false; PhoneTransferQueue restored=PhoneTransferQueue.load(queue.file);
            restored.run(backend,()->false);
            assertEquals(2,restored.completed()); assertEquals(3,copies[0]); assertEquals(2,catalog().list().size());
            assertEquals("newer",read(catalog().runtime(first),"data/drives/rh-29/c/System/Data/save.dat"));
        }
    }
    private File zip(String... paths) throws IOException {
        File archive=new File(root,"source.zip");
        try(ZipOutputStream output=new ZipOutputStream(new FileOutputStream(archive))) {
            for(String path:paths) { output.putNextEntry(new ZipEntry(path)); if(!path.endsWith("/"))output.write("fixture".getBytes(StandardCharsets.UTF_8)); output.closeEntry(); }
        }
        return archive;
    }
    private void rejectedZip(String... paths) throws Exception {
        try(Eka2l1Transfer transfer=new Eka2l1Transfer(root,SILENT)) {
            try { transfer.readZip(zip(paths)); fail("Archive must be rejected"); } catch(IOException expected) { }
            assertTrue(catalog().list().isEmpty());
        }
    }
    @Test public void zipTraversalRejected() throws Exception { rejectedZip("../escape"); assertFalse(new File(root,"escape").exists()); }
    @Test public void zipImplicitParentCaseCollisionRejected() throws Exception { rejectedZip("data/A/save","data/a/other"); }
    @Test public void zipFileDirectoryCollisionRejected() throws Exception { rejectedZip("data/file","data/file/child"); }
    @Test public void zipDirectoryFileCollisionRejected() throws Exception { rejectedZip("data/file/child","data/file"); }
    @Test public void folderWithoutRegistrationRejected() throws Exception {
        Files.createDirectories(source.toPath()); write(source,"game.app","not-a-phone");
        try(Eka2l1Transfer transfer=new Eka2l1Transfer(root,SILENT)) {
            try { transfer.readDirectory(source); fail(); } catch(SystemFailure expected) { assertEquals(SystemFailure.Code.MISSING_REGISTRATION,expected.code); }
        }
    }
    @Test public void duplicateFirmwareCaseRejectedByNativeParser() throws Exception {
        try { Emulator.inspectEka2l1Phones(registration("RH-29",false)+registration("rh-29",false)); fail(); }
        catch(IOException expected) { }
    }
    @Test public void nativeParserSkipsDeletedPhonesAndNormalizesSignedUid() throws Exception {
        String deleted=registration("OLD",false)+"  pending-deletion: true\n";
        String[] phones=Emulator.inspectEka2l1Phones(deleted+registration("RH-29",false).replace("270562329","-1"));
        assertEquals(5,phones.length); assertEquals("RH-29",phones[0]); assertEquals("false",phones[3]); assertTrue(phones[4].contains("4294967295"));
    }
    @Test public void zipAndFolderBothPreserveCaseSensitiveGuestPaths() throws Exception {
        fixture(false); File archive=new File(root,"complete.zip");
        try(ZipOutputStream out=new ZipOutputStream(new FileOutputStream(archive)); java.util.stream.Stream<Path> files=Files.walk(source.toPath())) {
            for(Path file:(Iterable<Path>)files.filter(Files::isRegularFile)::iterator) {
                out.putNextEntry(new ZipEntry("wrapper/"+source.toPath().relativize(file).toString())); Files.copy(file,out); out.closeEntry();
            }
        }
        try(Eka2l1Transfer transfer=new Eka2l1Transfer(root,SILENT)) {
            transfer.readZip(archive); SystemFileCatalog.Entry phone=transfer.install(destination,transfer.phones.get(0),VALID);
            assertEquals("game",read(catalog().runtime(phone),"data/drives/rh-29/e/System/Apps/Game/Game.app"));
        }
    }
    @Test public void linkedSourceIsRejected() throws Exception {
        fixture(false); Files.createSymbolicLink(new File(source,"data/link").toPath(),source.toPath());
        try(Eka2l1Transfer transfer=new Eka2l1Transfer(root,SILENT)) {
            try { transfer.readDirectory(source); fail(); } catch(SystemFailure expected) { assertEquals(SystemFailure.Code.LINKED_FILES,expected.code); }
        }
    }
    @Test public void safTreeCopiesWithoutRequestingSourceWrites() throws Exception {
        fixture(false);
        try(Eka2l1Transfer transfer=new Eka2l1Transfer(root,SILENT)) {
            transfer.readFolder(provider(false,false),android.provider.DocumentsContract.buildTreeDocumentUri("fixture.eka","root"));
            SystemFileCatalog.Entry phone=transfer.install(destination,transfer.phones.get(0),VALID);
            assertEquals("original-save",read(catalog().runtime(phone),"data/drives/rh-29/c/System/Data/save.dat"));
        }
    }
    @Test public void deniedNestedFolderDoesNotBecomePartialImport() throws Exception {
        fixture(false);
        try(Eka2l1Transfer transfer=new Eka2l1Transfer(root,SILENT)) {
            try { transfer.readFolder(provider(true,false),android.provider.DocumentsContract.buildTreeDocumentUri("fixture.eka","root")); fail(); }
            catch(IOException expected) { }
            assertTrue(catalog().list().isEmpty());
        }
    }
    @Test public void nullProviderListingDoesNotBecomeEmptyPhone() throws Exception {
        fixture(false);
        try(Eka2l1Transfer transfer=new Eka2l1Transfer(root,SILENT)) {
            try { transfer.readFolder(provider(false,true),android.provider.DocumentsContract.buildTreeDocumentUri("fixture.eka","root")); fail(); }
            catch(IOException expected) { }
            assertTrue(catalog().list().isEmpty());
        }
    }
    private android.content.Context provider(boolean denyNested,boolean nullListing) {
        android.content.Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        android.content.ContentProvider provider=new android.content.ContentProvider() {
            private File file(android.net.Uri uri) {
                String id=android.provider.DocumentsContract.getDocumentId(uri);
                if(denyNested && id.startsWith("root/data/"))throw new SecurityException("Fixture blocked nested folder");
                return id.equals("root")?source:new File(source,id.substring(5));
            }
            public boolean onCreate() { return true; }
            public String getType(android.net.Uri uri) { return "application/octet-stream"; }
            public android.database.Cursor query(android.net.Uri uri,String[] columns,String s,String[] args,String order) {
                if(nullListing) return null;
                File file=file(uri); android.database.MatrixCursor result=new android.database.MatrixCursor(columns);
                File[] entries=uri.getLastPathSegment().equals("children")?file.listFiles():new File[]{file};
                for(File entry:Objects.requireNonNull(entries)) {
                    android.database.MatrixCursor.RowBuilder row=result.newRow();
                    String path=source.toPath().relativize(entry.toPath()).toString();
                    for(String column:columns) {
                        Object value=null;
                        switch(column) {
                            case "document_id": value=path.isEmpty()?"root":"root/"+path; break;
                            case "_display_name": value=entry.getName(); break;
                            case "mime_type": value=entry.isDirectory()?android.provider.DocumentsContract.Document.MIME_TYPE_DIR:"application/octet-stream"; break;
                            case "_size": value=entry.length(); break;
                            case "last_modified": value=entry.lastModified(); break;
                        }
                        row.add(value);
                    }
                }
                return result;
            }
            public android.os.ParcelFileDescriptor openFile(android.net.Uri uri,String mode) throws FileNotFoundException {
                assertEquals("r",mode); return android.os.ParcelFileDescriptor.open(file(uri),android.os.ParcelFileDescriptor.MODE_READ_ONLY);
            }
            public android.net.Uri insert(android.net.Uri uri,android.content.ContentValues values) { throw new AssertionError("Source write"); }
            public int update(android.net.Uri uri,android.content.ContentValues values,String s,String[] args) { throw new AssertionError("Source write"); }
            public int delete(android.net.Uri uri,String s,String[] args) { throw new AssertionError("Source write"); }
        };
        android.content.pm.ProviderInfo info=new android.content.pm.ProviderInfo(); info.authority="fixture.eka"; provider.attachInfo(context,info);
        return new android.content.ContextWrapper(context) {
            public android.content.ContentResolver getContentResolver() { return android.content.ContentResolver.wrap(provider); }
        };
    }
}
