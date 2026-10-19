package ru.playsoftware.j2meloader.input;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.preference.PreferenceManager;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Assume;
import org.junit.Test;
import ru.playsoftware.j2meloader.BuildConfig;
import ru.playsoftware.j2meloader.MainActivity;
import static org.junit.Assert.*;

/** Opt-in screenshots and real settings flow using a caller-provided game. */
public class TouchAppearanceUiTest {
    @Test public void visualEditorAppliesAndPersistsThroughRealGameMenu() throws Exception {
        String uri=InstrumentationRegistry.getArguments().getString("layoutGameUri");
        Assume.assumeNotNull(uri);
        SharedPreferences prefs=PreferenceManager.getDefaultSharedPreferences(context);
        String previous=prefs.getString("pref_palette",null);
        ui=instrumentation.getUiAutomation();
        try {
            instrumentation.runOnMainSync(()->prefs.edit().putString("pref_palette","retro").commit());
            launch(uri);
            change("Controls","On-screen buttons","Always");
            change("Display","Orientation","Portrait");
            change("Controls","Touch layout","Phone");
            change("Controls","Button theme","Dark");
            click("Game menu"); click("Controls"); click("Edit touch layout");
            click("Position tab");
            AccessibilityNodeInfo slider=description(ui.getRootInActiveWindow(),"Block size"); assertNotNull(slider);
            android.os.Bundle value=new android.os.Bundle();
            value.putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE,10);
            if (slider.getRangeInfo()==null || slider.getRangeInfo().getCurrent()!=10)
                assertTrue(slider.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId(),value));
            click("Move left"); capture("editor-position-portrait");
            click("Use layout"); click("Try changes"); click("Keep"); capture("editor-applied-game");
            leave(); launch(uri);
            click("Game menu"); click("Controls"); click("Edit touch layout"); click("Position tab");
            waitNode("Block size: 80%"); capture("editor-persisted"); click("Cancel");
            click("Back"); click("Close");
            change("Display","Orientation","Landscape");
            change("Controls","Touch layout","Gamepad");
            capture("editor-gamepad-landscape");
            leave();
        } finally {
            instrumentation.runOnMainSync(()->{
                SharedPreferences.Editor edit=prefs.edit();
                if(previous==null) edit.remove("pref_palette"); else edit.putString("pref_palette",previous);
                edit.commit();
            });
        }
    }
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final Context context = instrumentation.getTargetContext();
    private UiAutomation ui;
    private androidx.test.core.app.ActivityScenario<MainActivity> lcdLibrary;

    @Test public void lcdEffectsApplyUndoAndPersist() throws Exception {
        String uri=InstrumentationRegistry.getArguments().getString("touchGameUri");
        Assume.assumeNotNull(uri);
        checkLcdEffects(() -> launch(uri));
    }

    public void checkLcdEffects(Runnable launchGame) throws Exception {
        ui=instrumentation.getUiAutomation();
        launchGame.run();
        waitNode("Show game controls",60000); SystemClock.sleep(2500);
        change("Display","Screen effect","Off"); capture("lcd-0-off");
        change("Display","Screen effect","LCD Soft"); capture("lcd-1-soft");
        change("Display","Screen effect","LCD Classic"); capture("lcd-2-classic");
        click("Game menu"); click("Display"); capture("lcd-3-settings");
        click("Screen effect"); click("Off"); click("Try changes"); click("Undo");
        click("Game menu"); click("Display"); waitNode("LCD Classic");
        click("Back"); click("Close");
        change("Display","Image rotation","90\u00b0"); capture("lcd-4-rotated");
        change("Display","Image rotation","0\u00b0");
        leave(); launchGame.run(); waitNode("Show game controls",60000);
        click("Game menu"); click("Display"); waitNode("LCD Classic");
        click("Back"); click("Close"); capture("lcd-5-relaunched");
        change("Display","Screen effect","Off"); leave();
        if (lcdLibrary != null) { lcdLibrary.close(); lcdLibrary = null; }
    }

    @Test public void screenshotExportsWhenRequested() throws Exception {
        String uri=InstrumentationRegistry.getArguments().getString("exportGameUri");
        Assume.assumeNotNull(uri);
        checkScreenshotExports(() -> launch(uri));
    }

    public void checkScreenshotExports(Runnable launchGame) throws Exception {
        ui=instrumentation.getUiAutomation();
        boolean visible=ru.playsoftware.j2meloader.util.Screenshots.isButtonVisible(context);
        java.util.ArrayList<Uri> created=new java.util.ArrayList<>();
        try {
            ru.playsoftware.j2meloader.util.Screenshots.setButtonVisible(context,true);
            launchGame.run(); waitNode("Show game controls",60000);
            change("Display","Image rotation","0\u00b0");
            change("Display","Screen effect","LCD Classic");
            change("Display","Screenshot image","Clean");
            Bitmap clean=export("export-clean",created);
            change("Display","Screenshot image","With LCD effect");
            Bitmap styled=export("export-lcd",created);
            assertTrue(styled.getWidth()>=clean.getWidth()); assertTrue(styled.getHeight()>=clean.getHeight());
            assertFalse(clean.sameAs(styled));
            double ratio=(double)styled.getWidth()/styled.getHeight();
            assertEquals((double)clean.getWidth()/clean.getHeight(),ratio,.01);
            clean.recycle(); styled.recycle();
            click("Game menu"); click("Display"); capture("lcd-export-settings");
            click("Screenshot image"); click("Clean"); click("Try changes"); click("Undo");
            click("Game menu"); click("Display"); assertChoice("Screenshot image","With LCD effect"); click("Back"); click("Close");
            change("Display","Image rotation","90\u00b0");
            Bitmap rotated=export("export-lcd-rotated",created);
            assertEquals(1/ratio,(double)rotated.getWidth()/rotated.getHeight(),.02); rotated.recycle();
            change("Display","Image rotation","0\u00b0");
            leave(); launchGame.run(); waitNode("Show game controls",60000);
            click("Game menu"); click("Display"); assertChoice("Screenshot image","With LCD effect"); click("Back"); click("Close");
            change("Display","Screenshot image","Clean");
            change("Display","Screen effect","Off"); leave();
        } finally {
            ru.playsoftware.j2meloader.util.Screenshots.setButtonVisible(context,visible);
            for(Uri uri:created) context.getContentResolver().delete(uri,null,null);
            if(lcdLibrary!=null) {lcdLibrary.close();lcdLibrary=null;}
        }
    }

    private void assertChoice(String setting,String value) {
        AccessibilityNodeInfo node=description(ui.getRootInActiveWindow(),setting);
        assertNotNull(node);
        node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.getId());
        waitNode(value);
    }

    private long latestExport() {
        try(android.database.Cursor cursor=context.getContentResolver().query(
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,new String[]{"_id"},
                "relative_path=? AND is_pending=0 AND owner_package_name=?",
                new String[]{"Pictures/AbyssME/",context.getPackageName()},"_id DESC")) {
            return cursor!=null && cursor.moveToFirst()?cursor.getLong(0):0;
        }
    }

    private Bitmap export(String name,java.util.List<Uri> created) throws Exception {
        long before=latestExport();
        if(find(ui.getRootInActiveWindow(),"Show game controls")!=null) click("Show game controls");
        click(find(ui.getRootInActiveWindow(),"Take screenshot")!=null?"Take screenshot":"Screenshot");
        long until=SystemClock.uptimeMillis()+15000, id;
        do {SystemClock.sleep(100);id=latestExport();} while(id<=before && SystemClock.uptimeMillis()<until);
        assertTrue("Screenshot was not published",id>before);
        Uri uri=android.content.ContentUris.withAppendedId(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,id);
        created.add(uri);
        File dir=new File(context.getExternalFilesDir(null),"lcd-exports"); assertTrue(dir.isDirectory()||dir.mkdirs());
        try(java.io.InputStream in=context.getContentResolver().openInputStream(uri)) {
            java.nio.file.Files.copy(in,new File(dir,name+".png").toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        Bitmap bitmap=android.graphics.BitmapFactory.decodeFile(new File(dir,name+".png").getPath());
        assertNotNull(bitmap); return bitmap;
    }

    @Test public void lcdPresentationCostWhenRequested() throws Exception {
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("lcdPerf")));
        String uri=InstrumentationRegistry.getArguments().getString("touchGameUri");
        Assume.assumeNotNull(uri); ui=instrumentation.getUiAutomation(); launch(uri);
        File dir=new File(context.getExternalFilesDir(null),"lcd-performance");
        assertTrue(dir.isDirectory()||dir.mkdirs());
        int sample=0;
        for(String mode:new String[]{"Off","LCD Classic","LCD Soft","Off"}) {
            change("Display","Screen effect",mode);
            SystemClock.sleep(6000); // Let shortcuts disappear before measuring steady presentation.
            shellTo("dumpsys gfxinfo " + context.getPackageName() + " reset",new File(dir,"reset.txt"));
            SystemClock.sleep(8000);
            shellTo("dumpsys gfxinfo " + context.getPackageName() + " framestats",
                    new File(dir,(sample++)+"-"+mode.replace(' ','-')+".txt"));
        }
        leave();
    }
    private void shellTo(String command,File file) throws Exception {
        try(java.io.InputStream input=new android.os.ParcelFileDescriptor.AutoCloseInputStream(ui.executeShellCommand(command));
            FileOutputStream output=new FileOutputStream(file)) {
            byte[] bytes=new byte[8192]; int count;
            while((count=input.read(bytes))!=-1) output.write(bytes,0,count);
        }
    }

    @Test public void lightAppCanKeepDarkControlsInBothLayoutsAndOrientations() throws Exception {
        String uri = InstrumentationRegistry.getArguments().getString("touchGameUri");
        Assume.assumeFalse(BuildConfig.HANDHELD_MODE);
        Assume.assumeNotNull(uri);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String previous = preferences.getString("pref_theme", null);
        ui = instrumentation.getUiAutomation();
        try {
            assertTrue(preferences.edit().putString("pref_theme", "light").commit());
            launch(uri);
            change("Display", "Orientation", "Portrait");
            change("Controls", "Touch layout", "Phone");
            change("Controls", "Button theme", "Dark");
            capture("01-dark-phone-portrait");
            click("Game menu"); click("Controls");
            waitNode("Dark"); capture("02-light-app-dark-controls-setting");
            click("Back"); click("Close");
            change("Controls", "Touch layout", "Gamepad");
            capture("03-dark-gamepad-portrait");
            change("Display", "Orientation", "Landscape");
            capture("04-dark-gamepad-landscape");
            change("Controls", "Touch layout", "Phone");
            capture("05-dark-phone-landscape");
            change("Display", "Orientation", "Portrait");
            change("Controls", "Button theme", "Light");
            capture("06-light-phone-portrait");
            change("Controls", "Button theme", "Dark");
            leave(); launch(uri);
            capture("07-dark-phone-relaunched");
            SystemClock.sleep(GameChrome.HIDE_DELAY_MS + 500);
            waitNode("Show game controls");
            assertNull(find(ui.getRootInActiveWindow(), "Game menu"));
            capture("08-dark-phone-collapsed");
            click("Game menu"); click("Controls"); waitNode("Dark");
            click("Back"); click("Close"); leave();
        } finally {
            SharedPreferences.Editor editor = preferences.edit();
            if (previous == null) editor.remove("pref_theme"); else editor.putString("pref_theme", previous);
            assertTrue(editor.commit());
        }
    }

    private void launch(String uri) {
        // An installed converted directory avoids Android storage grants/import dialogs.
        if (uri.startsWith("/")) {
            if (lcdLibrary == null) lcdLibrary = androidx.test.core.app.ActivityScenario.launch(MainActivity.class);
            lcdLibrary.onActivity(activity -> ru.playsoftware.j2meloader.config.Config.startApp(activity,"LCD test",uri,false));
        } else {
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(uri), context, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
        waitNode("Show game controls", 60000); SystemClock.sleep(2500);
    }
    private void leave() {
        click("Game menu"); click("Exit game"); click("Leave without saving");
        SystemClock.sleep(1000);
    }
    private void change(String page, String setting, String value) {
        click("Game menu"); click(page);
        AccessibilityNodeInfo choice = description(ui.getRootInActiveWindow(), setting);
        if (choice == null || find(choice, value) == null) { click(setting); click(value); }
        click("Try changes"); click("Keep"); SystemClock.sleep(750);
    }
    private AccessibilityNodeInfo find(AccessibilityNodeInfo node, String text) {
        if (node == null) return null;
        if (node.isVisibleToUser() && (text.contentEquals(node.getContentDescription() == null ? "" : node.getContentDescription())
                || text.contentEquals(node.getText() == null ? "" : node.getText()))) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo match = find(node.getChild(i), text);
            if (match != null) return match;
        }
        return null;
    }
    private AccessibilityNodeInfo waitNode(String text) {
        return waitNode(text, 20000);
    }
    private AccessibilityNodeInfo waitNode(String text, long timeout) {
        long until = SystemClock.uptimeMillis() + timeout;
        do {
            AccessibilityNodeInfo root = ui.getRootInActiveWindow();
            AccessibilityNodeInfo node = find(root, text);
            if (node != null) return node;
            if ("Try changes".equals(text) || "Edit touch layout".equals(text) || "Exit game".equals(text)) scroll(root);
            SystemClock.sleep(100);
        } while (SystemClock.uptimeMillis() < until);
        try { capture("failure-" + text.replace(' ', '-')); }
        catch (Exception error) { android.util.Log.w("TouchAppearanceUiTest", "Failure capture", error); }
        throw new AssertionError("Missing: " + text);
    }
    private boolean scroll(AccessibilityNodeInfo node) {
        if (node == null) return false;
        if (node.isScrollable() && node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) return true;
        for (int i = 0; i < node.getChildCount(); i++) if (scroll(node.getChild(i))) return true;
        return false;
    }
    private void click(String text) {
        idle();
        if ("Game menu".equals(text) && find(ui.getRootInActiveWindow(), "Show game controls") != null)
            click("Show game controls");
        AccessibilityNodeInfo node = description(ui.getRootInActiveWindow(), text);
        if (node == null) node = waitNode(text);
        if (!node.isVisibleToUser()) {
            node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.getId());
            idle();
        }
        node.refresh();
        if (String.valueOf(node.getClassName()).endsWith("Spinner")
                || !node.isClickable() || !node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            Rect bounds = new Rect(); node.getBoundsInScreen(bounds);
            assertTrue(node.isVisibleToUser() && !bounds.isEmpty());
            long now = SystemClock.uptimeMillis();
            MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, bounds.centerX(), bounds.centerY(), 0);
            MotionEvent up = MotionEvent.obtain(now, now + 50, MotionEvent.ACTION_UP, bounds.centerX(), bounds.centerY(), 0);
            down.setSource(InputDevice.SOURCE_TOUCHSCREEN); up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            try { assertTrue(ui.injectInputEvent(down, true)); assertTrue(ui.injectInputEvent(up, true)); }
            finally { down.recycle(); up.recycle(); }
        }
        SystemClock.sleep(200);
    }
    private AccessibilityNodeInfo description(AccessibilityNodeInfo node, String text) {
        if (node == null) return null;
        if (text.contentEquals(node.getContentDescription() == null ? "" : node.getContentDescription())) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo match = description(node.getChild(i), text);
            if (match != null) return match;
        }
        return null;
    }
    private void idle() {
        try { ui.waitForIdle(300, 5000); }
        catch (java.util.concurrent.TimeoutException error) { throw new AssertionError("UI did not settle", error); }
    }
    private void capture(String name) throws Exception {
        if (name.startsWith("lcd-")) SystemClock.sleep(2000);
        ui.waitForIdle(300, 5000);
        if (name.matches("0[134567]-.*") && find(ui.getRootInActiveWindow(), "Show game controls") != null) {
            click("Show game controls"); SystemClock.sleep(200);
        }
        Bitmap bitmap = ui.takeScreenshot(); assertNotNull(bitmap);
        if (name.matches("0[134567]-.*")) {
            Rect button = new Rect(); waitNode("Game menu").getBoundsInScreen(button);
            int pixel = bitmap.getPixel(button.left + button.width() / 6, button.centerY());
            double brightness = androidx.core.graphics.ColorUtils.calculateLuminance(pixel);
            assertTrue("Shortcut background must match " + name + ": " + Integer.toHexString(pixel),
                    name.startsWith("06-") ? brightness > .5 : brightness < .2);
        }
        File directory = new File(context.getExternalFilesDir(null), "touch-theme-captures");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        try (FileOutputStream output = new FileOutputStream(new File(directory, name + ".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
        } finally { bitmap.recycle(); }
    }
}
