package ru.playsoftware.j2meloader.runtime;

import org.junit.Test;
import static org.junit.Assert.*;
import ru.playsoftware.j2meloader.input.ControllerInput;
import java.util.ArrayList;
import java.util.List;

public class FastForwardTest {
    @Test public void holdRestoresCustomSpeedAndFrameLimit() {
        int[] applied = {0}; FastForward ff = new FastForward(v -> applied[0] = v);
        ff.configure(75, 400, false);
        ff.button(true); assertEquals(400, applied[0]); assertEquals(120, ff.frameLimit(30));
        assertEquals(0, ff.frameLimit(0));
        ff.button(false); assertEquals(75, applied[0]); assertEquals(30, ff.frameLimit(30));
    }
    @Test public void toggleIgnoresRepeatsAndResetsOnPause() {
        FastForward ff = new FastForward(v -> {}); ff.configure(125, 300, true);
        ff.button(true); ff.button(true); ff.button(false); assertTrue(ff.active());
        ff.button(true); ff.button(false); assertFalse(ff.active());
        ff.toggle(); assertTrue(ff.active()); ff.stop(); assertEquals(125, ff.speed());
        ff.button(false); assertFalse(ff.active());
    }
    @Test public void reconfigureDiscardsActivationAndNeverSlowsBase() {
        FastForward ff = new FastForward(v -> {}); ff.configure(300, 200, false);
        ff.toggle(); assertEquals(300, ff.speed());
        ff.configure(50, 400, true); assertFalse(ff.active()); assertEquals(50, ff.speed());
    }
    @Test public void triggersDeduplicateKeyAxisSourcesAndDoNotLeakIntoGame() {
        List<Integer> guest = new ArrayList<>(); List<Boolean> shortcut = new ArrayList<>();
        ControllerInput input = new ControllerInput(k -> k, new ControllerInput.Sink() {
            public void press(int k) { guest.add(k); }
            public void release(int k) { guest.add(-k); }
            public void repeat(int k) { guest.add(k); }
        });
        input.shortcut(() -> 105, shortcut::add);
        input.key(1,105,true,false); input.key(1,105,true,true);
        input.axes(1,0,0,0,0,0,1);
        input.key(1,105,false,false);
        assertEquals(java.util.Arrays.asList(true), shortcut); assertTrue(guest.isEmpty());
        input.axes(1,0,0,0,0,0,0);
        assertEquals(java.util.Arrays.asList(true,false), shortcut);
        input.key(1,96,true,false); input.clear();
        assertEquals(java.util.Arrays.asList(96,-96), guest);
    }
    @Test public void shortcutStaysHeldAcrossDevicesAndReleasesOnDisconnect() {
        List<Boolean> values = new ArrayList<>();
        ControllerInput input = new ControllerInput(k -> 0, new ControllerInput.Sink() {
            public void press(int k) { fail(); } public void release(int k) { fail(); } public void repeat(int k) { fail(); }
        });
        input.shortcut(() -> 107, values::add);
        input.key(1,107,true,false); input.key(2,107,true,false); input.removeDevice(1);
        assertEquals(java.util.Arrays.asList(true), values); input.removeDevice(2);
        assertEquals(java.util.Arrays.asList(true,false), values);
    }
}
