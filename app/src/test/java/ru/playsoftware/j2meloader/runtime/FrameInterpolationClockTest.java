package ru.playsoftware.j2meloader.runtime;
import org.junit.Test;
import static org.junit.Assert.*;

public class FrameInterpolationClockTest {
    @Test public void startupAndResetShowRealFrame() {
        FrameInterpolationClock c=new FrameInterpolationClock();
        c.frame(0); assertEquals(1f,c.phase(0),0);
        c.frame(50_000_000); assertEquals(0f,c.phase(50_000_000),0);
        c.reset(); c.frame(60_000_000); assertEquals(1f,c.phase(60_000_000),0);
    }
    @Test public void interpolatesWithoutChangingInputTime() {
        FrameInterpolationClock c=new FrameInterpolationClock(); c.frame(0); c.frame(50_000_000);
        assertEquals(0.5f,c.phase(75_000_000),0.0001);
        assertEquals(1f,c.phase(100_000_000),0);
        assertEquals(1f,c.phase(200_000_000),0);
    }
    @Test public void pausesAndHighRateStreamsDoNotInterpolate() {
        FrameInterpolationClock c=new FrameInterpolationClock(); c.frame(0); c.frame(16_000_000);
        assertEquals(1f,c.phase(16_000_000),0);
        c.frame(500_000_000); assertEquals(1f,c.phase(500_000_000),0);
    }
    @Test public void delayIsBoundedAndNeverNegative() {
        FrameInterpolationClock c=new FrameInterpolationClock(); c.frame(0); c.frame(200_000_000);
        assertEquals(0f,c.phase(199_000_000),0);
        assertEquals(1f,c.phase(300_000_000),0);
    }
}
