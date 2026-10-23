package ru.playsoftware.j2meloader.mophun;

import android.content.Intent;
import android.graphics.Bitmap;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.Assume;
import ru.playsoftware.j2meloader.config.ProfileModel;
import ru.playsoftware.j2meloader.config.ProfilesManager;
import static org.junit.Assert.*;

/** Optional maintainer-supplied installed fixture; never ships game data or changes saved settings. */
public class MophunGpuGameTest {
    private static Object field(Object target,String name) {
        try { Field f=target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target); }
        catch(Exception error) { throw new AssertionError(error); }
    }
    private static void invoke(Object target,String name) {
        try { Method m=target.getClass().getDeclaredMethod(name); m.setAccessible(true); m.invoke(target); }
        catch(Exception error) { throw new AssertionError(error); }
    }
    private void capture(String name) throws Exception {
        Bitmap image=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(image);
        File file=new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null),name+".png");
        try(FileOutputStream output=new FileOutputStream(file)) { assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,output)); }
        image.recycle();
    }
    @Test public void installedGameKeepsTouchLcdAndPreviewAcrossGpuModes() throws Exception {
        String id=InstrumentationRegistry.getArguments().getString("mophunGame"); Assume.assumeNotNull(id);
        Intent intent=new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),MophunGameActivity.class);
        intent.putExtra("id",id);
        try(ActivityScenario<MophunGameActivity> scenario=ActivityScenario.launch(intent)) {
            try {
                Thread.sleep(1500);
                scenario.onActivity(activity -> {
                    assertFalse(activity.isFinishing());
                    ProfileModel profile=(ProfileModel)field(activity,"profile");
                    assertFalse(ProfilesManager.isPreview(profile)); ProfilesManager.beginPreview(profile);
                    profile.fsrUpscaling=true; profile.frameInterpolation=true; profile.fsrSharpness=50;
                    profile.controlMode=1; profile.touchVisibility=1; profile.touchLayout=0; profile.orientation=2;
                    invoke(activity,"applyPreview");
                });
                Thread.sleep(2000);
                scenario.onActivity(activity -> {
                    MophunGpuView view=(MophunGpuView)field(activity,"gpuView");
                    assertNotNull(field(view,"presented")); assertEquals(false,field(view,"failed"));
                    MophunSession session=(MophunSession)field(activity,"session");
                    assertNull(session.error()); assertTrue(session.screen().presentationSerial()>0);
                });
                capture("mophun-gpu-phone");
                if ("true".equals(InstrumentationRegistry.getArguments().getString("mophunPlay"))) {
                    scenario.onActivity(activity -> activity.dispatchKeyEvent(new android.view.KeyEvent(
                            android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_BUTTON_A)));
                    Thread.sleep(100);
                    scenario.onActivity(activity -> activity.dispatchKeyEvent(new android.view.KeyEvent(
                            android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_BUTTON_A)));
                    Thread.sleep(2000); capture("mophun-gpu-after-confirm");
                    scenario.onActivity(activity -> {
                        assertNull(((MophunSession)field(activity,"session")).error());
                        assertEquals(false,field(field(activity,"gpuView"),"failed"));
                    });
                }
                scenario.onActivity(activity -> {
                    ProfileModel profile=(ProfileModel)field(activity,"profile");
                    profile.touchLayout=1; profile.orientation=3; profile.screenRotation=90;
                    profile.screenEffect=1;
                    invoke(activity,"applyPreview");
                });
                Thread.sleep(1500); capture("mophun-gpu-landscape-lcd");
                scenario.onActivity(activity -> {
                    assertEquals(false,field(field(activity,"gpuView"),"failed"));
                    invoke(activity,"showMenu");
                });
                Thread.sleep(300); capture("mophun-gpu-menu");
                scenario.onActivity(activity -> {
                    ((android.app.Dialog)field(activity,"menu")).dismiss();
                    ProfileModel profile=(ProfileModel)field(activity,"profile");
                    profile.fsrUpscaling=false; profile.frameInterpolation=false;
                    invoke(activity,"applyPreview");
                });
                Thread.sleep(500); capture("mophun-gpu-canvas-restored");
                scenario.onActivity(activity -> assertEquals(false,field(field(activity,"gpuView"),"active")));
            } finally {
                scenario.onActivity(activity -> {
                    ProfileModel profile=(ProfileModel)field(activity,"profile");
                    if(profile!=null && ProfilesManager.isPreview(profile)) invoke(activity,"undoPreview");
                });
            }
        }
    }
}
