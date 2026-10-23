package ru.playsoftware.j2meloader.config;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class ImagePresetsTest {
    @Test public void retroPresetsAreStandaloneAndUndoRestoresEverything() {
        ProfileModel profile=new ProfileModel();
        profile.frameInterpolation=true; profile.fsrUpscaling=true; profile.screenFilter=true;
        profile.screenEffect=2; profile.mophunCpuBudget=200; profile.emulationSpeed=150;
        profile.screenRotation=90; profile.screenScaleType=3;
        String baseline=new Gson().toJson(profile);
        for(int preset=ImagePresets.XBRZ;preset<=ImagePresets.CRT_EASY;preset++) {
            ProfilesManager.beginPreview(profile);
            ImagePresets.apply(profile,preset,true);
            assertEquals(preset,ImagePresets.selected(profile,true));
            assertFalse(profile.frameInterpolation); assertFalse(profile.fsrUpscaling);
            assertFalse(profile.screenFilter); assertEquals(0,profile.screenEffect);
            assertEquals(200,profile.mophunCpuBudget); assertEquals(150,profile.emulationSpeed);
            assertEquals(90,profile.screenRotation); assertEquals(3,profile.screenScaleType);
            assertEquals(2,ImagePresets.selected(profile,false));
            ProfilesManager.undoPreview(profile);
            assertEquals(baseline,new Gson().toJson(profile));
        }
        assertEquals(0,new Gson().fromJson("{}",ProfileModel.class).getRetroShader());
        profile.retroShader=999; assertEquals(0,profile.getRetroShader());
        profile.retroShader=1; ImagePresets.apply(profile,ImagePresets.SMOOTH,true);
        assertEquals(0,profile.getRetroShader());
    }
    @Test public void presetsOnlyChangeSpatialFiltering() {
        ProfileModel profile=new ProfileModel();
        profile.frameInterpolation=true; profile.mophunCpuBudget=200; profile.emulationSpeed=150;
        profile.screenScaleType=2; profile.screenRotation=90; profile.screenEffect=2;
        profile.fsrSharpness=77; profile.screenFilter=true;
        JsonObject original=new Gson().toJsonTree(profile).getAsJsonObject();
        for(int preset=0;preset<3;preset++) {
            ImagePresets.apply(profile,preset,true);
            assertEquals(preset,ImagePresets.selected(profile,true));
            JsonObject current=new Gson().toJsonTree(profile).getAsJsonObject();
            for(String key:new String[]{"FsrUpscaling","FsrSharpness","ScreenFilter"}) {
                original.remove(key); current.remove(key);
            }
            assertEquals(original,current);
        }
    }
    @Test public void customValuesAreDerivedNotOverwrittenAndUndoIsExact() {
        ProfileModel profile=new ProfileModel();
        profile.fsrUpscaling=true; profile.fsrSharpness=73; profile.screenFilter=false;
        assertEquals(ImagePresets.CUSTOM,ImagePresets.selected(profile,true));
        ProfilesManager.beginPreview(profile);
        ImagePresets.apply(profile,ImagePresets.PIXEL_SHARP,true);
        assertFalse(profile.screenFilter); assertFalse(profile.fsrUpscaling);
        ProfilesManager.undoPreview(profile);
        assertTrue(profile.fsrUpscaling); assertEquals(73,profile.fsrSharpness);
        assertEquals(ImagePresets.CUSTOM,ImagePresets.selected(profile,true));
        ImagePresets.apply(profile,ImagePresets.CUSTOM,true);
        assertEquals(73,profile.fsrSharpness);
        ImagePresets.apply(profile,2,false);
        assertEquals(73,profile.fsrSharpness); assertTrue(profile.fsrUpscaling);
    }
    @Test public void presetsPersistThroughExistingProfileSerialization() throws Exception {
        android.content.Context context=androidx.test.core.app.ApplicationProvider.getApplicationContext();
        java.io.File dir=java.nio.file.Files.createTempDirectory(context.getCacheDir().toPath(),"preset-").toFile();
        try {
            ProfileModel profile=new ProfileModel(dir);
            for(int preset=0;preset<5;preset++) {
                ImagePresets.apply(profile,preset,true);
                assertTrue(ProfilesManager.saveConfig(profile));
                assertEquals(preset,ImagePresets.selected(ProfilesManager.loadConfig(dir),true));
            }
        } finally {
            for(java.io.File file:dir.listFiles()) assertTrue(file.delete());
            assertTrue(dir.delete());
        }
    }
}
