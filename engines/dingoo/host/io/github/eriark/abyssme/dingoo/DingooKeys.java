/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.dingoo;

import ru.playsoftware.j2meloader.input.ControllerInput;
import static javax.microedition.lcdui.Canvas.*;

final class DingooKeys implements ControllerInput.Sink {
    private final int[] holds = new int[12];
    private int button(int key) {
        switch (key) {
            case KEY_UP: case '2': return 4;
            case KEY_DOWN: case '8': return 5;
            case KEY_LEFT: case '4': return 6;
            case KEY_RIGHT: case '6': return 7;
            case KEY_FIRE: return 8;
            case KEY_SOFT_RIGHT: case '0': return 0;
            case KEY_SOFT_LEFT: case '1': return 9;
            case '5': case '3': return 1;
            case '7': case KEY_CLEAR: return 2;
            case '9': case KEY_SEND: return 3;
            case '*': return 10;
            case '#': return 11;
            default: return -1;
        }
    }
    public void press(int key) {
        int id = button(key);
        if (id >= 0 && holds[id]++ == 0) DingooCore.button(id,true);
    }
    public void release(int key) {
        int id = button(key);
        if (id >= 0 && holds[id] > 0 && --holds[id] == 0) DingooCore.button(id,false);
    }
    public void repeat(int key) {}
}
