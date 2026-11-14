/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.input;

import androidx.test.platform.app.InstrumentationRegistry;
import java.util.*;
import org.junit.Test;
import ru.playsoftware.j2meloader.config.*;
import static org.junit.Assert.*;

public class ExtraTouchButtonTest {
    private void main(Runnable r){InstrumentationRegistry.getInstrumentation().runOnMainSync(r);}
    private TouchDeck deck(ProfileModel p,List<String> events){return new TouchDeck(p,1,new TouchDeck.Sink(){
        public void press(int k){events.add("+"+k);}public void release(int k){events.add("-"+k);}public void repeat(int k){events.add("r"+k);}public void feedback(){}public void invalidate(){}
    });}
    private TouchDeck.Key key(TouchDeck d,String id){for(TouchDeck.Key k:d.getKeys())if(k.id.equals(id))return k;throw new AssertionError(id);}
    private void down(TouchDeck d,int pointer,String id){TouchDeck.Key k=key(d,id);assertTrue(d.down(pointer,k.bounds.centerX(),k.bounds.centerY()));}
    private ProfileModel profile(){ProfileModel p=new ProfileModel();p.touchLayout=1;p.extraTouchButtons=new ArrayList<>();for(int i=0;i<2;i++){
        ExtraTouchButton b=new ExtraTouchButton();b.code=i==0?49:51;b.portrait.x=b.landscape.x=.35f+i*.3f;b.portrait.y=b.landscape.y=.15f;p.extraTouchButtons.add(b);
    }return p;}
    @Test public void twoExtraKeysAndDpadWorkTogetherAndLayoutRebuildReleasesAll(){main(()->{
        ProfileModel p=profile();List<String> events=new ArrayList<>();TouchDeck d=deck(p,events);d.layout(640,360);
        down(d,0,"extra:"+p.extraTouchButtons.get(0).id);down(d,1,"extra:"+p.extraTouchButtons.get(1).id);down(d,2,"up");
        assertTrue(events.containsAll(Arrays.asList("+49","+51","+-1")));d.layout(360,640);
        assertTrue(events.containsAll(Arrays.asList("-49","-51","--1")));assertFalse(d.up(0));d.cancel();
    });}
    @Test public void duplicateTargetsReleaseOnlyAfterLastFingerAndModeSwitchCancels(){main(()->{
        ProfileModel p=profile();p.extraTouchButtons.get(1).code=49;List<String> events=new ArrayList<>();TouchDeck d=deck(p,events);d.layout(640,360);
        down(d,0,"extra:"+p.extraTouchButtons.get(0).id);down(d,1,"extra:"+p.extraTouchButtons.get(1).id);d.up(0);assertEquals(Arrays.asList("+49"),events);
        down(d,2,"literal:10000");assertEquals(Arrays.asList("+49","-49"),events);
        assertNotNull(key(d,"extra:"+p.extraTouchButtons.get(1).id));d.cancel();
    });}
    @Test public void orientationsRemainIndependentAndMalformedButtonsAreIgnored(){main(()->{
        ProfileModel p=profile();List<String> events=new ArrayList<>();TouchDeck d=deck(p,events);d.layout(640,360);String id="extra:"+p.extraTouchButtons.get(0).id;
        float portrait=p.extraTouchButtons.get(0).portrait.x;d.moveExtra(id,20,10);assertEquals(portrait,p.extraTouchButtons.get(0).portrait.x,0);
        assertTrue(p.extraTouchButtons.get(0).landscape.x>portrait);
        ExtraTouchButton invalid=new ExtraTouchButton();invalid.code=999;p.extraTouchButtons.add(invalid);p.extraTouchButtons.add(null);d.layout(360,640);
        int extras=0;for(TouchDeck.Key k:d.getKeys())if(k.isExtra()){extras++;assertTrue(k.bounds.top>=56);assertTrue(k.bounds.right<=356);}
        assertEquals(2,extras);d.cancel();
    });}
    @Test public void editorCopyIsDetachedAndExtraFireDoesNotMoveDpadDrawing(){main(()->{
        ProfileModel p=profile();p.extraTouchButtons.get(0).code=-5;ProfileModel copy=ProfilesManager.copy(p);copy.extraTouchButtons.get(0).code=57;
        assertEquals(-5,p.extraTouchButtons.get(0).code);TouchLayoutEditor.copyLayout(copy,p);assertEquals(57,p.extraTouchButtons.get(0).code);
        copy.extraTouchButtons.clear();assertEquals(2,p.extraTouchButtons.size());
        TouchDeck d=deck(p,new ArrayList<>());d.layout(360,640);android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(360,640,android.graphics.Bitmap.Config.ARGB_8888);
        d.draw(new android.graphics.Canvas(image));image.recycle();d.cancel();
    });}
    @Test public void editorAddsTwoButtonsAndRestoresDraftWithoutChangingTargetUntilConfirm(){
        try(androidx.test.core.app.ActivityScenario<ru.playsoftware.j2meloader.settings.SettingsActivity> scenario=androidx.test.core.app.ActivityScenario.launch(ru.playsoftware.j2meloader.settings.SettingsActivity.class)){
            scenario.onActivity(activity->{
                ProfileModel p=new ProfileModel();p.touchLayout=1;
                TouchLayoutEditor editor=new TouchLayoutEditor(activity,p,()->{});editor.show();
                find(editor.getWindow().getDecorView(),"Extra tab").performClick();
                find(editor.getWindow().getDecorView(),"+ Add button").performClick();
                find(editor.getWindow().getDecorView(),"+ Add button").performClick();
                android.os.Bundle state=editor.onSaveInstanceState();editor.cancel();assertNull(p.extraTouchButtons);
                TouchLayoutEditor restored=new TouchLayoutEditor(activity,p,()->{});restored.show();restored.onRestoreInstanceState(state);
                find(restored.getWindow().getDecorView(),"Use layout").performClick();assertEquals(2,p.extraTouchButtons.size());
                TouchLayoutEditor removed=new TouchLayoutEditor(activity,p,()->{});removed.show();
                find(removed.getWindow().getDecorView(),"Extra tab").performClick();find(removed.getWindow().getDecorView(),"Remove button").performClick();
                removed.cancel();assertEquals(2,p.extraTouchButtons.size());
            });
        }
    }
    @Test public void addedButtonsAreVisibleInActualEditorWindow()throws Exception{
        TouchLayoutEditor[] editor={null};
        try(androidx.test.core.app.ActivityScenario<ru.playsoftware.j2meloader.settings.SettingsActivity> scenario=androidx.test.core.app.ActivityScenario.launch(ru.playsoftware.j2meloader.settings.SettingsActivity.class)){
            scenario.onActivity(activity->{editor[0]=new TouchLayoutEditor(activity,profile(),()->{});editor[0].show();find(editor[0].getWindow().getDecorView(),"Extra tab").performClick();});
            android.os.SystemClock.sleep(350);
            android.graphics.Bitmap image=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(image);
            android.content.Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();java.io.File folder=new java.io.File(c.getFilesDir(),"profile-ui-captures");folder.mkdirs();
            try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(folder,"extra-buttons.png"))){assertTrue(image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out));}image.recycle();
            main(()->editor[0].dismiss());
        }
    }
    private android.view.View find(android.view.View v,String text){
        if(text.contentEquals(v.getContentDescription()==null?"":v.getContentDescription())
                ||v instanceof android.widget.TextView && text.contentEquals(((android.widget.TextView)v).getText()))return v;
        if(v instanceof android.view.ViewGroup)for(int i=0;i<((android.view.ViewGroup)v).getChildCount();i++){
            android.view.View found=find(((android.view.ViewGroup)v).getChildAt(i),text);if(found!=null)return found;
        }return null;
    }

}
