/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.wipi;

import com.parkjeongseop.wipi.WipiNative;
import java.util.HashMap;
import ru.playsoftware.j2meloader.input.ControllerInput;
import static javax.microedition.lcdui.Canvas.*;

/** Multiple physical devices are merged by ControllerInput. Touch owns its own presses. */
final class WipiKeys implements ControllerInput.Sink {
    private final HashMap<Integer,Integer> holds = new HashMap<>();
    public void press(int key) {
        String guest = name(key);
        if (guest == null) return;
        int count = holds.getOrDefault(key, 0);
        holds.put(key, count + 1);
        if (count == 0) WipiNative.nativeKeyDown(guest);
    }
    public void release(int key) {
        int count = holds.getOrDefault(key, 0);
        if (count == 1) { holds.remove(key); WipiNative.nativeKeyUp(name(key)); }
        else if (count > 1) holds.put(key, count - 1);
    }
    public void repeat(int key) {} // The guest runtime owns key repeat.
    private static String name(int key) {
        if ((key >= '0' && key <= '9') || key == '*' || key == '#') return Character.toString((char) key);
        switch (key) {
            case KEY_UP: return "UP";
            case KEY_DOWN: return "DOWN";
            case KEY_LEFT: return "LEFT";
            case KEY_RIGHT: return "RIGHT";
            case KEY_FIRE: return "OK";
            case KEY_SOFT_LEFT: return "SOFT_L";
            case KEY_SOFT_RIGHT: return "SOFT_R";
            case KEY_CLEAR: return "CLR";
            case KEY_SEND: return "CALL";
            case KEY_END: return "HANGUP";
            default: return null;
        }
    }
}
