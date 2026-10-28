package ru.playsoftware.j2meloader.config;

import androidx.test.core.app.ApplicationProvider;
import android.content.Context;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import java.io.File;
import java.nio.file.Files;
import static org.junit.Assert.*;

public class SettingsTrialTest {
    private File dir;
    private ProfileModel model;
    @Before public void setup() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        dir = Files.createTempDirectory(context.getCacheDir().toPath(), "settings-trial-").toFile();
        model = new ProfileModel(dir);
        assertTrue(ProfilesManager.saveConfig(model));
    }
    @After public void cleanup() { for (File f : dir.listFiles()) assertTrue(f.delete()); assertTrue(dir.delete()); }

    @Test public void previewNeverAutosavesAndUndoRestoresAllSettings() {
        String properties = model.systemProperties;
        ProfilesManager.beginPreview(model);
        model.screenWidth = 128; model.screenHeight = 160; model.systemProperties = "test";
        model.touchSize = 2; model.orientation = 3;
        assertTrue(ProfilesManager.saveConfig(model));
        assertEquals(240, ProfilesManager.loadConfig(dir).screenWidth);
        ProfilesManager.undoPreview(model);
        assertEquals(240, model.screenWidth); assertEquals(properties, model.systemProperties);
        assertEquals(0, model.touchSize); assertFalse(ProfilesManager.isPreview(model));
    }
    @Test public void repeatedPreviewKeepsOriginalBaseline() {
        ProfilesManager.beginPreview(model); model.screenWidth = 128;
        ProfilesManager.beginPreview(model); model.screenWidth = 176;
        ProfilesManager.undoPreview(model); assertEquals(240, model.screenWidth);
    }

    @Test public void touchThemeCanBeTriedUndoneAndKeptPerGame() {
        ProfilesManager.beginPreview(model); model.touchTheme = 1;
        assertFalse(ProfilesManager.previewNeedsRestart(model));
        assertTrue(ProfilesManager.saveConfig(model));
        assertEquals(0, ProfilesManager.loadConfig(dir).touchTheme);
        ProfilesManager.undoPreview(model); assertEquals(0, model.touchTheme);
        ProfilesManager.beginPreview(model); model.touchTheme = 1;
        assertTrue(ProfilesManager.keepPreview(model));
        assertEquals(1, ProfilesManager.loadConfig(dir).touchTheme);
        ProfilesManager.beginPreview(model); model.touchTheme = 2;
        ProfilesManager.undoPreview(model); assertEquals(1, model.touchTheme);
        assertEquals(0, new ProfileModel().touchTheme);
    }

    @Test public void touchMappingsKeepUndoRestartAndInterruptedTrialAreIndependent() throws Exception {
        ProfileModel draft = ProfilesManager.copy(model);
        draft.touchGamepadMappings = ru.playsoftware.j2meloader.input.TouchMappings.template(true);
        draft.touchGamepadMappings.put("down_left", 55);
        SettingsTrial.stage(draft); SettingsTrial.claim(model);
        assertEquals(Integer.valueOf(55), model.touchGamepadMappings.get("down_left"));
        assertFalse(ProfilesManager.previewNeedsRestart(model));
        assertTrue(ProfilesManager.saveConfig(model));
        assertNull(ProfilesManager.loadConfig(dir).touchGamepadMappings);
        ProfileModel interrupted = ProfilesManager.loadConfig(dir); SettingsTrial.claim(interrupted);
        assertNull(interrupted.touchGamepadMappings);
        assertTrue(ProfilesManager.keepPreview(model));
        ProfileModel restarted = ProfilesManager.loadConfig(dir);
        assertEquals(Integer.valueOf(50), restarted.touchGamepadMappings.get("up"));
        assertEquals(Integer.valueOf(55), restarted.touchGamepadMappings.get("down_left"));
        ProfilesManager.beginPreview(restarted); restarted.touchGamepadMappings.clear();
        ProfilesManager.undoPreview(restarted);
        assertEquals(Integer.valueOf(55), restarted.touchGamepadMappings.get("down_left"));
    }

    @Test public void frameLimitKeepUndoAndRelaunchRetainConfirmedValue() {
        int original = model.fpsLimit;
        ProfilesManager.beginPreview(model); model.fpsLimit = 20;
        assertTrue(ProfilesManager.saveConfig(model));
        assertEquals(original, ProfilesManager.loadConfig(dir).fpsLimit);
        ProfilesManager.undoPreview(model); assertEquals(original, model.fpsLimit);
        ProfilesManager.beginPreview(model); model.fpsLimit = 30;
        assertTrue(ProfilesManager.keepPreview(model));
        assertEquals(30, ProfilesManager.loadConfig(dir).fpsLimit);
        ProfilesManager.beginPreview(model); model.fpsLimit = 0;
        ProfilesManager.undoPreview(model); assertEquals(30, model.fpsLimit);
    }
    @Test public void keepCommitsAndNextPreviewUsesNewBaseline() {
        ProfilesManager.beginPreview(model); model.screenWidth = 128;
        assertTrue(ProfilesManager.keepPreview(model));
        assertEquals(128, ProfilesManager.loadConfig(dir).screenWidth);
        ProfilesManager.beginPreview(model); model.screenWidth = 176;
        ProfilesManager.undoPreview(model); assertEquals(128, model.screenWidth);
    }

    @Test public void speedPreviewCanBeUndoneKeptAndTransferredToLaunch() throws Exception {
        ProfileModel draft = ProfilesManager.copy(model); draft.emulationSpeed = 50;
        SettingsTrial.stage(draft); SettingsTrial.claim(model);
        assertEquals(50, model.getEmulationSpeed());
        assertEquals(100, ProfilesManager.loadConfig(dir).getEmulationSpeed());
        assertFalse(ProfilesManager.previewNeedsRestart(model));
        ProfilesManager.undoPreview(model); assertEquals(100, model.getEmulationSpeed());
        ProfilesManager.beginPreview(model); model.emulationSpeed = 200;
        assertTrue(ProfilesManager.keepPreview(model));
        assertEquals(200, ProfilesManager.loadConfig(dir).getEmulationSpeed());
        ProfilesManager.beginPreview(model); model.emulationSpeed = 75;
        ProfilesManager.undoPreview(model); assertEquals(200, model.getEmulationSpeed());
        assertEquals(100, new ProfileModel().getEmulationSpeed());
    }

    @Test public void nativeFrameLimitDefaultUnlimitedAndLaunchDraftRemainDistinct() throws Exception {
        assertNull(ProfilesManager.loadConfig(dir).nativeFpsLimit);
        assertEquals(60, ProfilesManager.loadConfig(dir).getNativeFpsLimit());
        ProfileModel draft = ProfilesManager.copy(model); draft.nativeFpsLimit = 15;
        SettingsTrial.stage(draft); SettingsTrial.claim(model);
        assertEquals(15, model.getNativeFpsLimit());
        assertEquals(60, ProfilesManager.loadConfig(dir).getNativeFpsLimit());
        ProfilesManager.undoPreview(model); assertNull(model.nativeFpsLimit);
        ProfilesManager.beginPreview(model); model.nativeFpsLimit = 0;
        assertFalse(ProfilesManager.previewNeedsRestart(model));
        assertTrue(ProfilesManager.keepPreview(model));
        ProfileModel restarted = ProfilesManager.loadConfig(dir);
        assertEquals(0, restarted.getNativeFpsLimit());
        ProfilesManager.beginPreview(restarted); restarted.nativeFpsLimit = 30;
        ProfilesManager.undoPreview(restarted); assertEquals(0, restarted.getNativeFpsLimit());
        assertEquals(60, new ProfileModel().getNativeFpsLimit());
    }
    @Test public void launchDraftIsConsumedOnceAndCrashKeepsConfirmedProfile() throws Exception {
        ProfileModel draft = ProfilesManager.copy(model); draft.screenWidth = 128;
        SettingsTrial.stage(draft);
        assertEquals(240, ProfilesManager.loadConfig(dir).screenWidth);
        SettingsTrial.claim(model);
        assertEquals(128, model.screenWidth); assertTrue(ProfilesManager.isPreview(model));
        ProfileModel nextProcess = ProfilesManager.loadConfig(dir);
        SettingsTrial.claim(nextProcess);
        assertEquals(240, nextProcess.screenWidth); assertFalse(ProfilesManager.isPreview(nextProcess));
    }
    @Test public void draftCopyDoesNotAliasMappings() {
        model.ensureCustomKeyMappingProfile();
        ProfileModel copy = ProfilesManager.copy(model);
        copy.keyMappingProfiles.get(copy.activeKeyMappingProfile).name = "Draft only";
        assertNotEquals("Draft only", model.keyMappingProfiles.get(model.activeKeyMappingProfile).name);
    }
    @Test public void failedKeepRetainsUndo() {
        ProfilesManager.beginPreview(model); model.screenWidth = 128;
        File actual = model.dir; model.dir = new File(dir, "missing");
        assertFalse(ProfilesManager.keepPreview(model)); assertTrue(ProfilesManager.isPreview(model));
        model.dir = actual; ProfilesManager.undoPreview(model); assertEquals(240, model.screenWidth);
    }
    @Test public void malformedDraftLeavesConfirmedConfigAlone() throws Exception {
        Files.write(new File(dir, "settings-trial.json").toPath(), "bad json".getBytes());
        try { SettingsTrial.claim(model); fail(); } catch (java.io.IOException expected) { }
        assertEquals(240, model.screenWidth); assertFalse(ProfilesManager.isPreview(model));
        assertFalse(new File(dir, "settings-trial.json").exists());
    }
    @Test public void onlyEngineChangesNeedRestartForUndo() {
        ProfilesManager.beginPreview(model);
        model.screenWidth = 176; model.screenRotation = 180; model.touchLayout = 1;
        assertFalse(ProfilesManager.previewNeedsRestart(model));
        model.graphicsMode++;
        assertTrue(ProfilesManager.previewNeedsRestart(model));
        ProfilesManager.undoPreview(model);
        assertFalse(ProfilesManager.previewNeedsRestart(model));
        ProfilesManager.beginPreview(model); model.systemProperties = "different";
        assertTrue(ProfilesManager.previewNeedsRestart(model));
    }
    @Test public void trialsNeverTouchSiblingSaveFiles() throws Exception {
        File save = new File(dir, "test-save.rms");
        byte[] contents = new byte[]{0, 3, -1, 7};
        Files.write(save.toPath(), contents);
        ProfileModel draft = ProfilesManager.copy(model); draft.screenRotation = 180;
        SettingsTrial.stage(draft); SettingsTrial.claim(model); ProfilesManager.undoPreview(model);
        SettingsTrial.stage(draft); SettingsTrial.claim(model); assertTrue(ProfilesManager.keepPreview(model));
        assertArrayEquals(contents, Files.readAllBytes(save.toPath()));
    }
    @Test public void interruptedAtomicWriteRecoversConfirmedProfile() throws Exception {
        File config = new File(dir, Config.MIDLET_CONFIG_FILE);
        assertTrue(config.renameTo(new File(config.getPath() + ".bak")));
        Files.write(config.toPath(), "broken".getBytes());
        assertEquals(240, ProfilesManager.loadConfig(dir).screenWidth);
    }

    @Test public void phonePresetsRemovePreviousVendorButKeepCustomPropertiesAndControls() throws Exception {
        model.systemProperties = "microedition.profiles: MIDP-1.0\nmicroedition.configuration: CLDC-1.0\n"
                + "microedition.encoding: UTF-8\nmy.game.option: enabled\n";
        model.screenWidth = 176; model.screenHeight = 220; model.touchSize = 2;
        ProfileModel draft = ProfilesManager.copy(model);
        GamePresets.phone(draft, "Nokia S40");
        assertTrue(draft.systemProperties.contains("com.nokia.mid.ui.softnotification"));
        GamePresets.phone(draft, "Sony Ericsson JP-8");
        assertFalse(draft.systemProperties.contains("com.nokia."));
        assertTrue(draft.systemProperties.contains("com.sonyericsson.java.platform: JP-8"));
        assertTrue(draft.systemProperties.contains("MIDP-1.0"));
        assertTrue(draft.systemProperties.contains("CLDC-1.0"));
        assertTrue(draft.systemProperties.contains("microedition.encoding: UTF-8"));
        assertTrue(draft.systemProperties.contains("my.game.option: enabled"));
        assertEquals(176, draft.screenWidth); assertEquals(220, draft.screenHeight); assertEquals(2, draft.touchSize);
        SettingsTrial.stage(draft); SettingsTrial.claim(model);
        assertTrue(ProfilesManager.previewNeedsRestart(model));
        ProfilesManager.undoPreview(model);
        assertFalse(model.systemProperties.contains("SonyEricsson"));
        GamePresets.phone(draft, "Generic MIDP");
        assertFalse(draft.systemProperties.contains("com.sonyericsson."));
    }

    @Test public void automaticPhoneIgnoresOldWeakSuggestions() {
        model.suggestedCompatibilityProfile = "Nokia S40";
        model.compatibilityConfidence = "low";
        assertEquals("Generic MIDP", GamePresets.automaticPhone(model));
        model.compatibilityConfidence = "high";
        assertEquals("Nokia S40", GamePresets.automaticPhone(model));
    }

    @Test public void cancelledRestartDoesNotLeaveAPendingTrial() throws Exception {
        ProfileModel draft = ProfilesManager.copy(model);
        GamePresets.phone(draft, "Samsung");
        SettingsTrial.stage(draft); SettingsTrial.discard(dir); SettingsTrial.claim(model);
        assertFalse(ProfilesManager.isPreview(model));
    }
}
