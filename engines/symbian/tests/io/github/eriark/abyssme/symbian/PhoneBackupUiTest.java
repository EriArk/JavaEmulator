package io.github.eriark.abyssme.symbian;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.test.platform.app.InstrumentationRegistry;
import com.github.eka2l1.emu.Emulator;
import org.junit.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;

public final class PhoneBackupUiTest {
    private final Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
    private final Context context=instrumentation.getTargetContext();
    private UiAutomation ui;
    @Test public void restorePreviewCancelCopyAndNativeGameLaunchPreserveOriginal() throws Exception {
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("backupUi")));
        ui=instrumentation.getUiAutomation();
        SystemFileCatalog catalog=new SystemFileCatalog(context);
        SymbianLibraryIndex.Entry original=new SymbianLibraryIndex(context).read().stream().filter(e -> e.title.equals("Snakes")).findFirst().orElse(null);
        assertNotNull("Provision the private Snakes fixture first",original);
        SystemFileCatalog.Entry phone=catalog.read(original.system);
        File active=new File(context.getFilesDir(),"symbian-systems/active.json"); byte[] priorActive=Files.exists(active.toPath())?Files.readAllBytes(active.toPath()):null;
        Set<String> before=new HashSet<>(); for(SystemFileCatalog.Entry e:catalog.list())before.add(e.id);
        File archive=File.createTempFile("backup-ui-fixture-",".zip",context.getCacheDir());
        SystemFileCatalog.Entry restored=null;
        Map<String,String> sourceBefore=hashes(new File(catalog.runtime(phone),"data/drives"));
        try {
            Emulator.stopSession();
            new PhoneBackup(context.getFilesDir()).exportPhone(phone,archive,Emulator.portablePhoneSettings(catalog.runtime(phone).getAbsolutePath()),text -> {});
            context.startActivity(new Intent(context,SymbianActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .putExtra("symbian.backups",true));
            waitText("Restore phone backup",30000); capture("symbian-backups-menu.png"); click("Back up phone");
            waitText("Choose phone to back up",5000); click(phone.label());
            waitText("Choose where to save",5000); capture("symbian-backup-export.png");
            if("true".equals(InstrumentationRegistry.getArguments().getString("backupSaf"))) {
                click("Choose where to save"); waitText("Save",10000); capture("symbian-backup-save-picker.png"); click("Save");
                waitText("Backup saved",90000); capture("symbian-backup-saved.png"); click("Close");
            } else click("Cancel");
            click("Library"); SystemClock.sleep(1200);
            openArchive(archive); waitText("Restore as new phone",30000); capture("symbian-backup-preview.png"); click("Cancel");
            assertEquals(before.size(),catalog.list().size()); click("Library"); SystemClock.sleep(1200);
            openArchive(archive); waitText("Restore as new phone",30000); click("Restore as new phone");
            waitText("Phone restored",90000); capture("symbian-backup-restored.png");
            for(SystemFileCatalog.Entry entry:catalog.list())if(!before.contains(entry.id)) {assertNull(restored);restored=entry;}
            assertNotNull(restored); equalFiles(sourceBefore,hashes(new File(catalog.runtime(phone),"data/drives")),false);
            equalFiles(sourceBefore,hashes(new File(catalog.runtime(restored),"data/drives")),true);
            SymbianLibraryIndex.Entry game=null;
            for(SymbianLibraryIndex.Entry entry:new SymbianLibraryIndex(context).read())if(entry.system.equals(restored.id)&&entry.title.equals("Snakes"))game=entry;
            assertNotNull("Restored games must be in the shared library",game);
            click("Library"); SystemClock.sleep(1200);
            context.startActivity(new Intent(context,SymbianActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .putExtra(SymbianLibraryIndex.SYSTEM_EXTRA,game.system).putExtra(SymbianLibraryIndex.UID_EXTRA,game.uid));
            waitText("Game menu",20000); SystemClock.sleep(9000); capture("symbian-backup-game.png");
            click("Game menu"); waitText("Resume",5000); click("Exit game"); click("Leave without saving"); SystemClock.sleep(1800);
            equalFiles(sourceBefore,hashes(new File(catalog.runtime(phone),"data/drives")),false);
        } finally {
            // Only test-created restored identities are removed, after the host activity is closed.
            context.startActivity(new Intent(context,ru.playsoftware.j2meloader.MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK)); SystemClock.sleep(2000);
            Emulator.stopSession();
            if(priorActive==null) Files.deleteIfExists(active.toPath()); else Files.write(active.toPath(),priorActive);
            for(SystemFileCatalog.Entry entry:catalog.list())if(!before.contains(entry.id)&&entry.restored) {
                PhoneBackupTest.remove(catalog.set(entry.id));
                File[] index=new File(context.getFilesDir(),"symbian-library").listFiles(f -> f.getName().startsWith(entry.id));
                if(index!=null)for(File file:index)Files.deleteIfExists(file.toPath());
            }
            Files.deleteIfExists(archive.toPath());
        }
    }
    private void equalFiles(Map<String,String> expected,Map<String,String> actual,boolean bootedCopy) {
        Set<String> names=new TreeSet<>(expected.keySet());names.addAll(actual.keySet());
        for(String name:names) {
            // EKA2L1 recreates its SMS service settings on preparing a guest. All archive
            // payload hashes were checked before this native validation boot.
            if(bootedCopy && name.equals("rh-29/c/System/Data/sms_settings.dat"))continue;
            assertEquals("Phone file changed: "+name,expected.get(name),actual.get(name));
        }
    }
    private Map<String,String> hashes(File folder) throws IOException {
        Map<String,String> hashes=new TreeMap<>();
        try(java.util.stream.Stream<Path> stream=Files.walk(folder.toPath())) {
            for(Path path:(Iterable<Path>)stream.filter(Files::isRegularFile)::iterator)
                hashes.put(folder.toPath().relativize(path).toString(),SystemFileCatalog.hash(path.toFile()));
        }
        return hashes;
    }
    private void openArchive(File archive) throws IOException {
        File folder=new File(SymbianFiles.root(context),"imports"); Files.createDirectories(folder.toPath());
        File staged=File.createTempFile("input-","-phone.zip",folder); Files.copy(archive.toPath(),staged.toPath(),StandardCopyOption.REPLACE_EXISTING);
        context.startActivity(new Intent(context,SymbianActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra("symbian.importFile",staged.getName()).putExtra("symbian.importName","phone.zip"));
    }
    private AccessibilityNodeInfo find(AccessibilityNodeInfo node,String text) {
        if(node==null)return null;
        if(text.equalsIgnoreCase(node.getText()==null?"":node.getText().toString()) || text.contentEquals(node.getContentDescription()==null?"":node.getContentDescription()))return node;
        for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo found=find(node.getChild(i),text);if(found!=null)return found;}
        return null;
    }
    private AccessibilityNodeInfo waitText(String text,long timeout) {
        long end=SystemClock.uptimeMillis()+timeout;
        do {AccessibilityNodeInfo node=find(ui.getRootInActiveWindow(),text);if(node!=null)return node;SystemClock.sleep(100);}while(SystemClock.uptimeMillis()<end);
        capture("symbian-backup-failure.png"); throw new AssertionError("Missing: "+text);
    }
    private void click(String text) {
        AccessibilityNodeInfo node=waitText(text,10000); node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.getId());
        SystemClock.sleep(150);node.refresh();assertTrue("Click "+text,node.performAction(AccessibilityNodeInfo.ACTION_CLICK));SystemClock.sleep(200);
    }
    private void capture(String name) {
        SystemClock.sleep(450);
        Bitmap bitmap=ui.takeScreenshot();assertNotNull(bitmap);
        try(FileOutputStream out=new FileOutputStream(new File(context.getExternalFilesDir(null),name))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}
        catch(IOException e){throw new AssertionError(e);}finally{bitmap.recycle();}
    }
}
