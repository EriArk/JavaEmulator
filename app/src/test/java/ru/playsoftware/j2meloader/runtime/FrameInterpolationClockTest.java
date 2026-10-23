package ru.playsoftware.j2meloader.runtime;
import org.junit.Test;
import static org.junit.Assert.*;

public class FrameInterpolationClockTest {
    @Test public void finishingAPairCannotRewindAndNextSourcePairCanInterpolate() {
        FrameInterpolationClock c=new FrameInterpolationClock(); c.frame(0); c.frame(50_000_000);
        assertEquals(0.5f,c.phase(75_000_000),0.0001f);
        c.finish(); assertEquals(1f,c.phase(75_000_000),0);
        c.frame(100_000_000); assertEquals(0.5f,c.phase(125_000_000),0.0001f);
    }
    @Test public void twentyAndTwentyFiveFpsDoNotFinishThenHoldBeforeNextFrame() {
        for(long interval:new long[]{40_000_000,50_000_000}) {
            FrameInterpolationClock c=new FrameInterpolationClock(); c.frame(0); c.frame(interval);
            assertEquals(0.75f,c.phase(interval+interval*3/4),0.0001f);
            assertEquals(1f,c.phase(interval*2),0);
        }
    }

    @Test public void stalledSubmissionDoesNotReplayTheWholeLongIntervalAtHigherSpeed() {
        FrameInterpolationClock c=new FrameInterpolationClock(); c.frame(0); c.frame(150_000_000);
        assertEquals(2f/3f,c.phase(150_000_000),0.0001f);
        assertEquals(5f/6f,c.phase(175_000_000),0.0001f);
        assertEquals(1f,c.phase(200_000_000),0);
    }

    @Test public void repeatedOrEarlierDrawTimesCannotRewindPresentedMotion() {
        FrameInterpolationClock c=new FrameInterpolationClock(); c.frame(0); c.frame(50_000_000);
        float phase=c.phase(80_000_000);
        assertEquals(phase,c.phase(75_000_000),0);
    }

    @Test public void slowGamesReachTheCurrentFrameWithinTheLatencyBudget() {
        for(long interval:new long[]{40_000_000,50_000_000,100_000_000,200_000_000}) {
            FrameInterpolationClock c=new FrameInterpolationClock(); c.frame(0); c.frame(interval);
            assertEquals(1f,c.phase(interval+50_000_000),0);
        }
    }
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
        assertEquals(0.75f,c.phase(199_000_000),0);
        assertEquals(1f,c.phase(250_000_000),0);
    }

    @Test public void steadyTwentyTwentyFiveAndThirtyFpsAdvanceEvenlyAtVsync() {
        for(long interval:new long[]{50_000_000,40_000_000,33_333_333})
        for(long refresh:new long[]{16_666_667,11_111_111,8_333_333}) {
            FrameInterpolationClock c=new FrameInterpolationClock(); c.frame(0); c.phase(0);
            long latest=0;
            for(long now=refresh;now<5_000_000_000L;now+=refresh) {
                while(latest+interval<=now) { latest+=interval; c.frame(latest); }
                float phase=c.phase(now);
                if(latest>=interval*2) {
                    double position=latest-interval+phase*(double)interval;
                    assertEquals("No fast-then-hold cadence at interval "+interval,now-interval,position,4);
                }
            }
        }
    }

    @Test public void variableCadenceStaysMonotonicAndWithinAvailableFrameBudget() {
        FrameInterpolationClock c=new FrameInterpolationClock(); c.frame(0); c.phase(0);
        long latest=0; double shown=0;
        for(long interval:new long[]{50,33,40,25,60,150,201,16,50,30,42}) {
            interval*=1_000_000; long previous=latest; latest+=interval; c.frame(latest);
            for(long offset:new long[]{0,5_000_000,15_000_000}) {
                double position=previous+c.phase(latest+offset)*(double)interval;
                assertTrue("Never go backwards",position+4>=shown);
                assertTrue("Never extrapolate beyond the newest pixels",position<=latest+4);
                assertTrue("At most 50 ms behind the newest available frame",latest-position<=50_000_004);
                shown=position;
            }
        }
    }

    @Test public void lateRenderingConsumesTheBudgetInsteadOfStartingAnotherDelay() {
        FrameInterpolationClock c=new FrameInterpolationClock(); c.frame(0); c.phase(0);
        c.frame(50_000_000);
        assertEquals(0.8f,c.phase(90_000_000),0.0001f);
        assertEquals(1f,c.phase(110_000_000),0);
        c.frame(100_000_000);
        assertEquals(0.2f,c.phase(110_000_000),0.0001f);
    }

    @Test public void negativeNanoTimeOriginAndDiscontinuitiesAreSafe() {
        FrameInterpolationClock c=new FrameInterpolationClock(); c.frame(-1_000_000_000);
        assertEquals(1f,c.phase(-1_000_000_000),0);
        c.frame(-950_000_000); assertEquals(0.5f,c.phase(-925_000_000),0.0001f);
        c.frame(-950_000_000); assertEquals(1f,c.phase(-950_000_000),0);
        c.frame(-960_000_000); assertEquals(1f,c.phase(-960_000_000),0);
        c.frame(-910_000_000); assertEquals(0.5f,c.phase(-885_000_000),0.0001f);
        c.reset(); assertEquals(1f,c.phase(0),0);
    }
}
