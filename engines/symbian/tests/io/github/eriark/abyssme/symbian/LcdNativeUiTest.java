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
    @Test public void screenOptionsReachTheRealMenuAndSurviveReturnToLibrary() throws Exception {
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("phoneScreenOptions")));
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        ui = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        String title = InstrumentationRegistry.getArguments().getString("lcdNativeTitle", "Asphalt3");
        SymbianLibraryIndex.Entry game = new SymbianLibraryIndex(context).read().stream()
                .filter(e -> e.title.equals(title)).findFirst().orElse(null);
        assertNotNull("Provision the permitted phone fixture", game);
        SystemFileCatalog.Entry phone = new SystemFileCatalog(context).read(game.system);
        int expected = SymbianGameSettings.supportsGuestScreen(phone.os) ? 7 : 1;
        context.startActivity(new Intent(context, SymbianActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra(SymbianLibraryIndex.SYSTEM_EXTRA, game.system)
                .putExtra(SymbianLibraryIndex.UID_EXTRA, game.uid));
        await("Show game controls", 60000); SystemClock.sleep(1500);
        assertEquals(expected, PhoneScreenOptions.read(context.getFilesDir(), game.system));
        tap("Show game controls"); tap("Game menu"); tap("Display");
        tap(context.getString(ru.playsoftware.j2meloader.R.string.game_performance_advanced));
        await(context.getString(expected == 1 ? ru.playsoftware.j2meloader.R.string.symbian_screen_legacy
                : ru.playsoftware.j2meloader.R.string.symbian_screen_title), 5000);
        await(PhoneLibraryTitles.read(context.getFilesDir(), game.system).getJSONObject("titles")
                .optString(Long.toString(game.uid), title), 5000);
        capture(context, "screen-options-" + title);
        assertTrue(ui.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK));
        tap("Exit game"); tap("Leave without saving");
        SystemClock.sleep(1500);
        assertEquals("Library process can reuse discovered modes", expected,
                PhoneScreenOptions.read(context.getFilesDir(), game.system));
    }
    @Test public void runningNativeFramesSurviveMenuPause() throws Exception {
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("lcdRunningFrames")));
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        ui = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        String title = InstrumentationRegistry.getArguments().getString("lcdNativeTitle", "Snakes");
        SymbianLibraryIndex.Entry game = new SymbianLibraryIndex(context).read().stream()
                .filter(e -> e.title.equals(title)).findFirst().orElse(null);
        assertNotNull("Provision the private animated fixture", game);
        boolean platform = "true".equals(InstrumentationRegistry.getArguments().getString("lcdViaPlatform"));
        ru.playsoftware.j2meloader.config.ProfileModel original = SymbianGameSettings.load(context.getFilesDir(), game.system,
                platform ? GameInstallFlow.PLATFORM_UID : game.uid);
        boolean externalReady = "true".equals(InstrumentationRegistry.getArguments().getString("lcdWaitForReady"));
        File ready = new File(context.getExternalFilesDir(null), "lcd-frames-ready");
        if (externalReady && ready.exists()) assertTrue(ready.delete());
        try {
        if (platform) new SymbianRuntimeTest().prepareModernLcdFixture();
        else {
        context.startActivity(new Intent(context, SymbianActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra(SymbianLibraryIndex.SYSTEM_EXTRA, game.system)
                .putExtra(SymbianLibraryIndex.UID_EXTRA, game.uid));
        await("Show game controls", 60000);
        // Allow the external fixture driver to enter gameplay before sampling.
        if (!externalReady) SystemClock.sleep(Long.parseLong(InstrumentationRegistry.getArguments().getString("lcdFrameSetupMillis", "15000")));
        }
        if (externalReady) {
            long deadline = SystemClock.uptimeMillis() + 300000;
            while (!ready.exists() && SystemClock.uptimeMillis() < deadline) {
                assertFixtureForeground(context);
                SystemClock.sleep(250);
            }
            assertTrue("Fixture driver did not signal gameplay readiness", ready.isFile());
            assertTrue(ready.delete());
        }
        // Sample only the central game area, never the menu or chrome animation.
        assertMovingFrames(context, "before-pause");
        for (String preset : new String[]{"Resume", "LCD Soft", "LCD Classic", "Original"}) {
            tap("Show game controls"); tap("Game menu");
            if (!preset.equals("Resume")) { tap("Display"); tap(preset); tap("Keep"); }
            SystemClock.sleep(1500);
            tap("Resume");
            assertMovingFrames(context, "after-pause-" + preset.replace(' ', '-'));
        }
        tap("Show game controls"); tap("Game menu"); tap("Exit game"); tap("Leave without saving");
        if (platform) tap("Finished");
        } finally {
            if (externalReady && ready.exists()) assertTrue(ready.delete());
            assertTrue(ru.playsoftware.j2meloader.config.ProfilesManager.saveConfig(original));
        }
    }
    private void assertMovingFrames(Context context, String name) throws Exception {
        int heldKey = Integer.parseInt(InstrumentationRegistry.getArguments().getString("lcdFrameHeldKey", "0"));
        long keyDown = SystemClock.uptimeMillis();
        Bitmap previous = null;
        boolean keyPressed = false;
        try {
            assertFixtureForeground(context);
            if (heldKey != 0) {
                injectHeldKey(keyDown, heldKey, KeyEvent.ACTION_DOWN);
                keyPressed = true;
            }
            SystemClock.sleep(5500);
            assertFixtureForeground(context);
            previous = ui.takeScreenshot(); assertNotNull(previous);
            int changed = 0;
            for (int sample = 0; sample < 8; sample++) {
                SystemClock.sleep(750);
                assertFixtureForeground(context);
                Bitmap current = ui.takeScreenshot(); assertNotNull(current);
                int different = 0, total = 0;
                for (int y = current.getHeight() / 4; y < current.getHeight() * 3 / 4; y += 4)
                    for (int x = current.getWidth() / 3; x < current.getWidth() * 2 / 3; x += 4) {
                        if (current.getPixel(x,y) != previous.getPixel(x,y)) different++;
                        total++;
                    }
                if (different > total / 100) changed++;
                previous.recycle(); previous = current;
            }
            capture(context, name);
            assertTrue("Native game frames stopped: " + name + " (changed samples=" + changed + ")", changed >= 3);
        } finally {
            if (previous != null) previous.recycle();
            if (keyPressed) injectHeldKey(keyDown, heldKey, KeyEvent.ACTION_UP);
        }
    }
    private void injectHeldKey(long downTime, int code, int action) {
        assertTrue(ui.injectInputEvent(new KeyEvent(downTime, SystemClock.uptimeMillis(), action,
                code, 0, 0, -1, 0, 0, InputDevice.SOURCE_GAMEPAD), true));
    }
    private void assertFixtureForeground(Context context) {
        AccessibilityNodeInfo root = ui.getRootInActiveWindow();
        assertNotNull("Native fixture window closed", root);
        assertEquals("Native fixture lost the foreground", context.getPackageName(), String.valueOf(root.getPackageName()));
        assertNotNull("Native fixture returned to its library", find(root, "Show game controls"));
    }
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
            AccessibilityNodeInfo root=ui.getRootInActiveWindow();
            String target=InstrumentationRegistry.getInstrumentation().getTargetContext().getPackageName();
            AccessibilityNodeInfo node=root != null && target.contentEquals(root.getPackageName() == null ? "" : root.getPackageName())
                    ? find(root,text) : null;
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
