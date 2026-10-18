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
        context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(uri), context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
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
