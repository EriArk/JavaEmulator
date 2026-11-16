/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.config;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import ru.playsoftware.j2meloader.settings.SettingsActivity;
import ru.playsoftware.j2meloader.input.GameMenuDialog;
import java.io.*;
import java.nio.file.Files;
import org.junit.Test;
import static org.junit.Assert.*;

public class SharedProfileUiTest {
    private GameMenuDialog menu;
    private void main(Runnable r){InstrumentationRegistry.getInstrumentation().runOnMainSync(r);}
    private View find(View v,String text){
        if(v instanceof TextView && text.contentEquals(((TextView)v).getText()))return v;
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=find(((ViewGroup)v).getChildAt(i),text);if(found!=null)return found;}
        return null;
    }
    private View described(View view,String label){
        if(label.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View v=described(((ViewGroup)view).getChildAt(i),label);if(v!=null)return v;}
        return null;
    }
    private void press(String text){main(()->{View v=find(menu.getWindow().getDecorView(),text);assertNotNull(text,v);v.performClick();});}
    private void waitFor(String text){
        for(int i=0;i<100;i++){boolean[] found={false};main(()->found[0]=find(menu.getWindow().getDecorView(),text)!=null);if(found[0])return;android.os.SystemClock.sleep(50);}fail(text);
    }
    private void capture(String name)throws Exception{
        android.os.SystemClock.sleep(250);android.graphics.Bitmap bitmap=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(bitmap);
        Context c=ApplicationProvider.getApplicationContext();File folder=new File(c.getFilesDir(),"profile-ui-captures");folder.mkdirs();
        try(FileOutputStream out=new FileOutputStream(new File(folder,name+".png"))){assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out));}bitmap.recycle();
    }
    private void remove(File f)throws Exception{File[] children=f.listFiles();if(children!=null)for(File c:children)remove(c);assertTrue(f.delete());}
    @Test public void choosesNamedBaseClearsOverridesAndBulkAssignsTwoGames()throws Exception{
        Context c=ApplicationProvider.getApplicationContext();File root=Files.createTempDirectory(c.getCacheDir().toPath(),"shared-ui-").toFile();
        SharedProfiles.Catalog catalog=new SharedProfiles.Catalog();
        SharedProfiles.Entry pad=SharedProfiles.create(catalog,"Standard Gamepad",new com.google.gson.JsonObject());pad.values.addProperty("touchLayout",1);pad.values.addProperty("touchTheme",2);
        SharedProfiles.create(catalog,"Full Keypad",new com.google.gson.JsonObject()).values.addProperty("touchLayout",0);SharedProfiles.write(root,catalog);
        File one=new File(root,"configs/First game"),two=new File(root,"configs/Second game");assertTrue(one.mkdirs());assertTrue(two.mkdirs());
        ProfileModel game=new ProfileModel(one);game.touchTheme=0;assertTrue(ProfilesManager.saveConfig(game));assertTrue(ProfilesManager.saveConfig(new ProfileModel(two)));
        try(ActivityScenario<SettingsActivity> scenario=ActivityScenario.launch(SettingsActivity.class)){
            scenario.onActivity(activity->menu=SharedProfileDialog.show(activity,game,null));
            waitFor("Choose base profile");press("Choose base profile");press("Standard Gamepad");
            assertEquals(pad.id,game.sharedProfileId);assertEquals(2,game.touchTheme);capture("game-profile");
            press("Manage shared profiles");capture("profile-manager");press("Assign profiles to games");
            press("First game");press("Second game");press("Choose profile for selection");press("Standard Gamepad");
            press("Also reset control game overrides");press("Apply to selected games");android.os.SystemClock.sleep(250);
            capture("bulk-confirmation");
            android.view.accessibility.AccessibilityNodeInfo window=InstrumentationRegistry.getInstrumentation().getUiAutomation().getRootInActiveWindow();
            boolean confirmed=false;
            for(android.view.accessibility.AccessibilityNodeInfo node:window.findAccessibilityNodeInfosByText("Confirm"))
                if("Confirm".equalsIgnoreCase(node.getText()==null?"":node.getText().toString()))confirmed=node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK);
            assertTrue(confirmed);
            waitFor("+ New profile");
            assertEquals(pad.id,ProfilesManager.loadConfig(one).sharedProfileId);assertEquals(pad.id,ProfilesManager.loadConfig(two).sharedProfileId);
            assertEquals(Integer.valueOf(1),ProfilesManager.loadConfig(two).touchLayout);
            press("Standard Gamepad");press("Edit profile");capture("shared-editor");
        }finally{main(()->{if(menu!=null)menu.dismiss();});remove(root);}
    }
    private android.widget.Spinner spinner(View view) {
        if(view instanceof android.widget.Spinner)return (android.widget.Spinner)view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){
            android.widget.Spinner found=spinner(((ViewGroup)view).getChildAt(i));if(found!=null)return found;
        }
        return null;
    }
    @Test public void graphicsEditorSelectsShaderWithoutChangingControlProfile()throws Exception{
        Context c=ApplicationProvider.getApplicationContext();File root=Files.createTempDirectory(c.getCacheDir().toPath(),"graphics-ui-").toFile();
        SharedProfiles.Catalog catalog=new SharedProfiles.Catalog();
        SharedProfiles.Entry image=SharedProfiles.GRAPHICS.create(catalog,"My image",new com.google.gson.JsonObject());image.values.addProperty("RetroShader",2);SharedProfiles.GRAPHICS.write(root,catalog);
        File dir=new File(root,"configs/Game");assertTrue(dir.mkdirs());ProfileModel game=new ProfileModel(dir);game.sharedProfileId="independent-pad";game.touchSize=2;assertTrue(ProfilesManager.saveConfig(game));
        try(ActivityScenario<SettingsActivity> scenario=ActivityScenario.launch(SettingsActivity.class)){
            scenario.onActivity(activity->menu=SharedProfileDialog.show(activity,game,null,SharedProfiles.GRAPHICS));
            waitFor("Choose base profile");press("Choose base profile");press("My image");assertEquals(image.id,game.graphicsProfileId);assertEquals(2,game.retroShader);assertEquals("independent-pad",game.sharedProfileId);assertEquals(2,game.touchSize);
            press("Manage shared profiles");press("My image");press("Edit profile");
            main(()->{
                View rootView=menu.getWindow().getDecorView();assertNotNull(find(rootView,"Image preset"));assertNull(find(rootView,"Touch layout"));
                View label=find(rootView,"Image preset");android.widget.Spinner choice=spinner((View)label.getParent());assertNotNull(choice);choice.setSelection(3);
            });
            android.os.SystemClock.sleep(250);capture("graphics-editor");press("Save shared settings");android.os.SystemClock.sleep(250);
            android.view.accessibility.AccessibilityNodeInfo window=InstrumentationRegistry.getInstrumentation().getUiAutomation().getRootInActiveWindow();boolean confirmed=false;
            for(android.view.accessibility.AccessibilityNodeInfo node:window.findAccessibilityNodeInfosByText("Confirm"))
                if("Confirm".equalsIgnoreCase(node.getText()==null?"":node.getText().toString()))confirmed=node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK);
            assertTrue(confirmed);waitFor("+ New profile");assertEquals(1,SharedProfiles.GRAPHICS.read(root).profiles.get(0).values.get("RetroShader").getAsInt());
            assertEquals("independent-pad",game.sharedProfileId);assertEquals(2,game.touchSize);
        }finally{main(()->{if(menu!=null)menu.dismiss();});remove(root);}
    }

    @Test public void physicalEditorSavesSharedBindingsWithoutChangingOtherCategories()throws Exception{
        Context c=ApplicationProvider.getApplicationContext();File root=Files.createTempDirectory(c.getCacheDir().toPath(),"physical-ui-").toFile();
        SharedProfiles.Catalog catalog=new SharedProfiles.Catalog();
        SharedProfiles.Entry entry=SharedProfiles.PHYSICAL.create(catalog,"My controller",new com.google.gson.JsonObject());SharedProfiles.PHYSICAL.write(root,catalog);
        File dir=new File(root,"configs/Game");assertTrue(dir.mkdirs());ProfileModel game=new ProfileModel(dir);game.sharedProfileId="touch-profile";game.graphicsProfileId="image-profile";assertTrue(ProfilesManager.saveConfig(game));
        try(ActivityScenario<SettingsActivity> scenario=ActivityScenario.launch(SettingsActivity.class)){
            scenario.onActivity(activity->menu=SharedProfileDialog.show(activity,game,null,SharedProfiles.PHYSICAL));
            waitFor("Choose base profile");press("Choose base profile");press("My controller");assertEquals(entry.id,game.physicalProfileId);
            press("Manage shared profiles");press("My controller");press("Edit profile");capture("physical-profile-editor");
            press("Edit physical buttons / sticks");
            java.lang.reflect.Field editor=GameMenuDialog.class.getDeclaredField("editor");editor.setAccessible(true);
            android.app.Dialog dialog=(android.app.Dialog)editor.get(menu);assertNotNull(dialog);
            main(()->{
                View rootView=dialog.getWindow().getDecorView();View sticks=find(rootView,"Sticks");assertNotNull(sticks);sticks.performClick();
                View up=described(rootView,"Right stick Up");assertNotNull(up);up.performClick();
                View five=described(rootView,"Phone key 5");assertNotNull(five);five.performClick();
            });
            capture("physical-button-mapper");
            main(()->{View save=find(dialog.getWindow().getDecorView(),"Use mapping");assertNotNull(save);save.performClick();});
            press("Save shared settings");android.os.SystemClock.sleep(250);
            android.view.accessibility.AccessibilityNodeInfo window=InstrumentationRegistry.getInstrumentation().getUiAutomation().getRootInActiveWindow();boolean confirmed=false;
            for(android.view.accessibility.AccessibilityNodeInfo node:window.findAccessibilityNodeInfosByText("Confirm"))
                if("Confirm".equalsIgnoreCase(node.getText()==null?"":node.getText().toString()))confirmed=node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK);
            assertTrue(confirmed);waitFor("+ New profile");
            assertTrue(SharedProfiles.PHYSICAL.read(root).profiles.get(0).values.has("KeyMappingProfiles"));
            ProfileModel loaded=SharedProfiles.PHYSICAL.editor(SharedProfiles.PHYSICAL.read(root),entry.id);
            assertEquals(53,loaded.getActiveKeyMappings().get(-1021));
            assertEquals(-3,loaded.getActiveKeyMappings().get(-1003));
            assertEquals("touch-profile",game.sharedProfileId);assertEquals("image-profile",game.graphicsProfileId);
            capture("physical-profile-manager");
        }finally{main(()->{if(menu!=null)menu.dismiss();});remove(root);}
    }

}
