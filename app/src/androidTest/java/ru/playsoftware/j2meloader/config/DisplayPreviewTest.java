/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.config;

import androidx.test.core.app.ApplicationProvider;
import android.content.Context;
import org.junit.*;
import java.io.File;
import java.nio.file.Files;
import static org.junit.Assert.*;

public class DisplayPreviewTest {
    private File dir;
    private ProfileModel profile;
    private int renders;
    @Before public void setup() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        dir = Files.createTempDirectory(context.getCacheDir().toPath(), "display-preview-").toFile();
        profile = new ProfileModel(dir); profile.screenFilter = false;
        assertTrue(ProfilesManager.saveConfig(profile));
    }
    @After public void cleanup() {
        for (File f : dir.listFiles()) assertTrue(f.delete()); assertTrue(dir.delete());
    }
    private DisplayPreview preview() { return new DisplayPreview(profile, () -> renders++); }
    @Test public void liveApplyNeverSavesAndUndoRedrawsPreviousFrame() {
        DisplayPreview session = preview(); ProfileModel draft = session.draft();
        draft.screenFilter = true; draft.retroShader = 2; draft.screenScaleType = 3;
        draft.emulationSpeed = 200; draft.touchSize = 2;
        session.apply(draft); assertEquals(1, renders); assertEquals(2, profile.getRetroShader());
        assertEquals(100, profile.emulationSpeed); assertEquals(0, profile.touchSize);
        assertTrue(ProfilesManager.saveConfig(profile));
        assertEquals(0, ProfilesManager.loadConfig(dir).getRetroShader());
        session.undo(); session.undo(); assertEquals(2, renders);
        assertEquals(0, profile.getRetroShader()); assertFalse(profile.screenFilter);
        assertFalse(ProfilesManager.isPreview(profile));
    }
    @Test public void keepSavesPictureButNotOlderPendingControlTrial() {
        ProfilesManager.beginPreview(profile); profile.touchSize = 2; profile.emulationSpeed = 200;
        DisplayPreview session = preview(); ProfileModel draft = session.draft(); draft.retroShader = 1;
        session.apply(draft); assertTrue(session.keep()); session.undo();
        ProfileModel saved = ProfilesManager.loadConfig(dir);
        assertEquals(1, saved.getRetroShader()); assertEquals(0, saved.touchSize); assertEquals(100, saved.emulationSpeed);
        assertEquals(2, profile.touchSize); assertTrue(ProfilesManager.isPreview(profile));
        ProfilesManager.undoPreview(profile); assertEquals(1, profile.getRetroShader()); assertEquals(0, profile.touchSize);
    }
    @Test public void cancelNestedTrialPreservesEarlierDraft() {
        ProfilesManager.beginPreview(profile); profile.screenRotation = 90; profile.touchSize = 2;
        DisplayPreview session = preview(); ProfileModel draft = session.draft(); draft.screenRotation = 180;
        session.apply(draft); session.undo();
        assertEquals(90, profile.screenRotation); assertEquals(2, profile.touchSize);
        ProfilesManager.undoPreview(profile); assertEquals(0, profile.screenRotation); assertEquals(0, profile.touchSize);
    }
    @Test public void failedRendererRestoresProfileAndAutosaveGuard() {
        DisplayPreview session = new DisplayPreview(profile, () -> { renders++; if (renders == 1) throw new IllegalStateException("fixture"); });
        ProfileModel draft = session.draft(); draft.retroShader = 2;
        try { session.apply(draft); fail(); } catch (IllegalStateException expected) { }
        assertEquals(0, profile.getRetroShader()); assertEquals(2, renders);
        assertFalse(ProfilesManager.isPreview(profile));
    }
    @Test public void failedSaveStaysReversibleAndRepeatedKeepUsesNewBaseline() {
        DisplayPreview session = preview(); ProfileModel draft = session.draft(); draft.retroShader = 1;
        session.apply(draft);
        File unavailable = new File(dir.getParentFile(), dir.getName() + "-offline");
        assertTrue(dir.renameTo(unavailable));
        try { assertFalse(session.keep()); assertTrue(ProfilesManager.isPreview(profile)); }
        finally { assertTrue(unavailable.renameTo(dir)); }
        assertTrue(session.keep());
        draft = session.draft(); draft.retroShader = 2; session.apply(draft); session.undo();
        assertEquals(1, profile.getRetroShader()); assertEquals(1, ProfilesManager.loadConfig(dir).getRetroShader());
    }
}
