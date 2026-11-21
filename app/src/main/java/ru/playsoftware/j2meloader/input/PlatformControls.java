/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.input;

import ru.playsoftware.j2meloader.config.ProfileModel;
import static javax.microedition.lcdui.Canvas.*;

/** The same guest keys drive the touch surface and physical mapping editor. */
public final class PlatformControls {
    public static final int APP1 = 0x1201, APP2 = 0x1202, APP3 = 0x1203, APP4 = 0x1204;
    public static final int DELETE = 0x1210, INSERT = 0x1211, F1 = 0x1212, F2 = 0x1213;

    public static final class Button {
        public final int code;
        public final String face, name;
        private Button(int code, String face, String name) {
            this.code = code;
            this.face = face;
            this.name = name;
        }
    }
    private static Button b(int code, String name) { return new Button(code, name, name); }
    private static Button b(int code, String face, String name) { return new Button(code, face, name); }
    static final Button[] DIRECTIONS = {b(KEY_UP,"↑","Up"), b(KEY_DOWN,"↓","Down"),
            b(KEY_LEFT,"←","Left"), b(KEY_RIGHT,"→","Right")};
    private static final Button A = b(KEY_FIRE,"A"), B = b(KEY_SOFT_RIGHT,"B");
    private static final Button START = b('9',"Start"), SELECT = b('7',"Select");
    private static final Button L = b('*',"L"), R = b('#',"R");

    // Each row occupies the right-hand action block. Empty cells preserve the face-button geometry.
    final Button[][] rows;
    private final boolean retro;
    final String prefix;
    private PlatformControls(String id, boolean retro, Button[]... rows) {
        this.prefix = "platform:" + id + ":";
        this.retro = retro;
        this.rows = rows;
    }
    int touchTarget(ProfileModel profile, int code) {
        Integer value = profile.platformTouchMappings == null ? null : profile.platformTouchMappings.get(prefix + code);
        return value != null && find(value) != null ? value : code;
    }
    private static Button[] row(Button... buttons) { return buttons; }

    private static final PlatformControls TWO_BUTTON = new PlatformControls("two_button", true,
            row(B,A), row(SELECT,START));
    private static final PlatformControls GP32 = new PlatformControls("gp32", true,
            row(L,R), row(B,A), row(SELECT,START));
    private static final PlatformControls DINGOO = new PlatformControls("dingoo", true,
            row(L,R), row(null,b(KEY_SOFT_LEFT,"X"),null), row(b('5',"Y"),null,A),
            row(null,B,null), row(SELECT,START));
    private static final PlatformControls GIZMONDO = new PlatformControls("gizmondo", false,
            row(b('#',"L"),b(KEY_CLEAR,"R")), row(null,b(KEY_SOFT_LEFT,"■","Stop"),null),
            row(b('*',"◀◀","Rewind"),null,b(KEY_SOFT_RIGHT,"▶▶","Forward")),
            row(null,b(KEY_FIRE,"▶","Play"),null),
            row(b('2',"Home"),b('4',"Vol","Volume"),b('6',"Light","Brightness")),
            row(b('8',"Zone","Geofence"),b('0',"Power")));
    private static final PlatformControls CYBIKO = new PlatformControls("cybiko", true,
            row(b(KEY_FIRE,"Enter"),b(KEY_SOFT_RIGHT,"Esc"),b(KEY_SOFT_LEFT,"Select")),
            row(b('5',"Space"),b('7',"Tab"),b('9',"Menu")), row(b('*',"Shift"),b('#',"Help")),
            row(b(DELETE,"Del","Delete"),b(INSERT,"Ins","Insert"),b(F1,"F1"),b(F2,"F2")));
    private static final PlatformControls PORTFOLIO = new PlatformControls("portfolio", false,
            row(b(KEY_FIRE,"Enter"),b(KEY_SOFT_LEFT,"Space"),b(KEY_SOFT_RIGHT,"Esc")),
            row(b(KEY_CLEAR,"⌫","Backspace"),b('*',"Tab"),b('#',"F1")));
    private static final PlatformControls POCKET_PC = new PlatformControls("pocket_pc", false,
            row(b(KEY_FIRE,"Action"),b(KEY_SOFT_LEFT,"Space"),b(KEY_SOFT_RIGHT,"Esc")),
            row(b(KEY_CLEAR,"⌫","Backspace"),b('*',"Tab"),b('#',"Ctrl")),
            row(b(APP1,"App 1"),b(APP2,"App 2")), row(b(APP3,"App 3"),b(APP4,"App 4")));
    private static final PlatformControls PALM = new PlatformControls("palm", true,
            row(b(KEY_FIRE,"App 1"),b(KEY_SOFT_RIGHT,"App 2")),
            row(b(KEY_SOFT_LEFT,"App 3"),b('5',"App 4")),row(b('9',"Select")));
    private static final PlatformControls PALM_CLASSIC = new PlatformControls("palm_classic", true,
            row(b(KEY_FIRE,"App 1"),b(KEY_SOFT_RIGHT,"App 2")),
            row(b(KEY_SOFT_LEFT,"App 3"),b('5',"App 4")));
    private static final PlatformControls FLASH = new PlatformControls("flash", false,
            row(b(KEY_FIRE,"Enter"),b(KEY_CLEAR,"⌫","Backspace")),
            row(b(KEY_SOFT_LEFT,"PgUp","Page Up"),b(KEY_SOFT_RIGHT,"PgDn","Page Down")));
    private static final PlatformControls WEBOS = new PlatformControls("webos", false,
            row(b(KEY_FIRE,"Enter"),b(KEY_SOFT_LEFT,"Space"),b(KEY_SOFT_RIGHT,"Esc")));
    private static final PlatformControls FIREFOX = new PlatformControls("firefox", false,
            row(b(KEY_FIRE,"Enter"),b(KEY_CLEAR,"⌫","Backspace")),
            row(b(KEY_SOFT_LEFT,"Soft L","Soft Left"),b(KEY_SOFT_RIGHT,"Soft R","Soft Right")));

