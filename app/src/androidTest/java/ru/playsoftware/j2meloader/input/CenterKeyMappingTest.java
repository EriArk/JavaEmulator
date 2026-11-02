package ru.playsoftware.j2meloader.input;

import android.util.SparseIntArray;
import android.view.KeyEvent;
import javax.microedition.lcdui.keyboard.KeyMapper;
import org.junit.Test;
import static org.junit.Assert.*;

public class CenterKeyMappingTest {
    @Test public void centerHasTheSameDefaultAsEnterAndAllowsUserOverride() {
        SparseIntArray defaults = KeyMapper.getDefaultKeyMap();
        assertNotEquals(0, defaults.get(KeyEvent.KEYCODE_DPAD_CENTER));
        assertEquals(defaults.get(KeyEvent.KEYCODE_ENTER), defaults.get(KeyEvent.KEYCODE_DPAD_CENTER));
        SparseIntArray custom = new SparseIntArray(); custom.put(KeyEvent.KEYCODE_DPAD_CENTER, '5');
        assertEquals('5', KeyMapper.resolveMappings(custom).get(KeyEvent.KEYCODE_DPAD_CENTER));
        custom.put(KeyEvent.KEYCODE_DPAD_CENTER, 0);
        assertEquals(0, KeyMapper.resolveMappings(custom).get(KeyEvent.KEYCODE_DPAD_CENTER));
    }
}
