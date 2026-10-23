package ru.playsoftware.j2meloader.config;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class ImagePresetsTest {
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
            for(int preset=0;preset<3;preset++) {
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
