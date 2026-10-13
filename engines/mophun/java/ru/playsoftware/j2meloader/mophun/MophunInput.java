package ru.playsoftware.j2meloader.mophun;

import java.util.HashSet;
import java.util.Set;
import java.util.HashMap;
import java.util.Map;
import ru.playsoftware.j2meloader.input.ControllerInput;
import wolphun.Screen;

/** MIDP-shaped mapper outputs translated into Mophun buttons and literal digits. */
public final class MophunInput implements ControllerInput.Sink {
    private final java.util.function.Supplier<Screen> screen;
    private final Set<Integer> held = new HashSet<>();
    private final Map<Integer, Integer> counts = new HashMap<>();
    private int mask;
    public MophunInput(java.util.function.Supplier<Screen> screen) { this.screen = screen; }
    public void press(int key) { if (held.add(key)) change(key, 1); }
    public void release(int key) { if (held.remove(key)) change(key, -1); }
    public void repeat(int key) { }

    /** Independent ownership prevents touch releases from cancelling a physical key. */
    public ControllerInput.Sink newSource() {
        return new ControllerInput.Sink() {
            private final Set<Integer> own = new HashSet<>();
            public void press(int key) { if (own.add(key)) change(key, 1); }
            public void release(int key) { if (own.remove(key)) change(key, -1); }
            public void repeat(int key) { }
        };
    }

    private void change(int key, int delta) {
        int count = counts.getOrDefault(key, 0) + delta;
        if (count > 0) counts.put(key, count); else counts.remove(key);
        Screen target = screen.get();
        if (target == null) return;
        if (key >= 0 && key < 128) target.setKeypad(key, count > 0);
        int next = 0;
        for (int value : counts.keySet()) next |= buttons(value);
        target.setButtonBit(mask & ~next, false);
        target.setButtonBit(next, true);
        mask = next;
    }

    private static int buttons(int key) {
        switch (key) {
            case -1: case '2': return Screen.UP;
            case -2: case '8': return Screen.DOWN;
            case -3: case '4': return Screen.LEFT;
            case -4: case '6': return Screen.RIGHT;
            case -5: case -10: case '5': case '*': return Screen.FIRE;
            case -6: case '0': return Screen.SELECT;
            case -7: case -8: case -11: case '#': return Screen.FIRE2;
            case '1': return Screen.UP | Screen.LEFT;
            case '3': return Screen.UP | Screen.RIGHT;
            case '7': return Screen.DOWN | Screen.LEFT;
            case '9': return Screen.DOWN | Screen.RIGHT;
            default: return 0;
        }
    }
}
