package ru.playsoftware.j2meloader.applist;

import java.util.HashMap;
import java.util.Map;

/** One navigation step per trigger press, including devices reporting both key and axis. */
public final class LibraryTriggers {
    private final Map<Integer, boolean[]> devices = new HashMap<>();
    public int key(int device, boolean right, boolean down) { return update(device, right, false, down); }
    public int axis(int device, boolean right, float value) {
        boolean[] state = devices.computeIfAbsent(device, id -> new boolean[4]);
        boolean down = state[right ? 3 : 1] ? value > .25f : value >= .65f;
        return update(device, right, true, down);
    }
    private int update(int device, boolean right, boolean axis, boolean down) {
        boolean[] state = devices.computeIfAbsent(device, id -> new boolean[4]);
        int offset = right ? 2 : 0;
        boolean wasDown = state[offset] || state[offset + 1];
        state[offset + (axis ? 1 : 0)] = down;
        return !wasDown && down ? (right ? 1 : -1) : 0;
    }
    public void reset() { devices.clear(); }
}
