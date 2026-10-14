package io.github.eriark.abyssme.symbian;

import java.util.*;
import ru.playsoftware.j2meloader.input.ControllerInput;

/** Shared phone-key vocabulary, translated once at the native boundary. */
final class SymbianInput {
    interface NativeKeys { void key(int scan, boolean down); }
    private final NativeKeys nativeKeys;
    private final Map<Integer, Integer> owners = new HashMap<>();
    private final List<Set<Integer>> sources = new ArrayList<>();
    SymbianInput(NativeKeys keys) { nativeKeys = keys; }
    static int scan(int key) {
        switch (key) {
            case -1: return 0x10;
            case -2: return 0x11;
            case -3: return 0x0e;
            case -4: return 0x0f;
            case -5: return 0xa7;
            case -6: return 0xa4;
            case -7: return 0xa5;
            case -8: return 1; // Backspace
            case -10: return 0xc4; // Yes / call
            case -11: return 0xc5; // No / end
            default: return key >= '0' && key <= '9' || key == '*' || key == '#' ? key : 0;
        }
    }
    ControllerInput.Sink source() {
        Set<Integer> pressed = new HashSet<>(); sources.add(pressed);
        return new ControllerInput.Sink() {
            public void press(int key) {
                int scan = scan(key);
                if (scan == 0 || !pressed.add(scan)) return;
                int count = owners.getOrDefault(scan, 0); owners.put(scan, count + 1);
                if (count == 0) nativeKeys.key(scan, true);
            }
            public void release(int key) {
                int scan = scan(key); if (!pressed.remove(scan)) return;
                int count = owners.get(scan) - 1;
                if (count == 0) { owners.remove(scan); nativeKeys.key(scan, false); }
                else owners.put(scan, count);
            }
            public void repeat(int key) { /* The guest generates key repeats while held. */ }
        };
    }
    void clear() {
        for (int scan : owners.keySet()) nativeKeys.key(scan, false);
        owners.clear(); for (Set<Integer> source : sources) source.clear();
    }
}
