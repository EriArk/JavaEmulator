package ru.playsoftware.j2meloader.catalog;

import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import java.io.File;
import java.io.FileOutputStream;
import ru.playsoftware.j2meloader.BuildConfig;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.settings.SettingsActivity;
import ru.playsoftware.j2meloader.settings.SettingsFragment;
import ru.playsoftware.j2meloader.ui.ConsoleSurface;
import static org.junit.Assert.*;

/** Non-destructive navigation checks. Never clears the library or account. */
public class ReleaseJourneyTest {
    @Test public void experimentalCapabilityIsIndependentOfDebugBuild() {
        assertTrue(BuildConfig.EXPERIMENTAL_MULTIPLAYER);
    }
    @Test public void settingsMaterialsAndDismissedBrightnessPreservePreference() throws Exception {
        InstrumentationRegistry.getInstrumentation().getUiAutomation();
        String[] original={null};
        try(ActivityScenario<SettingsActivity> scene=ActivityScenario.launch(SettingsActivity.class)) {
            idle();
            scene.onActivity(a->{
                assertTrue(a.findViewById(R.id.settings_content).getBackground() instanceof ConsoleSurface);
                SettingsFragment f=(SettingsFragment)a.getSupportFragmentManager().findFragmentById(R.id.settings_content);
                androidx.preference.ListPreference brightness=f.findPreference("pref_theme");
                original[0]=brightness.getValue();
                View title=find(a.getWindow().getDecorView(),"Brightness");assertNotNull(title);
                while(!title.isClickable()&&title.getParent() instanceof View)title=(View)title.getParent();
                assertTrue(title.getBackground() instanceof ConsoleSurface);
                f.onDisplayPreferenceDialog(brightness);
            });
            idle();capture("brightness.png");
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);idle();
            scene.onActivity(a->{
                SettingsFragment f=(SettingsFragment)a.getSupportFragmentManager().findFragmentById(R.id.settings_content);
                assertEquals(original[0],((androidx.preference.ListPreference)f.findPreference("pref_theme")).getValue());
            });
            capture("settings-dark.png");
            scene.onActivity(a->a.getDelegate().setLocalNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO));
            idle();capture("settings-light.png");
        }
    }
    @Test public void transferActionsRemainReachableAcrossRotation() throws Exception {
        InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try(ActivityScenario<LibraryImportActivity> scene=ActivityScenario.launch(LibraryImportActivity.class)) {
            for(int orientation:new int[]{ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}) {
                scene.onActivity(a->a.setRequestedOrientation(orientation));idle();
                scene.onActivity(a->{
                    View importAction=find(a.getWindow().getDecorView(),"Import from J2ME Loader");
                    assertNotNull(importAction);assertTrue(importAction.isEnabled());
                    assertTrue(importAction.getBackground() instanceof ConsoleSurface);
                    assertTrue(importAction.getHeight()>=48*a.getResources().getDisplayMetrics().density);
                    assertNotNull(find(a.getWindow().getDecorView(),"Move from AbyssME Phone"));
                });
                capture(orientation==ActivityInfo.SCREEN_ORIENTATION_PORTRAIT?"transfer-portrait.png":"transfer-landscape.png");
            }
            scene.recreate();idle();
            scene.onActivity(a->assertNotNull(find(a.getWindow().getDecorView(),"Import from J2ME Loader")));
        }
    }
    private void idle()throws Exception {InstrumentationRegistry.getInstrumentation().waitForIdleSync();Thread.sleep(800);}
    private void capture(String name)throws Exception {
        if(!"true".equals(InstrumentationRegistry.getArguments().getString("captureUi")))return;
        Bitmap bitmap=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(bitmap);
        File dir=new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null),"release-journey");
        assertTrue(dir.isDirectory()||dir.mkdirs());
        try(FileOutputStream out=new FileOutputStream(new File(dir,name))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}finally{bitmap.recycle();}
    }
    private View find(View view,String label){
        if(view instanceof TextView&&label.contentEquals(((TextView)view).getText()))return view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){
            View found=find(((ViewGroup)view).getChildAt(i),label);if(found!=null)return found;
        }
        return null;
    }
}
