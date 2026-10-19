package ru.playsoftware.j2meloader.input;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.FrameLayout;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import ru.playsoftware.j2meloader.MainActivity;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.config.ProfileModel;
import ru.playsoftware.j2meloader.config.ProfilesManager;
import static org.junit.Assert.*;

public class FastForwardControlTest {
    private void main(Runnable action) { InstrumentationRegistry.getInstrumentation().runOnMainSync(action); }
    @Test public void profileRoundTripAndPreviewDoNotPersistActivation() throws Exception {
        java.io.File directory = java.nio.file.Files.createTempDirectory(
                InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir().toPath(), "ff-profile").toFile();
        try {
            ProfileModel p = new ProfileModel(directory);
            p.emulationSpeed = 75; p.fastForwardSpeed = 400; p.fastForwardButton = 105; p.fastForwardToggle = true;
            assertTrue(ProfilesManager.saveConfig(p));
            ProfileModel loaded = ProfilesManager.loadConfig(directory);
            assertEquals(400, loaded.fastForwardSpeed); assertEquals(105, loaded.fastForwardButton);
            assertTrue(loaded.fastForwardToggle); assertEquals(75, loaded.getEmulationSpeed());
            ProfilesManager.beginPreview(loaded); loaded.fastForwardButton = 106; loaded.fastForwardSpeed = 300;
            ProfilesManager.undoPreview(loaded); assertEquals(105, loaded.fastForwardButton); assertEquals(400, loaded.fastForwardSpeed);
        } finally {
            for (java.io.File f : directory.listFiles()) assertTrue(f.delete());
            assertTrue(directory.delete());
        }
    }
    @Test public void activeShortcutRemainsAccessibleWithoutResizingAndCanBeHidden() {
        main(() -> {
            FrameLayout root = new FrameLayout(InstrumentationRegistry.getInstrumentation().getTargetContext());
            ProfileModel p = new ProfileModel(); p.emulationSpeed = 75; p.fpsLimit = 30; p.fastForwardSpeed = 400;
            int[] speed = {0}, frames = {0};
            FastForwardControl control = new FastForwardControl(root, p, () -> true, v -> speed[0] = v, v -> frames[0] = v, true);
            GameChrome chrome = new GameChrome(root,p); control.changed(chrome::render);
            try {
                chrome.update(true,true,true); View button = root.findViewById(R.id.game_fast_forward_button);
                assertEquals(View.GONE,button.getVisibility()); root.findViewById(R.id.game_controls_handle).performClick();
                button.performClick(); assertTrue(control.active()); assertEquals(400,speed[0]); assertEquals(120,frames[0]);
                chrome.collapse(); assertEquals(View.VISIBLE,button.getVisibility());
                assertEquals("Stop fast-forward",button.getContentDescription());
                button.performClick(); assertEquals(75,speed[0]); assertEquals(30,frames[0]);
                assertEquals(75,p.emulationSpeed);
                p.fastForwardOnScreen = false; control.toggle(); chrome.render(); assertEquals(View.GONE,button.getVisibility());
                control.stop(); assertFalse(control.active());
            } finally { control.stop(); chrome.collapse(); }
        });
    }
    @Test public void menuCommandWaitsForFocusAndUnavailableClockCannotAccelerate() {
        android.app.Instrumentation inst = InstrumentationRegistry.getInstrumentation();
        Activity activity = inst.startActivitySync(new Intent(inst.getTargetContext(), MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        FastForwardControl[] control = {null}; GameMenuDialog[] menu = {null}; boolean[] available = {true};
        try {
            main(() -> {
                FrameLayout root = new FrameLayout(activity); activity.setContentView(root);
                ProfileModel p = new ProfileModel();
                control[0] = new FastForwardControl(root,p,() -> available[0],v -> {},v -> {},true);
                menu[0] = new GameMenuDialog(activity,"Fast-forward test"); menu[0].show(); menu[0].page("Game menu");
                menu[0].setOnDismissListener(d -> control[0].stop());
                control[0].menuAction(menu[0]);
            });
            inst.waitForIdleSync();
            main(() -> clickText(menu[0].getWindow().getDecorView(),"Fast-forward 200%"));
            android.os.SystemClock.sleep(500); inst.waitForIdleSync();
            main(() -> {
                assertTrue("Menu command must survive focus restoration",control[0].active());
                control[0].stop(); available[0] = false; control[0].toggle(); assertFalse(control[0].active());
                assertEquals(0,control[0].binding());
            });
        } finally { main(() -> { if (menu[0] != null) menu[0].dismiss(); if (control[0] != null) control[0].stop(); activity.finish(); }); }
    }
    private void clickText(View view,String text) {
        if (view instanceof android.widget.TextView && text.contentEquals(((android.widget.TextView)view).getText())) { view.performClick(); return; }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup)view;
            for (int i=0;i<group.getChildCount();i++) clickText(group.getChildAt(i),text);
        }
    }
}
