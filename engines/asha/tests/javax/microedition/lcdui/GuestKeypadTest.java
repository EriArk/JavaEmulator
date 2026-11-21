/* SPDX-License-Identifier: GPL-3.0-only */
package javax.microedition.lcdui;

import android.os.SystemClock;
import android.view.MotionEvent;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.test.platform.app.InstrumentationRegistry;
import com.nokia.mid.ui.VirtualKeyboard;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class GuestKeypadTest {
    @Test public void keypadChangesReleaseHeldKeysAndUsePhoneKeyCodes() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            List<Integer> events = new ArrayList<>();
            boolean[] closed = {false};
            GuestKeypadView panel = new GuestKeypadView(InstrumentationRegistry.getInstrumentation()
                    .getTargetContext(), (key, action) -> { events.add(key); events.add(action); },
                    () -> closed[0] = true);
            panel.configure(VirtualKeyboard.VKB_TYPE_ITUT, VirtualKeyboard.VKB_MODE_ALPHA_LOWER_CASE);
            TextView two = key(panel, 0, 1);
            assertEquals("2 abc", two.getText().toString());
            touch(two, MotionEvent.ACTION_DOWN);
            panel.configure(VirtualKeyboard.VKB_TYPE_ITUT, VirtualKeyboard.VKB_MODE_ALPHA_UPPER_CASE_LOCKED);
            touch(two, MotionEvent.ACTION_UP);
            assertEquals(Arrays.asList(50, 0, 50, 1), events);
            assertEquals("2 ABC", key(panel, 0, 1).getText().toString());
            panel.configure(VirtualKeyboard.VKB_TYPE_ITUT, VirtualKeyboard.VKB_MODE_NUMERIC);
            assertEquals("2", key(panel, 0, 1).getText().toString());
            panel.configure(VirtualKeyboard.VKB_TYPE_GAME, VirtualKeyboard.VKB_MODE_DEFAULT);
            key(panel, 1, 1).performClick();
            assertEquals(Arrays.asList(50, 0, 50, 1, Canvas.KEY_FIRE, 0, Canvas.KEY_FIRE, 1), events);
            touch(key(panel, 0, 3), MotionEvent.ACTION_DOWN);
            touch(key(panel, 0, 3), MotionEvent.ACTION_CANCEL);
            assertFalse(closed[0]);
            key(panel, 0, 3).performClick();
            assertTrue(closed[0]);
        });
    }

    private static TextView key(GuestKeypadView panel, int row, int column) {
        return (TextView) ((LinearLayout) panel.getChildAt(row)).getChildAt(column);
    }

    private static void touch(TextView key, int action) {
        long now = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(now, now, action, 2, 2, 0);
        try { key.onTouchEvent(event); } finally { event.recycle(); }
    }
}
