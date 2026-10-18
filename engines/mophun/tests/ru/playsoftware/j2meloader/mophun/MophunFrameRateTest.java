package ru.playsoftware.j2meloader.mophun;

import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import org.junit.Test;
import wolphun.Screen;
import javax.microedition.lcdui.FramePacer;
import static org.junit.Assert.*;

public class MophunFrameRateTest {
    @Test public void presentedFramesUseHostPacerWithoutTheOldSixtyFpsCap() {
        Screen screen = new Screen(101, 80, 1);
        int[] rate = {15};
        FramePacer pacer = new FramePacer(() -> rate[0]);
        screen.hostFramePacer = pacer::awaitFrame;
        for (int fps : new int[]{15, 30, 60, 0, 20}) {
            rate[0] = fps; pacer.reset(); screen.present();
            long start = System.nanoTime();
            for (int i = 0; i < 12; i++) screen.present();
            long elapsed = System.nanoTime() - start;
            if (fps > 0) assertTrue("Early frames at " + fps, elapsed >= 12_000_000_000L / fps - 5_000_000L);
            else assertTrue("Unlimited retained a hidden 60 FPS cap", elapsed < 150_000_000L);
        }
        int[] frame = new int[101 * 80]; assertTrue(screen.copyPresented(frame));
    }

    @Test public void sessionRetainsRateBeforeWorkerStarts() throws Exception {
        File root = InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir();
        try (MophunSession session = new MophunSession(new File(root, "unused.mpn"), root, 0, 101, 80)) {
            assertEquals(60, session.getFrameRateLimit());
            session.setFrameRateLimit(15); assertEquals(15, session.getFrameRateLimit());
            session.pause(true); session.pause(false); assertEquals(15, session.getFrameRateLimit());
            session.setFrameRateLimit(0); assertEquals(0, session.getFrameRateLimit());
        }
    }
}
