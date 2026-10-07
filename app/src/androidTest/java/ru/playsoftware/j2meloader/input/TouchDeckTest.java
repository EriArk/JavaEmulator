package ru.playsoftware.j2meloader.input;

import android.graphics.RectF;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.gson.Gson;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.ArrayList;
import java.util.Arrays;
import ru.playsoftware.j2meloader.config.ProfileModel;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class TouchDeckTest {
    private TouchDeck create(ProfileModel p, ArrayList<String> events) {
        return new TouchDeck(p,1,new TouchDeck.Sink() {
            public void press(int code) { events.add("+"+code); }
            public void release(int code) { events.add("-"+code); }
            public void repeat(int code) { events.add("r"+code); }
            public void invalidate() { }
        });
    }
    @Test public void layoutsReserveGameAreaAndDoNotOverlap() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (int style=0;style<2;style++) for (int size=0;size<3;size++) {
                ProfileModel p = new ProfileModel(); p.touchLayout=style; p.touchSize=size;
                TouchDeck deck=create(p,new ArrayList<>());
                for (int[] dimensions : new int[][]{{360,640},{640,360},{620,540},{320,568},{800,360},{412,915}}) {
                    int w=dimensions[0], h=dimensions[1];
                    deck.layout(w,h);
                    RectF area=deck.gameArea(w,h);
                    assertTrue(area.width()>0 && area.height()>0);
                    ArrayList<TouchDeck.Key> keys=deck.getKeys();
                    for (int i=0;i<keys.size();i++) {
                        RectF key=keys.get(i).bounds;
                        assertTrue(keys.get(i).label,key.left>=0 && key.top>=0 && key.right<=w+.01f && key.bottom<=h+.01f);
                        assertTrue("Touch target",key.width()>=47.9f && key.height()>=47.9f);
                        assertFalse("Game overlap",RectF.intersects(area,key));
                        for (int j=i+1;j<keys.size();j++) {
                            RectF inset = new RectF(key); inset.inset(.1f,.1f);
                            assertFalse("Overlapping keys",RectF.intersects(inset,keys.get(j).bounds));
                        }
                    }
                }
                deck.cancel();
            }
        });
    }
    @Test public void multiTouchAndSlidingKeepSharedKeysPressed() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            ProfileModel p=new ProfileModel(); p.touchLayout=1;
            ArrayList<String> events=new ArrayList<>();
            TouchDeck deck=create(p,events); deck.layout(800,360);
            TouchDeck.Key up=key(deck,"\u2191"), diagonal=key(deck,"\u2196");
            deck.down(0,up.bounds.centerX(),up.bounds.centerY());
            deck.down(1,up.bounds.centerX(),up.bounds.centerY());
            deck.up(0); assertEquals(Arrays.asList("+-1"),events);
            deck.move(1,diagonal.bounds.centerX(),diagonal.bounds.centerY());
            assertEquals(Arrays.asList("+-1","+-3"),events);
            deck.cancel();
            assertTrue(events.contains("--1")); assertTrue(events.contains("--3"));
            assertFalse(deck.up(1));
            events.clear();
            assertFalse(deck.down(31,400,100));
            assertFalse(deck.move(31,up.bounds.centerX(),up.bounds.centerY()));
            assertTrue(events.isEmpty());
        });
    }
    @Test public void rotateReleasesInputsAndSettingsRoundTrip() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            ProfileModel p=new ProfileModel(); p.touchLayout=0;
            p.touchPortraitReach=1; p.touchLandscapeReach=0;
            ArrayList<String> events=new ArrayList<>();
            TouchDeck deck=create(p,events); deck.layout(360,640);
            TouchDeck.Key five=key(deck,"5");
            deck.down(0,five.bounds.centerX(),five.bounds.centerY());
            deck.layout(640,360);
            assertEquals(Arrays.asList("+53","-53"),events);
            ProfileModel copy=new Gson().fromJson(new Gson().toJson(p),ProfileModel.class);
            assertEquals(0,copy.touchLayout.intValue());
            assertEquals(1,copy.touchPortraitReach,0);
            assertEquals(0,copy.touchLandscapeReach,0);
            assertEquals(100,copy.touchOpacity);
        });
    }
    private TouchDeck.Key key(TouchDeck deck,String label) {
        for (TouchDeck.Key key:deck.getKeys()) if (label.equals(key.label)) return key;
        throw new AssertionError(label);
    }

    @Test public void portraitGamepadCanHoldDirectionAndActionAndSwitchToNumbers() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            ProfileModel p=new ProfileModel(); p.touchLayout=1;
            ArrayList<String> events=new ArrayList<>();
            TouchDeck deck=create(p,events); deck.layout(360,640);
            TouchDeck.Key left=key(deck,"\u2190"), action=key(deck,"5");
            assertTrue(deck.down(0,left.bounds.centerX(),left.bounds.centerY()));
            assertTrue(deck.down(1,action.bounds.centerX(),action.bounds.centerY()));
            assertEquals(Arrays.asList("+-3","+53"),events);
            TouchDeck.Key numbers=key(deck,"123");
            deck.down(2,numbers.bounds.centerX(),numbers.bounds.centerY());
            assertTrue(events.contains("--3")); assertTrue(events.contains("-53"));
            for(String label:new String[]{"1","2","3","4","5","6","7","8","9","*","0","#"})
                assertNotNull(key(deck,label));
            deck.cancel();
        });
    }
}
