package ru.playsoftware.j2meloader.mophun;

import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import org.junit.Test;
import wolphun.Screen;
import javax.microedition.lcdui.FramePacer;
import static org.junit.Assert.*;

public class MophunFrameRateTest {
    @Test public void cpuBudgetIsIndependentFromClockAndFrameLimit() throws Exception {
        File root=InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir();
        try(MophunSession session=new MophunSession(new File(root,"unused.mpn"),root,0,101,80)) {
            session.setFrameRateLimit(30); session.setEmulationSpeed(100);
            for(int budget:new int[]{100,150,200,300}) {
                session.setCpuBudget(budget); assertEquals(budget,session.getCpuBudget());
                assertEquals(100,session.getEmulationSpeed()); assertEquals(30,session.getFrameRateLimit());
            }
            session.pause(true); session.pause(false); assertEquals(300,session.getCpuBudget());
            session.setCpuBudget(-1); assertEquals(100,session.getCpuBudget());
            session.setCpuBudget(Integer.MAX_VALUE); assertEquals(300,session.getCpuBudget());
        }
    }
    @Test public void speedChangesGameClockButNotFrameCapOrPausedTime() throws Exception {
        long[] real = {0}; MophunClock clock = new MophunClock(() -> real[0]);
        clock.speed(50); real[0] = 200; assertEquals(100,clock.getAsLong());
        clock.pause(true); real[0] = 500; clock.speed(200); assertEquals(100,clock.getAsLong());
        clock.pause(false); real[0] = 600; assertEquals(300,clock.getAsLong());
        File root = InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir();
        try (MophunSession session = new MophunSession(new File(root,"unused.mpn"),root,0,101,80)) {
            session.setEmulationSpeed(50); assertEquals(50,session.getEmulationSpeed());
            assertEquals(60,session.getFrameRateLimit());
            session.setFrameRateLimit(15); assertEquals(50,session.getEmulationSpeed());
            session.setEmulationSpeed(400); assertEquals(400,session.getEmulationSpeed());
            assertEquals(15,session.getFrameRateLimit());
            session.setEmulationSpeed(75); assertEquals(75,session.getEmulationSpeed());
        }
    }
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
