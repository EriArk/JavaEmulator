package ru.playsoftware.j2meloader.input;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;
import static ru.playsoftware.j2meloader.input.ControllerInput.*;

public class ControllerInputTest {
    private final Map<Integer, Integer> map = new HashMap<>();
    private final ArrayList<String> events = new ArrayList<>();
    private final ControllerInput input = new ControllerInput(k -> map.getOrDefault(k, 0),
            new ControllerInput.Sink() {
                public void press(int key) { events.add("+" + key); }
                public void release(int key) { events.add("-" + key); }
                public void repeat(int key) { events.add("r" + key); }
            });

    private void expect(String... expected) {
        assertEquals(Arrays.asList(expected), events);
        events.clear();
    }

    @Test public void dpadTransitionsUseExclusiveDigit() {
        map.put(21, 4); map.put(20, 8); map.put(DPAD_DL, 7);
        input.key(1, 21, true, false); expect("+4");
        input.key(1, 20, true, false); expect("-4", "+7");
        input.key(1, 21, false, false); expect("-7", "+8");
        input.key(1, 20, false, false); expect("-8");
    }

    @Test public void oldProfilesKeepTwoDirections() {
        map.put(STICK_UP, 2); map.put(STICK_LEFT, 4);
        input.axes(1, -1, -1, 0, 0, 0, 0); expect("+2", "+4");
        input.clear(); expect("-2", "-4");
    }

    @Test public void stickDiagonalIsAtomicAndCustomizable() {
        map.put(STICK_UL, 7); map.put(STICK_UP, 2); map.put(STICK_LEFT, 4);
        input.axes(1, -1, -1, 0, 0, 0, 0); expect("+7");
        input.axes(1, 0, -1, 0, 0, 0, 0); expect("-7", "+2");
        input.axes(1, 0, 0, 0, 0, 0, 0); expect("-2");
    }

    @Test public void allDiagonalIdsAreDistinct() {
        int[] masks = {UP | LEFT, UP | RIGHT, DOWN | LEFT, DOWN | RIGHT};
        for (int i = 0; i < masks.length; i++) {
            assertEquals(-1011 - i, diagonal(masks[i], false));
            assertEquals(-1005 - i, diagonal(masks[i], true));
        }
    }

    @Test public void hatAndKeyEventsDoNotDoublePress() {
        map.put(21, 4);
        input.key(1, 21, true, false);
        input.axes(1, 0, 0, -1, 0, 0, 0); expect("+4");
        input.key(1, 21, false, false); expect();
        input.axes(1, 0, 0, 0, 0, 0, 0); expect("-4");
    }

    @Test public void twoDevicesCannotMakeACombinedDiagonal() {
        map.put(21, 4); map.put(20, 8); map.put(DPAD_DL, 7);
        input.key(1, 21, true, false); input.key(2, 20, true, false);
        expect("+4", "+8");
    }

    @Test public void removingDevicePreservesOtherHeldSource() {
        map.put(96, 5); map.put(STICK_UP, 5);
        input.key(1, 96, true, false); input.axes(2, 0, -1, 0, 0, 0, 0);
        expect("+5");
        input.removeDevice(1); expect();
        input.removeDevice(2); expect("-5");
    }

    @Test public void clearReleasesOldBindingBeforeProfileChange() {
        map.put(96, 5); input.key(1, 96, true, false); expect("+5");
        input.clear(); map.put(96, 7); expect("-5");
        input.key(1, 96, false, false); expect();
        input.key(1, 96, true, false); expect("+7");
    }

    @Test public void explicitUnboundDiagonalSuppressesCardinals() {
        map.put(21, 4); map.put(20, 8); map.put(DPAD_DL, UNBOUND);
        input.axes(1, 0, 0, -1, 1, 0, 0); expect();
    }

    @Test public void axisHysteresisAvoidsBoundaryChatter() {
        assertEquals(LEFT, axisMask(-0.46f, 0, 0));
        assertEquals(LEFT, axisMask(-0.36f, 0, LEFT));
        assertEquals(0, axisMask(-0.29f, 0, LEFT));
        assertEquals(0, axisMask(-0.36f, 0, 0));
    }

    @Test public void triggerAxisAndButtonShareOneOutput() {
        map.put(104, 7);
        input.axes(1, 0, 0, 0, 0, 1, 0); input.key(1, 104, true, false); expect("+7");
        input.axes(1, 0, 0, 0, 0, 0, 0); expect();
        input.key(1, 104, false, false); expect("-7");
    }

    @Test public void repeatAfterPauseDoesNotRepress() {
        map.put(96, 5);
        input.key(1, 96, true, false); input.clear(); expect("+5", "-5");
        input.key(1, 96, true, true); expect();
        input.key(1, 96, true, false); input.key(1, 96, true, true); expect("+5", "r5");
    }
}
