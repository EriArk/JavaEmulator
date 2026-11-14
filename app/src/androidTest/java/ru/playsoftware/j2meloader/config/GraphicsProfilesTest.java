/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.config;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.*;
import static org.junit.Assert.*;

public class GraphicsProfilesTest {
    private File root;
    private final SharedProfiles.Store graphics=SharedProfiles.GRAPHICS;
    @Before public void setup()throws Exception{root=Files.createTempDirectory(((Context)ApplicationProvider.getApplicationContext()).getCacheDir().toPath(),"graphics-profiles-").toFile();}
    @After public void cleanup()throws Exception{remove(root);}
    private void remove(File f)throws Exception{File[] files=f.listFiles();if(files!=null)for(File c:files)remove(c);assertTrue(f.delete());}
    private ProfileModel game(String name)throws Exception{File d=new File(root,"configs/"+name);assertTrue(d.mkdirs());return new ProfileModel(d);}
    private void raw(File f,Object object)throws Exception{Files.write(f.toPath(),new Gson().toJson(object).getBytes(StandardCharsets.UTF_8));}
    @Test public void independentDefaultsAssignmentsAndLinkedUpdates()throws Exception{
        SharedProfiles.Catalog controls=new SharedProfiles.Catalog();controls.defaults.addProperty("touchSize",1);
        SharedProfiles.Entry pad=SharedProfiles.create(controls,"Pad",new JsonObject());pad.values.addProperty("touchLayout",1);SharedProfiles.write(root,controls);
        SharedProfiles.Catalog images=new SharedProfiles.Catalog();images.defaults.addProperty("ScreenScaleType",3);
        SharedProfiles.Entry crt=graphics.create(images,"CRT",new JsonObject());crt.values.addProperty("RetroShader",2);graphics.write(root,images);
        ProfileModel p=game("A");assertEquals(3,p.screenScaleType);assertEquals(1,p.touchSize);
        SharedProfiles.assign(p,pad.id,controls);graphics.assign(p,crt.id,images);p.screenRotation=90;assertTrue(ProfilesManager.saveConfig(p));
        crt.values.addProperty("ScreenScaleRatio",75);graphics.write(root,images);
        p=ProfilesManager.loadConfig(p.dir);assertEquals(75,p.screenScaleRatio);assertEquals(90,p.screenRotation);assertEquals(2,p.retroShader);assertEquals(pad.id,p.sharedProfileId);
        graphics.assign(p,null,images);graphics.clear(p,null,images);assertEquals(pad.id,p.sharedProfileId);assertEquals(Integer.valueOf(1),p.touchLayout);
        graphics.assign(p,crt.id,images);SharedProfiles.assign(p,null,controls);SharedProfiles.clear(p,null,controls);assertEquals(crt.id,p.graphicsProfileId);assertEquals(2,p.retroShader);
    }
    @Test public void allGraphicsValuesRoundTripWithoutControlOrCompatibilityKeys()throws Exception{
        ProfileModel expected=game("A");expected.screenScaleType=3;expected.screenScaleRatio=75;expected.screenGravity=4;expected.orientation=3;expected.screenRotation=270;
        expected.screenBackgroundColor=0x123456;expected.screenFilter=true;expected.fsrUpscaling=true;expected.fsrSharpness=83;
        expected.frameInterpolation=true;expected.screenEffect=2;expected.screenEffectIntensity=45;expected.screenshotWithEffect=true;expected.mophunCaptureEnhanced=false;
        JsonObject values=graphics.values(expected);values.addProperty("touchLayout",1);values.addProperty("ScreenWidth",999);values.addProperty("GraphicsMode",0);values.addProperty("MophunCpuBudget",300);
        SharedProfiles.Catalog c=new SharedProfiles.Catalog();SharedProfiles.Entry e=graphics.create(c,"Custom",values);graphics.write(root,c);
        ProfileModel actual=game("B");graphics.assign(actual,e.id,c);assertTrue(ProfilesManager.saveConfig(actual));actual=ProfilesManager.loadConfig(actual.dir);
        assertEquals(graphics.values(expected),graphics.values(actual));assertEquals(240,actual.screenWidth);assertEquals(1,actual.graphicsMode);assertEquals(100,actual.mophunCpuBudget);assertEquals(Integer.valueOf(0),actual.touchLayout);
        assertFalse(SharedProfiles.values(expected).has("ScreenEffect"));assertFalse(graphics.read(root).profiles.get(0).values.has("touchLayout"));
    }
    @Test public void beta6SplitPreservesLinkedLcdAndExistingGraphics()throws Exception{
        SharedProfiles.Catalog old=new SharedProfiles.Catalog();old.version=1;old.defaults.addProperty("ScreenEffectIntensity",60);
        SharedProfiles.Entry e=new SharedProfiles.Entry();e.id="old-pad";e.name="Old Pad";e.values.addProperty("touchLayout",1);e.values.addProperty("ScreenEffect",2);old.profiles.add(e);raw(new File(root,SharedProfiles.FILE),old);
        ProfileModel p=game("legacy");p.graphicsSettings=false;p.graphicsOverrides=null;p.graphicsSnapshot=null;p.sharedSettings=true;p.sharedProfileId=e.id;p.sharedOverrides=new JsonObject();
        p.sharedOverrides.addProperty("ScreenEffectIntensity",35);p.sharedOverrides.add("touchPlacements",JsonNull.INSTANCE);p.retroShader=1;p.screenScaleRatio=75;raw(new File(p.dir,"config.json"),p);
        // Editing controls first must not discard the LCD half for unopened games.
        SharedProfiles.Catalog controls=SharedProfiles.read(root);SharedProfiles.write(root,controls);
        assertTrue(new File(root,SharedProfiles.GRAPHICS_FILE).isFile());assertFalse(SharedProfiles.read(root).profiles.get(0).values.has("ScreenEffect"));
        p=ProfilesManager.loadConfig(p.dir);assertEquals(e.id,p.graphicsProfileId);assertEquals(e.id,p.sharedProfileId);assertEquals(2,p.screenEffect);assertEquals(35,p.screenEffectIntensity);assertEquals(1,p.retroShader);assertEquals(75,p.screenScaleRatio);
        assertFalse(p.graphicsOverrides.has("ScreenEffect"));assertTrue(p.sharedOverrides.get("touchPlacements").isJsonNull());assertTrue(ProfilesManager.saveConfig(p));
        SharedProfiles.Catalog images=graphics.read(root);graphics.find(images,e.id).values.addProperty("ScreenEffect",1);graphics.write(root,images);
        p=ProfilesManager.loadConfig(p.dir);assertEquals(1,p.screenEffect);assertEquals(35,p.screenEffectIntensity);assertEquals(1,p.retroShader);
    }
    @Test public void preProfileGamesKeepAllGraphicsAsOverrides()throws Exception{
        ProfileModel p=game("old");p.graphicsSettings=false;p.sharedSettings=false;p.screenScaleType=0;p.fsrUpscaling=true;p.fsrSharpness=22;raw(new File(p.dir,"config.json"),p);
        SharedProfiles.Catalog c=new SharedProfiles.Catalog();c.defaults.addProperty("ScreenScaleType",3);c.defaults.addProperty("FsrSharpness",90);graphics.write(root,c);
        p=ProfilesManager.loadConfig(p.dir);assertEquals(0,p.screenScaleType);assertEquals(22,p.fsrSharpness);assertTrue(p.fsrUpscaling);assertEquals(graphics.FIELDS.length,p.graphicsOverrides.size());
    }
    @Test public void pictureKeepAndUndoCarryOnlyGraphicsAssignment()throws Exception{
        SharedProfiles.Catalog c=new SharedProfiles.Catalog();SharedProfiles.Entry e=graphics.create(c,"CRT",new JsonObject());e.values.addProperty("RetroShader",2);graphics.write(root,c);
        ProfileModel p=game("trial");assertTrue(ProfilesManager.saveConfig(p));ProfilesManager.beginPreview(p);p.touchSize=2;
        DisplayPreview preview=new DisplayPreview(p,()->{});ProfileModel draft=preview.draft();graphics.assign(draft,e.id,c);preview.apply(draft);preview.undo();assertNull(p.graphicsProfileId);assertEquals(2,p.touchSize);
        preview.apply(draft);assertTrue(preview.keep());ProfileModel saved=ProfilesManager.loadConfig(p.dir);assertEquals(e.id,saved.graphicsProfileId);assertEquals(2,saved.retroShader);assertEquals(0,saved.touchSize);
        ProfilesManager.undoPreview(p);assertEquals(e.id,p.graphicsProfileId);assertEquals(0,p.touchSize);
        e.values.addProperty("RetroShader",1);graphics.write(root,c);assertEquals(1,ProfilesManager.loadConfig(p.dir).retroShader);
    }
    @Test public void trialHandoffKeepsBothIndependentSelections()throws Exception{
        SharedProfiles.Catalog c=new SharedProfiles.Catalog();SharedProfiles.Entry e=graphics.create(c,"xBRZ",new JsonObject());e.values.addProperty("RetroShader",1);graphics.write(root,c);
        ProfileModel p=game("trial");assertTrue(ProfilesManager.saveConfig(p));ProfileModel draft=ProfilesManager.copy(p);graphics.assign(draft,e.id,c);draft.touchSize=2;
        SettingsTrial.stage(draft);SettingsTrial.claim(p);assertEquals(e.id,p.graphicsProfileId);assertEquals(1,p.retroShader);assertEquals(2,p.touchSize);assertNull(ProfilesManager.loadConfig(p.dir).graphicsProfileId);
        assertTrue(ProfilesManager.keepPreview(p));assertEquals(e.id,ProfilesManager.loadConfig(p.dir).graphicsProfileId);
    }
    @Test public void portableCollisionKeepsDestinationAndReusesImportedIdentity()throws Exception{
        SharedProfiles.Catalog c=new SharedProfiles.Catalog();SharedProfiles.Entry e=graphics.create(c,"CRT",new JsonObject());e.values.addProperty("RetroShader",2);graphics.write(root,c);
        ProfileModel p=game("export");graphics.assign(p,e.id,c);assertTrue(ProfilesManager.saveConfig(p));byte[] bytes=SharedProfiles.portableConfig(new File(p.dir,"config.json"));
        e.values.addProperty("RetroShader",1);graphics.write(root,c);
        ProfileModel one=new Gson().fromJson(new String(bytes,StandardCharsets.UTF_8),ProfileModel.class),two=new Gson().fromJson(new String(bytes,StandardCharsets.UTF_8),ProfileModel.class);
        SharedProfiles.importPortable(one,root);SharedProfiles.importPortable(two,root);assertNotEquals(e.id,one.graphicsProfileId);assertEquals(one.graphicsProfileId,two.graphicsProfileId);assertEquals(1,graphics.find(graphics.read(root),e.id).values.get("RetroShader").getAsInt());
        one.sharedRoot=root;SharedProfiles.resolve(one,true);assertEquals(2,one.retroShader);
    }
    @Test public void malformedGraphicsCatalogDoesNotBreakControlsOrStoredImage()throws Exception{
        ProfileModel p=game("saved");p.retroShader=2;p.touchSize=1;assertTrue(ProfilesManager.saveConfig(p));Files.write(new File(root,SharedProfiles.GRAPHICS_FILE).toPath(),"broken".getBytes(StandardCharsets.UTF_8));
        p=ProfilesManager.loadConfig(p.dir);assertEquals(2,p.retroShader);assertEquals(1,p.touchSize);
        try{graphics.read(root);fail();}catch(IOException expected){}
    }
}
