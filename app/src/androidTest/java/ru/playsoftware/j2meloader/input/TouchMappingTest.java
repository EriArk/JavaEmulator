package ru.playsoftware.j2meloader.input;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import ru.playsoftware.j2meloader.config.ProfileModel;
import ru.playsoftware.j2meloader.config.ProfilesManager;
import ru.playsoftware.j2meloader.settings.SettingsActivity;
import static org.junit.Assert.*;

public class TouchMappingTest {
    private void main(Runnable task) { InstrumentationRegistry.getInstrumentation().runOnMainSync(task); }
    private TouchDeck deck(ProfileModel p, List<String> events) {
        return new TouchDeck(p,1,new TouchDeck.Sink() {
            public void press(int code) { events.add("+"+code); }
            public void release(int code) { events.add("-"+code); }
            public void repeat(int code) { events.add("r"+code); }
            public void feedback() { } public void invalidate() { }
        });
    }
    private TouchDeck.Key key(TouchDeck deck, String id) {
        for (TouchDeck.Key k : deck.getKeys()) if (id.equals(k.id)) return k;
        throw new AssertionError(id);
    }
    private void down(TouchDeck deck, int pointer, String id) {
        TouchDeck.Key k = key(deck,id); assertTrue(deck.down(pointer,k.bounds.centerX(),k.bounds.centerY()));
    }
    @Test public void bothOrientationsKeepStableAssignmentsAndDrawingGeometry() {
        main(() -> {
            ProfileModel p = new ProfileModel(); p.touchLayout=1;
            List<String> events = new ArrayList<>(); TouchDeck deck=deck(p,events);
            for (int[] size : new int[][]{{360,640},{640,360},{240,240}}) {
                p.touchGamepadMappings=null; deck.layout(size[0],size[1]);
                android.graphics.RectF bounds = new android.graphics.RectF(key(deck,"fire").bounds);
                p.touchGamepadMappings=TouchMappings.template(true); deck.layout(size[0],size[1]);
                assertEquals(bounds,key(deck,"fire").bounds); assertEquals("5",key(deck,"fire").displayLabel());
                down(deck,1,"up"); down(deck,2,"fire"); deck.cancel();
                assertTrue(events.contains("+50")); assertTrue(events.contains("-50"));
                assertTrue(events.contains("+53")); assertTrue(events.contains("-53")); events.clear();
                android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(size[0],size[1],android.graphics.Bitmap.Config.ARGB_8888);
                deck.draw(new android.graphics.Canvas(image)); image.recycle();
            }
        });
    }
    @Test public void sharedTargetsDiagonalsAndMidPressRebindReleaseExactlyOnce() {
        main(() -> {
            ProfileModel p=new ProfileModel(); p.touchLayout=1; p.touchGamepadMappings=TouchMappings.template(true);
            List<String> events=new ArrayList<>(); TouchDeck deck=deck(p,events); deck.layout(640,360);
            down(deck,0,"fire"); down(deck,1,"action_right"); deck.up(0);
            assertEquals(Arrays.asList("+53"),events); deck.up(1);
            assertEquals(Arrays.asList("+53","-53"),events); events.clear();
            down(deck,0,"up_left"); down(deck,1,"up"); deck.up(0);
            assertTrue(events.contains("-52")); assertFalse(events.contains("-50"));
            p.touchGamepadMappings.put("up",-6); deck.layout(360,640);
            assertTrue(events.contains("-50")); assertFalse(deck.up(1)); events.clear();
            p.touchGamepadMappings.put("up_left",55); deck.layout(360,640);
            down(deck,0,"up_left"); deck.cancel(); assertEquals(Arrays.asList("+55","-55"),events);
        });
    }
    @Test public void literalKeypadAndNumbersSwitchRemainEscapeHatches() {
        main(() -> {
            ProfileModel p=new ProfileModel(); p.touchLayout=1; p.touchGamepadMappings=TouchMappings.template(true);
            p.touchGamepadMappings.put("action_right",-7);
            List<String> events=new ArrayList<>(); TouchDeck deck=deck(p,events); deck.layout(640,360);
            down(deck,0,"action_right"); down(deck,1,"literal:10000");
            assertEquals(Arrays.asList("+-7","--7"),events); events.clear();
            down(deck,2,"literal:53"); deck.cancel(); assertEquals(Arrays.asList("+53","-53"),events);
            p.touchLayout=0; deck.layout(360,640);
            assertEquals("\u2191",key(deck,"literal:-1").displayLabel());
            assertArrayEquals(new int[]{-1},key(deck,"literal:-1").codes);
            assertEquals(Integer.valueOf(-7),p.touchGamepadMappings.get("action_right"));
        });
    }
    @Test public void repeatStopsAfterHideAndNeverChangesHeldCodeWithoutRelease() throws Exception {
        List<String> events=java.util.Collections.synchronizedList(new ArrayList<>());
        TouchDeck[] deck={null};
        main(() -> {
            ProfileModel p=new ProfileModel(); p.touchLayout=1; p.touchGamepadMappings=TouchMappings.template(true);
            deck[0]=deck(p,events); deck[0].layout(640,360); down(deck[0],0,"up");
            p.touchGamepadMappings.put("up",-7);
        });
        try {
            Thread.sleep(600); main(() -> deck[0].setEnabled(false));
            assertTrue(events.contains("r50")); assertFalse(events.contains("r-7"));
            assertEquals("-50",events.get(events.size()-1)); int count=events.size();
            Thread.sleep(200); assertEquals(count,events.size());
        } finally { main(() -> deck[0].cancel()); }
    }
    @Test public void profileCopyAndPreviewRollbackAreDetachedFromOtherGamesAndControllers() {
        ProfileModel p = new com.google.gson.Gson().fromJson("{}",ProfileModel.class);
        assertNull(p.touchGamepadMappings);
        p.keyMappings = new android.util.SparseIntArray(); p.keyMappings.put(96,53);
        ProfileModel other = ProfilesManager.copy(p), draft=ProfilesManager.copy(p);
        draft.touchGamepadMappings=TouchMappings.template(true);
        ProfilesManager.beginPreview(p); ProfilesManager.copyInto(draft,p);
        draft.touchGamepadMappings.put("up",55); assertEquals(Integer.valueOf(50),p.touchGamepadMappings.get("up"));
        assertNull(other.touchGamepadMappings); assertEquals(53,p.keyMappings.get(96));
        ProfilesManager.undoPreview(p); assertNull(p.touchGamepadMappings); assertEquals(53,p.keyMappings.get(96));
    }
    @Test public void editorRestoresDetachedMappingAndResetDoesNotErasePositions() {
        try(ActivityScenario<SettingsActivity> scenario=ActivityScenario.launch(SettingsActivity.class)) {
            scenario.onActivity(activity -> {
                ProfileModel p=new ProfileModel(); p.touchLayout=1;
                p.touchPlacements=new java.util.HashMap<>(); TouchPlacement placement=new TouchPlacement(); placement.x=.3f;
                p.touchPlacements.put("portrait:1:0",placement);
                TouchLayoutEditor editor=new TouchLayoutEditor(activity,p,()->{}); editor.show();
                find(editor.getWindow().getDecorView(),"Buttons tab").performClick();
                find(editor.getWindow().getDecorView(),"2 (50)").performClick();
                Bundle state=editor.onSaveInstanceState(); editor.cancel(); assertNull(p.touchGamepadMappings);
                TouchLayoutEditor restored=new TouchLayoutEditor(activity,p,()->{}); restored.show(); restored.onRestoreInstanceState(state);
                find(restored.getWindow().getDecorView(),"Use layout").performClick();
                assertEquals(Integer.valueOf(50),p.touchGamepadMappings.get("up"));
                TouchLayoutEditor reset=new TouchLayoutEditor(activity,p,()->{}); reset.show();
                find(reset.getWindow().getDecorView(),"Buttons tab").performClick();
                find(reset.getWindow().getDecorView(),"Reset button mappings").performClick();
                find(reset.getWindow().getDecorView(),"Use layout").performClick();
                assertNull(p.touchGamepadMappings); assertEquals(.3f,p.touchPlacements.get("portrait:1:0").x,0);
            });
        }
    }
    private View find(View view,String description) {
        if(description.contentEquals(view.getContentDescription()==null ? "" : view.getContentDescription())) return view;
        if(view instanceof ViewGroup) for(int i=0;i<((ViewGroup)view).getChildCount();i++) {
            View result=find(((ViewGroup)view).getChildAt(i),description); if(result!=null) return result;
        }
        return null;
    }
}
