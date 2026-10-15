package io.github.eriark.abyssme.symbian;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.test.platform.app.InstrumentationRegistry;
import org.apache.commons.compress.archivers.sevenz.*;
import org.junit.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import ru.playsoftware.j2meloader.MainActivity;
import static org.junit.Assert.*;

/** Opt-in end-to-end checks with owner-provided phone dumps, never bundled fixtures. */
public class RawPhoneArchiveUiTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final Context context = instrumentation.getTargetContext();
    private UiAutomation ui;
    @Test public void rawZipAndSevenImportThroughExternalEntryAndShowPersistentFailure() throws Exception {
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("rawPhoneUi")));
        File inputs = new File(context.getExternalFilesDir(null),"symbian-input");
        File rom = new File(inputs,"n95.rom"), rpkg = new File(inputs,"n95.rpkg");
        Assume.assumeTrue(rom.isFile() && rpkg.isFile());
        Assume.assumeFalse(new PendingGameImport(new File(context.getFilesDir(),"symbian-game-import")).exists());
        File root = Files.createTempDirectory(context.getCacheDir().toPath(),"raw-phone-ui-").toFile();
        ui = instrumentation.getUiAutomation();
        SystemFileCatalog catalog = new SystemFileCatalog(context);
        int afterZip = -1;
        try {
            for(boolean seven:new boolean[]{false,true}) {
                File archive = archive(root,seven,rom,rpkg);
                open(archive); waitText("System import",120000);
                assertFalse(catalog.list().isEmpty());
                if(!seven) afterZip = catalog.list().size();
                else assertEquals("Reimport must not create another phone",afterZip,catalog.list().size());
                capture(seven ? "raw-phone-seven-import.png" : "raw-phone-zip-import.png");
                click("Close"); click("Library"); waitText("All games",15000);
            }
            File broken = new File(root,"missing-rom.7z");
            try(SevenZOutputFile out = new SevenZOutputFile(broken)) {
                SevenZArchiveEntry entry = new SevenZArchiveEntry();entry.setName("nested/SYM.RPKG");entry.setSize(3);
                out.putArchiveEntry(entry);out.write(new byte[]{1,2,3});out.closeArchiveEntry();
            }
            open(broken); waitText("No phone ROM found in this archive",30000);
            SystemClock.sleep(5500); waitText("No phone ROM found in this archive",1000);
            assertEquals(afterZip,catalog.list().size()); capture("raw-phone-import-error.png");
            click("Close"); click("Library"); waitText("All games",15000);
        } finally { PhoneBackupTest.remove(root); }
    }
    private File archive(File root,boolean seven,File rom,File rpkg) throws Exception {
        File result = new File(root,seven ? "nested-phone.7z" : "nested-phone.zip");
        File[] files={rom,rpkg};String[] names={"Phone/N95.ROM","Phone/SYM.RpKg"};
        if(seven) {
            try(SevenZOutputFile out=new SevenZOutputFile(result)) {
                for(int i=0;i<2;i++) {
                    out.putArchiveEntry(out.createArchiveEntry(files[i],names[i]));
                    try(InputStream in=new FileInputStream(files[i])) {byte[] buffer=new byte[65536];for(int n;(n=in.read(buffer))!=-1;)out.write(buffer,0,n);}
                    out.closeArchiveEntry();
                }
            }
        } else {
            try(ZipOutputStream out=new ZipOutputStream(new FileOutputStream(result))) {
                for(int i=0;i<2;i++) {out.putNextEntry(new ZipEntry(names[i]));Files.copy(files[i].toPath(),out);out.closeEntry();}
            }
        }
        return result;
    }
    private void open(File file) {
        context.startActivity(new Intent(context,MainActivity.class).setAction(Intent.ACTION_VIEW)
                .setData(Uri.fromFile(file)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
    private AccessibilityNodeInfo waitText(String text,long timeout) {
        long end=SystemClock.uptimeMillis()+timeout;
        do {
            AccessibilityNodeInfo root=ui.getRootInActiveWindow();
            if(root!=null) for(AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText(text))
                if(node.getText()!=null && text.equalsIgnoreCase(node.getText().toString()))return node;
            SystemClock.sleep(100);
        }while(SystemClock.uptimeMillis()<end);
        throw new AssertionError("Missing text: "+text);
    }
    private void click(String text) {
        AccessibilityNodeInfo node=waitText(text,15000);
        while(!node.isClickable() && node.getParent()!=null)node=node.getParent();
        assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK));
    }
    private void capture(String name) throws Exception {
        ui.waitForIdle(500,5000);Bitmap bitmap=ui.takeScreenshot();assertNotNull(bitmap);
        try(OutputStream out=new FileOutputStream(new File(context.getExternalFilesDir(null),name))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));
        }finally{bitmap.recycle();}
    }
}
