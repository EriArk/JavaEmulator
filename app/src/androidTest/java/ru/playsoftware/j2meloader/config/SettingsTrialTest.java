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
    @Test public void keepCommitsAndNextPreviewUsesNewBaseline() {
        ProfilesManager.beginPreview(model); model.screenWidth = 128;
        assertTrue(ProfilesManager.keepPreview(model));
        assertEquals(128, ProfilesManager.loadConfig(dir).screenWidth);
        ProfilesManager.beginPreview(model); model.screenWidth = 176;
        ProfilesManager.undoPreview(model); assertEquals(128, model.screenWidth);
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
}
