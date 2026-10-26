/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.symbian;

import android.graphics.Bitmap;
import android.graphics.RectF;
import android.view.KeyEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.IOException;
import java.util.UUID;
import org.junit.Test;
import ru.playsoftware.j2meloader.config.*;
import ru.playsoftware.j2meloader.input.TouchDeck;
import ru.playsoftware.j2meloader.settings.SettingsActivity;
import ru.playsoftware.j2meloader.util.FileUtils;
import static org.junit.Assert.*;

public final class SymbianGameSettingsTest {
    private final File files = InstrumentationRegistry.getInstrumentation().getTargetContext().getFilesDir();
    private String identity() { String id = UUID.randomUUID().toString().replace("-", ""); return id + id; }

    @Test public void identityValidationPreventsEscapingThePhoneProfileStore() throws Exception {
        for (String id : new String[]{null, "", "../elsewhere", "LEGACY", "a"}) {
            try { SymbianGameSettings.load(files, id, 1); fail("Accepted invalid phone identity"); }
            catch (IOException expected) { }
        }
        for (long uid : new long[]{0, -1, 0x100000000L}) {
            try { SymbianGameSettings.load(files, "legacy", uid); fail("Accepted invalid UID"); }
            catch (IOException expected) { }
        }
    }

    @Test public void openingSettingsDoesNotSaveAndPreservesNativeDefaults() throws Exception {
        String system = identity();
        try {
            ProfileModel draft = SymbianGameSettings.load(files, system, 0xffffffffL);
            assertNull(ProfilesManager.loadConfig(draft.dir));
            assertEquals("4294967295", draft.dir.getName());
            assertEquals(TouchDeck.PHONE, draft.touchLayout.intValue());
            assertEquals('5', draft.getActiveKeyMappings().get(KeyEvent.KEYCODE_BUTTON_X));
            assertEquals(-7, draft.getActiveKeyMappings().get(KeyEvent.KEYCODE_BUTTON_B));
            draft.screenRotation = 180;
            assertEquals(0, SymbianGameSettings.load(files, system, 0xffffffffL).screenRotation);
        } finally { FileUtils.deleteDirectory(PhoneBackup.profiles(files, system)); }
    }

    @Test public void libraryTrialReachesRuntimeAndKeepUndoAreIsolatedByPhoneAndGame() throws Exception {
        String system = identity(), other = identity();
        SymbianSessionUi[] session = {null};
        int[] scale = {-1};
        SymbianSessionUi.Host host = new SymbianSessionUi.Host() {
            public RectF touchBounds() { return null; }
            public void touch(int x, int y, int pressure, int action, int pointer) { }
            public void key(int scan, boolean down) { }
            public void pause(boolean paused) { }
            public void display(int value) { scale[0] = value; }
            public void frameRate(int fps) { }
            public void leave() { }
            public void restart() { }
            public Bitmap capture() { return null; }
        };
        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            for (String decision : new String[]{"Undo", "Keep"}) {
                ProfileModel baseline = SymbianGameSettings.load(files, system, 1);
                ProfileModel draft = ProfilesManager.copy(baseline);
                draft.screenRotation = 180; draft.screenScaleType = 2; draft.touchLayout = TouchDeck.GAMEPAD;
                draft.nativeFpsLimit = 30;
                SettingsTrial.stage(draft);
                // Reading from the library must not consume another process's pending trial.
                assertEquals(0, SymbianGameSettings.load(files, system, 1).screenRotation);
                scenario.onActivity(activity -> {
                    try {
                        session[0] = new SymbianSessionUi(activity, new TextureView(activity), system, 1, "Trial fixture", host);
                        activity.setContentView(session[0].root);
                        assertTrue(ProfilesManager.isPreview(session[0].profile));
                        assertEquals(180, session[0].profile.screenRotation); assertEquals(2, scale[0]);
                        assertNotNull(find(session[0].root, decision));
                        find(session[0].root, decision).performClick();
                        assertFalse(ProfilesManager.isPreview(session[0].profile));
                        assertEquals(decision.equals("Keep") ? 180 : 0, session[0].profile.screenRotation);
                        session[0].close(); session[0] = null;
                    } catch (IOException error) { throw new AssertionError(error); }
                });
                ProfileModel saved = SymbianGameSettings.load(files, system, 1);
                SettingsTrial.claim(saved);
                assertFalse("Trial is consumed only once", ProfilesManager.isPreview(saved));
                assertEquals(decision.equals("Keep") ? 180 : 0, saved.screenRotation);
                assertEquals(0, SymbianGameSettings.load(files, other, 1).screenRotation);
                assertEquals(0, SymbianGameSettings.load(files, system, 2).screenRotation);
            }
            ProfileModel unconfirmed = SymbianGameSettings.load(files, system, 1);
            unconfirmed.screenRotation = 90; SettingsTrial.stage(unconfirmed);
            scenario.onActivity(activity -> {
                try {
                    session[0] = new SymbianSessionUi(activity, new TextureView(activity), system, 1, "Abandoned trial", host);
                    session[0].close(); session[0] = null;
                } catch (IOException error) { throw new AssertionError(error); }
            });
            assertEquals("Closing without Keep preserves confirmed settings", 180,
                    SymbianGameSettings.load(files, system, 1).screenRotation);
        } finally {
            if (session[0] != null) InstrumentationRegistry.getInstrumentation().runOnMainSync(session[0]::close);
            FileUtils.deleteDirectory(PhoneBackup.profiles(files, system));
            FileUtils.deleteDirectory(PhoneBackup.profiles(files, other));
        }
    }

    private View find(View view, String description) {
        if (description.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription())) return view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)view).getChildCount(); i++) {
            View found = find(((ViewGroup)view).getChildAt(i), description);
            if (found != null) return found;
        }
        return null;
    }
}
