package ru.playsoftware.j2meloader.config;

import android.util.SparseIntArray;
import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import com.google.gson.annotations.SerializedName;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Iterator;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

/** Run against R8 builds too: debug alone cannot catch renamed/removed fields. */
public class ProfileSerializationTest {
    @Test public void integerPictureSizeUsesExistingProfileKeyAndUndo() throws Exception {
        ProfileModel profile=roundTrip("{\"Version\":4,\"ScreenScaleType\":3,\"ScreenScaleRatio\":75}");
        assertEquals(3,profile.screenScaleType); assertEquals(75,profile.screenScaleRatio);
        ProfilesManager.beginPreview(profile); profile.screenScaleType=1;
        ProfilesManager.undoPreview(profile); assertEquals(3,profile.screenScaleType);
    }
    @Test public void enhancementsDefaultOffAndSurviveSaveAndUndo() throws Exception {
        ProfileModel old = roundTrip("{}");
        assertFalse(old.fsrUpscaling); assertFalse(old.frameInterpolation);
        assertEquals(50, old.fsrSharpness); assertEquals(100, old.mophunCpuBudget);
        assertTrue(old.mophunCaptureEnhanced);
        ProfileModel enhanced = roundTrip("{\"FsrUpscaling\":true,\"FsrSharpness\":75,\"FrameInterpolation\":true,\"MophunCpuBudget\":200}");
        ProfilesManager.beginPreview(old); ProfilesManager.copyInto(enhanced, old);
        assertTrue(old.fsrUpscaling); assertTrue(old.frameInterpolation); assertEquals(200, old.mophunCpuBudget);
        ProfilesManager.undoPreview(old);
        assertFalse(old.fsrUpscaling); assertFalse(old.frameInterpolation);
        assertEquals(50, old.fsrSharpness); assertEquals(100, old.mophunCpuBudget);
    }
    @Test public void originalMophunCaptureSurvivesSaveAndPreviewUndo() throws Exception {
        ProfileModel profile = roundTrip("{\"MophunCaptureEnhanced\":false}");
        assertFalse(profile.mophunCaptureEnhanced);
        ProfilesManager.beginPreview(profile);
        ProfileModel draft = ProfilesManager.copy(profile); draft.mophunCaptureEnhanced = true;
        ProfilesManager.copyInto(draft, profile); assertTrue(profile.mophunCaptureEnhanced);
        ProfilesManager.undoPreview(profile); assertFalse(profile.mophunCaptureEnhanced);
    }
    @Test public void everyPersistedFieldHasAStableNameAndRemainsWritable() {
        for (Field field : ProfileModel.class.getFields()) {
            int flags = field.getModifiers();
            if (Modifier.isStatic(flags) || Modifier.isTransient(flags)) continue;
            assertNotNull("Missing JSON name: " + field, field.getAnnotation(SerializedName.class));
            assertFalse("Read-only persisted field: " + field, Modifier.isFinal(flags));
        }
    }

    @Test public void touchSettingsKeepExistingJsonKeys() throws Exception {
        String json = "{\"touchLayout\":1,\"touchSize\":2,\"touchOpacity\":73,"
                + "\"touchPortraitReach\":0.25,\"touchLandscapeReach\":-0.5}";
        ProfileModel profile = roundTrip(json);
        assertEquals(Integer.valueOf(1), profile.touchLayout);
        assertEquals(2, profile.touchSize); assertEquals(73, profile.touchOpacity);
        assertEquals(0.25f, profile.touchPortraitReach, 0f);
        assertEquals(-0.5f, profile.touchLandscapeReach, 0f);
    }

    @Test public void preparationHintsSurviveSerialization() throws Exception {
        String json = "{\"compatibilityConfidence\":\"high\",\"compatibilityReasons\":\"declared\","
                + "\"suggestedCompatibilityProfile\":\"Nokia S40\",\"detectedScreenWidth\":176,\"detectedScreenHeight\":208}";
        ProfileModel profile = roundTrip(json);
        assertEquals("high", profile.compatibilityConfidence);
        assertEquals("declared", profile.compatibilityReasons);
        assertEquals("Nokia S40", profile.suggestedCompatibilityProfile);
        assertEquals(176, profile.detectedScreenWidth); assertEquals(208, profile.detectedScreenHeight);
    }

    @Test public void mappingListKeepsItsConcreteTypeAndDoesNotAlias() {
        ProfileModel original = new ProfileModel();
        SparseIntArray mappings = new SparseIntArray(); mappings.put(19, 50);
        original.keyMappingProfiles = new ArrayList<>();
        original.keyMappingProfiles.add(new ProfileModel.KeyMappingProfile("Custom", mappings));
        ProfileModel copy = ProfilesManager.copy(original);
        assertEquals("Custom", copy.keyMappingProfiles.get(0).name);
        assertEquals(50, copy.keyMappingProfiles.get(0).mappings.get(19));
        copy.keyMappingProfiles.get(0).mappings.put(19, 56);
        assertEquals(50, original.keyMappingProfiles.get(0).mappings.get(19));
    }

    @Test public void copyIntoKeepsRuntimeIdentityWhileCopyingTouchAndHints() {
        ProfileModel source = new ProfileModel();
        source.touchOpacity = 61; source.touchSize = 2; source.detectedScreenWidth = 176;
        ProfileModel target = new ProfileModel();
        File dir = new File("runtime-only"); target.dir = dir;
        ProfilesManager.beginPreview(target);
        ProfileModel baseline = target.previewBaseline;
        ProfilesManager.copyInto(source, target);
        assertSame(dir, target.dir); assertSame(baseline, target.previewBaseline);
        assertEquals(61, target.touchOpacity); assertEquals(2, target.touchSize);
        assertEquals(176, target.detectedScreenWidth);
        assertEquals(100, baseline.touchOpacity);
    }

    private ProfileModel roundTrip(String json) throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File dir = Files.createTempDirectory(context.getCacheDir().toPath(), "profile-json-").toFile();
        File file = new File(dir, "config.json");
        try {
            Files.write(file.toPath(), json.getBytes(StandardCharsets.UTF_8));
            ProfileModel profile = ProfilesManager.loadConfig(dir);
            assertNotNull(profile);
            assertTrue(ProfilesManager.saveConfig(profile));
            JSONObject expected = new JSONObject(json);
            JSONObject actual = new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            Iterator<String> keys = expected.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                assertEquals(key, expected.get(key), actual.get(key));
            }
            return profile;
        } finally {
            for (File child : dir.listFiles()) assertTrue(child.delete());
            assertTrue(dir.delete());
        }
    }
}