    private static PlatformControls phone(boolean calls) {
        Button[] special = calls ? row(b(KEY_SEND,"Call"), b(KEY_CLEAR,"CLR","Clear"), b(KEY_END,"End")) : row(b(KEY_CLEAR,"⌫","Backspace"));
        return new PlatformControls(calls ? "wipi" : "keypad", false,
                row(b(KEY_SOFT_LEFT,"L","Soft Left"), b(KEY_FIRE,"OK"), b(KEY_SOFT_RIGHT,"R","Soft Right")),
                row(b('1',"1"),b('2',"2"),b('3',"3")), row(b('4',"4"),b('5',"5"),b('6',"6")),
                row(b('7',"7"),b('8',"8"),b('9',"9")), row(b('*',"*"),b('0',"0"),b('#',"#")), special);
    }
    private static final PlatformControls WIPI = phone(true), KEYPAD = phone(false);
    boolean phoneKeypad() { return this == WIPI || this == KEYPAD; }

    public static PlatformControls forEngine(String engine) {
        if (engine == null) return null;
        switch (engine) {
            case "wipi": return WIPI;
            case "mre": case "kaios": return KEYPAD;
            case "piece": case "supervision": case "megaduck": case "gamate": case "gameking": return TWO_BUTTON;
            case "gp32": return GP32;
            case "dingoo": return DINGOO;
            case "gizmondo": return GIZMONDO;
            case "cybiko": return CYBIKO;
            case "portfolio": return PORTFOLIO;
            case "pocketpc": return POCKET_PC;
            case "palm": return PALM;
            case "palm-classic": return PALM_CLASSIC;
            case "flash": return FLASH;
            case "webos": return WEBOS;
            case "firefox": return FIREFOX;
            default: return null;
        }
    }

    java.util.List<Button> buttons() {
        java.util.List<Button> result = new java.util.ArrayList<>();
        for (Button direction : DIRECTIONS) if (supportsDirection(direction.code)) result.add(direction);
        for (Button[] row : rows) for (Button button : row) if (button != null) result.add(button);
        return result;
    }
    boolean supportsDirection(int code) { return this != PALM_CLASSIC || code == KEY_UP || code == KEY_DOWN; }
    public static boolean extraCode(int code) {
        return code == KEY_CLEAR || code == KEY_SEND || code == KEY_END || code >= APP1 && code <= APP4 || code >= DELETE && code <= F2;
    }
    public static android.util.SparseIntArray resolveMappings(PlatformControls platform, android.util.SparseIntArray overrides) {
        android.util.SparseIntArray map = javax.microedition.lcdui.keyboard.KeyMapper.resolveMappings(overrides);
        if (platform == GIZMONDO) {
            // Match the face positions of a standard Android controller.
            putDefault(map, overrides, android.view.KeyEvent.KEYCODE_BUTTON_X, '*');
            putDefault(map, overrides, android.view.KeyEvent.KEYCODE_BUTTON_Y, KEY_SOFT_LEFT);
            putDefault(map, overrides, android.view.KeyEvent.KEYCODE_BUTTON_L1, '#');
            putDefault(map, overrides, android.view.KeyEvent.KEYCODE_BUTTON_R1, KEY_CLEAR);
        }
        return map;
    }
    private static void putDefault(android.util.SparseIntArray map, android.util.SparseIntArray overrides, int source, int target) {
        if (overrides == null || overrides.indexOfKey(source) < 0) map.put(source, target);
    }

    Button find(int code) {
        for (Button direction : DIRECTIONS) if (direction.code == code && supportsDirection(code)) return direction;
        for (Button[] row : rows) for (Button button : row)
            if (button != null && button.code == code) return button;
        return null;
    }
    Button binding(int code) {
        Button direct = find(code);
        if (direct != null) return direct;
        if (retro) {
            switch (code) {
                case '2': return find(KEY_UP);
                case '8': return find(KEY_DOWN);
                case '4': return find(KEY_LEFT);
                case '6': return find(KEY_RIGHT);
                case '1': return find(KEY_SOFT_LEFT);
                case '0': return find(KEY_SOFT_RIGHT);
                case '3': return find('5');
                case KEY_CLEAR: return find('7');
                case KEY_SEND: return find('9');
            }
        } else if (this == GIZMONDO) {
            switch (code) {
                case '5': return find(KEY_FIRE);
                case '1': return find(KEY_SOFT_LEFT);
                case '3': return find(KEY_SOFT_RIGHT);
                case '7': return find('*');
            }
        }
        return null;
    }
    public static String[] layoutNames(ProfileModel p, String[] original) {
        if (p.platformControls == null) return original;
        return new String[]{"Platform buttons", original[1], original[0]};
    }
    public static int layoutChoice(ProfileModel p) {
        if (p.touchLayout != null && p.touchLayout == TouchDeck.GAMEPAD) return 1;
        return p.platformControls != null && p.forcePhoneKeypad ? 2 : 0;
    }
    public static void selectLayout(ProfileModel p, int index) {
        p.touchLayout = index == 1 ? TouchDeck.GAMEPAD : TouchDeck.PHONE;
        p.forcePhoneKeypad = index == 2;
    }
}
