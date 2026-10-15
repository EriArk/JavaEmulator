package io.github.eriark.abyssme.symbian;

import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import org.junit.*;
import static org.junit.Assert.*;

public class GameRemovalTest {
    private File root;
    private static final String PHONE=String.format(Locale.ROOT,"%064x",1);
    private Fake backend;
    private GameRemoval removal;
    private static class Fake implements GameRemoval.Backend {
        String[] plan={"100","0","Two games","1.0.0","2","10","First","11","Second","c:\\game.app"};
        String[] apps={"10","First","Symbian","11","Second","Symbian","20","Other","Symbian"};
        int removals,publishes; boolean fail,partial,complete=true,loseOther,publishFail;
        public String[] inspect(long uid) { return plan; }
        public String[] apps() { return apps; }
        public void remove(long uid,long owner,int index) throws IOException {
            assertEquals(100,owner); assertEquals(0,index); removals++;
            if(fail)throw new IOException("Failed removal");
            apps=partial?new String[]{"11","Second","Symbian","20","Other","Symbian"}
                    :loseOther?new String[0]:new String[]{"20","Other","Symbian"};
        }
        public void reload() { }
        public boolean complete(GameRemoval.Plan p) { return complete; }
        public void publish(String[] values,Set<Long> affected) throws IOException {
            if(publishFail)throw new IOException("Disk full");
            assertEquals(new HashSet<>(Arrays.asList(10L,11L)),affected); publishes++;
        }
    }
    @Before public void setup() throws Exception {
        root=Files.createTempDirectory(InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir().toPath(),"removal-").toFile();
        backend=new Fake(); removal=new GameRemoval(root,PHONE,backend);
    }
    @After public void cleanup() throws Exception { PhoneBackupTest.remove(root); }
    @Test public void previewIsReadOnlyAndSuccessRemovesWholePackageOnly() throws Exception {
        GameRemoval.Plan plan=removal.inspect(10); assertEquals(0,backend.removals); assertFalse(removal.pending());
        assertTrue(plan.description("N95 / RM-320").contains("Second"));
        removal.remove(plan); assertEquals(1,backend.publishes); assertFalse(removal.pending());
        assertArrayEquals(new String[]{"20","Other","Symbian"},backend.apps);
    }
    @Test public void changedPackageCannotUseOldConfirmation() throws Exception {
        GameRemoval.Plan plan=removal.inspect(10); backend.plan[3]="2.0.0";
        try { removal.remove(plan); fail(); } catch(IOException expected) { }
        assertEquals(0,backend.removals); assertFalse(removal.pending());
    }
    @Test public void nativeFailureRetainsRecordAndAllowsExplicitSamePlanRetry() throws Exception {
        backend.fail=true;
        try { removal.remove(removal.inspect(10)); fail(); } catch(IOException expected) { }
        assertTrue(removal.pending()); assertFalse(removal.recover()); assertEquals(0,backend.publishes);
        backend.fail=false; removal.remove(removal.inspect(10)); assertFalse(removal.pending());
    }
    @Test public void partialRemovalDoesNotEraseLibraryReference() throws Exception {
        backend.partial=true;
        try { removal.remove(removal.inspect(10)); fail(); } catch(IOException expected) { }
        assertTrue(removal.pending()); assertEquals(0,backend.publishes);
    }
    @Test public void leftoverFilesOrRegistryBlockRecoveryEvenWhenAppsDisappear() throws Exception {
        backend.complete=false;
        try { removal.remove(removal.inspect(10)); fail(); } catch(IOException expected) { }
        assertFalse(removal.recover()); assertEquals(0,backend.publishes);
        backend.complete=true;
        assertTrue(new GameRemoval(root,PHONE,backend).recover()); assertFalse(removal.pending());
    }
    @Test public void unrelatedMissingAppBlocksPublication() throws Exception {
        backend.loseOther=true;
        try { removal.remove(removal.inspect(10)); fail(); } catch(IOException expected) { }
        assertTrue(removal.pending()); assertEquals(0,backend.publishes);
    }
    @Test public void publicationFailureIsRecoverableWithoutRepeatingNativeRemoval() throws Exception {
        backend.publishFail=true;
        try { removal.remove(removal.inspect(10)); fail(); } catch(IOException expected) { }
        backend.publishFail=false;
        assertTrue(new GameRemoval(root,PHONE,backend).recover()); assertEquals(1,backend.removals);
    }
    @Test public void pendingRemovalBlocksOrdinarySnapshotReplacementButNotOtherPhones() throws Exception {
        SymbianLibraryIndex index=new SymbianLibraryIndex(root);
        SystemFileCatalog.Entry phone=new SystemFileCatalog.Entry(PHONE,"Phone","RM-320","epoc93fp1");
        index.publish(phone,backend.apps,false);
        backend.fail=true;
        try { removal.remove(removal.inspect(10)); fail(); } catch(IOException expected) { }
        try { index.publish(phone,new String[0],false); fail(); } catch(IOException expected) { }
        assertEquals(3,index.read().size());
        index.publish(new SystemFileCatalog.Entry(String.format(Locale.ROOT,"%064x",2),"Other phone","RH-29","epoc6"),
                new String[]{"10","Other installation","N-Gage"},false);
        assertEquals(4,index.read().size());
    }
    @Test public void invalidIdentityAndPlanAreRejectedBeforeAnyMutation() throws Exception {
        try { new GameRemoval(root,"../phone",backend); fail(); } catch(IOException expected) { }
        for(String[] rows:new String[][]{null,new String[0],{"100","0","Pkg","1","1","0","Bad","c:\\bad"}}) {
            backend.plan=rows; try { removal.inspect(10); fail(); } catch(IOException expected) { }
        }
        assertEquals(0,backend.removals); assertFalse(removal.pending());
    }
    @Test public void unreadablyLargeJournalIsRejectedBeforeNativeRemoval() throws Exception {
        backend.plan=Arrays.copyOf(backend.plan,20000);
        char[] text=new char[2048]; Arrays.fill(text,'a');
        Arrays.fill(backend.plan,10,backend.plan.length,new String(text));
        try { removal.remove(removal.inspect(10)); fail(); } catch(IOException expected) { }
        assertEquals(0,backend.removals); assertFalse(removal.pending());
    }
    @Test public void successfulPublicationPrunesOnlyRemovedPhoneArtwork() throws Exception {
        String other=String.format(Locale.ROOT,"%064x",2);
        SymbianLibraryIndex index=new SymbianLibraryIndex(root);
        SystemFileCatalog.Entry phone=new SystemFileCatalog.Entry(PHONE,"Phone","RM-320","epoc93fp1");
        index.publish(phone,backend.apps,false);
        index.publish(new SystemFileCatalog.Entry(other,"Other","RM-320","epoc93fp1"),backend.apps,false);
        for(String name:new String[]{PHONE+"-10.png",PHONE+"-10-cover.png",PHONE+"-20.png",other+"-10.png"})
            Files.write(new File(root,name).toPath(),new byte[]{1});
        index.publishRemoval(phone,new String[]{"20","Other","Symbian"},new HashSet<>(Arrays.asList(10L,11L)));
        assertFalse(new File(root,PHONE+"-10.png").exists()); assertFalse(new File(root,PHONE+"-10-cover.png").exists());
        assertTrue(new File(root,PHONE+"-20.png").isFile()); assertTrue(new File(root,other+"-10.png").isFile());
        assertEquals(4,index.read().size());
    }
}
