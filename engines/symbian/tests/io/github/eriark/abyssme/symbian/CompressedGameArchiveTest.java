package io.github.eriark.abyssme.symbian;

import androidx.test.platform.app.InstrumentationRegistry;
import org.apache.commons.compress.archivers.sevenz.*;
import org.junit.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;
import static io.github.eriark.abyssme.symbian.SisComponentsTest.*;

public final class CompressedGameArchiveTest {
    private File root;
    @Before public void setup() throws Exception {root=Files.createTempDirectory(InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir().toPath(),"compressed-test-").toFile();}
    @After public void cleanup() throws Exception {PhoneBackupTest.remove(root);}
    private File seven(Map<String,byte[]> files) throws Exception {
        File file=File.createTempFile("archive-",".bin",root);
        try(SevenZOutputFile out=new SevenZOutputFile(file)) {
            for(Map.Entry<String,byte[]> item:files.entrySet()) {
                SevenZArchiveEntry e=new SevenZArchiveEntry();e.setName(item.getKey());e.setSize(item.getValue().length);e.setDirectory(item.getKey().endsWith("/"));
                out.putArchiveEntry(e);out.write(item.getValue());out.closeArchiveEntry();
            }
        }
        return file;
    }
    private byte[] pkg(int uid) throws Exception {return Files.readAllBytes(sis(root,uid+".sis",uid,controller(uid,1,new byte[0][]),true).toPath());}
    private void noDecodedFiles() {assertEquals(0,root.listFiles(f->f.getName().startsWith("decoded-")).length);}
    @Test public void renamedSevenZSinglePackageAndFirmwareHaveDifferentRoutes() throws Exception {
        byte[] bytes=pkg(100);File source=seven(Collections.singletonMap("game.sisx",bytes));
        assertEquals(ImportKind.Kind.CONTAINER,ImportKind.inspect(source,"unknown.bin"));
        File owned;
        try(GameImport game=GameImport.inspect(source)) {assertEquals(GameImport.Kind.SIS_MODERN,game.kind);assertArrayEquals(bytes,Files.readAllBytes(game.file.toPath()));owned=game.file;}
        assertFalse(owned.exists());assertTrue(source.exists());noDecodedFiles();
        File system=seven(Collections.singletonMap("phone.rom",new byte[512]));
        assertEquals(ImportKind.Kind.SYSTEM,ImportKind.inspect(system,"phones.bin"));
    }
    @Test public void batchMetadataSurvivesConversionAndConversionIsDeterministic() throws Exception {
        Map<String,byte[]> files=new LinkedHashMap<>();files.put("Games/",new byte[0]);files.put("Games/a.sis",pkg(100));files.put("Games/b.sisx",pkg(200));
        File source=seven(files),a=CompressedGameArchive.toZip(source,t->{}),b=CompressedGameArchive.toZip(source,t->{});
        assertEquals(SystemFileCatalog.hash(a),SystemFileCatalog.hash(b));assertEquals(ImportKind.Kind.BATCH,ImportKind.inspect(a,"games.7z"));
        ArchiveBatch store=new ArchiveBatch(new File(root,"batch"));store.beginZip(a,"Games",t->{});
        assertEquals(2,store.read().items.size());for(ArchiveBatch.Item item:store.read().items)assertEquals("",item.problem);
    }
    @Test public void unsafeCollisionAndFileDirectoryConflictsLeaveNoPartialZip() throws Exception {
        for(String path:new String[]{"../escape.sis","/escape.sis","C:/escape.sis","a/./game.sis","a//game.sis"}) {
            File source=seven(Collections.singletonMap(path,pkg(100)));
            try {CompressedGameArchive.toZip(source,t->{});fail(path);}catch(IOException expected){}
            assertTrue(source.exists());noDecodedFiles();
        }
        for(String[] paths:new String[][]{{"game.sis","GAME.SIS"},{"a","a/game.sis"},{"a/game.sis","a"}}) {
            Map<String,byte[]> files=new LinkedHashMap<>();for(String path:paths)files.put(path,pkg(100));
            try {CompressedGameArchive.toZip(seven(files),t->{});fail(Arrays.toString(paths));}catch(IOException expected){}
            noDecodedFiles();
        }
    }
    @Test public void cancelledConversionAndCorruptArchiveRetainOriginalOnly() throws Exception {
        File source=seven(Collections.singletonMap("large.sis",new byte[512*1024]));final int[] calls={0};
        try {CompressedGameArchive.toZip(source,t->{if(++calls[0]>=3)throw new InterruptedIOException("Cancel");});fail();}catch(InterruptedIOException expected){}
        assertTrue(calls[0]>=3);noDecodedFiles();assertTrue(source.exists());
        try(RandomAccessFile file=new RandomAccessFile(source,"rw")){file.setLength(10);}
        try {CompressedGameArchive.toZip(source,t->{});fail();}catch(IOException expected){}
        noDecodedFiles();
    }
    @Test public void nestedSevenZCanBeSelectedAsOneGameInZipBatch() throws Exception {
        File nested=seven(Collections.singletonMap("game.sis",pkg(100)));
        Map<String,byte[]> files=new LinkedHashMap<>();files.put("one.7z",Files.readAllBytes(nested.toPath()));files.put("two.sis",pkg(200));
        File zip=ArchiveBatchTest.zip(root,"outer.zip",files);ArchiveBatch store=new ArchiveBatch(new File(root,"batch"));store.beginZip(zip,"Games",t->{});
        for(ArchiveBatch.Item item:store.read().items) {assertEquals("",item.problem);try(GameImport game=GameImport.inspect(store.payload(item))){assertEquals(GameImport.Kind.SIS_MODERN,game.kind);}}
    }
    @Test public void suppliedAsphaltRarAndSolidSevenZDecode() throws Exception {
        String rar=InstrumentationRegistry.getArguments().getString("rarFixture"),seven=InstrumentationRegistry.getArguments().getString("sevenFixture");
        Assume.assumeNotNull(rar,seven);
        File source=new File(root,"asphalt.rar");Files.copy(new File(rar).toPath(),source.toPath());
        assertEquals(ImportKind.Kind.CONTAINER,ImportKind.inspect(source,"renamed.bin"));
        try(GameImport game=GameImport.inspect(source)){assertEquals(GameImport.Kind.NGAGE2,game.kind);assertEquals(12110467L,game.file.length());}
        File batch=new File(root,"games.7z");Files.copy(new File(seven).toPath(),batch.toPath());
        File zip=CompressedGameArchive.toZip(batch,t->{});ArchiveBatch store=new ArchiveBatch(new File(root,"batch"));store.beginZip(zip,"Games",t->{});
        assertEquals(4,store.read().items.size());for(ArchiveBatch.Item item:store.read().items)assertEquals(item.name,"",item.problem);
    }
    @Test public void rar5DecodesAndEncryptedArchiveFailsWithoutOutput() throws Exception {
        String good=InstrumentationRegistry.getArguments().getString("rar5Fixture"),bad=InstrumentationRegistry.getArguments().getString("encryptedFixture");
        Assume.assumeNotNull(good,bad);
        File source=new File(root,"rar5.rar");Files.copy(new File(good).toPath(),source.toPath());
        File converted=CompressedGameArchive.toZip(source,t->{});
        try(java.util.zip.ZipFile zip=new java.util.zip.ZipFile(converted)){assertEquals(1200,zip.getEntry("test.bin").getSize());}
        Files.delete(converted.toPath());
        File encrypted=new File(root,"encrypted.rar");Files.copy(new File(bad).toPath(),encrypted.toPath());
        try {CompressedGameArchive.toZip(encrypted,t->{});fail();}catch(IOException expected){}
        noDecodedFiles();assertTrue(encrypted.exists());
    }
    @Test public void recoveryOnlyRemovesUnclaimedDecoderOutput() throws Exception {
        File leftover=new File(root,"decoded-interrupted.zip"),owned=new File(root,"input-original.7z");
        Files.write(leftover.toPath(),new byte[]{1});Files.write(owned.toPath(),new byte[]{2});
        CompressedGameArchive.recover(root);assertFalse(leftover.exists());assertTrue(owned.exists());
    }
}
