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

public class SharedProfilesTest {
    private File root;
    @Before public void setup() throws Exception {root=Files.createTempDirectory(((Context)ApplicationProvider.getApplicationContext()).getCacheDir().toPath(),"shared-profile-").toFile();}
    @After public void cleanup() throws Exception {remove(root);}
    private void remove(File f)throws Exception{File[] a=f.listFiles();if(a!=null)for(File c:a)remove(c);assertTrue(f.delete());}
    private ProfileModel game(String name)throws Exception{File d=new File(root,"configs/"+name);assertTrue(d.mkdirs());ProfileModel p=new ProfileModel(d);assertEquals(root,SharedProfiles.root(p)); if(SharedProfiles.read(root).defaults.has("touchTheme"))assertEquals("Constructor must resolve global default; overrides="+p.sharedOverrides,SharedProfiles.read(root).defaults.get("touchTheme").getAsInt(),p.touchTheme);assertTrue(ProfilesManager.saveConfig(p));return p;}
    private SharedProfiles.Catalog catalog(int filter)throws Exception{SharedProfiles.Catalog c=new SharedProfiles.Catalog();c.defaults.addProperty("touchTheme",filter);SharedProfiles.write(root,c);return c;}
    @Test public void legacySettingsNeverBecomeImplicitGlobalDefaults()throws Exception{
        catalog(2);ProfileModel p=game("legacy");JsonObject raw=new Gson().toJsonTree(p).getAsJsonObject();
        for(String key:new String[]{"sharedSettings","sharedOverrides","sharedProfileId","sharedPortable"})raw.remove(key);
        raw.addProperty("touchTheme",0);raw.addProperty("touchSize",2);
        Files.write(new File(p.dir,"config.json").toPath(),raw.toString().getBytes(StandardCharsets.UTF_8));
        p=ProfilesManager.loadConfig(p.dir);assertEquals(0,p.touchTheme);assertEquals(2,p.touchSize);
        assertTrue(p.sharedOverrides.has("touchTheme"));assertTrue(ProfilesManager.saveConfig(p));
        assertEquals(0,ProfilesManager.loadConfig(p.dir).touchTheme);
    }
    @Test public void linkedEditsPreserveExceptionsAndClearReturnsToAssignedProfile()throws Exception{
        SharedProfiles.Catalog c=catalog(1);SharedProfiles.Entry first=SharedProfiles.create(c,"Gamepad",new JsonObject());first.values.addProperty("touchTheme",2);
        SharedProfiles.Entry second=SharedProfiles.create(c,"Other",new JsonObject());second.values.addProperty("touchTheme",0);SharedProfiles.write(root,c);
        ProfileModel a=game("a"),b=game("b");assertEquals(1,a.touchTheme);
        SharedProfiles.assign(a,first.id,c);SharedProfiles.assign(b,second.id,c);assertTrue(ProfilesManager.saveConfig(a));assertTrue(ProfilesManager.saveConfig(b));
        a.touchSize=2;assertTrue(ProfilesManager.saveConfig(a));first.values.addProperty("touchTheme",1);first.values.addProperty("touchSize",1);SharedProfiles.write(root,c);
        a=ProfilesManager.loadConfig(a.dir);assertEquals(1,a.touchTheme);assertEquals(2,a.touchSize);assertEquals(0,ProfilesManager.loadConfig(b.dir).touchTheme);
        SharedProfiles.clear(a,"touchSize",c);assertEquals(1,a.touchSize);assertEquals(first.name,SharedProfiles.source(a,"touchSize",c));
        SharedProfiles.assign(a,null,c);assertEquals(1,a.touchTheme);assertNull(a.sharedProfileId);
    }
    @Test public void previewUndoAndKeepDoNotMutateSharedDefinitions()throws Exception{
        SharedProfiles.Catalog c=catalog(1);ProfileModel a=game("a");ProfilesManager.beginPreview(a);a.touchTheme=2;a.touchSize=2;
        ProfilesManager.undoPreview(a);assertEquals(1,a.touchTheme);assertEquals(0,a.touchSize);assertEquals(0,a.sharedOverrides.size());
        ProfilesManager.beginPreview(a);a.touchTheme=2;assertTrue(ProfilesManager.keepPreview(a));
        assertEquals(2,ProfilesManager.loadConfig(a.dir).touchTheme);assertEquals(1,SharedProfiles.read(root).defaults.get("touchTheme").getAsInt());
    }
    @Test public void trialHandoffPreservesExplicitEditsAndInheritance()throws Exception{
        catalog(1);ProfileModel a=game("trial");ProfileModel draft=ProfilesManager.copy(a);draft.touchSize=2;
        SettingsTrial.stage(draft);ProfileModel running=ProfilesManager.loadConfig(a.dir);SettingsTrial.claim(running);
        assertEquals(2,running.touchSize);assertEquals(0,ProfilesManager.loadConfig(a.dir).touchSize);
        assertTrue(ProfilesManager.keepPreview(running));assertEquals(2,ProfilesManager.loadConfig(a.dir).touchSize);
    }
    @Test public void deletedAndMissingProfilesFallBackWithoutLosingOverrides()throws Exception{
        SharedProfiles.Catalog c=catalog(1);SharedProfiles.Entry e=SharedProfiles.create(c,"Old",new JsonObject());e.values.addProperty("touchTheme",2);SharedProfiles.write(root,c);
        ProfileModel p=game("a");SharedProfiles.assign(p,e.id,c);p.touchSize=2;assertTrue(ProfilesManager.saveConfig(p));c.profiles.clear();SharedProfiles.write(root,c);
        p=ProfilesManager.loadConfig(p.dir);assertEquals(1,p.touchTheme);assertEquals(2,p.touchSize);
    }
    @Test public void portableImportDoesNotReplaceSameIdOrSameNameAndRemainsLinked()throws Exception{
        SharedProfiles.Catalog c=catalog(0);SharedProfiles.Entry e=SharedProfiles.create(c,"Pad",new JsonObject());e.values.addProperty("touchTheme",1);SharedProfiles.write(root,c);
        ProfileModel a=game("a");SharedProfiles.assign(a,e.id,c);assertTrue(ProfilesManager.saveConfig(a));
        e.values.addProperty("touchTheme",2);SharedProfiles.write(root,c);
        byte[] bytes=SharedProfiles.portableConfig(new File(a.dir,"config.json"));
        ProfileModel imported=new Gson().fromJson(new String(bytes,StandardCharsets.UTF_8),ProfileModel.class);assertEquals(2,imported.sharedPortable.get("touchTheme").getAsInt());
        e.values.addProperty("touchTheme",1);SharedProfiles.write(root,c);
        SharedProfiles.importPortable(imported,root);String importedId=imported.sharedProfileId;assertNotEquals(e.id,importedId);
        assertEquals(1,SharedProfiles.find(SharedProfiles.read(root),e.id).values.get("touchTheme").getAsInt());
        ProfileModel other=new Gson().fromJson(new String(bytes,StandardCharsets.UTF_8),ProfileModel.class);SharedProfiles.importPortable(other,root);assertEquals(importedId,other.sharedProfileId);
    }
    @Test public void importedUnknownSettingsAndPhysicalBindingsNeverEnterProfile()throws Exception{
        SharedProfiles.Catalog c=catalog(0);c.defaults.addProperty("ScreenWidth",999);c.defaults.addProperty("ControlMode",2);c.defaults.addProperty("ActiveKeyMappingProfile",3);SharedProfiles.write(root,c);
        ProfileModel p=game("a");assertEquals(240,p.screenWidth);assertEquals(0,p.controlMode);assertEquals(0,p.activeKeyMappingProfile);
        assertFalse(SharedProfiles.read(root).defaults.has("ScreenWidth"));
    }
    @Test public void malformedCatalogRetainsSavedEffectiveSettings()throws Exception{
        catalog(2);ProfileModel p=game("a");Files.write(new File(root,SharedProfiles.FILE).toPath(),"broken".getBytes(StandardCharsets.UTF_8));
        assertEquals(2,ProfilesManager.loadConfig(p.dir).touchTheme);
        try{SharedProfiles.read(root);fail();}catch(IOException expected){}
    }
    @Test public void nestedDisplayKeepPreservesControlTrialAndLinkedLcdOverride()throws Exception{
        catalog(1);SharedProfiles.Catalog graphics=new SharedProfiles.Catalog();graphics.defaults.addProperty("ScreenEffect",1);SharedProfiles.GRAPHICS.write(root,graphics);
        ProfileModel p=game("a");ProfilesManager.beginPreview(p);p.touchSize=2;
        DisplayPreview preview=new DisplayPreview(p,()->{});ProfileModel draft=preview.draft();draft.screenEffect=2;preview.apply(draft);assertTrue(preview.keep());
        assertEquals(2,ProfilesManager.loadConfig(p.dir).screenEffect);assertEquals(0,ProfilesManager.loadConfig(p.dir).touchSize);
        ProfilesManager.undoPreview(p);assertEquals(2,p.screenEffect);assertEquals(0,p.touchSize);
    }
    @Test public void distinctNamedProfilesWithEqualValuesKeepSeparateIdentitiesOnImport()throws Exception{
        SharedProfiles.Catalog source=new SharedProfiles.Catalog();
        SharedProfiles.Entry a=SharedProfiles.create(source,"First",new JsonObject()),b=SharedProfiles.create(source,"Second",new JsonObject());
        SharedProfiles.importCatalog(source,root);SharedProfiles.Catalog restored=SharedProfiles.read(root);
        assertEquals(2,restored.profiles.size());assertNotNull(SharedProfiles.find(restored,a.id));assertNotNull(SharedProfiles.find(restored,b.id));
        SharedProfiles.importCatalog(source,root);assertEquals(2,SharedProfiles.read(root).profiles.size());
    }
    @Test public void recreatedDraftCapturesChangesMadeAfterRestoration()throws Exception{
        catalog(1);ProfileModel original=game("a");ProfileModel recreated=new Gson().fromJson(new Gson().toJson(original),ProfileModel.class);recreated.dir=original.dir;
        recreated.touchSize=2;assertTrue(ProfilesManager.saveConfig(recreated));assertEquals(2,ProfilesManager.loadConfig(original.dir).touchSize);
    }

}
