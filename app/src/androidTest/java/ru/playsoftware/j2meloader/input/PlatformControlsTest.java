/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.input;

import android.view.ContextThemeWrapper;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import org.junit.Test;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.config.ProfileModel;
import ru.playsoftware.j2meloader.config.ProfilesManager;
import static org.junit.Assert.*;

public class PlatformControlsTest {
    static class Events implements TouchDeck.Sink, ControllerInput.Sink {
        final List<String> events = new ArrayList<>();
        public void press(int code) { events.add("down " + code); }
        public void release(int code) { events.add("up " + code); }
        public void repeat(int code) {}
        public void feedback() {}
        public void invalidate() {}
    }
    @Test public void nativeButtonsAreReachableInBothOrientationsAndReleaseOnCancel() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (String engine : Arrays.asList("dingoo","gp32","piece","supervision","megaduck","gamate","gameking",
                    "wipi","kaios","mre","gizmondo","cybiko","palm","palm-classic","portfolio","pocketpc","flash","webos","firefox")) {
                ProfileModel p = new ProfileModel();
                p.platformControls = PlatformControls.forEngine(engine);
                Events sink = new Events();
                TouchDeck deck = new TouchDeck(p, 1, sink);
                for (int[] size : new int[][]{{360,640},{640,360},{400,350},{350,400}}) {
                    deck.layout(size[0],size[1]);
                    for (PlatformControls.Button b : p.platformControls.buttons()) {
                        TouchDeck.Key key = deck.getKeys().stream().filter(k -> k.id.equals(p.platformControls.prefix + b.code))
                                .findFirst().orElseThrow(AssertionError::new);
                        assertEquals(b.face,key.displayLabel());
                        assertTrue(engine,key.bounds.left >= 0 && key.bounds.top >= 0 && key.bounds.right <= size[0]+.01f && key.bounds.bottom <= size[1]+.01f);
                        sink.events.clear();
                        assertTrue(deck.down(1,key.bounds.centerX(),key.bounds.centerY()));
                        deck.cancel();
                        assertEquals(engine,Arrays.asList("down "+b.code,"up "+b.code),sink.events);
                    }
                }
            }
        });
    }
    @Test public void remappedTouchAndPhysicalMapperSendTheSameGuestButton() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            ProfileModel p = new ProfileModel();
            p.platformControls = PlatformControls.forEngine("gizmondo");
            p.ensureKeyMappingProfiles();
            p.platformTouchMappings = new HashMap<>();
            p.platformTouchMappings.put(p.platformControls.prefix + (-5), (int)'6');
            ProfileModel draft = ProfilesManager.copy(p);
            Events events = new Events();
            TouchDeck deck = new TouchDeck(draft, 1, events);
            deck.layout(640,360);
            TouchDeck.Key play = deck.getKeys().stream().filter(k -> k.id.equals(p.platformControls.prefix + (-5)))
                    .findFirst().orElseThrow(AssertionError::new);
            assertEquals("Light",play.displayLabel());
            deck.down(1,play.bounds.centerX(),play.bounds.centerY()); deck.up(1);
            ControllerMapperView view = new ControllerMapperView(new ContextThemeWrapper(
                    InstrumentationRegistry.getInstrumentation().getTargetContext(), R.style.AppTheme),
                    "Gizmondo",draft.keyMappingProfiles,0,new ControllerMapperView.Listener() {
                public void save(ArrayList<ProfileModel.KeyMappingProfile> presets,int active) {
                    draft.keyMappingProfiles = presets; draft.setActiveKeyMappingProfile(active);
                }
                public void cancel() { fail(); }
            });
            view.setPlatform(draft.platformControls);
            button(view,"Brightness").performClick();
            view.setSaveLabel("Apply test mapping");
            button(view,"Apply test mapping").performClick();
            ProfileModel saved = ProfilesManager.copy(draft);
            ControllerInput pad = new ControllerInput(code -> saved.getActiveKeyMappings().get(code),events);
            pad.key(7, KeyEvent.KEYCODE_BUTTON_A,true,false);
            pad.key(7, KeyEvent.KEYCODE_BUTTON_A,false,false);
            assertEquals(Arrays.asList("down 54","up 54","down 54","up 54"),events.events);
            assertNull(p.getActiveKeyMappings());
        });
    }
    private static Button button(View v,String name) {
        if (v instanceof Button && (name.contentEquals(((Button)v).getText()) || name.equals(v.getContentDescription()))) return (Button)v;
        if (v instanceof ViewGroup) for (int i=0;i<((ViewGroup)v).getChildCount();i++) {
            Button found = button(((ViewGroup)v).getChildAt(i),name);
            if (found != null) return found;
        }
        return null;
    }
    @Test public void existingKeypadAndCustomBindingsStayAvailable() {
        for (String engine : Arrays.asList("asha","j2me")) assertNull(PlatformControls.forEngine(engine));
        ProfileModel p = new ProfileModel();
        p.platformControls = PlatformControls.forEngine("gizmondo");
        PlatformControls.selectLayout(p,2);
        assertEquals(2,PlatformControls.layoutChoice(ProfilesManager.copy(p)));
        android.util.SparseIntArray previous = new android.util.SparseIntArray();
        previous.put(KeyEvent.KEYCODE_BUTTON_X, '3');
        assertEquals('3',PlatformControls.resolveMappings(p.platformControls,previous).get(KeyEvent.KEYCODE_BUTTON_X));
        assertEquals('*',PlatformControls.resolveMappings(p.platformControls,null).get(KeyEvent.KEYCODE_BUTTON_X));
        assertEquals("Forward",p.platformControls.binding('3').name);
    }
}
