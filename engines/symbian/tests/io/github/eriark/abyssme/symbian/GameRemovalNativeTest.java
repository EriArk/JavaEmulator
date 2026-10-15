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

/** Explicitly opted-in destructive checks, exclusively on copied test phones. */
public class GameRemovalNativeTest {
    private final Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
    private final Context context=instrumentation.getTargetContext();
    private final UiAutomation ui=instrumentation.getUiAutomation();
    @Test public void realSisAndSisxUninstallReinstallAndPreserveOtherPhoneData() throws Exception {
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("removalNative")));
        File fixture=new File(context.getExternalFilesDir(null),"symbian-input/removal-games.zip");
        assertTrue("Supply the local SIS/SISX selection",fixture.isFile());
        SystemFileCatalog catalog=new SystemFileCatalog(context);
        recoverTestPhones(catalog);
        SystemFileCatalog.Entry modern=catalog.list().stream().filter(p->p.firmware.equals("RM-320")).findFirst().orElse(null);
        SystemFileCatalog.Entry legacy=catalog.list().stream().filter(p->p.firmware.equals("RH-29")).findFirst().orElse(null);
        assertNotNull(modern); assertNotNull(legacy);
        File root=Files.createTempDirectory(context.getCacheDir().toPath(),"removal-native-").toFile();
        File active=new File(context.getFilesDir(),"symbian-systems/active.json");
        byte[] oldActive=active.isFile()?Files.readAllBytes(active.toPath()):null;
        Files.write(recoveryFile().toPath(),new org.json.JSONObject().put("active",oldActive==null?"":new String(oldActive,StandardCharsets.UTF_8)).toString().getBytes(StandardCharsets.UTF_8));
        List<SystemFileCatalog.Entry> copies=new ArrayList<>();
        boolean launched=false;
        try {
            SystemFileCatalog.Entry oldCopy=copy(catalog,legacy,root),newCopy=copy(catalog,modern,root);
            copies.add(oldCopy); copies.add(newCopy);
            ArchiveBatch archive=new ArchiveBatch(new File(root,"archive")); archive.beginZip(fixture,"Removal games",s->{});
            List<ArchiveBatch.Item> selected=new ArrayList<>();
            for(ArchiveBatch.Item item:archive.read().items) if(item.selected) selected.add(item);
            assertEquals(3,selected.size());
            Map<String,String> original=hashData(catalog.runtime(modern));
            Map<String,String> originalOld=hashData(catalog.runtime(legacy));
            for(ArchiveBatch.Item item:selected) {
                SystemFileCatalog.Entry phone=item.kind==GameImport.Kind.SIS_LEGACY?oldCopy:newCopy;
                Emulator.stopSession(); Emulator.prepare(catalog.runtime(phone).getAbsolutePath());
                assertEquals(item.name,0,Emulator.installApp(archive.payload(item).getAbsolutePath()));
            }
            for(ArchiveBatch.Item item:selected) {
                SystemFileCatalog.Entry phone=item.kind==GameImport.Kind.SIS_LEGACY?oldCopy:newCopy;
                String runtime=catalog.runtime(phone).getAbsolutePath();
                System.out.println("Uninstall fixture: "+item.name);
                Emulator.stopSession(); Emulator.prepare(runtime);
                String[] before=Emulator.getLibraryApps();
                String needle=item.name.toLowerCase(Locale.ROOT).contains("frozen")?"frozen":item.kind==GameImport.Kind.SIS_LEGACY?"lock":"golf";
                long uid=0;
                for(int i=0;i<before.length;i+=3) if(before[i+1].toLowerCase(Locale.ROOT).contains(needle)) uid=Long.parseLong(before[i]);
                assertTrue(item.name,uid>0);
                GameRemoval.Plan plan=new GameRemoval.Plan(uid,Emulator.inspectRemoval(uid,runtime));
                assertTrue(plan.affected.contains(uid));
                try { Emulator.inspectRemoval(uid,catalog.runtime(modern).getAbsolutePath()); fail("Wrong phone accepted"); }
                catch(IOException expected) { }
                SymbianLibraryIndex index=new SymbianLibraryIndex(context); index.publish(phone,before,true);
                File sentinel=new File(catalog.runtime(phone),"data/drives/"+phone.firmware.toLowerCase(Locale.ROOT)+"/c/abyssme-unrelated-test/save.dat");
                sentinel.getParentFile().mkdirs(); Files.write(sentinel.toPath(),new byte[]{1,4,9,16});
                Map<String,String> data=hashData(catalog.runtime(phone));
                Emulator.stopSession();
                context.startActivity(new Intent(context,SymbianActivity.class).putExtra(SymbianLibraryIndex.SYSTEM_EXTRA,phone.id)
                        .putExtra(SymbianLibraryIndex.UID_EXTRA,uid).putExtra(SymbianLibraryIndex.REMOVE_EXTRA,true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                launched=true; waitText("Uninstall from phone?"); capture("symbian-uninstall-"+needle+".png");
                assertTrue("Cancel is not the default controller action",waitText("Cancel").isFocused());
                waitText("Back up this phone");
                ui.injectInputEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN,android.view.KeyEvent.KEYCODE_BUTTON_A),true);
                ui.injectInputEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP,android.view.KeyEvent.KEYCODE_BUTTON_A),true);
                SystemClock.sleep(800); launched=false;
                assertFilesUnchanged(data,hashData(catalog.runtime(phone)),null,phone);
                context.startActivity(new Intent(context,SymbianActivity.class).putExtra(SymbianLibraryIndex.SYSTEM_EXTRA,phone.id)
                        .putExtra(SymbianLibraryIndex.UID_EXTRA,uid).putExtra(SymbianLibraryIndex.REMOVE_EXTRA,true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                launched=true; waitText("Uninstall from phone?"); click("Uninstall"); waitText("Uninstalled");
                capture("symbian-uninstalled-"+needle+".png"); click("Library"); SystemClock.sleep(1000); launched=false;
                Emulator.prepare(runtime);
                assertTrue("Registry/files remain",Emulator.removalComplete(plan.rows,runtime));
                assertFalse(GameRemoval.apps(Emulator.getLibraryApps()).contains(uid));
                assertFalse(index.read().stream().anyMatch(e->e.system.equals(phone.id)&&plan.affected.contains(e.uid)));
                Set<Long> unrelated=GameRemoval.apps(before); unrelated.removeAll(plan.affected);
                assertTrue(GameRemoval.apps(Emulator.getLibraryApps()).containsAll(unrelated));
                assertFilesUnchanged(data,hashData(catalog.runtime(phone)),plan,phone);
                assertEquals(0,Emulator.installApp(archive.payload(item).getAbsolutePath()));
                Emulator.prepare(runtime);
                assertTrue("Reinstall did not restore game",GameRemoval.apps(Emulator.getLibraryApps()).contains(uid));
                if(item.kind==GameImport.Kind.SIS_LEGACY) continue;
                File dependent=SisComponentsTest.sis(root,"dependent.sis",0xE000AB33,
                        SisComponentsTest.controller(0xE000AB33,1,new byte[][]{SisComponentsTest.need((int)plan.owner,0,-1)}),true);
                assertEquals(0,Emulator.installApp(dependent.getAbsolutePath())); Emulator.prepare(runtime);
                try { Emulator.inspectRemoval(uid,runtime); fail("A required package was removable"); }
                catch(IOException expected) { assertTrue(expected.getMessage(),expected.getMessage().contains("needs")); }
            }
            assertEquals("Original N95 changed",original,hashData(catalog.runtime(modern)));
            assertEquals("Original N-Gage changed",originalOld,hashData(catalog.runtime(legacy)));
        } finally {
            if(launched) { ui.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK); SystemClock.sleep(500);
                ui.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK); SystemClock.sleep(800); }
            Emulator.stopSession();
            for(SystemFileCatalog.Entry copy:copies) {
                File[] index=new File(context.getFilesDir(),"symbian-library").listFiles(f->f.getName().startsWith(copy.id+".")||f.getName().startsWith(copy.id+"-"));
                if(index!=null) for(File file:index) Files.deleteIfExists(file.toPath());
                PhoneBackupTest.remove(catalog.set(copy.id));
            }
            if(oldActive==null)new android.util.AtomicFile(active).delete();else Files.write(active.toPath(),oldActive);
            Files.deleteIfExists(recoveryFile().toPath());
            PhoneBackupTest.remove(root);
        }
    }
    private File recoveryFile() { return new File(context.getCacheDir(),"removal-native-active.json"); }
    private void recoverTestPhones(SystemFileCatalog catalog) throws Exception {
        Emulator.stopSession();
        List<SystemFileCatalog.Entry> stale=new ArrayList<>();
        for(SystemFileCatalog.Entry entry:catalog.list()) if(entry.model.equals("Removal test phone")) stale.add(entry);
        File active=new File(context.getFilesDir(),"symbian-systems/active.json");
        if(recoveryFile().isFile()) {
            String saved=new org.json.JSONObject(new String(Files.readAllBytes(recoveryFile().toPath()),StandardCharsets.UTF_8)).getString("active");
            if(saved.isEmpty())new android.util.AtomicFile(active).delete();else Files.write(active.toPath(),saved.getBytes(StandardCharsets.UTF_8));
        } else if(active.isFile()) {
            String id=new org.json.JSONObject(new String(Files.readAllBytes(active.toPath()),StandardCharsets.UTF_8)).getString("id");
            for(SystemFileCatalog.Entry entry:stale) if(entry.id.equals(id)) {
                String restore=InstrumentationRegistry.getArguments().getString("restorePhone");
                assertNotNull("Provide the original phone after a pre-journal test crash",restore);
                SystemFileCatalog.Entry original=catalog.read(restore);
                assertNotEquals("Removal test phone",original.model); catalog.activate(original);
            }
        }
        for(SystemFileCatalog.Entry entry:stale) {
            File[] index=new File(context.getFilesDir(),"symbian-library").listFiles(f->f.getName().startsWith(entry.id+".")||f.getName().startsWith(entry.id+"-"));
            if(index!=null)for(File file:index)Files.deleteIfExists(file.toPath());
            PhoneBackupTest.remove(catalog.set(entry.id));
        }
        File[] roots=context.getCacheDir().listFiles(f->f.isDirectory()&&f.getName().startsWith("removal-native-"));
        if(roots!=null)for(File root:roots)PhoneBackupTest.remove(root);
        Files.deleteIfExists(recoveryFile().toPath());
    }
    private void assertFilesUnchanged(Map<String,String> before,Map<String,String> after,GameRemoval.Plan removed,SystemFileCatalog.Entry phone) {
        String root="data/drives/"+phone.firmware.toLowerCase(Locale.ROOT)+"/";
        for(Map.Entry<String,String> file:before.entrySet()) {
            String path=file.getKey().replace('\\','/').toLowerCase(Locale.ROOT);
            // The upstream SMS service rewrites this settings file during engine teardown.
            if(path.endsWith("/system/data/sms_settings.dat")) continue;
            boolean owned=false;
            if(removed!=null) {
                owned=path.startsWith(root+"c/sys/install/sisregistry/"+String.format(Locale.ROOT,"%08x",removed.owner)+"/");
                for(int i=5+removed.affected.size()*2;i<removed.rows.length;i++) {
                    String guest=removed.rows[i].replace('\\','/').toLowerCase(Locale.ROOT);
                    String target=root+guest.charAt(0)+guest.substring(2);
                    owned|=path.equals(target)||(target.endsWith("/")&&path.startsWith(target));
                }
            }
            if(!owned) assertEquals("Unrelated file changed: "+file.getKey(),file.getValue(),after.get(file.getKey()));
        }
    }
    private Map<String,String> hashData(File runtime) throws Exception {
        Map<String,String> hashes=new TreeMap<>(); File drives=new File(runtime,"data/drives");
        try(java.util.stream.Stream<Path> stream=Files.walk(drives.toPath())) {
            for(Path p:(Iterable<Path>)stream::iterator) if(Files.isRegularFile(p) && !drives.toPath().relativize(p).startsWith("z"))
                hashes.put(runtime.toPath().relativize(p).toString(),SystemFileCatalog.hash(p.toFile()));
        } return hashes;
    }
    private SystemFileCatalog.Entry copy(SystemFileCatalog catalog,SystemFileCatalog.Entry original,File root) throws Exception {
        Emulator.stopSession(); File source=catalog.runtime(original); String[] settings=Emulator.portablePhoneSettings(source.getAbsolutePath());
        File identity=new File(root,UUID.randomUUID()+".rom"); Files.write(identity.toPath(),UUID.randomUUID().toString().getBytes(StandardCharsets.US_ASCII));
        return catalog.add(Collections.singletonList(identity),(runtime,r,p)-> {
            try(java.util.stream.Stream<Path> paths=Files.walk(source.toPath())) {
                for(Path path:(Iterable<Path>)paths::iterator) {
                    Path target=runtime.toPath().resolve(source.toPath().relativize(path));
                    if(Files.isDirectory(path))Files.createDirectories(target);else Files.copy(path,target);
                }
            }
            Files.write(new File(runtime,"config.yml").toPath(),settings[0].getBytes(StandardCharsets.UTF_8));
            Files.write(new File(runtime,"data/devices.yml").toPath(),settings[1].getBytes(StandardCharsets.UTF_8));
            return new String[]{original.firmware,"Removal test phone",original.os};
        });
    }
    private AccessibilityNodeInfo waitText(String text) {
        long end=SystemClock.uptimeMillis()+30000;
        do { AccessibilityNodeInfo root=ui.getRootInActiveWindow();
            if(root!=null) for(AccessibilityNodeInfo n:root.findAccessibilityNodeInfosByText(text)) if(n.getText()!=null&&text.equals(n.getText().toString()))return n;
            SystemClock.sleep(150);
        } while(SystemClock.uptimeMillis()<end);
        try {capture("symbian-uninstall-failure.png");}catch(Exception ignored){}
        throw new AssertionError("Missing text: "+text);
    }
    private void click(String text) { AccessibilityNodeInfo n=waitText(text); while(!n.isClickable()&&n.getParent()!=null)n=n.getParent(); assertTrue(n.performAction(AccessibilityNodeInfo.ACTION_CLICK)); }
    private void capture(String name) throws IOException {
        SystemClock.sleep(400); Bitmap image=ui.takeScreenshot(); assertNotNull(image);
        try(OutputStream out=new FileOutputStream(new File(context.getExternalFilesDir(null),name))) { image.compress(Bitmap.CompressFormat.PNG,100,out); }
        finally {image.recycle();}
    }
}
