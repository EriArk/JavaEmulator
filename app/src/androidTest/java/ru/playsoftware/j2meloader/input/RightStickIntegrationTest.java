/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.input;

import android.content.Context;
import android.os.SystemClock;
import android.util.SparseIntArray;
import android.view.ContextThemeWrapper;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.keyboard.KeyMapper;
import org.junit.Test;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.config.ProfileModel;
import static org.junit.Assert.*;

public class RightStickIntegrationTest {
    private Context context() { return new ContextThemeWrapper(InstrumentationRegistry.getInstrumentation().getTargetContext(), R.style.AppTheme); }
    private void main(Runnable r) { InstrumentationRegistry.getInstrumentation().runOnMainSync(r); }
    private int device() {
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice d = InputDevice.getDevice(id);
            if (d != null && d.getMotionRange(MotionEvent.AXIS_Z, InputDevice.SOURCE_JOYSTICK) != null
                    && d.getMotionRange(MotionEvent.AXIS_RZ, InputDevice.SOURCE_JOYSTICK) != null) return id;
        }
        throw new AssertionError("This suite requires a registered Z/RZ joystick fixture");
    }
    private MotionEvent motion(int device, float leftY, float rightX, float rightY) {
        MotionEvent.PointerProperties p = new MotionEvent.PointerProperties(); p.id = 0;
        MotionEvent.PointerCoords c = new MotionEvent.PointerCoords();
        c.setAxisValue(MotionEvent.AXIS_Y,leftY); c.setAxisValue(MotionEvent.AXIS_Z,rightX); c.setAxisValue(MotionEvent.AXIS_RZ,rightY);
        long t = SystemClock.uptimeMillis();
        return MotionEvent.obtain(t,t,MotionEvent.ACTION_MOVE,1,new MotionEvent.PointerProperties[]{p},new MotionEvent.PointerCoords[]{c},0,0,1,1,device,0,InputDevice.SOURCE_JOYSTICK,0);
    }
    @Test public void androidMetadataDecodesBothSticksWithoutMixingOutputs() {
        SparseIntArray map = new SparseIntArray(); map.put(ControllerInput.STICK_UP,2); map.put(ControllerInput.RIGHT_STICK_RIGHT,6);
        ArrayList<String> out = new ArrayList<>();
        ControllerInput input = new ControllerInput(map::get,new ControllerInput.Sink() {
            public void press(int k) { out.add("+"+k); } public void release(int k) { out.add("-"+k); } public void repeat(int k) { }
        });
        int id=device();
        MotionEvent e=motion(id,-1,1,0); ControllerMotion.update(input,e); e.recycle();
        assertEquals(Arrays.asList("+2","+6"),out); out.clear();
        e=motion(id,-1,0,0); ControllerMotion.update(input,e); e.recycle(); assertEquals(Arrays.asList("-6"),out);
        out.clear(); input.removeDevice(id); assertEquals(Arrays.asList("-2"),out);
    }
    @Test public void mappingsPersistAndOldPresetsRemainUnbound() {
        for (ProfileModel.KeyMappingProfile p:KeyMapper.createBuiltInProfiles())
            for(int code=-1021;code>=-1028;code--) assertEquals(0,KeyMapper.resolveMappings(p.mappings).get(code));
        ProfileModel p=new ProfileModel(); p.ensureKeyMappingProfiles();
        p.keyMappingProfiles.add(new ProfileModel.KeyMappingProfile("Dual sticks",new SparseIntArray()));
        p.activeKeyMappingProfile=p.keyMappingProfiles.size()-1;
        p.getActiveKeyMappings().put(ControllerInput.RIGHT_STICK_UP,Canvas.KEY_SOFT_LEFT);
        p.getActiveKeyMappings().put(ControllerInput.RIGHT_STICK_UR,ControllerInput.UNBOUND);
        ProfileModel restored=new Gson().fromJson(new Gson().toJson(p),ProfileModel.class);
        assertEquals(Canvas.KEY_SOFT_LEFT,restored.getActiveKeyMappings().get(ControllerInput.RIGHT_STICK_UP));
        assertEquals(ControllerInput.UNBOUND,restored.getActiveKeyMappings().get(ControllerInput.RIGHT_STICK_UR));
        assertEquals(Canvas.KEY_UP,KeyMapper.resolveMappings(restored.getActiveKeyMappings()).get(ControllerInput.STICK_UP));
        assertEquals(0,KeyMapper.resolveMappings(new ProfileModel().getActiveKeyMappings()).get(ControllerInput.RIGHT_STICK_UP));
    }
    @Test public void editorDetectsRightDirectionAndSavesOnlyItsBinding() throws Exception {
        AtomicReference<ControllerMapperView> editor=new AtomicReference<>();
        AtomicReference<SparseIntArray> saved=new AtomicReference<>();
        main(()->{
            ControllerMapperView v=new ControllerMapperView(context(),"Right stick check",KeyMapper.createBuiltInProfiles(),0,new ControllerMapperView.Listener(){
                public void cancel() { } public void save(ArrayList<ProfileModel.KeyMappingProfile> profiles,int active) { saved.set(KeyMapper.resolveMappings(profiles.get(active).mappings)); }
            }); editor.set(v);
            find(v,context().getString(R.string.mapper_detect)).performClick();
            MotionEvent e=motion(device(),0,1,-1); v.captureAxes(e); e.recycle();
        });
        Thread.sleep(200);
        main(()->{
            ControllerMapperView v=editor.get();
            String label=context().getString(R.string.controls_right_stick_direction,context().getResources().getStringArray(R.array.controls_directions)[5]);
            assertTrue(find(v,label).isSelected());
            find(v,"Phone key 3").performClick(); button(v,context().getString(R.string.mapper_save)).performClick();
            assertEquals(51,saved.get().get(ControllerInput.RIGHT_STICK_UR));
            assertEquals(Canvas.KEY_UP,saved.get().get(ControllerInput.STICK_UP));
            assertEquals(0,saved.get().get(ControllerInput.RIGHT_STICK_UP));
        });
    }
    private View find(View v,String description) {
        if(description.contentEquals(v.getContentDescription()==null?"":v.getContentDescription())) return v;
        if(v instanceof ViewGroup) for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=find(((ViewGroup)v).getChildAt(i),description);if(found!=null)return found;}
        return null;
    }
    private View button(View v,String label) {
        if(v instanceof Button && label.contentEquals(((Button)v).getText()))return v;
        if(v instanceof ViewGroup) for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=button(((ViewGroup)v).getChildAt(i),label);if(found!=null)return found;}
        return null;
    }
}
