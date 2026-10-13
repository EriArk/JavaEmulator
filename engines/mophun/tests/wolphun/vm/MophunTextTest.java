package wolphun.vm;

import java.util.Arrays;
import org.junit.Test;
import wolphun.Screen;
import static org.junit.Assert.*;

public class MophunTextTest {
    @Test public void relativeSizesAreOrderedAndExplicitPixelsAreRespected() {
        Screen screen = new Screen(128, 160, 1);
        screen.selectFont(1, 0); int small = screen.fontHeight();
        screen.selectFont(0, 0); int normal = screen.fontHeight();
        screen.selectFont(2, 0); int large = screen.fontHeight();
        assertTrue(small < normal && normal < large);
        screen.selectFont(0x80000008, 0);
        assertEquals(small, screen.fontHeight());
    }

    @Test public void tinyTextKeepsCoverageAndOutlineUsesBackgroundColor() {
        Screen screen = new Screen(128, 160, 1);
        Arrays.fill(screen.fb, 0xff000000);
        screen.selectFont(0x80000008, 0);
        screen.drawText(0, 0, "New game", 0xffffffff);
        int intermediate = 0;
        for(int p : screen.fb) if(p != 0xff000000 && p != 0xffffffff) intermediate++;
        assertTrue("Thin strokes must not be discarded", intermediate > 0);
        Arrays.fill(screen.fb, 0xff0000ff);
        screen.selectFont(0, 0x08000000);
        screen.drawText(0, 0, "New game", 0xffffff00, 0xffff0000);
        boolean red = false, yellow = false;
        for(int p : screen.fb) {
            if(((p>>16)&255) > 100 && ((p>>8)&255) == 0) red = true;
            if(((p>>16)&255) > 100 && ((p>>8)&255) > 100) yellow = true;
        }
        assertTrue(red && yellow);
    }
}
