package io.github.eriark.abyssme.symbian;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.net.Uri;
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
import static io.github.eriark.abyssme.symbian.SisComponentsTest.*;

/** Real activity, Android document picker and native installation on a disposable COPY. */
public final class SisComponentsUiTest {
    private final Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
    private final Context context=instrumentation.getTargetContext();
    private final UiAutomation ui=instrumentation.getUiAutomation();
    private static final int GAME=0xE000AB01, COMPONENT=0xE000AB02;
    @Test public void missingComponentPickerResumeAndNativeRegistration() throws Exception {
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("componentUi")));
        PendingGameImport pending=new PendingGameImport(new File(context.getFilesDir(),"symbian-game-import"));
        Assume.assumeFalse("Preserve owner's pending import",pending.exists());
        SystemFileCatalog catalog=new SystemFileCatalog(context);
        SystemFileCatalog.Entry sourcePhone=catalog.list().stream().filter(e->e.firmware.equals("RM-320")).findFirst().orElse(null);
        Assume.assumeNotNull(sourcePhone);
        File source=catalog.runtime(sourcePhone);
        File root=Files.createTempDirectory(context.getCacheDir().toPath(),"components-ui-").toFile();
        File active=new File(context.getFilesDir(),"symbian-systems/active.json");
        File bindings=new File(context.getFilesDir(),"symbian-systems/bindings.json");
        byte[] oldActive=active.isFile()?Files.readAllBytes(active.toPath()):null;
        byte[] oldBindings=bindings.isFile()?Files.readAllBytes(bindings.toPath()):null;
        SystemFileCatalog.Entry copy=null; boolean launched=false; String savedHash=null;
        Uri document=null;
        try {
            Emulator.stopSession(); String[] settings=Emulator.portablePhoneSettings(source.getAbsolutePath());
            File identity=new File(root,"test.rom"); Files.write(identity.toPath(),UUID.randomUUID().toString().getBytes(StandardCharsets.US_ASCII));
            copy=catalog.add(Collections.singletonList(identity),(runtime,r,p) -> {
                try(java.util.stream.Stream<Path> paths=Files.walk(source.toPath())) {
                    for(Path path:(Iterable<Path>)paths::iterator) {
                        Path target=runtime.toPath().resolve(source.toPath().relativize(path));
                        if(Files.isDirectory(path)) Files.createDirectories(target); else Files.copy(path,target);
                    }
                }
                Files.write(new File(runtime,"config.yml").toPath(),settings[0].getBytes(StandardCharsets.UTF_8));
                Files.write(new File(runtime,"data/devices.yml").toPath(),settings[1].getBytes(StandardCharsets.UTF_8));
                return new String[]{sourcePhone.firmware,"Component test phone",sourcePhone.os};
            });
            File game=sis(root,"game.sis",GAME,controller(GAME,1,new byte[][]{need(COMPONENT,1,-1)}),true);
            File component=sis(root,"runtime.sis",COMPONENT,controller(COMPONENT,1,new byte[0][]),true);
            String componentHash=SystemFileCatalog.hash(component);
            Assume.assumeFalse(new File(context.getFilesDir(),"symbian-components/"+componentHash+".sis").exists());
            savedHash=componentHash;
            try(GameImport input=GameImport.inspect(game)) { pending.begin(input,"Component test game"); }
            pending.update(pending.read(),copy.id,PendingGameImport.Step.READY);
            context.startActivity(new Intent(context,SymbianActivity.class)
                    .putExtra(SymbianLibraryIndex.SYSTEM_EXTRA,copy.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            launched=true;
            waitText(catalog.list().size()+" phones available"); click("Pending import"); click("Continue import"); waitText("Component needed"); capture("symbian-component-needed.png");
            assertEquals(PendingGameImport.Step.COMPONENT,pending.read().step);
            click("Later");
            String documentName="AbyssME-test-runtime-"+System.currentTimeMillis()+".sis";
            ContentValues values=new ContentValues(); values.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME,documentName);
            values.put(android.provider.MediaStore.MediaColumns.MIME_TYPE,"application/vnd.symbian.install");
            values.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,android.os.Environment.DIRECTORY_DOWNLOADS);
            values.put(android.provider.MediaStore.MediaColumns.IS_PENDING,1);
            document=context.getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,values); assertNotNull(document);
            try(OutputStream out=context.getContentResolver().openOutputStream(document); InputStream in=new FileInputStream(component)) {
                byte[] buffer=new byte[65536]; for(int n;(n=in.read(buffer))!=-1;) out.write(buffer,0,n);
            }
            values.clear(); values.put(android.provider.MediaStore.MediaColumns.IS_PENDING,0); context.getContentResolver().update(document,values,null,null);
            click("Pending import"); click("Continue import"); waitText("Component needed"); click("Add installer");
            SystemClock.sleep(700);
            AccessibilityNodeInfo roots=description(ui.getRootInActiveWindow(),"Show roots");
            if(roots!=null) { assertTrue(roots.performAction(AccessibilityNodeInfo.ACTION_CLICK)); click("Downloads"); }
            click(documentName); waitText("Installation complete"); assertFalse(pending.exists());
            capture("symbian-component-installed.png");
            click("Library"); SystemClock.sleep(1000);
            context.startActivity(new Intent(context,SymbianActivity.class)
                    .putExtra(SymbianLibraryIndex.SYSTEM_EXTRA,copy.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitText(catalog.list().size()+" phones available"); click("Saved components"); waitText("Add installer"); capture("symbian-saved-components.png");
            click("Close"); click("Library"); SystemClock.sleep(1000); launched=false;
            Emulator.prepare(catalog.runtime(copy).getAbsolutePath());
            assertArrayEquals(new int[]{1,0,0},Emulator.installedPackageVersion(Integer.toUnsignedLong(COMPONENT)));
            assertArrayEquals(new int[]{1,0,0},Emulator.installedPackageVersion(Integer.toUnsignedLong(GAME)));
        } finally {
            if(launched) { ui.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK); SystemClock.sleep(500); ui.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK); SystemClock.sleep(1000); }
            Emulator.stopSession();
            if(pending.exists() && "Component test game".equals(pending.read().name)) pending.discard();
            if(savedHash!=null) new ComponentStore(new File(context.getFilesDir(),"symbian-components")).remove(savedHash);
            if(document!=null) context.getContentResolver().delete(document,null,null);
            if(copy!=null) {
                String id=copy.id;
                File[] snapshots=new File(context.getFilesDir(),"symbian-library").listFiles(f->f.getName().startsWith(id+".") || f.getName().startsWith(id+"-"));
                if(snapshots!=null) for(File file:snapshots) Files.deleteIfExists(file.toPath());
                PhoneBackupTest.remove(catalog.set(copy.id));
            }
            restore(active,oldActive); restore(bindings,oldBindings); PhoneBackupTest.remove(root);
        }
    }
    private static void restore(File file,byte[] bytes) throws IOException {
        if(bytes==null) new android.util.AtomicFile(file).delete(); else Files.write(file.toPath(),bytes);
    }
    private AccessibilityNodeInfo description(AccessibilityNodeInfo node,String text) {
        if(node==null) return null;
        if(text.contentEquals(node.getContentDescription()==null ? "" : node.getContentDescription())) return node;
        for(int i=0;i<node.getChildCount();i++) { AccessibilityNodeInfo found=description(node.getChild(i),text); if(found!=null)return found; }
        return null;
    }
    private AccessibilityNodeInfo waitText(String text) {
        long end=SystemClock.uptimeMillis()+20000;
        do {
            AccessibilityNodeInfo root=ui.getRootInActiveWindow();
            if(root!=null) for(AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText(text))
                if(node.getText()!=null && text.equals(node.getText().toString())) return node;
            SystemClock.sleep(100);
        } while(SystemClock.uptimeMillis()<end);
        throw new AssertionError("Missing text: "+text);
    }
    private void click(String text) {
        long end=SystemClock.uptimeMillis()+15000;
        do {
            AccessibilityNodeInfo root=ui.getRootInActiveWindow();
            if(root!=null) for(AccessibilityNodeInfo match:root.findAccessibilityNodeInfosByText(text)) {
                if(match.getText()==null || !text.equals(match.getText().toString())) continue;
                AccessibilityNodeInfo node=match;
                while(!node.isClickable() && node.getParent()!=null) node=node.getParent();
                if(node.isClickable() && node.isEnabled() && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return;
            }
            SystemClock.sleep(100);
        } while(SystemClock.uptimeMillis()<end);
        try {capture("symbian-component-ui-failure.png");} catch(IOException ignored) { }
        throw new AssertionError("Cannot click: "+text);
    }
    private void capture(String name) throws IOException {
        SystemClock.sleep(350); Bitmap bitmap=ui.takeScreenshot(); assertNotNull(bitmap);
        try(OutputStream out=new FileOutputStream(new File(context.getExternalFilesDir(null),name))) { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out)); }
        finally { bitmap.recycle(); }
    }
}
