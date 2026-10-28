package ru.playsoftware.j2meloader.input;

import java.util.HashMap;
import java.util.Map;

/** Stable gamepad identities, independent of layout coordinates and controller profiles. */
public final class TouchMappings {
    private TouchMappings() { }
    public static final String[] IDS = {"up", "down", "left", "right", "fire",
            "up_left", "up_right", "down_left", "down_right", "soft_left", "soft_right",
            "action_top", "action_left", "action_right", "action_bottom"};
    private static final int[][] DEFAULTS = {{-1}, {-2}, {-3}, {-4}, {-5},
            {-1,-3}, {-1,-4}, {-2,-3}, {-2,-4}, {-6}, {-7}, {49}, {42}, {53}, {48}};
    private static final String[][] DIAGONALS = {{"up","left"}, {"up","right"},
            {"down","left"}, {"down","right"}};

    public static boolean validCode(int code) {
        return code >= -7 && code <= -1 || code >= 48 && code <= 57 || code == 42 || code == 35;
    }
    public static int index(String id) {
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return i;
        return -1;
    }
    public static int[] resolve(Map<String, Integer> mappings, String id) {
        int index = index(id);
        if (index < 0) throw new IllegalArgumentException("Unknown touch button: " + id);
        Integer value = mappings == null ? null : mappings.get(id);
        if (value != null && validCode(value)) return new int[]{value};
        if (index >= 5 && index <= 8) {
            String[] pair = DIAGONALS[index - 5];
            int first = resolve(mappings, pair[0])[0], second = resolve(mappings, pair[1])[0];
            return first == second ? new int[]{first} : new int[]{first, second};
        }
        return DEFAULTS[index].clone();
    }
    public static HashMap<String, Integer> template(boolean numpad) {
        HashMap<String, Integer> result = new HashMap<>();
        if (numpad) {
            result.put("up", 50); result.put("down", 56); result.put("left", 52);
            result.put("right", 54); result.put("fire", 53);
        }
        return result;
    }
    public static String glyph(int code) {
        switch (code) {
            case -1: return "\u2191";
            case -2: return "\u2193";
            case -3: return "\u2190";
            case -4: return "\u2192";
            case -5: return "OK";
            case -6: return "L";
            case -7: return "R";
            default: return validCode(code) ? Character.toString((char) code) : "?";
        }
    }
}
