package ru.playsoftware.j2meloader.mophun;

import android.graphics.RectF;
import android.view.MotionEvent;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import org.junit.Test;
import ru.playsoftware.j2meloader.config.ProfileModel;
import ru.playsoftware.j2meloader.input.ControllerInput;
import ru.playsoftware.j2meloader.input.TouchDeck;
import wolphun.Screen;
import static org.junit.Assert.*;

public class MophunTouchTest {
    private ProfileModel profile(int layout) {
        ProfileModel result = new ProfileModel(new File("touch-test"));
        result.touchLayout = layout;
        return result;
    }
    private TouchDeck.Key key(MophunTouchControls controls, String label) {
        for (TouchDeck.Key key : controls.deck.getKeys()) if (key.label.equals(label)) return key;
        throw new AssertionError(label);
    }
    private void event(MophunTouchControls controls, int action, int[] ids, float... xy) {
        MotionEvent.PointerProperties[] props = new MotionEvent.PointerProperties[ids.length];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[ids.length];
        for (int i = 0; i < ids.length; i++) {
            props[i] = new MotionEvent.PointerProperties(); props[i].id = ids[i];
            props[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
            coords[i] = new MotionEvent.PointerCoords(); coords[i].x = xy[2*i]; coords[i].y = xy[2*i+1];
            coords[i].pressure = 1; coords[i].size = 1;
        }
        MotionEvent e = MotionEvent.obtain(0, 1, action, ids.length, props, coords, 0, 0, 1, 1, 0, 0,
                android.view.InputDevice.SOURCE_TOUCHSCREEN, 0);
        try { controls.touch(e); } finally { e.recycle(); }
    }
    private void at(MophunTouchControls controls, int action, String label) {
        RectF r = key(controls, label).bounds;
        event(controls, action, new int[]{0}, r.centerX(), r.centerY());
    }

    @Test public void touchAndPhysicalSourcesDoNotReleaseEachOther() {
        Screen screen = new Screen(101, 80, 1);
        MophunInput input = new MophunInput(() -> screen);
        ControllerInput.Sink touch = input.newSource();
        input.press('5'); touch.press('5'); touch.release('5');
        assertTrue(screen.keypadPressed('5'));
        assertEquals(Screen.FIRE, screen.getButtons());
        touch.press('5'); input.release('5');
        assertTrue(screen.keypadPressed('5'));
        touch.release('5');
        assertFalse(screen.keypadPressed('5'));
        assertEquals(0, screen.getButtons());
        input.press(-3); touch.press('7'); input.release(-3);
        assertEquals(Screen.LEFT | Screen.DOWN, screen.getButtons());
        touch.release('7');
        assertEquals(0, screen.getButtons());
    }

    @Test public void multiTouchSlideAndCancelReachMophunWithoutOpeningMenu() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Screen screen = new Screen(101, 80, 1);
            int[] menu = {0};
            MophunTouchControls controls = new MophunTouchControls(profile(TouchDeck.GAMEPAD), 1, 8,
                    new MophunInput(() -> screen).newSource(), () -> {}, () -> menu[0]++);
            controls.layout(360, 640);
            RectF left = key(controls, "\u2190").bounds, fire = key(controls, "5").bounds;
            RectF diagonal = key(controls, "\u2199").bounds;
            at(controls, MotionEvent.ACTION_DOWN, "\u2190");
            event(controls, MotionEvent.ACTION_POINTER_DOWN | (1 << 8), new int[]{0,7},
                    left.centerX(), left.centerY(), fire.centerX(), fire.centerY());
            assertEquals(Screen.LEFT | Screen.FIRE, screen.getButtons());
            event(controls, MotionEvent.ACTION_MOVE, new int[]{0,7},
                    diagonal.centerX(), diagonal.centerY(), fire.centerX(), fire.centerY());
            assertEquals(Screen.LEFT | Screen.DOWN | Screen.FIRE, screen.getButtons());
            event(controls, MotionEvent.ACTION_POINTER_UP | (1 << 8), new int[]{0,7},
                    diagonal.centerX(), diagonal.centerY(), fire.centerX(), fire.centerY());
            assertEquals(Screen.LEFT | Screen.DOWN, screen.getButtons());
            event(controls, MotionEvent.ACTION_CANCEL, new int[]{0}, 0, 0);
            assertEquals(0, screen.getButtons());
            assertFalse(screen.keypadPressed('5'));
            assertEquals(0, menu[0]);
        });
    }

    @Test public void rotationAndNumberToggleReleaseKeysAndReserveImageArea() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Screen screen = new Screen(101,80,1);
            ProfileModel profile = profile(TouchDeck.GAMEPAD);
            MophunTouchControls controls = new MophunTouchControls(profile, 1, 8,
                    new MophunInput(() -> screen).newSource(), () -> {}, () -> fail("Unexpected menu"));
            controls.layout(360,640);
            at(controls, MotionEvent.ACTION_DOWN, "5");
            controls.layout(640,360);
            assertEquals(0, screen.getButtons());
            assertFalse(screen.keypadPressed('5'));
            at(controls, MotionEvent.ACTION_DOWN, "123");
            at(controls, MotionEvent.ACTION_UP, "Actions");
            for (String digit : new String[]{"1","2","3","4","5","6","7","8","9","*","0","#"}) {
                at(controls, MotionEvent.ACTION_DOWN, digit);
                assertTrue(screen.keypadPressed(digit.charAt(0)));
                at(controls, MotionEvent.ACTION_UP, digit);
                assertFalse(screen.keypadPressed(digit.charAt(0)));
                assertEquals(0, screen.getButtons());
            }
            for (int layout : new int[]{TouchDeck.PHONE, TouchDeck.GAMEPAD}) {
                profile.touchLayout = layout;
                for (int[] size : new int[][]{{360,640},{640,360},{320,480},{480,320}}) {
                    controls.layout(size[0],size[1]);
                    for (TouchDeck.Key key : controls.deck.getKeys())
                        assertFalse(RectF.intersects(controls.gameArea(),key.bounds));
                }
            }
            controls.cancel();
        });
    }

    @Test public void gameTapOpensMenuButControlSlideAndMultitouchDoNot() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            int[] taps = {0};
            Screen screen = new Screen(101,80,1);
            MophunTouchControls controls = new MophunTouchControls(profile(TouchDeck.PHONE), 1, 8,
                    new MophunInput(() -> screen).newSource(), () -> {}, () -> taps[0]++);
            controls.layout(360,640);
            RectF area = controls.gameArea();
            float x = area.centerX(), y = area.centerY();
            at(controls, MotionEvent.ACTION_DOWN, "5");
            event(controls, MotionEvent.ACTION_MOVE, new int[]{0}, x,y);
            event(controls, MotionEvent.ACTION_UP, new int[]{0}, x,y);
            assertEquals(0,taps[0]);
            event(controls, MotionEvent.ACTION_DOWN, new int[]{0}, x,y);
            event(controls, MotionEvent.ACTION_POINTER_DOWN | (1 << 8), new int[]{0,1}, x,y,x+10,y);
            event(controls, MotionEvent.ACTION_POINTER_UP | (1 << 8), new int[]{0,1}, x,y,x+10,y);
            event(controls, MotionEvent.ACTION_UP, new int[]{0}, x,y);
            assertEquals(0,taps[0]);
            event(controls, MotionEvent.ACTION_DOWN, new int[]{0}, x,y);
            event(controls, MotionEvent.ACTION_UP, new int[]{0}, x,y);
            assertEquals(1,taps[0]);
        });
    }
}
