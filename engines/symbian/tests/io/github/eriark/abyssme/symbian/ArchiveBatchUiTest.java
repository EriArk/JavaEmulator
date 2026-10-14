package io.github.eriark.abyssme.symbian;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.test.platform.app.InstrumentationRegistry;
import com.github.eka2l1.emu.Emulator;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.*;
import static org.junit.Assert.*;

/** Supplied SIS/SISX packages, real queue UI and native installation on copied phones only. */
public final class ArchiveBatchUiTest {
    private final Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
    private final Context context=instrumentation.getTargetContext();
    private final UiAutomation ui=instrumentation.getUiAutomation();
    @Test public void stagedArchiveRouteAndRetainedSelection() throws Exception {
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("batchPreview")));
        String fixture=InstrumentationRegistry.getArguments().getString("batchFixture"); Assume.assumeNotNull(fixture);
        PendingGameImport pending=new PendingGameImport(new File(context.getFilesDir(),"symbian-game-import"));
        ArchiveBatch store=new ArchiveBatch(new File(context.getFilesDir(),"symbian-archive-import"));
        Assume.assumeFalse("Preserve owner's import",pending.exists() || store.exists());
        File staged=SymbianFiles.stage(context,android.net.Uri.fromFile(new File(fixture))); String id=null;
        try {
            context.startActivity(new Intent(context,SymbianActivity.class).putExtra("symbian.importFile",staged.getName())
                    .putExtra("symbian.importName","Symbian test games.zip").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitText("Archive import"); id=store.read().id; waitText("Import 3");
            capture("symbian-archive-route-preview.png");
            click("Select all"); waitText("Import 4"); click("Select all"); waitText("Import 0");
            SystemClock.sleep(400); assertTrue(store.read().items.stream().noneMatch(i->i.selected));
            ui.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK); SystemClock.sleep(400);
            click("Archive queue"); waitText("Import 0");
            capture("symbian-archive-retained-selection.png");
            ui.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK); SystemClock.sleep(400); click("Library"); SystemClock.sleep(700);
        } finally {
            if(id!=null && store.exists() && id.equals(store.read().id))store.discard();
            Files.deleteIfExists(staged.toPath());
        }
    }
    @Test public void portraitAndLandscapeViewFitsRealWindow() throws Exception {
        Assume.assumeFalse(ru.playsoftware.j2meloader.BuildConfig.HANDHELD_MODE);
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("batchPreview")));
        String fixture=InstrumentationRegistry.getArguments().getString("batchFixture"); Assume.assumeNotNull(fixture);
        File root=Files.createTempDirectory(context.getCacheDir().toPath(),"batch-view-").toFile();
        ArchiveBatch store=new ArchiveBatch(new File(root,"batch")); store.beginZip(new File(fixture),"Symbian test games",message->{});
        ArchiveBatch.Batch batch=store.read();
        Activity activity=instrumentation.startActivitySync(new Intent(context,ru.playsoftware.j2meloader.MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        try {
            for(int orientation:new int[]{android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}) {
                instrumentation.runOnMainSync(()->activity.setRequestedOrientation(orientation)); SystemClock.sleep(900);
                instrumentation.runOnMainSync(()->activity.setContentView(new ArchiveBatchView(activity,batch,new ArchiveBatchView.Host() {
                    public void changed(){} public void row(int id){} public void start(){} public void others(){} public void close(){} public void remove(){} public void done(){}
                })));
                waitText("Import 3"); SystemClock.sleep(350);
                android.graphics.Rect window=new android.graphics.Rect(),button=new android.graphics.Rect();
                ui.getRootInActiveWindow().getBoundsInScreen(window); waitText("Import 3").getBoundsInScreen(button);
                assertTrue("Footer must fit",window.contains(button));
                assertEquals("Actual viewport orientation",orientation==android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,window.height()>window.width());
                capture(orientation==android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT ? "symbian-archive-phone-portrait.png" : "symbian-archive-phone-landscape.png");
            }
        } finally { instrumentation.runOnMainSync(activity::finish); PhoneBackupTest.remove(root); }
    }
    @Test public void realArchivePreviewAndNativeQueue() throws Exception {
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("batchUi")));
        String fixture=InstrumentationRegistry.getArguments().getString("batchFixture"); Assume.assumeNotNull(fixture);
        PendingGameImport pending=new PendingGameImport(new File(context.getFilesDir(),"symbian-game-import"));
        ArchiveBatch store=new ArchiveBatch(new File(context.getFilesDir(),"symbian-archive-import"));
        Assume.assumeFalse("Preserve owner's import",pending.exists() || store.exists());
        SystemFileCatalog catalog=new SystemFileCatalog(context);
        SystemFileCatalog.Entry modern=catalog.list().stream().filter(e->e.firmware.equals("RM-320")).findFirst().orElse(null);
        SystemFileCatalog.Entry legacy=catalog.list().stream().filter(e->e.firmware.equals("RH-29")).findFirst().orElse(null);
        Assume.assumeNotNull(modern,legacy);
        File root=Files.createTempDirectory(context.getCacheDir().toPath(),"batch-ui-").toFile();
        File active=new File(context.getFilesDir(),"symbian-systems/active.json"), bindings=new File(context.getFilesDir(),"symbian-systems/bindings.json");
        byte[] oldActive=active.isFile()?Files.readAllBytes(active.toPath()):null,oldBindings=bindings.isFile()?Files.readAllBytes(bindings.toPath()):null;
        List<SystemFileCatalog.Entry> copies=new ArrayList<>(); boolean launched=false; String batchId=null;
        try {
            SystemFileCatalog.Entry oldCopy=copy(catalog,legacy,root); copies.add(oldCopy);
            SystemFileCatalog.Entry newCopy=copy(catalog,modern,root); copies.add(newCopy);
            File source=new File(root,"supplied.archive"); Files.copy(new File(fixture).toPath(),source.toPath());
            File preview=CompressedGameArchive.accepts(source) ? CompressedGameArchive.toZip(source,message->{}) : source;
            store.beginZip(preview,"Symbian test games",message->{});
            ArchiveBatch.Batch batch=store.read(); batchId=batch.id;
            int selected=0;
            for(ArchiveBatch.Item item:batch.items) {
                assertEquals(item.name,"",item.problem);
                item.system=item.kind==GameImport.Kind.SIS_LEGACY ? oldCopy.id : "";
                if(item.selected)selected++;
            }
            assertEquals("Do not install both resolution variants",3,selected); store.save(batch);
            context.startActivity(new Intent(context,SymbianActivity.class).putExtra(SymbianLibraryIndex.SYSTEM_EXTRA,newCopy.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            launched=true;
            waitText(catalog.list().size()+" phones available"); SystemClock.sleep(300);
            click("Archive queue"); waitText("Archive import"); capture("symbian-archive-preview.png");
            click("Import 3");
            waitText("Choose system for this game");waitText("Reuse for this package type in this archive");capture("symbian-archive-phone-choice.png");
            click("Archive test phone (RM-320)");
            long end=SystemClock.uptimeMillis()+120000;
            do {
                batch=store.read();
                boolean work=false;
                for(ArchiveBatch.Item item:batch.items) if(item.selected && (item.state==ArchiveBatch.State.READY || item.state==ArchiveBatch.State.ACTIVE)) work=true;
                if(batch.started && !work) break;
                SystemClock.sleep(300);
            } while(SystemClock.uptimeMillis()<end);
            StringBuilder result=new StringBuilder(); int done=0;
            for(ArchiveBatch.Item item:batch.items) {
                result.append(item.name).append(": ").append(item.state).append(" ").append(item.detail).append('\n');
                if(item.state==ArchiveBatch.State.DONE)done++;
            }
            Files.write(new File(context.getExternalFilesDir(null),"symbian-archive-native-result.txt").toPath(),result.toString().getBytes(StandardCharsets.UTF_8));
            capture("symbian-archive-results.png");
            assertEquals(result.toString(),3,done); assertFalse(pending.exists());
            assertEquals(newCopy.id,batch.phones.get(GameImport.Kind.SIS_MODERN));
            for(ArchiveBatch.Item item:batch.items)if(item.selected && item.kind==GameImport.Kind.SIS_MODERN)assertEquals(newCopy.id,item.system);
            List<SymbianLibraryIndex.Entry> library=new SymbianLibraryIndex(context).read();
            assertTrue("Legacy game missing from shared library",library.stream().anyMatch(e->e.system.equals(oldCopy.id) && e.title.toLowerCase(Locale.ROOT).contains("lock")));
            assertTrue("SIS game missing from shared library",library.stream().anyMatch(e->e.system.equals(newCopy.id) && e.title.toLowerCase(Locale.ROOT).contains("golf")));
            assertTrue("SISX game missing from shared library",library.stream().anyMatch(e->e.system.equals(newCopy.id) && e.title.toLowerCase(Locale.ROOT).contains("frozen")));
            waitText("Archive import"); waitText("Done");
            List<Long> modernUids=new ArrayList<>();
            for(ArchiveBatch.Item item:batch.items)if(item.selected && item.kind==GameImport.Kind.SIS_MODERN)modernUids.add(SisMetadata.read(store.payload(item)).uid);
            click("Done"); SystemClock.sleep(1000); assertFalse(store.exists()); launched=false;
            Emulator.prepare(catalog.runtime(newCopy).getAbsolutePath());
            for(long uid:modernUids)assertNotNull("Installed package "+uid,Emulator.installedPackageVersion(uid));
        } finally {
            if(launched) {
                ui.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK); SystemClock.sleep(400);
                ui.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK); SystemClock.sleep(1000);
            }
            Emulator.stopSession();
            if(batchId!=null && store.exists() && batchId.equals(store.read().id)) {
                if(pending.exists() && store.read().owned(pending.read().owner)!=null)pending.discard();
                store.discard();
            }
            for(SystemFileCatalog.Entry copy:copies) {
                File[] snapshots=new File(context.getFilesDir(),"symbian-library").listFiles(f->f.getName().startsWith(copy.id+".") || f.getName().startsWith(copy.id+"-"));
                if(snapshots!=null)for(File file:snapshots)Files.deleteIfExists(file.toPath());
                PhoneBackupTest.remove(catalog.set(copy.id));
            }
            restore(active,oldActive); restore(bindings,oldBindings); PhoneBackupTest.remove(root);
        }
    }
    private SystemFileCatalog.Entry copy(SystemFileCatalog catalog,SystemFileCatalog.Entry original,File root) throws Exception {
        Emulator.stopSession(); File source=catalog.runtime(original);
        String[] settings=Emulator.portablePhoneSettings(source.getAbsolutePath());
        File identity=new File(root,UUID.randomUUID()+".rom"); Files.write(identity.toPath(),UUID.randomUUID().toString().getBytes(StandardCharsets.US_ASCII));
        return catalog.add(Collections.singletonList(identity),(runtime,r,p)-> {
            try(java.util.stream.Stream<Path> paths=Files.walk(source.toPath())) {
                for(Path path:(Iterable<Path>)paths::iterator) {
                    Path target=runtime.toPath().resolve(source.toPath().relativize(path));
                    if(Files.isDirectory(path))Files.createDirectories(target); else Files.copy(path,target);
                }
            }
            Files.write(new File(runtime,"config.yml").toPath(),settings[0].getBytes(StandardCharsets.UTF_8));
            Files.write(new File(runtime,"data/devices.yml").toPath(),settings[1].getBytes(StandardCharsets.UTF_8));
            return new String[]{original.firmware,"Archive test phone",original.os};
        });
    }
    private static void restore(File file,byte[] bytes) throws IOException {
        if(bytes==null)new android.util.AtomicFile(file).delete();else Files.write(file.toPath(),bytes);
    }
    private AccessibilityNodeInfo waitText(String text) {
        long end=SystemClock.uptimeMillis()+20000;
        do {
            AccessibilityNodeInfo root=ui.getRootInActiveWindow();
            if(root!=null)for(AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText(text))
                if(node.getText()!=null && text.equals(node.getText().toString()))return node;
            SystemClock.sleep(100);
        }while(SystemClock.uptimeMillis()<end);
        try { capture("symbian-archive-ui-failure.png"); } catch(IOException ignored) { }
        throw new AssertionError("Missing text: "+text);
    }
    private void click(String text) {
        AccessibilityNodeInfo node=waitText(text);
        while(!node.isClickable() && node.getParent()!=null)node=node.getParent();
        assertTrue("Cannot click "+text,node.performAction(AccessibilityNodeInfo.ACTION_CLICK));
    }
    private void capture(String name) throws IOException {
        SystemClock.sleep(500); Bitmap bitmap=ui.takeScreenshot(); assertNotNull(bitmap);
        try(OutputStream out=new FileOutputStream(new File(context.getExternalFilesDir(null),name))) { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out)); }
        finally {bitmap.recycle();}
    }
}
