package ru.playsoftware.j2meloader.input;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Stateful controller input, independent of Android views and MIDlet threads. */
public final class ControllerInput {
    public static final int UNBOUND = Integer.MIN_VALUE;
    public static final int UP = 1, DOWN = 2, LEFT = 4, RIGHT = 8;
    public static final int STICK_UP = -1001, STICK_DOWN = -1002;
    public static final int STICK_LEFT = -1003, STICK_RIGHT = -1004;
    public static final int STICK_UL = -1005, STICK_UR = -1006;
    public static final int STICK_DL = -1007, STICK_DR = -1008;
    public static final int DPAD_UL = -1011, DPAD_UR = -1012;
    public static final int DPAD_DL = -1013, DPAD_DR = -1014;

    public interface Mapping { int get(int input); }
    public interface Sink {
        void press(int key);
        void release(int key);
        void repeat(int key);
    }

    private static final class Device {
        final Set<Integer> buttons = new LinkedHashSet<>();
        int dpad, hat, stick;
        boolean leftTrigger, rightTrigger;
    }

    private final Mapping mapping;
    private final Sink sink;
    private final Map<Integer, Device> devices = new LinkedHashMap<>();
    private Set<Integer> held = new LinkedHashSet<>();

    public ControllerInput(Mapping mapping, Sink sink) {
        this.mapping = mapping;
        this.sink = sink;
    }

    // Android's stable DPAD key codes; keeping these here permits plain JVM tests.
    public static int direction(int key) {
        switch (key) {
            case 19: return UP;
            case 20: return DOWN;
            case 21: return LEFT;
            case 22: return RIGHT;
            default: return 0;
        }
    }

    public static int diagonal(int mask, boolean stick) {
        switch (mask) {
            case UP | LEFT: return stick ? STICK_UL : DPAD_UL;
            case UP | RIGHT: return stick ? STICK_UR : DPAD_UR;
            case DOWN | LEFT: return stick ? STICK_DL : DPAD_DL;
            case DOWN | RIGHT: return stick ? STICK_DR : DPAD_DR;
            default: return 0;
        }
    }

    public static boolean isDiagonal(int input) {
        return (input <= STICK_UL && input >= STICK_DR) || (input <= DPAD_UL && input >= DPAD_DR);
    }

    public static int axisMask(float x, float y, int previous) {
        int result = 0;
        if (x < -threshold(previous, LEFT)) result |= LEFT;
        if (x > threshold(previous, RIGHT)) result |= RIGHT;
        if (y < -threshold(previous, UP)) result |= UP;
        if (y > threshold(previous, DOWN)) result |= DOWN;
        return result;
    }

    private static float threshold(int mask, int bit) {
        return (mask & bit) != 0 ? 0.30f : 0.45f;
    }

    public synchronized void key(int deviceId, int input, boolean down, boolean repeat) {
        Device device = devices.computeIfAbsent(deviceId, id -> new Device());
        int bit = direction(input);
        if (repeat) {
            if (!down || (bit != 0 ? (device.dpad & bit) == 0 : !device.buttons.contains(input))) return;
            Set<Integer> repeated = new LinkedHashSet<>();
            if (bit != 0) addDirections(repeated, device.dpad | device.hat, false);
            else add(repeated, mapping.get(input));
            for (int key : repeated) if (held.contains(key)) sink.repeat(key);
            return;
        }
        if (bit != 0) device.dpad = down ? device.dpad | bit : device.dpad & ~bit;
        else if (down) device.buttons.add(input);
        else device.buttons.remove(input);
        reconcile();
    }

    public synchronized void axes(int deviceId, float x, float y, float hatX, float hatY,
                     float leftTrigger, float rightTrigger) {
        Device device = devices.computeIfAbsent(deviceId, id -> new Device());
        device.stick = axisMask(x, y, device.stick);
        device.hat = axisMask(hatX, hatY, device.hat);
        device.leftTrigger = leftTrigger > (device.leftTrigger ? 0.30f : 0.55f);
        device.rightTrigger = rightTrigger > (device.rightTrigger ? 0.30f : 0.55f);
        reconcile();
    }

    public synchronized void removeDevice(int deviceId) {
        devices.remove(deviceId);
        reconcile();
    }

    public synchronized void clear() {
        devices.clear();
        reconcile();
    }

    private void reconcile() {
        Set<Integer> next = new LinkedHashSet<>();
        for (Device device : devices.values()) {
            addDirections(next, device.dpad | device.hat, false);
            addDirections(next, device.stick, true);
            for (int button : device.buttons) add(next, mapping.get(button));
            if (device.leftTrigger) add(next, mapping.get(104));
            if (device.rightTrigger) add(next, mapping.get(105));
        }
        // Release replaced cardinal outputs before pressing a diagonal. Output sets
        // also prevent a second source from releasing a key that is still held.
        for (int key : held) if (!next.contains(key)) sink.release(key);
        for (int key : next) if (!held.contains(key)) sink.press(key);
        held = next;
    }

    private void addDirections(Set<Integer> output, int mask, boolean stick) {
        int diagonal = diagonal(mask, stick);
        int target = diagonal == 0 ? 0 : mapping.get(diagonal);
        if (target != 0) {
            add(output, target);
            return;
        }
        if ((mask & UP) != 0) add(output, mapping.get(stick ? STICK_UP : 19));
        if ((mask & DOWN) != 0) add(output, mapping.get(stick ? STICK_DOWN : 20));
        if ((mask & LEFT) != 0) add(output, mapping.get(stick ? STICK_LEFT : 21));
        if ((mask & RIGHT) != 0) add(output, mapping.get(stick ? STICK_RIGHT : 22));
    }

    private static void add(Set<Integer> output, int key) {
        if (key != 0 && key != UNBOUND) output.add(key);
    }
}
