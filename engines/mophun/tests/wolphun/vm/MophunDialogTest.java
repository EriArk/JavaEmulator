package wolphun.vm;

import android.os.SystemClock;
import java.util.Arrays;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import wolphun.Screen;
import static org.junit.Assert.*;

public class MophunDialogTest {
    private void frames(Screen screen, int count) {
        int end = screen.flips + count;
        long deadline = SystemClock.elapsedRealtime() + 3000;
        while (screen.flips < end && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(5);
        assertTrue("Dialog stopped presenting", screen.flips >= end);
    }

    private void check(int flags, int button, int expected, boolean selectNo) throws Exception {
        check(flags, button, expected, selectNo, 128, 160);
    }
    private void check(int flags, int button, int expected, boolean selectNo, int width, int height) throws Exception {
        Screen screen = new Screen(width,height,1);
        Arrays.fill(screen.fb, 0xff314159);
        FutureTask<Integer> result = new FutureTask<>(() -> screen.showMessageBox(flags, null, "Leave game?"));
        Thread thread = new Thread(result, "dialog-test");
        thread.start();
        try {
            frames(screen, 3);
            int[] pixels = new int[width*height];
            assertTrue(screen.copyPresented(pixels));
            for (int x = 3; x < width-3; x++) {
                assertEquals("Action labels must not overlap the border", 0xffffffff, pixels[(height-4)*width+x]);
            }
            if (selectNo) {
                screen.setButtonBit(Screen.RIGHT, true); frames(screen, 2); screen.clearButtons();
            }
            screen.setButtonBit(button, true); frames(screen, 2); screen.clearButtons();
            assertEquals(expected, result.get(3, TimeUnit.SECONDS).intValue());
            for (int pixel : screen.fb) assertEquals("Underlying frame must be restored", 0xff314159, pixel);
        } finally { thread.interrupt(); thread.join(3000); assertFalse(thread.isAlive()); }
    }

    @Test public void yesNoAcceptsFire() throws Exception { check(0x42, Screen.FIRE, 1, false); }
    @Test public void yesNoRejectsFireTwo() throws Exception { check(0x42, Screen.FIRE2, 0, false); }
    @Test public void yesNoRejectsBack() throws Exception { check(0x42, Screen.SELECT, 0, false); }
    @Test public void rightThenFireSelectsNo() throws Exception { check(0x42, Screen.FIRE, 0, true); }
    @Test public void okCancelCanBeCancelled() throws Exception { check(4, Screen.FIRE2, 0, false); }
    @Test public void plainOkAcceptsSelect() throws Exception { check(0, Screen.SELECT, 1, false); }
    @Test public void compactDialogKeepsItsActionsInsideTheBorder() throws Exception { check(4, Screen.FIRE2, 0, false, 101, 80); }

    @Test public void interruptionCancelsAndOpeningKeyMustBeReleased() throws Exception {
        Screen screen = new Screen(128,160,1);
        screen.setButtonBit(Screen.FIRE, true);
        FutureTask<Integer> result = new FutureTask<>(() -> screen.showMessageBox(2,null,"Leave game?"));
        Thread thread = new Thread(result);
        thread.start();
        try {
            frames(screen, 5);
            assertFalse("Held opening key must not confirm", result.isDone());
            thread.interrupt();
            assertEquals(0, result.get(3, TimeUnit.SECONDS).intValue());
        } finally { thread.interrupt(); thread.join(3000); assertFalse(thread.isAlive()); }
    }
}
