package io.github.eriark.abyssme.symbian;

import android.content.*;
import android.app.*;
import android.graphics.*;
import android.os.SystemClock;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.*;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import static org.junit.Assert.*;

public class LcdNativeUiTest {
    private UiAutomation ui;
    @Test public void livePresetTilesRemainResponsiveWhileNativeGameIsPaused() throws Exception {
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("lcdNativePresets")));
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String title = InstrumentationRegistry.getArguments().getString("lcdNativeTitle", "Snakes");
        SymbianLibraryIndex.Entry game = new SymbianLibraryIndex(context).read().stream()
                .filter(e -> e.title.equals(title)).findFirst().orElse(null);
        assertNotNull("Provision the private game fixture",game);
        ru.playsoftware.j2meloader.config.ProfileModel original = SymbianGameSettings.load(context.getFilesDir(),game.system,game.uid);
        try {
        ui = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        context.startActivity(new Intent(context,SymbianActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra(SymbianLibraryIndex.SYSTEM_EXTRA,game.system)
                .putExtra(SymbianLibraryIndex.UID_EXTRA,game.uid));
        await("Show game controls",60000); SystemClock.sleep(2500);
        for (int round = 0; round < 3; round++) {
            tap("Show game controls"); tap("Game menu"); tap("Display");
            for (String preset : new String[]{"LCD Soft", "LCD Classic", "Original", "LCD Classic"}) {
                tap(preset); await("Keep",5000); await("Undo",5000);
            }
            capture(context,"presets-" + round);
            tap(round == 1 ? "Undo" : "Keep"); tap("Resume");
            SystemClock.sleep(600); capture(context,"game-" + round);
            assertEquals(2, SymbianGameSettings.load(context.getFilesDir(),game.system,game.uid).screenEffect);
            if (round == 0) {
                ActivityManager.AppTask task = context.getSystemService(ActivityManager.class).getAppTasks().stream()
                        .filter(t -> t.getTaskInfo().topActivity != null
                                && t.getTaskInfo().topActivity.getClassName().equals(SymbianActivity.class.getName()))
                        .findFirst().orElse(null);
                assertNotNull(task);
                long now=SystemClock.uptimeMillis();
                ui.injectInputEvent(new KeyEvent(now,now,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_HOME,0),true);
                ui.injectInputEvent(new KeyEvent(now,now+50,KeyEvent.ACTION_UP,KeyEvent.KEYCODE_HOME,0),true);
                SystemClock.sleep(500); task.moveToFront(); await("Show game controls",10000);
            }
        }
        tap("Show game controls"); tap("Game menu"); tap("Display"); tap("Original"); tap("Keep");
        assertEquals(0,SymbianGameSettings.load(context.getFilesDir(),game.system,game.uid).screenEffect);
        tap("Exit game"); tap("Leave without saving");
        } finally {
            assertTrue(ru.playsoftware.j2meloader.config.ProfilesManager.saveConfig(original));
        }
    }
    private AccessibilityNodeInfo find(AccessibilityNodeInfo node, String text) {
        if (node == null) return null;
        if (node.isVisibleToUser() && (text.contentEquals(node.getText() == null ? "" : node.getText())
                || text.contentEquals(node.getContentDescription() == null ? "" : node.getContentDescription()))) return node;
        for (int i=0;i<node.getChildCount();i++) {
            AccessibilityNodeInfo match=find(node.getChild(i),text); if(match!=null) return match;
        }
        return null;
    }
    private AccessibilityNodeInfo await(String text,long timeout) {
        long end=SystemClock.uptimeMillis()+timeout;
        do {
            AccessibilityNodeInfo node=find(ui.getRootInActiveWindow(),text);
            if(node!=null) return node;
            SystemClock.sleep(100);
        } while(SystemClock.uptimeMillis()<end);
        throw new AssertionError("Missing or unresponsive native UI: "+text);
    }
    private void tap(String text) {
        AccessibilityNodeInfo node=await(text,10000);
        Rect bounds=new Rect(); node.getBoundsInScreen(bounds);
        long now=SystemClock.uptimeMillis();
        MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,bounds.centerX(),bounds.centerY(),0);
        MotionEvent up=MotionEvent.obtain(now,now+50,MotionEvent.ACTION_UP,bounds.centerX(),bounds.centerY(),0);
        down.setSource(InputDevice.SOURCE_TOUCHSCREEN); up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        try { assertTrue(ui.injectInputEvent(down,true)); assertTrue(ui.injectInputEvent(up,true)); }
        finally { down.recycle(); up.recycle(); }
        SystemClock.sleep(250);
    }
    private void capture(Context context,String name) throws IOException {
        File folder=new File(context.getExternalFilesDir(null),"lcd-native-presets");
        assertTrue(folder.isDirectory() || folder.mkdirs());
        Bitmap screenshot=ui.takeScreenshot(); assertNotNull(screenshot);
        try(FileOutputStream out=new FileOutputStream(new File(folder,name+".png"))) {
            assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG,100,out));
        } finally { screenshot.recycle(); }
    }
    @Test public void nativeScreenshotExports() throws Exception {
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("lcdNativeExport")));
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        SymbianLibraryIndex.Entry game=new SymbianLibraryIndex(context).read().stream()
                .filter(e -> e.title.equals("Snakes")).findFirst().orElse(null);
        assertNotNull("Provision the private Snakes fixture",game);
        new ru.playsoftware.j2meloader.input.TouchAppearanceUiTest().checkScreenshotExports(() -> {
            context.startActivity(new Intent(context,SymbianActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .putExtra(SymbianLibraryIndex.SYSTEM_EXTRA,game.system)
                    .putExtra(SymbianLibraryIndex.UID_EXTRA,game.uid));
        });
    }
    @Test public void nativeEffectsApplyUndoAndPersist() throws Exception {
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("lcdNative")));
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        SymbianLibraryIndex.Entry game=new SymbianLibraryIndex(context).read().stream()
                .filter(e -> e.title.equals("Snakes")).findFirst().orElse(null);
        assertNotNull("Provision the private Snakes fixture",game);
        new ru.playsoftware.j2meloader.input.TouchAppearanceUiTest().checkLcdEffects(() -> {
            context.startActivity(new Intent(context,SymbianActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .putExtra(SymbianLibraryIndex.SYSTEM_EXTRA,game.system)
                    .putExtra(SymbianLibraryIndex.UID_EXTRA,game.uid));
        });
    }
}
