// SPDX-License-Identifier: GPL-3.0-only
package ru.playsoftware.j2meloader.catalog;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.settings.SettingsActivity;
import ru.playsoftware.j2meloader.util.AppLanguage;
import static org.junit.Assert.*;

public class AppLanguageTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private void choose(String tag) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> AppLanguage.select(context, tag));
        awaitApplied(tag);
    }
    private void awaitApplied(String tag) {
        String expected=tag.isEmpty()?AppLanguage.systemLocale(context).toLanguageTag():tag;
        long deadline=SystemClock.uptimeMillis()+7000;
        while(!expected.equals(context.getResources().getConfiguration().getLocales().get(0).toLanguageTag())
                && SystemClock.uptimeMillis()<deadline) SystemClock.sleep(50);
        assertEquals("Applied application locale",expected,
                context.getResources().getConfiguration().getLocales().get(0).toLanguageTag());
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertEquals(tag,AppLanguage.selected(context));
    }
    private Bundle probe(String method) {
        return context.getContentResolver().call(Uri.parse("content://"+context.getPackageName()+".languageProbe"),method,null,null);
    }
    private Bundle await(String key,String expected) {
        long deadline=SystemClock.uptimeMillis()+7000; Bundle result;
        do { result=probe("get"); if(expected.equals(result.getString(key)))return result; SystemClock.sleep(50); }
        while(SystemClock.uptimeMillis()<deadline);
        assertEquals(key,expected,result.getString(key));return result;
    }
    @Test public void selectionSurvivesRecreationAndPreservesSeparateProcessAndGuestLocale() {
        String original=AppLanguage.selected(context),guest=AppLanguage.systemLocale(context).toLanguageTag();
        String property=System.getProperty("microedition.locale");
        try(ActivityScenario<SettingsActivity> scenario=ActivityScenario.launch(SettingsActivity.class)) {
            choose("en");
            context.startActivity(new Intent().setClassName(context,"ru.playsoftware.j2meloader.localization.LanguageProbeActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            int starts=await("screen","Settings").getInt("starts");
            choose("es");
            Bundle spanish=await("screen","Ajustes");
            assertEquals(starts,spanish.getInt("starts"));assertEquals(guest,spanish.getString("guest"));
            assertEquals("es",spanish.getString("selected"));await("title","Ajustes");
            probe("finish");
            long deadline=SystemClock.uptimeMillis()+7000;
            while(scenario.getState()!=androidx.lifecycle.Lifecycle.State.RESUMED
                    && SystemClock.uptimeMillis()<deadline) SystemClock.sleep(50);
            assertEquals(androidx.lifecycle.Lifecycle.State.RESUMED,scenario.getState());
            scenario.onActivity(activity -> assertEquals("Ajustes",activity.getString(R.string.settings_title)));
            scenario.recreate();scenario.onActivity(activity -> assertEquals("Ajustes",activity.getString(R.string.settings_title)));
            assertEquals(property,System.getProperty("microedition.locale"));
            choose("en");await("title","Settings");assertEquals(starts,probe("get").getInt("starts"));
            choose("");assertEquals("",AppLanguage.selected(context));assertEquals(guest,AppLanguage.systemLocale(context).toLanguageTag());
        } finally { choose(original);probe("finish"); }
    }
    @Test public void runtimeActivitiesHandleLanguageWithoutRecreation() throws Exception {
        for(String name:new String[]{"javax.microedition.shell.MicroActivity","ru.playsoftware.j2meloader.mophun.MophunGameActivity"}) {
            ActivityInfo info=context.getPackageManager().getActivityInfo(new android.content.ComponentName(context,name),0);
            assertEquals(ActivityInfo.CONFIG_LOCALE|ActivityInfo.CONFIG_LAYOUT_DIRECTION,
                    info.configChanges&(ActivityInfo.CONFIG_LOCALE|ActivityInfo.CONFIG_LAYOUT_DIRECTION));
        }
        if(ru.playsoftware.j2meloader.BuildConfig.SYMBIAN_EXPERIMENT) {
            ActivityInfo info=context.getPackageManager().getActivityInfo(new android.content.ComponentName(context,"io.github.eriark.abyssme.symbian.SymbianActivity"),0);
            assertTrue((info.configChanges&ActivityInfo.CONFIG_LOCALE)!=0);
        }
    }
    @Test public void unsupportedSelectionCannotChangeStoredLanguage() {
        String before=AppLanguage.selected(context);
        try { AppLanguage.select(context,"not-a-language");fail(); } catch(IllegalArgumentException expected) { }
        assertEquals(before,AppLanguage.selected(context));
    }

    @Test public void platformAndInAppLanguageListsMatchAndStorageIsPrivate() throws Exception {
        java.util.Set<String> tags=new java.util.HashSet<>(java.util.Arrays.asList(AppLanguage.TAGS));
        tags.remove("");java.util.Set<String> declared=new java.util.HashSet<>();
        try(android.content.res.XmlResourceParser xml=context.getResources().getXml(R.xml.app_locales)) {
            while(xml.next()!=org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if(xml.getEventType()==org.xmlpull.v1.XmlPullParser.START_TAG && "locale".equals(xml.getName()))
                    declared.add(xml.getAttributeValue("http://schemas.android.com/apk/res/android","name"));
            }
        }
        assertEquals(tags,declared);
        android.content.pm.ProviderInfo provider=context.getPackageManager().resolveContentProvider(context.getPackageName()+".language",0);
        assertNotNull(provider);assertFalse(provider.exported);assertEquals(context.getPackageName(),provider.processName);
    }

    @Test public void settingsPickerChangesLanguageAndKeepsNativeNamesVisible() {
        String original=AppLanguage.selected(context);
        try(ActivityScenario<SettingsActivity> scenario=ActivityScenario.launch(SettingsActivity.class)) {
            choose("en");
            scenario.onActivity(activity -> {
                ru.playsoftware.j2meloader.settings.SettingsFragment fragment =
                        (ru.playsoftware.j2meloader.settings.SettingsFragment) activity.getSupportFragmentManager().getFragments().get(0);
                fragment.findPreference("pref_language").performClick();
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                android.view.View spanish=null;
                for(android.view.View root:android.view.inspector.WindowInspector.getGlobalWindowViews()) {
                    android.view.View match=find(root,"Español");
                    if(match!=null)spanish=match;
                }
                assertNotNull("Native language name in picker",spanish);spanish.performClick();
            });
            awaitApplied("es");
            scenario.onActivity(activity -> {
                assertEquals("Ajustes",activity.getString(R.string.settings_title));
                ru.playsoftware.j2meloader.settings.SettingsFragment fragment =
                        (ru.playsoftware.j2meloader.settings.SettingsFragment) activity.getSupportFragmentManager().getFragments().get(0);
                assertEquals("Español",fragment.findPreference("pref_language").getSummary());
                fragment.findPreference("pref_language").performClick();
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            capture("spanish-language-picker.png");
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                boolean found=false;
                for(android.view.View root:android.view.inspector.WindowInspector.getGlobalWindowViews()) {
                    android.widget.Button english=(android.widget.Button)find(root,"English");
                    if(english!=null) {
                        found=true;
                        android.widget.Button spanish=(android.widget.Button)find(root,"Español");
                        assertEquals(0,english.getCompoundDrawablesRelative()[0].getAlpha());
                        assertEquals(255,spanish.getCompoundDrawablesRelative()[0].getAlpha());
                        android.view.accessibility.AccessibilityNodeInfo info=spanish.createAccessibilityNodeInfo();
                        assertTrue(info.isCheckable());assertTrue(info.isChecked());info.recycle();
                    }
                }
                assertTrue("Always possible to return to English",found);
            });
        } finally { choose(original); }
    }

    private static android.view.View find(android.view.View view,String label) {
        if(view instanceof android.widget.Button && label.contentEquals(((android.widget.Button)view).getText()))return view;
        if(view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group=(android.view.ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++) { android.view.View found=find(group.getChildAt(i),label);if(found!=null)return found; }
        }
        return null;
    }

    @Test public void advertisedLanguagesDisplayControlsAndExitKeepIndependentActions() {
        String original=AppLanguage.selected(context);
        ru.playsoftware.j2meloader.input.GameMenuDialog[] menu={null};
        ru.playsoftware.j2meloader.config.GameSettingsPages[] pages={null};
        try(ActivityScenario<SettingsActivity> scenario=ActivityScenario.launch(SettingsActivity.class)) {
          for(String tag:AppLanguage.TAGS) {
            if(tag.isEmpty())continue;
            choose(tag);
            try {
                scenario.onActivity(activity -> {
                    menu[0]=new ru.playsoftware.j2meloader.input.GameMenuDialog(activity,"Example game / 50%");
                    menu[0].useEdgePanel();menu[0].show();
                    pages[0]=new ru.playsoftware.j2meloader.config.GameSettingsPages(menu[0],
                            new ru.playsoftware.j2meloader.config.ProfileModel(),false,
                            new ru.playsoftware.j2meloader.config.GameSettingsPages.Host() {
                                public void preview(ru.playsoftware.j2meloader.config.ProfileModel draft) { fail("Unexpected save"); }
                                public void mapping(ru.playsoftware.j2meloader.config.ProfileModel draft,Runnable back) { }
                                public void advanced() { } public void back() { }
                            });
                    pages[0].display();
                });
                capture(tag+"-display.png");
                scenario.onActivity(activity -> {
                    if(!tag.equals("en"))assertNotEquals("Display",activity.getString(R.string.game_display));
                    pages[0].controls();
                });
                capture(tag+"-controls.png");
                scenario.onActivity(activity -> {
                    if(!tag.equals("en"))assertNotEquals("Controls",activity.getString(R.string.game_controls));
                    menu[0].confirmationPage(activity.getString(R.string.game_exit_question));
                    menu[0].message(activity.getString(R.string.game_exit_warning));
                    final int[] calls={0};
                    menu[0].action(activity.getString(R.string.game_resume),0,()->calls[0]++);
                    menu[0].action(activity.getString(R.string.game_leave_without_saving),0,()->calls[0]+=10);
                    find(menu[0].getWindow().getDecorView(),activity.getString(R.string.game_resume)).performClick();
                    assertEquals(1,calls[0]);
                });
                capture(tag+"-exit-warning.png");
            } finally { scenario.onActivity(activity -> { if(menu[0]!=null)menu[0].dismiss(); }); }
          }
        } finally { choose(original); }
    }
    private void capture(String name) {
        SystemClock.sleep(350);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        android.graphics.Bitmap image=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(image);
        java.io.File directory=new java.io.File(context.getExternalFilesDir(null),"localization-languages");
        assertTrue(directory.isDirectory()||directory.mkdirs());
        try(java.io.FileOutputStream output=new java.io.FileOutputStream(new java.io.File(directory,name))) {
            assertTrue(image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output));
        } catch(java.io.IOException error) { throw new AssertionError(error); }
        finally {image.recycle();}
    }
}
