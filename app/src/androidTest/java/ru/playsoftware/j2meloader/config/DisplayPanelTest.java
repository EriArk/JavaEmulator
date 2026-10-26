/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.config;

import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import org.junit.*;
import java.io.File;
import java.nio.file.Files;
import ru.playsoftware.j2meloader.input.GameMenuDialog;
import ru.playsoftware.j2meloader.settings.SettingsActivity;
import static org.junit.Assert.*;

public class DisplayPanelTest {
    private ActivityScenario<SettingsActivity> scenario;
    private GameMenuDialog menu;
    private ProfileModel profile;
    private int renders;
    @Before public void setup() {
        scenario = ActivityScenario.launch(SettingsActivity.class);
        scenario.onActivity(a -> {
            try { profile = new ProfileModel(Files.createTempDirectory(a.getCacheDir().toPath(), "display-ui-").toFile()); }
            catch (Exception e) { throw new AssertionError(e); }
            assertTrue(ProfilesManager.saveConfig(profile));
            menu = new GameMenuDialog(a, "Picture fixture"); menu.useEdgePanel(); menu.show();
        });
    }
    @After public void cleanup() {
        scenario.onActivity(a -> { menu.dismiss(); for (File f : profile.dir.listFiles()) assertTrue(f.delete()); assertTrue(profile.dir.delete()); });
        scenario.close();
    }
    private void show(boolean gpu, boolean nativePhone) {
        new DisplayPanel(menu, profile, gpu, nativePhone, () -> renders++, () -> menu.page("Game menu"),
                () -> menu.page("Advanced")).show();
    }
    private TextView find(String name) { return find(menu.getWindow().getDecorView(), name); }
    private TextView find(View view, String name) {
        if (view instanceof TextView && name.contentEquals(((TextView)view).getText())) return (TextView)view;
        if (view instanceof ViewGroup) for (int i=0;i<((ViewGroup)view).getChildCount();i++) {
            TextView found = find(((ViewGroup)view).getChildAt(i),name); if (found != null) return found;
        }
        return null;
    }
    @Test public void tilePreviewsImmediatelyAndBackRollsBack() {
        scenario.onActivity(a -> {
            show(true,false); assertNull(find("Try changes")); find("xBRZ").performClick();
            assertEquals(1,profile.getRetroShader()); assertTrue(renders > 0);
            assertEquals(0,ProfilesManager.loadConfig(profile.dir).getRetroShader());
            menu.onBackPressed(); assertEquals(0,profile.getRetroShader());
        });
    }
    @Test public void onlyKeepPersistsAndDismissUndoesNextTrial() {
        scenario.onActivity(a -> {
            show(true,false); find("CRT").performClick(); find("Keep").performClick();
            assertEquals(2,ProfilesManager.loadConfig(profile.dir).getRetroShader());
            show(true,false); find("Pixel sharp").performClick(); menu.dismiss();
            assertEquals(2,profile.getRetroShader());
        });
    }
    @Test public void interruptionAndAdvancedDiscardTemporaryPicture() {
        scenario.onActivity(a -> {
            show(true,false); find("xBRZ").performClick(); find("Performance & advanced").performClick();
            assertEquals(0,profile.getRetroShader());
            show(true,false); find("CRT").performClick(); menu.cancelPreviewPage();
            assertFalse(menu.isShowing()); assertEquals(0,profile.getRetroShader());
        });
    }
    @Test public void capabilitiesDoNotInventNativeShaderSupport() {
        scenario.onActivity(a -> {
            show(false,true); assertNull(find("FSR")); assertNull(find("xBRZ"));
            assertNull(find("Picture size")); assertNull(find("Image rotation"));
            find("LCD Classic").performClick(); assertEquals(2,profile.screenEffect);
            find("Undo").performClick(); assertEquals(0,profile.screenEffect);
            show(false,false); assertNull(find("CRT")); assertNotNull(find("Smooth"));
        });
    }
    @Test public void decisionButtonsStayOutsideScrollAndTileFocusSurvivesRefresh() {
        scenario.onActivity(a -> show(true,false));
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        scenario.onActivity(a -> find("CRT").performClick());
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        scenario.onActivity(a -> {
            assertTrue(find("CRT").hasFocus());
            for (String name : new String[]{"Keep", "Undo"}) {
                TextView button = find(name);
                for (ViewParent parent = button.getParent(); parent != null; parent = parent.getParent()) {
                    assertFalse(parent instanceof ScrollView);
                }
                android.graphics.Rect visible = new android.graphics.Rect();
                assertTrue(button.getGlobalVisibleRect(visible));
                assertEquals(button.getHeight(), visible.height());
                assertTrue(button.getHeight() >= Math.round(48 * a.getResources().getDisplayMetrics().density));
            }
        });
    }
}
