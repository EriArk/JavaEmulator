package ru.playsoftware.j2meloader.catalog;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.*;
import ru.playsoftware.j2meloader.MainActivity;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.applist.*;
import static org.junit.Assert.*;

public class LibraryInformationUiTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final Context context = instrumentation.getTargetContext();
    private UiAutomation ui;
    @Test public void nativeExportUsesDocumentPickerAndResolvesTheSameInstalledGame() throws Exception {
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("libraryInfoUi")));
        Assume.assumeFalse(ExternalLibrary.read(context).isEmpty());
        ui=instrumentation.getUiAutomation();
        File target=File.createTempFile("export-", ".abyssme", context.getCacheDir());
        MainActivity activity=(MainActivity)instrumentation.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        Instrumentation.ActivityMonitor monitor=new Instrumentation.ActivityMonitor() {
            @Override public Instrumentation.ActivityResult onStartActivity(Intent intent) {
                if(!Intent.ACTION_CREATE_DOCUMENT.equals(intent.getAction())) return null;
                assertTrue(intent.getStringExtra(Intent.EXTRA_TITLE).endsWith(".abyssme"));
                return new Instrumentation.ActivityResult(Activity.RESULT_OK,new Intent().setData(Uri.fromFile(target)));
            }
        };
        try {
            AppItem item=select(activity,true); key(KeyEvent.KEYCODE_BUTTON_X);
            waitText("Export launch file",10000);capture("native-launch-file-action.png");
            instrumentation.addMonitor(monitor);click("Export launch file");
            long end=SystemClock.uptimeMillis()+10000;
            while(target.length()==0 && SystemClock.uptimeMillis()<end)SystemClock.sleep(100);
            assertTrue(target.length()>0);
            assertEquals(item.getPath(),LauncherLink.resolve(context,Uri.fromFile(target)).getPath());
        } finally {
            instrumentation.removeMonitor(monitor);instrumentation.runOnMainSync(activity::finish);
            Files.deleteIfExists(target.toPath());
        }
    }
    @Test public void javaVersionIsReachableInGameActionsWithoutReimport() throws Exception {
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("libraryInfoUi")));
        ui=instrumentation.getUiAutomation();
        MainActivity activity=(MainActivity)instrumentation.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            AppItem item=select(activity,false);
            key(KeyEvent.KEYCODE_BUTTON_X);click("Game information");
            waitText(GameInformation.describe(item),10000);capture("java-game-information.png");
            key(KeyEvent.KEYCODE_BACK);
        } finally {instrumentation.runOnMainSync(activity::finish);}
    }
    private AppItem select(MainActivity activity,boolean nativeGame) {
        AtomicReference<AppItem> selected=new AtomicReference<>();long end=SystemClock.uptimeMillis()+15000;
        do {
            instrumentation.runOnMainSync(() -> {
                RecyclerView list=activity.findViewById(R.id.apps_recycler);
                if(list==null || list.getAdapter()==null || list.hasPendingAdapterUpdates())return;
                AppsListAdapter adapter=(AppsListAdapter)list.getAdapter();
                for(int i=0;i<adapter.getItemCount();i++) {
                    AppItem item=adapter.getItem(i);
                    if((item instanceof ExternalLibrary.Item)!=nativeGame || (!nativeGame && AdditionalGames.isManaged(new File(item.getPathExt()))))continue;
                    RecyclerView.ViewHolder row=list.findViewHolderForAdapterPosition(i);
                    if(row==null){list.scrollToPosition(i);return;}
                    row.itemView.setFocusableInTouchMode(true);
                    if(row.itemView.requestFocus())selected.set(item);
                    return;
                }
            });
            if(selected.get()==null)SystemClock.sleep(100);
        }while(selected.get()==null && SystemClock.uptimeMillis()<end);
        assertNotNull("Fixture game in library",selected.get());return selected.get();
    }
    private AccessibilityNodeInfo waitText(String text,long timeout) {
        long end=SystemClock.uptimeMillis()+timeout;
        do {AccessibilityNodeInfo root=ui.getRootInActiveWindow();
            if(root!=null)for(AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText(text))
                if(node.getText()!=null && text.equalsIgnoreCase(node.getText().toString()))return node;
            SystemClock.sleep(100);
        }while(SystemClock.uptimeMillis()<end);
        throw new AssertionError("Missing text: "+text);
    }
    private void click(String text) {AccessibilityNodeInfo node=waitText(text,10000);while(!node.isClickable() && node.getParent()!=null)node=node.getParent();assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK));}
    private void key(int code) {long now=SystemClock.uptimeMillis();ui.injectInputEvent(new KeyEvent(now,now,KeyEvent.ACTION_DOWN,code,0),true);ui.injectInputEvent(new KeyEvent(now,SystemClock.uptimeMillis(),KeyEvent.ACTION_UP,code,0),true);instrumentation.waitForIdleSync();}
    private void capture(String name) throws Exception {
        ui.waitForIdle(500,5000);Bitmap image=ui.takeScreenshot();assertNotNull(image);
        try(OutputStream out=new FileOutputStream(new File(context.getExternalFilesDir(null),name))){assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,out));}
        finally{image.recycle();}
    }
}
