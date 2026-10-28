package io.github.eriark.abyssme.symbian;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.DeflaterOutputStream;
import org.junit.Test;
import org.junit.Assume;
import static org.junit.Assert.*;

public final class SisComponentsTest {
    private final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
    static byte[] join(byte[]... parts) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream(); for(byte[] part:parts) out.write(part); return out.toByteArray();
    }
    static byte[] ints(int... values) { ByteBuffer b=ByteBuffer.allocate(values.length*4).order(ByteOrder.LITTLE_ENDIAN); for(int v:values) b.putInt(v); return b.array(); }
    static byte[] field(int type,byte[] body) throws IOException { return join(ints(type,body.length),body,new byte[(4-body.length%4)%4]); }
    static byte[] array(int type,byte[]... fields) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream(); out.write(ints(type));
        for(byte[] f:fields) out.write(f,4,f.length-4); return field(2,out.toByteArray());
    }
    static byte[] text(String s) throws IOException { return field(1,s.getBytes(StandardCharsets.UTF_16LE)); }
    static byte[] need(int uid,int min,int max) throws IOException {
        return field(18,join(field(9,ints(uid)),field(5,join(field(4,ints(min,0,0)),max<0 ? new byte[0] : field(4,ints(max,0,0)))),array(1,text(uid==0xE000AB02 ? "Test game runtime" : "Runtime "+uid))));
    }
    static byte[] controller(int uid,int version,byte[][] needs,byte[]... embedded) throws IOException {
        return typedController(uid,version,0,needs,embedded);
    }
    static byte[] typedController(int uid,int version,int type,byte[][] needs,byte[]... embedded) throws IOException {
        return field(13,join(field(14,join(field(9,ints(uid)),text("Test vendor"),array(1,text(uid==0xE000AB02 ? "Test game runtime" : "Package "+uid)),array(1,text("Test vendor")),
                field(4,ints(version,0,0)),field(8,join(field(6,new byte[]{(byte)0xEA,7,0,0}),field(7,new byte[]{0,0,0}))),new byte[]{(byte)type,0})),
                field(16,array(33)),field(15,array(11,field(11,ints(1)))),
                field(17,join(array(18,need(0x102032be,0,-1)),array(18,needs))),field(19,array(20)),
                field(28,join(array(24),array(13,embedded),array(26))),field(40,ints(0))));
    }
    static File sis(File root,String name,int uid,byte[] controller,boolean compressed) throws IOException {
        byte[] data=controller;
        if(compressed) { ByteArrayOutputStream b=new ByteArrayOutputStream(); try(DeflaterOutputStream z=new DeflaterOutputStream(b)) { z.write(controller); } data=b.toByteArray(); }
        File result=new File(root,name);
        Files.write(result.toPath(),join(ints(0x10201a7a,0,uid,0),field(12,join(field(3,join(ints(compressed?1:0,controller.length,0),data)),field(30,array(31,field(31,array(32))))))));
        return result;
    }
    private File root() throws Exception { return Files.createTempDirectory(context.getCacheDir().toPath(),"sis-components-").toFile(); }
    private File pkg(File root,int uid,int version,byte[]... deps) throws Exception { return sis(root,uid+"-"+version+".sis",uid,controller(uid,version,deps),true); }
    private void save(ComponentStore store,File file) throws Exception { try(GameImport input=GameImport.inspect(file)) { store.save(input,null); } }
    private static class Backend implements GameInstallFlow.Backend {
        final Map<Long,SisMetadata.Version> installed=new HashMap<>(); final List<Long> installs=new ArrayList<>();
        boolean fail, register=true;
        public void open(SystemFileCatalog.Entry entry) { }
        public boolean hasPlatform() { return false; }
        public void stage(File f,String hash) { }
        public SisMetadata.Version installedVersion(long uid) { return installed.get(uid); }
        public void install(File file,GameImport.Kind kind) throws IOException {
            SisMetadata m=SisMetadata.read(file); installs.add(m.uid); if(fail) throw new IOException("Interrupted");
            if(register) installed.put(m.uid,m.version);
        }
    }
    @Test public void metadataReadsCompressedPlainRangesAndEmbeddedWithoutPhoneUid() throws Exception {
        File root=root();
        for(boolean compressed:new boolean[]{false,true}) {
            byte[] controller=controller(100,2,new byte[][]{need(200,1,3)},controller(300,4,new byte[0][]));
            SisMetadata m=SisMetadata.read(sis(root,"sample",100,controller,compressed));
            assertEquals(100,m.uid); assertEquals("Package 100",m.name); assertEquals("2.0.0",m.version.toString());
            assertEquals(1,m.needs.size()); assertEquals(200,m.needs.get(0).uid); assertEquals(300,m.embedded.get(0).uid);
            assertTrue(m.needs.get(0).accepts(new SisMetadata.Version(3,0,0))); assertFalse(m.needs.get(0).accepts(new SisMetadata.Version(3,0,1)));
        }
    }
    @Test public void rejectsTruncationUidMismatchAndOversizedInflation() throws Exception {
        File root=root(); File good=pkg(root,100,1); byte[] bytes=Files.readAllBytes(good.toPath());
        for(int length:new int[]{0,15,24,bytes.length-1}) {
            File bad=new File(root,"bad"); Files.write(bad.toPath(),Arrays.copyOf(bytes,length));
            try { SisMetadata.read(bad); fail("truncated "+length); } catch(IOException expected) { }
        }
        byte[] changed=bytes.clone(); ByteBuffer.wrap(changed).order(ByteOrder.LITTLE_ENDIAN).putInt(8,999);
        Files.write(good.toPath(),changed); try { SisMetadata.read(good); fail(); } catch(IOException expected) { }
        changed=bytes.clone(); ByteBuffer.wrap(changed).order(ByteOrder.LITTLE_ENDIAN).putInt(36,SisMetadata.LIMIT+1);
        Files.write(good.toPath(),changed); try { SisMetadata.read(good); fail(); } catch(IOException expected) { }
    }
    @Test public void cacheChecksIdentityVersionHashAndDoesNotTouchSource() throws Exception {
        File root=root(); ComponentStore store=new ComponentStore(new File(root,"cache")); File file=pkg(root,200,2);
        SisMetadata.Need requested=new SisMetadata.Need(200,"Runtime",new SisMetadata.Version(3,0,0),null);
        try(GameImport input=GameImport.inspect(file)) {
            try { store.save(input,requested); fail(); } catch(IOException expected) { }
            assertTrue(store.list().isEmpty()); store.save(input,null); store.save(input,null);
        }
        assertEquals(1,store.list().size()); assertTrue(file.exists());
        ComponentStore.Item item=store.list().get(0); Files.write(item.file.toPath(),new byte[]{1});
        assertTrue(store.list().isEmpty()); save(store,file); assertEquals(1,store.list().size());
        store.remove(store.list().get(0).hash); assertTrue(store.list().isEmpty()); assertTrue(file.exists());
    }
    @Test public void recursivePlanIsOrderedReusesInstalledAndSelectsCompatibleVersion() throws Exception {
        File root=root(); ComponentStore store=new ComponentStore(new File(root,"cache")); Backend backend=new Backend();
        SisMetadata game=SisMetadata.read(pkg(root,100,1,need(200,2,3)));
        save(store,pkg(root,200,2,need(300,1,-1))); save(store,pkg(root,200,9)); save(store,pkg(root,300,1));
        ComponentPlan plan=new ComponentPlan(game,store,backend);
        assertEquals(2,plan.installs.size()); assertEquals(300,plan.installs.get(0).metadata.uid); assertEquals(200,plan.installs.get(1).metadata.uid);
        assertTrue(backend.installs.isEmpty()); backend.installed.put(200L,new SisMetadata.Version(2,0,0));
        assertTrue(new ComponentPlan(game,store,backend).installs.isEmpty());
    }
    @Test public void missingCycleConflictsAndDowngradesNeverInstall() throws Exception {
        File root=root(); ComponentStore store=new ComponentStore(new File(root,"cache")); Backend backend=new Backend();
        SisMetadata game=SisMetadata.read(pkg(root,100,1,need(200,1,2)));
        try { new ComponentPlan(game,store,backend); fail(); } catch(ComponentPlan.Missing e) { assertEquals(200,e.need.uid); }
        save(store,pkg(root,200,1,need(300,1,-1))); save(store,pkg(root,300,1,need(200,1,-1)));
        try { new ComponentPlan(game,store,backend); fail(); } catch(IOException expected) { assertTrue(expected.getMessage().contains("ircular")); }
        backend.installed.put(200L,new SisMetadata.Version(9,0,0));
        try { new ComponentPlan(game,store,backend); fail(); } catch(IOException expected) { assertTrue(expected.getMessage().contains("older")); }
        assertTrue(backend.installs.isEmpty());
    }
    @Test public void unconditionalEmbeddedComponentSatisfiesRequirement() throws Exception {
        File root=root(); ComponentStore store=new ComponentStore(new File(root,"cache"));
        SisMetadata game=SisMetadata.read(sis(root,"game",100,controller(100,1,new byte[][]{need(200,1,-1)},controller(200,1,new byte[0][])),true));
        assertTrue(new ComponentPlan(game,store,new Backend()).installs.isEmpty());
    }
    @Test public void overlappingRequirementsChooseSharedVersionBeforeInstalling() throws Exception {
        File root=root(); ComponentStore store=new ComponentStore(new File(root,"cache"));
        save(store,pkg(root,200,1)); save(store,pkg(root,200,2)); save(store,pkg(root,200,3));
        SisMetadata game=SisMetadata.read(pkg(root,100,1,need(200,1,3),need(200,1,2)));
        ComponentPlan plan=new ComponentPlan(game,store,new Backend());
        assertEquals(1,plan.installs.size()); assertEquals("2.0.0",plan.installs.get(0).metadata.version.toString());
    }
    @Test public void partialUpdatesWithSharedUidRetainAllNestedDependencies() throws Exception {
        File root=root(); ComponentStore store=new ComponentStore(new File(root,"cache")); Backend backend=new Backend();
        save(store,pkg(root,300,1)); save(store,pkg(root,400,2));
        byte[] first=typedController(200,1,2,new byte[][]{need(300,1,1)});
        byte[] second=typedController(200,2,2,new byte[0][],controller(500,1,new byte[][]{need(400,2,2)}));
        SisMetadata m=SisMetadata.read(sis(root,"updates.sis",100,controller(100,1,new byte[0][],first,second),true));
        ComponentPlan plan=new ComponentPlan(m,store,backend);
        assertEquals(2,plan.installs.size());
        assertEquals(300,plan.installs.get(0).metadata.uid); assertEquals(400,plan.installs.get(1).metadata.uid);
        assertEquals(2,m.embedded.size()); assertTrue(backend.installs.isEmpty());
    }
    @Test public void partialUpdateIsNeverMistakenForCompleteDependency() throws Exception {
        File root=root(); ComponentStore store=new ComponentStore(new File(root,"cache"));
        byte[] base=controller(200,1,new byte[0][]),update=typedController(200,4,2,new byte[0][]);
        SisMetadata m=SisMetadata.read(sis(root,"base-update.sis",100,
                controller(100,1,new byte[][]{need(200,4,4)},base,update),true));
        try { new ComponentPlan(m,store,new Backend()); fail(); }
        catch(ComponentPlan.Missing missing) { assertEquals(200,missing.need.uid); }
        m=SisMetadata.read(sis(root,"base-update.sis",100,
                controller(100,1,new byte[][]{need(200,1,1)},base,update),true));
        assertTrue(new ComponentPlan(m,store,new Backend()).installs.isEmpty());
    }
    @Test public void repeatedFullAugmentedPreinstalledAndIdenticalControllersStayBlocked() throws Exception {
        File root=root(); ComponentStore store=new ComponentStore(new File(root,"cache")); Backend backend=new Backend();
        for(int type:new int[]{0,1,2,3,4}) {
            byte[] first=typedController(200,1,type,new byte[0][]);
            byte[] second=typedController(200,type==2?1:2,type,new byte[0][]);
            for(boolean nested:new boolean[]{false,true}) {
                byte[] body=nested?typedController(200,3,type,new byte[0][],first,second)
                        :controller(100,1,new byte[0][],first,second);
                SisMetadata m=SisMetadata.read(sis(root,"conflict.sis",nested?200:100,body,true));
                try { new ComponentPlan(m,store,backend); fail("type="+type); }
                catch(SystemFailure failure) {
                    assertEquals(SystemFailure.Code.COMPONENT_BUNDLE_CONFLICT,failure.code);
                    assertTrue(failure.getMessage().contains("000000C8"));
                    assertTrue(failure.getMessage().contains(m.name));
                }
            }
        }
        assertTrue(backend.installs.isEmpty());
    }
    @Test public void partialUpdateRequirementsCannotHideConflictingRanges() throws Exception {
        File root=root(); ComponentStore store=new ComponentStore(new File(root,"cache")); Backend backend=new Backend();
        save(store,pkg(root,300,1)); save(store,pkg(root,300,2));
        SisMetadata m=SisMetadata.read(sis(root,"ranges.sis",100,controller(100,1,new byte[0][],
                typedController(200,1,2,new byte[][]{need(300,1,1)}),
                typedController(200,2,2,new byte[][]{need(300,2,2)})),true));
        try { new ComponentPlan(m,store,backend); fail(); }
        catch(SystemFailure failure) { assertEquals(SystemFailure.Code.COMPONENT_VERSION_CONFLICT,failure.code); }
        assertTrue(backend.installs.isEmpty());
    }
    @Test public void conflictsAndMissingTransitiveDependencyDoNotPartiallyInstall() throws Exception {
        File root=root(); ComponentStore store=new ComponentStore(new File(root,"cache")); Backend backend=new Backend();
        save(store,pkg(root,200,1,need(300,1,-1)));
        SisMetadata game=SisMetadata.read(pkg(root,100,1,need(200,1,-1)));
        try { new ComponentPlan(game,store,backend); fail(); } catch(ComponentPlan.Missing e) { assertEquals(300,e.need.uid); }
        assertTrue(backend.installs.isEmpty());
        store.clear(); save(store,pkg(root,200,1)); save(store,pkg(root,200,2));
        game=SisMetadata.read(pkg(root,100,2,need(200,1,1),need(200,2,2)));
        try { new ComponentPlan(game,store,backend); fail(); } catch(IOException e) { assertTrue(e.getMessage().contains("Conflicting")); }
        assertTrue(backend.installs.isEmpty());
    }
    private SystemFileCatalog catalog(File root) throws Exception {
        SystemFileCatalog catalog=new SystemFileCatalog(new File(root,"phones")); File rom=new File(root,"test.rom"); Files.write(rom.toPath(),new byte[]{1});
        catalog.add(Collections.singletonList(rom),(runtime,r,p) -> {
            File data=new File(runtime,"data"); assertTrue(data.mkdirs()); Files.write(new File(data,"devices.yml").toPath(),new byte[]{1});
            return new String[]{"RM-320","Test phone","epoc93fp1"};
        }); return catalog;
    }
    @Test public void pendingGameResumesAfterRecreationAndRejectsWrongComponent() throws Exception {
        File root=root(); PendingGameImport pending=new PendingGameImport(new File(root,"pending"));
        ComponentStore store=new ComponentStore(new File(root,"cache")); Backend backend=new Backend(); SystemFileCatalog catalog=catalog(root);
        File source=pkg(root,100,1,need(200,1,-1));
        try(GameImport game=GameImport.inspect(source)) { pending.begin(game,"Test game"); }
        GameInstallFlow flow=new GameInstallFlow(pending,catalog,backend,null,store);
        assertEquals(GameInstallFlow.Action.COMPONENT,flow.resume(null,false).action);
        assertEquals(PendingGameImport.Step.COMPONENT,pending.read().step); assertTrue(backend.installs.isEmpty());
        try(GameImport wrong=GameImport.inspect(pkg(root,300,1))) {
            try { flow.addComponent(wrong); fail(); } catch(IOException expected) { }
        }
        assertTrue(store.list().isEmpty()); Files.delete(source.toPath());
        pending=new PendingGameImport(new File(root,"pending")); pending.recover();
        flow=new GameInstallFlow(pending,catalog,backend,null,store);
        try(GameImport right=GameImport.inspect(pkg(root,200,1))) { assertEquals(GameInstallFlow.Action.DONE,flow.addComponent(right).action); }
        assertEquals(Arrays.asList(200L,100L),backend.installs); assertFalse(pending.exists()); assertEquals(1,store.list().size());
    }
    @Test public void failedComponentRegistrationRetainsGameAndRequiresRetry() throws Exception {
        File root=root(); PendingGameImport pending=new PendingGameImport(new File(root,"pending"));
        ComponentStore store=new ComponentStore(new File(root,"cache")); Backend backend=new Backend(); SystemFileCatalog catalog=catalog(root);
        try(GameImport game=GameImport.inspect(pkg(root,100,1,need(200,1,-1)))) { pending.begin(game,"Game"); }
        save(store,pkg(root,200,1)); backend.register=false;
        GameInstallFlow flow=new GameInstallFlow(pending,catalog,backend,null,store);
        try { flow.resume(null,false); fail(); } catch(IOException expected) { }
        assertEquals(GameInstallFlow.Action.RETRY,flow.resume(null,false).action); assertEquals(Arrays.asList(200L),backend.installs);
        backend.register=true; assertEquals(GameInstallFlow.Action.DONE,flow.resume(null,true).action);
        assertEquals(Arrays.asList(200L,200L,100L),backend.installs);
    }
    @Test public void retryAfterGameFailureDoesNotReinstallVerifiedComponent() throws Exception {
        File root=root(); PendingGameImport pending=new PendingGameImport(new File(root,"pending"));
        ComponentStore store=new ComponentStore(new File(root,"cache")); SystemFileCatalog catalog=catalog(root);
        Backend backend=new Backend() {
            boolean first=true;
            @Override public void install(File file,GameImport.Kind kind) throws IOException {
                if(SisMetadata.read(file).uid==100 && first) { first=false; throw new IOException("Interrupted game"); }
                super.install(file,kind);
            }
        };
        try(GameImport game=GameImport.inspect(pkg(root,100,1,need(200,1,-1)))) { pending.begin(game,"Game"); }
        save(store,pkg(root,200,1)); GameInstallFlow flow=new GameInstallFlow(pending,catalog,backend,null,store);
        try { flow.resume(null,false); fail(); } catch(IOException expected) { }
        assertEquals(Arrays.asList(200L),backend.installs);
        flow=new GameInstallFlow(new PendingGameImport(new File(root,"pending")),catalog,backend,null,store);
        assertEquals(GameInstallFlow.Action.RETRY,flow.resume(null,false).action);
        assertEquals(GameInstallFlow.Action.DONE,flow.resume(null,true).action);
        assertEquals(Arrays.asList(200L,100L),backend.installs);
    }
    @Test public void platformDependenciesKeepOriginalNgageGameAndResumeStaging() throws Exception {
        File root=root(); PendingGameImport pending=new PendingGameImport(new File(root,"pending"));
        ComponentStore store=new ComponentStore(new File(root,"cache")); PlatformStore platforms=new PlatformStore(new File(root,"platforms"));
        SystemFileCatalog catalog=catalog(root); int[] stages={0};
        Backend backend=new Backend() {
            @Override public boolean hasPlatform() { return installed.containsKey(0x20003b78L); }
            @Override public void stage(File file,String hash) { stages[0]++; }
        };
        File source=new File(root,"game.n-gage");
        Files.write(source.toPath(),"Content-Type: multipart/mixed; boundary=test\r\nContent-Description: Ngage-Install-File\r\n".getBytes(StandardCharsets.US_ASCII));
        try(GameImport game=GameImport.inspect(source)) { pending.begin(game,"N-Gage test"); }
        File installer=sis(root,"platform-updates.sis",0x20003b78,controller(0x20003b78,1,new byte[0][],
                typedController(300,1,2,new byte[0][]),typedController(300,2,2,new byte[][]{need(200,1,-1)})),true);
        try(GameImport platform=GameImport.inspect(installer)) { platforms.save(platform); }
        GameInstallFlow flow=new GameInstallFlow(pending,catalog,backend,platforms,store);
        assertEquals(GameInstallFlow.Action.COMPONENT,flow.resume(null,false).action); assertEquals(0,stages[0]); assertTrue(backend.installs.isEmpty());
        flow=new GameInstallFlow(new PendingGameImport(new File(root,"pending")),catalog,backend,platforms,store);
        try(GameImport runtime=GameImport.inspect(pkg(root,200,1))) { assertEquals(GameInstallFlow.Action.OPEN_PLATFORM,flow.addComponent(runtime).action); }
        assertEquals(Arrays.asList(200L,0x20003b78L),backend.installs); assertEquals(1,stages[0]);
        assertEquals(PendingGameImport.Step.GUEST,pending.read().step); assertTrue(source.isFile());
    }
    @Test public void conflictingPlatformDoesNotMutateGuestOrLosePendingGame() throws Exception {
        File root=root(); PendingGameImport pending=new PendingGameImport(new File(root,"pending"));
        ComponentStore store=new ComponentStore(new File(root,"cache")); PlatformStore platforms=new PlatformStore(new File(root,"platforms"));
        SystemFileCatalog catalog=catalog(root); Backend backend=new Backend();
        File source=new File(root,"game.n-gage");
        Files.write(source.toPath(),"Content-Type: multipart/mixed; boundary=test\r\nContent-Description: Ngage-Install-File\r\n".getBytes(StandardCharsets.US_ASCII));
        try(GameImport game=GameImport.inspect(source)) { pending.begin(game,"Pending game"); }
        String hash=pending.read().hash;
        GameInstallFlow flow=new GameInstallFlow(pending,catalog,backend,platforms,store);
        assertEquals(GameInstallFlow.Action.PLATFORM,flow.resume(null,false).action);
        File installer=sis(root,"conflicting-platform.sis",0x20003b78,controller(0x20003b78,1,new byte[0][],
                controller(300,1,new byte[0][]),controller(300,2,new byte[0][])),true);
        try(GameImport platform=GameImport.inspect(installer)) {
            try { flow.addPlatform(platform); fail(); }
            catch(SystemFailure failure) { assertEquals(SystemFailure.Code.COMPONENT_BUNDLE_CONFLICT,failure.code); }
        }
        assertTrue(backend.installs.isEmpty()); assertTrue(backend.installed.isEmpty());
        assertEquals(hash,pending.read().hash); assertEquals(hash,SystemFileCatalog.hash(pending.payload()));
        assertEquals(PendingGameImport.Step.PLATFORM,pending.read().step);
        assertTrue(source.isFile());
    }
    @Test public void realInstallerMetadata() throws Exception {
        String path=InstrumentationRegistry.getArguments().getString("sisFixture"); Assume.assumeTrue(path!=null);
        SisMetadata m=SisMetadata.read(new File(path));
        assertEquals(0x20003b78L,m.uid);
        System.out.println("SIS fixture: "+m.name+" "+m.version+", dependencies="+m.needs.size()+", embedded="+m.embedded.size());
    }
}
