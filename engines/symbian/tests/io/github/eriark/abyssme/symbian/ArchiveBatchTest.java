package io.github.eriark.abyssme.symbian;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.junit.*;
import static org.junit.Assert.*;
import static io.github.eriark.abyssme.symbian.SisComponentsTest.*;

public final class ArchiveBatchTest {
    private final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final List<File> roots=new ArrayList<>();
    private File root() throws Exception { File f=Files.createTempDirectory(context.getCacheDir().toPath(),"batch-test-").toFile(); roots.add(f); return f; }
    @After public void cleanup() throws Exception { for(File root:roots)PhoneBackupTest.remove(root); }
    private File pkg(File root,int uid,byte[]... needs) throws IOException { return sis(root,uid+".sisx",uid,controller(uid,1,needs),true); }
    static File zip(File root,String name,Map<String,byte[]> entries) throws IOException {
        File file=new File(root,name);
        try(ZipOutputStream out=new ZipOutputStream(new FileOutputStream(file))) {
            for(Map.Entry<String,byte[]> e:entries.entrySet()) { out.putNextEntry(new ZipEntry(e.getKey())); out.write(e.getValue()); out.closeEntry(); }
        } return file;
    }
    private Map<String,byte[]> entries(File... files) throws IOException {
        Map<String,byte[]> map=new LinkedHashMap<>(); for(File f:files) map.put(f.getName(),Files.readAllBytes(f.toPath())); return map;
    }
    private static final class Backend implements GameInstallFlow.Backend {
        final List<Long> installs=new ArrayList<>(); final Map<Long,SisMetadata.Version> installed=new HashMap<>(); long failUid=-1;
        public void open(SystemFileCatalog.Entry entry) { }
        public boolean hasPlatform() {return false;}
        public void stage(File file,String hash) { }
        public SisMetadata.Version installedVersion(long uid) { return installed.get(uid); }
        public void install(File file,GameImport.Kind kind) throws Exception {
            SisMetadata meta=SisMetadata.read(file); installs.add(meta.uid);
            if(failUid==meta.uid) throw new IOException("Test install failure"); installed.put(meta.uid,meta.version);
        }
    }
    private final class Setup {
        final File root; final ArchiveBatch store; final PendingGameImport pending; final ComponentStore components;
        final PlatformStore platforms; final SystemFileCatalog catalog; final Backend backend=new Backend();
        final GameInstallFlow flow; final ArchiveQueue queue;
        Setup(File... inputs) throws Exception {
            root=inputs[0].getParentFile(); store=new ArchiveBatch(new File(root,"batch")); pending=new PendingGameImport(new File(root,"pending"));
            components=new ComponentStore(new File(root,"components")); platforms=new PlatformStore(new File(root,"platforms"));
            catalog=new SystemFileCatalog(new File(root,"systems"));
            File rom=new File(root,"fixture.rom"); Files.write(rom.toPath(),new byte[]{1});
            catalog.add(Collections.singletonList(rom),(runtime,r,p)-> {
                File data=new File(runtime,"data"); data.mkdirs(); Files.write(new File(data,"devices.yml").toPath(),new byte[]{1});
                return new String[]{"RM-320","Test phone","epoc93fp1"};
            });
            flow=new GameInstallFlow(pending,catalog,backend,platforms,components); queue=new ArchiveQueue(store,pending,flow,components,platforms);
            List<String> names=new ArrayList<>(); for(File f:inputs) names.add(f.getName()); store.beginFiles(Arrays.asList(inputs),names,s->{});
        }
    }
    @Test public void previewsSISandSISXInfersDependencyAndKeepsDamagedRow() throws Exception {
        File root=root(); Map<String,byte[]> rows=entries(pkg(root,100,need(200,1,-1)),pkg(root,200),pkg(root,300)); rows.put("damaged.sis",new byte[]{1});
        File archive=zip(root,"games.zip",rows); assertEquals(ImportKind.Kind.BATCH,ImportKind.inspect(archive,"games.zip"));
        ArchiveBatch store=new ArchiveBatch(new File(root,"batch")); store.beginZip(archive,"Games",s->{});
        ArchiveBatch.Batch batch=store.read(); assertEquals(4,batch.items.size()); assertEquals(ArchiveBatch.Role.COMPONENT,batch.items.get(1).role);
        assertFalse(batch.items.get(3).selected); assertFalse(batch.items.get(3).problem.isEmpty()); assertTrue(archive.exists());
        store.discard(); assertTrue(archive.exists());
    }
    @Test public void unsafeAndDuplicatePathsAreRejectedBeforePublication() throws Exception {
        File root=root(); byte[] bytes=Files.readAllBytes(pkg(root,100).toPath());
        for(String name:new String[]{"../escape.sis","/absolute.sis","C:/file.sis","a/./file.sis","a//file.sis"}) {
            ArchiveBatch store=new ArchiveBatch(new File(root,"batch"));
            try { store.beginZip(zip(root,"bad.zip",Collections.singletonMap(name,bytes)),"Bad",s->{}); fail(name); } catch(IOException expected) { }
            assertFalse(store.exists());
        }
        Map<String,byte[]> rows=new LinkedHashMap<>(); rows.put("a.sis",bytes); rows.put("A.sis",bytes);
        ArchiveBatch store=new ArchiveBatch(new File(root,"batch"));
        try { store.beginZip(zip(root,"dupe.zip",rows),"Duplicate",s->{}); fail(); } catch(IOException expected) { }
        assertFalse(store.exists());
    }
    @Test public void sourceOrderDoesNotInstallGamesBeforeTheirComponents() throws Exception {
        File root=root(); Setup s=new Setup(pkg(root,100,need(200,1,-1)),pkg(root,200),pkg(root,300));
        assertNull(s.queue.run(null,false,t->{})); assertEquals(Arrays.asList(200L,100L,300L),s.backend.installs);
        assertFalse(s.pending.exists()); for(ArchiveBatch.Item item:s.store.read().items) assertEquals(ArchiveBatch.State.DONE,item.state);
        assertEquals(1,s.components.list().size()); assertNull(s.queue.run(null,false,t->{})); assertEquals(3,s.backend.installs.size());
    }
    @Test public void failureDoesNotCancelFollowingGamesAndExplicitRetryOnlyRetriesFailure() throws Exception {
        File root=root(); Setup s=new Setup(pkg(root,100),pkg(root,200)); s.backend.failUid=100;
        assertNull(s.queue.run(null,false,t->{})); assertEquals(Arrays.asList(100L,200L),s.backend.installs);
        assertEquals(ArchiveBatch.State.FAILED,s.store.read().items.get(0).state); assertEquals(ArchiveBatch.State.DONE,s.store.read().items.get(1).state);
        s.backend.failUid=-1; s.queue.retry(0); assertNull(s.queue.run(null,true,t->{})); assertEquals(Arrays.asList(100L,200L,100L),s.backend.installs);
    }
    @Test public void waitingGameCanBeDeferredWhileOthersContinue() throws Exception {
        File root=root(); Setup s=new Setup(pkg(root,100,need(300,1,-1)),pkg(root,200));
        assertEquals(GameInstallFlow.Action.COMPONENT,s.queue.run(null,false,t->{}).action); assertTrue(s.pending.exists());
        s.queue.defer(); assertFalse(s.pending.exists()); assertNull(s.queue.run(null,false,t->{})); assertEquals(Arrays.asList(200L),s.backend.installs);
        try(GameImport component=GameImport.inspect(pkg(root,300))) {s.components.save(component,null);}
        s.queue.retry(0); assertNull(s.queue.run(null,false,t->{})); assertEquals(Arrays.asList(200L,300L,100L),s.backend.installs);
    }
    @Test public void completedPendingReceiptSurvivesRecreationWithoutReinstalling() throws Exception {
        File root=root(); Setup s=new Setup(pkg(root,100),pkg(root,200)); ArchiveBatch.Batch batch=s.store.read();
        batch.started=true; batch.items.get(0).state=ArchiveBatch.State.ACTIVE; s.store.save(batch);
        try(GameImport game=GameImport.inspect(s.store.payload(batch.items.get(0)))) {s.pending.begin(game,"Game",batch.owner(batch.items.get(0)));}
        assertEquals(GameInstallFlow.Action.DONE,s.flow.resume(null,false).action); assertEquals(PendingGameImport.Step.COMPLETE,s.pending.read().step);
        PendingGameImport reopened=new PendingGameImport(new File(root,"pending")); reopened.recover(); assertTrue(reopened.exists());
        GameInstallFlow flow=new GameInstallFlow(reopened,s.catalog,s.backend,s.platforms,s.components);
        ArchiveQueue queue=new ArchiveQueue(new ArchiveBatch(new File(root,"batch")),reopened,flow,s.components,s.platforms);
        assertNull(queue.run(null,false,t->{})); assertEquals(Arrays.asList(100L,200L),s.backend.installs); assertFalse(reopened.exists());
    }
    @Test public void queueCommitBeforePendingCleanupDoesNotReplay() throws Exception {
        File root=root(); Setup s=new Setup(pkg(root,100)); ArchiveBatch.Batch batch=s.store.read();
        batch.started=true; batch.items.get(0).state=ArchiveBatch.State.DONE; s.store.save(batch);
        try(GameImport game=GameImport.inspect(s.store.payload(batch.items.get(0)))) {s.pending.begin(game,"Game",batch.owner(batch.items.get(0)));}
        assertNull(s.queue.run(null,false,t->{})); assertTrue(s.backend.installs.isEmpty()); assertFalse(s.pending.exists());
    }
    @Test public void interruptedNativeInstallRequiresConfirmationAndSelectionIsRetained() throws Exception {
        File root=root(); Setup s=new Setup(pkg(root,100),pkg(root,200)); ArchiveBatch.Batch batch=s.store.read();
        batch.started=true; batch.items.get(0).state=ArchiveBatch.State.ACTIVE; batch.items.get(1).selected=false; batch.items.get(1).state=ArchiveBatch.State.SKIPPED; s.store.save(batch);
        try(GameImport game=GameImport.inspect(s.store.payload(batch.items.get(0)))) {s.pending.begin(game,"Game",batch.owner(batch.items.get(0)));}
        s.pending.update(s.pending.read(),s.catalog.list().get(0).id,PendingGameImport.Step.INSTALLING);
        assertEquals(GameInstallFlow.Action.RETRY,s.queue.run(null,false,t->{}).action); assertTrue(s.backend.installs.isEmpty());
        assertNull(s.queue.run(null,true,t->{})); assertEquals(Arrays.asList(100L),s.backend.installs); assertEquals(ArchiveBatch.State.SKIPPED,s.store.read().items.get(1).state);
    }
    @Test public void separatePendingImportIsNeverReplaced() throws Exception {
        File root=root(); Setup s=new Setup(pkg(root,100));
        try(GameImport other=GameImport.inspect(pkg(root,999))) {s.pending.begin(other,"Other game");}
        try {s.queue.run(null,false,t->{});fail();}catch(IOException expected){}
        assertEquals("Other game",s.pending.read().name); assertFalse(s.store.read().started);
        s.queue.discard(); assertEquals("Other game",s.pending.read().name);
    }
    @Test public void changedRetainedFileFailsOnlyItsRow() throws Exception {
        File root=root(); Setup s=new Setup(pkg(root,100),pkg(root,200));
        Files.write(s.store.payload(s.store.read().items.get(0)).toPath(),new byte[]{3});
        assertNull(s.queue.run(null,false,t->{})); assertEquals(Arrays.asList(200L),s.backend.installs); assertEquals(ArchiveBatch.State.FAILED,s.store.read().items.get(0).state);
    }
    @Test public void cancelledPreviewLeavesNoPublishedQueueAndSourceRemains() throws Exception {
        File root=root(); File source=zip(root,"source.zip",entries(pkg(root,100),pkg(root,200))); ArchiveBatch store=new ArchiveBatch(new File(root,"batch"));
        try {store.beginZip(source,"Source",t->{throw new InterruptedIOException("Cancelled");});fail();}catch(InterruptedIOException expected){}
        assertFalse(store.exists()); assertTrue(source.isFile());
    }
    @Test public void duplicatesAreNotSelectedTwiceAndNestedSingleGameZipIsSupported() throws Exception {
        File root=root(); File game=pkg(root,100); Map<String,byte[]> files=entries(game); files.put("copy.sis",Files.readAllBytes(game.toPath()));
        files.put("nested.zip",Files.readAllBytes(zip(root,"one.zip",entries(pkg(root,200))).toPath()));
        ArchiveBatch store=new ArchiveBatch(new File(root,"batch")); store.beginZip(zip(root,"outer.zip",files),"Games",t->{});
        assertEquals(3,store.read().items.size()); assertFalse(store.read().items.get(1).selected); assertTrue(store.read().items.get(2).problem.isEmpty());
    }
    @Test public void realSuppliedSISandSISXPreview() throws Exception {
        String path=InstrumentationRegistry.getArguments().getString("batchFixture"); Assume.assumeNotNull(path);
        File root=root(); ArchiveBatch store=new ArchiveBatch(new File(root,"batch")); store.beginZip(new File(path),"Supplied games",t->{});
        int old=0,modern=0; for(ArchiveBatch.Item item:store.read().items) {
            assertEquals(item.name,"",item.problem); if(item.kind==GameImport.Kind.SIS_LEGACY)old++; if(item.kind==GameImport.Kind.SIS_MODERN)modern++;
        }
        assertEquals(1,old); assertEquals(3,modern); store.discard();
    }
    @Test public void versionsOfSameGameRequireExplicitSelection() throws Exception {
        File root=root(); File first=pkg(root,100),second=sis(root,"other.sis",100,controller(100,2,new byte[0][]),true);
        Setup s=new Setup(first,second); ArchiveBatch.Batch batch=s.store.read();
        assertTrue(batch.items.get(0).selected); assertFalse(batch.items.get(1).selected);
        assertTrue(batch.items.get(1).detail.startsWith("Alternative version"));
        assertNull(s.queue.run(null,false,t->{})); assertEquals(Arrays.asList(100L),s.backend.installs);
    }
    @Test public void deselectedFilesAndSkipSurviveResumeWithoutInstall() throws Exception {
        File root=root(); Setup s=new Setup(pkg(root,100),pkg(root,200)); ArchiveBatch.Batch batch=s.store.read();
        batch.items.get(0).selected=false; s.store.save(batch);
        try {s.queue.run(null,false,t->{throw new InterruptedIOException("Pause");});fail();}catch(InterruptedIOException expected){}
        assertTrue(s.store.read().started); s.queue.skip(1); assertNull(s.queue.run(null,false,t->{}));
        assertTrue(s.backend.installs.isEmpty()); assertFalse(s.pending.exists());
    }
    @Test public void noSelectionDoesNotStartOrMutatePending() throws Exception {
        File root=root(); Setup s=new Setup(pkg(root,100)); ArchiveBatch.Batch batch=s.store.read();
        batch.items.get(0).selected=false; s.store.save(batch);
        try {s.queue.run(null,false,t->{});fail();}catch(IOException expected){}
        assertFalse(s.store.read().started); assertFalse(s.pending.exists());
    }
    private SystemFileCatalog.Entry anotherPhone(Setup s) throws Exception {
        File rom=new File(s.root,"another.rom");Files.write(rom.toPath(),new byte[]{2});
        return s.catalog.add(Collections.singletonList(rom),(runtime,r,p)-> {
            new File(runtime,"data").mkdirs();Files.write(new File(runtime,"data/devices.yml").toPath(),new byte[]{1});
            return new String[]{"RM-356","Other phone","epoc94"};
        });
    }
    @Test public void rememberedChoiceSurvivesReloadAndOnlyAppliesInsideThisBatch() throws Exception {
        File root=root();Setup s=new Setup(pkg(root,100),pkg(root,200));SystemFileCatalog.Entry choice=anotherPhone(s);
        assertEquals(GameInstallFlow.Action.CHOOSE,s.queue.run(null,false,t->{}).action);
        s.queue.rememberPhone(choice.id,true);
        ArchiveQueue reopened=new ArchiveQueue(new ArchiveBatch(new File(root,"batch")),s.pending,s.flow,s.components,s.platforms);
        assertNull(reopened.run(choice.id,false,t->{}));
        for(ArchiveBatch.Item item:s.store.read().items)assertEquals(choice.id,item.system);
        try(GameImport game=GameImport.inspect(pkg(root,300))) {s.pending.begin(game,"Independent");}
        assertEquals(GameInstallFlow.Action.CHOOSE,s.flow.resume(null,false).action);
    }
    @Test public void existingGameBindingWinsOverArchivePreference() throws Exception {
        File root=root();File second=pkg(root,200);Setup s=new Setup(pkg(root,100),second);
        SystemFileCatalog.Entry bound=s.catalog.list().get(0),chosen=anotherPhone(s);
        try(GameImport game=GameImport.inspect(second)){s.catalog.bind(game.hash,bound);}
        assertEquals(GameInstallFlow.Action.CHOOSE,s.queue.run(null,false,t->{}).action);s.queue.rememberPhone(chosen.id,true);
        assertNull(s.queue.run(chosen.id,false,t->{}));
        assertEquals(chosen.id,s.store.read().items.get(0).system);assertEquals(bound.id,s.store.read().items.get(1).system);
    }
    @Test public void optOutAndUnavailablePreferenceStillAsk() throws Exception {
        File root=root();Setup s=new Setup(pkg(root,100),pkg(root,200));SystemFileCatalog.Entry choice=anotherPhone(s);
        assertEquals(GameInstallFlow.Action.CHOOSE,s.queue.run(null,false,t->{}).action);s.queue.rememberPhone(choice.id,false);
        assertEquals(GameInstallFlow.Action.CHOOSE,s.queue.run(choice.id,false,t->{}).action);assertTrue(s.store.read().phones.isEmpty());
        ArchiveBatch.Batch batch=s.store.read();batch.phones.put(GameImport.Kind.SIS_MODERN,String.join("",Collections.nCopies(64,"f")));s.store.save(batch);
        assertEquals(GameInstallFlow.Action.CHOOSE,s.queue.run(null,false,t->{}).action);
    }
    @Test public void preferenceDoesNotCrossPackageTypes() throws Exception {
        File root=root();Setup s=new Setup(pkg(root,100));SystemFileCatalog.Entry choice=anotherPhone(s);
        ArchiveBatch.Batch batch=s.store.read();batch.phones.put(GameImport.Kind.NGAGE2,choice.id);s.store.save(batch);
        assertEquals(GameInstallFlow.Action.CHOOSE,s.queue.run(null,false,t->{}).action);
    }
}
