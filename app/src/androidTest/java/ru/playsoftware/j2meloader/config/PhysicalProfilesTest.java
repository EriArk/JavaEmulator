/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.config;

import android.content.Context;
import android.util.SparseIntArray;
import androidx.test.core.app.ApplicationProvider;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.*;
import static org.junit.Assert.*;

public class PhysicalProfilesTest {
    private File root;
    private final SharedProfiles.Store physical=SharedProfiles.PHYSICAL;
    @Before public void setup()throws Exception{root=Files.createTempDirectory(((Context)ApplicationProvider.getApplicationContext()).getCacheDir().toPath(),"physical-profiles-").toFile();}
    @After public void cleanup()throws Exception{remove(root);}
    private void remove(File f)throws Exception{File[] files=f.listFiles();if(files!=null)for(File c:files)remove(c);assertTrue(f.delete());}
    private ProfileModel game(String name)throws Exception{File d=new File(root,"configs/"+name);assertTrue(d.mkdirs());return new ProfileModel(d);}
    private JsonObject bindings(int right){ProfileModel p=new ProfileModel();p.ensureCustomKeyMappingProfile();SparseIntArray map=p.getActiveKeyMappings().clone();map.put(-1021,right);p.setActiveKeyMappings(map);return physical.values(p);}
    private int right(ProfileModel p){return p.getActiveKeyMappings().get(-1021);}
    @Test public void globalMappingsPropagateWithoutTouchOrGraphicsChanges()throws Exception{
        SharedProfiles.Catalog c=new SharedProfiles.Catalog();c.defaults=bindings(53);physical.write(root,c);
        ProfileModel p=game("one");assertEquals(53,right(p));p.touchSize=2;p.retroShader=2;assertTrue(ProfilesManager.saveConfig(p));
        assertEquals(0,p.physicalOverrides.size());
        c.defaults=bindings(55);physical.write(root,c);p=ProfilesManager.loadConfig(p.dir);
        assertEquals(55,right(p));assertEquals(2,p.touchSize);assertEquals(2,p.retroShader);
        assertEquals(0,p.physicalOverrides.size());
    }
    @Test public void existingMappingsStayUntilExplicitReset()throws Exception{
        ProfileModel p=game("old");physical.applyValues(p,bindings(49));p.physicalSettings=false;p.physicalSnapshot=null;p.physicalOverrides=null;
        Files.write(new File(p.dir,"config.json").toPath(),new Gson().toJson(p).getBytes(StandardCharsets.UTF_8));
        SharedProfiles.Catalog c=new SharedProfiles.Catalog();c.defaults=bindings(53);physical.write(root,c);
        p=ProfilesManager.loadConfig(p.dir);assertEquals(49,right(p));assertEquals(3,p.physicalOverrides.size());
        physical.assign(p,null,c);assertEquals(49,right(p));physical.clear(p,"KeyMappings",c);assertEquals(53,right(p));assertEquals(0,p.physicalOverrides.size());
    }
    @Test public void namedAssignmentAndGameOverrideAreAtomic()throws Exception{
        SharedProfiles.Catalog c=new SharedProfiles.Catalog();SharedProfiles.Entry e=physical.create(c,"Two sticks",bindings(53));physical.write(root,c);
        ProfileModel p=game("named");physical.assign(p,e.id,c);assertEquals(53,right(p));
        p.getActiveKeyMappings().put(-1021,57);assertTrue(ProfilesManager.saveConfig(p));assertEquals(3,p.physicalOverrides.size());
        e.values=bindings(49);physical.write(root,c);p=ProfilesManager.loadConfig(p.dir);assertEquals(57,right(p));
        physical.clear(p,null,c);assertEquals(49,right(p));assertEquals(e.id,p.physicalProfileId);
    }
    @Test public void trialDiscardAndKeepPreservePhysicalInheritance()throws Exception{
        SharedProfiles.Catalog c=new SharedProfiles.Catalog();SharedProfiles.Entry e=physical.create(c,"Pad",bindings(53));physical.write(root,c);
        ProfileModel p=game("trial");assertTrue(ProfilesManager.saveConfig(p));ProfileModel draft=ProfilesManager.copy(p);physical.assign(draft,e.id,c);
        SettingsTrial.stage(draft);SettingsTrial.claim(p);assertEquals(53,right(p));assertNull(ProfilesManager.loadConfig(p.dir).physicalProfileId);
        assertTrue(ProfilesManager.keepPreview(p));p=ProfilesManager.loadConfig(p.dir);assertEquals(e.id,p.physicalProfileId);assertEquals(0,p.physicalOverrides.size());
        draft=ProfilesManager.copy(p);draft.getActiveKeyMappings().put(-1021,57);SettingsTrial.stage(draft);SettingsTrial.claim(p);ProfilesManager.undoPreview(p);assertEquals(53,right(p));
    }
    @Test public void portableCollisionPreservesDestinationAndReusesImport()throws Exception{
        SharedProfiles.Catalog c=new SharedProfiles.Catalog();SharedProfiles.Entry e=physical.create(c,"Pad",bindings(53));physical.write(root,c);
        ProfileModel p=game("export");physical.assign(p,e.id,c);assertTrue(ProfilesManager.saveConfig(p));byte[] bytes=SharedProfiles.portableConfig(new File(p.dir,"config.json"));
        e.values=bindings(49);physical.write(root,c);
        ProfileModel one=new Gson().fromJson(new String(bytes,StandardCharsets.UTF_8),ProfileModel.class),two=new Gson().fromJson(new String(bytes,StandardCharsets.UTF_8),ProfileModel.class);
        SharedProfiles.importPortable(one,root);SharedProfiles.importPortable(two,root);assertNotEquals(e.id,one.physicalProfileId);assertEquals(one.physicalProfileId,two.physicalProfileId);
        one.sharedRoot=root;SharedProfiles.resolve(one,true);assertEquals(53,right(one));assertEquals(49,right(physical.editor(physical.read(root),e.id)));
    }
    @Test public void corruptCatalogRetainsStoredMappingAndRejectsInvalidIndex()throws Exception{
        ProfileModel p=game("saved");physical.applyValues(p,bindings(53));assertTrue(ProfilesManager.saveConfig(p));
        Files.write(new File(root,SharedProfiles.PHYSICAL_FILE).toPath(),"broken".getBytes(StandardCharsets.UTF_8));assertEquals(53,right(ProfilesManager.loadConfig(p.dir)));
        SharedProfiles.Catalog c=new SharedProfiles.Catalog();c.defaults=bindings(53);c.defaults.addProperty("ActiveKeyMappingProfile",999);
        try{physical.write(root,c);fail();}catch(IOException expected){}
    }
}
