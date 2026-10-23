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
    private static Object invoke(Object target,String name) {
        try { Method m=target.getClass().getDeclaredMethod(name); m.setAccessible(true); return m.invoke(target); }
        catch(Exception error) { throw new AssertionError(error); }
    }
    private void capture(String name) throws Exception {
        Bitmap image=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(image);
        File file=new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null),name+".png");
        try(FileOutputStream output=new FileOutputStream(file)) { assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,output)); }
        image.recycle();
    }
    private static android.view.View find(android.view.View view,String label) {
        if(label.contentEquals(view.getContentDescription()==null?"":view.getContentDescription())
                || view instanceof android.widget.TextView && label.contentEquals(((android.widget.TextView)view).getText())) return view;
        if(view instanceof android.view.ViewGroup) for(int i=0;i<((android.view.ViewGroup)view).getChildCount();i++) {
            android.view.View result=find(((android.view.ViewGroup)view).getChildAt(i),label); if(result!=null) return result;
        }
        return null;
    }
    private static android.view.View menuRoot(Object activity) {
        return ((android.app.Dialog)field(activity,"menu")).getWindow().getDecorView();
    }
    @Test public void imagePresetsApplyThroughMenuAndKeepMotionAndCpuSeparate() throws Exception {
        String id=InstrumentationRegistry.getArguments().getString("mophunGame"); Assume.assumeNotNull(id);
        Intent intent=new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),MophunGameActivity.class);
        intent.putExtra("id",id);
        try(ActivityScenario<MophunGameActivity> scenario=ActivityScenario.launch(intent)) {
            try {
                Thread.sleep(1500);
                scenario.onActivity(activity -> {
                    ProfileModel profile=(ProfileModel)field(activity,"profile"); ProfilesManager.beginPreview(profile);
                    profile.screenRotation=0; profile.screenEffect=0; profile.screenScaleType=1;
                    profile.fsrUpscaling=false; profile.frameInterpolation=false; profile.screenFilter=false;
                    profile.controlMode=1; profile.touchVisibility=1; profile.touchLayout=0; profile.orientation=2;
                    invoke(activity,"applyPreview");
                });
                for(int preset=0;preset<3;preset++) {
                    final int mode=preset;
                    scenario.onActivity(activity -> {
                        invoke(activity,"showMenu");
                        ((ru.playsoftware.j2meloader.config.GameSettingsPages)invoke(activity,"settingsPages")).enhancements();
                        android.view.View label=find(menuRoot(activity),"Image preset");
                        android.view.ViewGroup row=(android.view.ViewGroup)label.getParent();
                        ((android.widget.Spinner)row.getChildAt(1)).setSelection(mode);
                    });
                    InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                    if(preset==0) { Thread.sleep(200); capture("mophun-image-presets-menu"); }
                    scenario.onActivity(activity -> {
                        int budget=((ProfileModel)field(activity,"profile")).mophunCpuBudget;
                        find(menuRoot(activity),"Try changes").performClick();
                        ProfileModel profile=(ProfileModel)field(activity,"profile");
                        assertEquals(mode,ru.playsoftware.j2meloader.config.ImagePresets.selected(profile,true));
                        assertFalse(profile.frameInterpolation); assertEquals(budget,profile.mophunCpuBudget);
                        // Screenshot automation can exceed the live 20-second trial on a loaded emulator.
                        // Freeze only the test's countdown, retaining the baseline for finally/Undo.
                        ((ru.playsoftware.j2meloader.input.SettingsPreviewBar)field(activity,"previewBar")).stop();
                    });
                    // Surface creation is asynchronous. Check readiness before a potentially slow screenshot.
                    if(mode==2) {
                        boolean[] ready={false};
                        long deadline=android.os.SystemClock.uptimeMillis()+10000;
                        do {
                            scenario.onActivity(activity -> {
                                MophunGpuView view=(MophunGpuView)field(activity,"gpuView");
                                if(view!=null) {
                                    assertEquals(false,field(view,"failed"));
                                    ready[0]=field(view,"presented")!=null;
                                }
                            });
                            if(!ready[0]) Thread.sleep(50);
                        } while(!ready[0] && android.os.SystemClock.uptimeMillis()<deadline);
                        assertTrue("Enhanced preset must present a GPU frame",ready[0]);
                    }
                    scenario.onActivity(activity -> {
                        assertNull(((MophunSession)field(activity,"session")).error());
                        MophunGpuView view=(MophunGpuView)field(activity,"gpuView");
                        if(mode==2) { assertNotNull(view); assertEquals(false,field(view,"failed")); assertNotNull(field(view,"presented")); }
                        else if(view!=null) assertEquals(false,field(view,"active"));
                    });
                    capture("mophun-image-preset-"+preset);
                }
                scenario.onActivity(activity -> {
                    ((ProfileModel)field(activity,"profile")).orientation=3; invoke(activity,"applyPreview");
                });
                // Rotation may recreate the activity; open the menu on the settled landscape instance.
                boolean[] landscape={false};
                long deadline=android.os.SystemClock.uptimeMillis()+15000;
                do {
                    InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                    scenario.onActivity(activity -> {
                        android.view.View root=activity.getWindow().getDecorView();
                        landscape[0]=root.getWidth()>root.getHeight();
                    });
                    if(!landscape[0]) Thread.sleep(100);
                } while(!landscape[0] && android.os.SystemClock.uptimeMillis()<deadline);
                assertTrue("Landscape layout must settle before opening the menu",landscape[0]);
                scenario.onActivity(activity -> {
                    invoke(activity,"showMenu");
                    ((ru.playsoftware.j2meloader.config.GameSettingsPages)invoke(activity,"settingsPages")).enhancements();
                });
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                Thread.sleep(500); capture("mophun-image-presets-landscape-menu");
            } finally {
                scenario.onActivity(activity -> {
                    if(field(activity,"menu")!=null) ((android.app.Dialog)field(activity,"menu")).dismiss();
                    ProfileModel profile=(ProfileModel)field(activity,"profile");
                    if(profile!=null && ProfilesManager.isPreview(profile)) invoke(activity,"undoPreview");
                });
            }
        }
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
