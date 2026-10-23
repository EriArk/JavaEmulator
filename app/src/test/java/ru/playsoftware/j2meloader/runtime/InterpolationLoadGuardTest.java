package ru.playsoftware.j2meloader.runtime;

import org.junit.Test;
import static org.junit.Assert.*;

public class InterpolationLoadGuardTest {
    private static final long BUDGET = 16_666_666L;

    @Test public void healthyFramesAndOneCompilationSpikeStayEnabled() {
        InterpolationLoadGuard g=new InterpolationLoadGuard();
        assertTrue(g.allow(0,false));
        g.submitted(1,200_000_000,BUDGET);
        assertTrue(g.allow(2,false));
        for(int i=0;i<100;i++) {
            g.submitted(i+3,5_000_000,BUDGET);
            assertTrue(g.allow(i+4,false));
        }
    }

    @Test public void sustainedCpuPressurePausesOnlyOptionalWork() {
        InterpolationLoadGuard g=new InterpolationLoadGuard();
        for(int i=0;i<3;i++) g.submitted(i,20_000_000,BUDGET);
        assertFalse(g.allow(3,false));
        assertFalse(g.allow(600_000_000,false)); // Time alone is not recovery evidence.
    }

    @Test public void overdueGpuFenceSuppressesImmediately() {
        InterpolationLoadGuard g=new InterpolationLoadGuard();
        assertFalse(g.allow(0,true));
        assertFalse(g.allow(1,false));
    }

    @Test public void recoveryNeedsBothCooldownAndHealthySubmissions() {
        InterpolationLoadGuard g=new InterpolationLoadGuard(); g.allow(0,true);
        for(int i=1;i<=3;i++) g.submitted(i,1_000_000,BUDGET);
        assertFalse(g.allow(499_999_999,false));
        assertTrue(g.allow(500_000_000,false));
    }

    @Test public void repeatedPressureExtendsCooldownAndClearsRecoveryEvidence() {
        InterpolationLoadGuard g=new InterpolationLoadGuard(); g.allow(0,true);
        for(int i=1;i<=3;i++) g.submitted(i,1_000_000,BUDGET);
        assertFalse(g.allow(450_000_000,true));
        assertFalse(g.allow(950_000_000,false));
        for(int i=1;i<=3;i++) g.submitted(950_000_000+i,1_000_000,BUDGET);
        assertTrue(g.allow(950_000_004,false));
    }

    @Test public void borderlineCostIsNotEnoughForRecovery() {
        InterpolationLoadGuard g=new InterpolationLoadGuard(); g.allow(0,true);
        for(int i=0;i<20;i++) g.submitted(i,14_000_000,BUDGET);
        assertFalse(g.allow(1_000_000_000,false));
    }

    @Test public void pressureUsesTheHostRefreshBudget() {
        for(float hz:new float[]{30,60,90,120,240}) {
            InterpolationLoadGuard g=new InterpolationLoadGuard(); long budget=InterpolationLoadGuard.refreshPeriod(hz);
            for(int i=0;i<3;i++) g.submitted(i,budget,budget);
            assertFalse(g.allow(3,false));
            g.reset(); assertTrue(g.allow(4,false));
            for(int i=0;i<10;i++) g.submitted(i,budget/2,budget);
            assertTrue(g.allow(11,false));
        }
    }

    @Test public void unavailableRefreshRateHasConservativeFallback() {
        for(float hz:new float[]{0,-1,Float.NaN,Float.POSITIVE_INFINITY,1000})
            assertEquals(BUDGET,InterpolationLoadGuard.refreshPeriod(hz));
    }

    @Test public void resetAndNegativeClockOriginDoNotLatchSuppression() {
        InterpolationLoadGuard g=new InterpolationLoadGuard(); g.allow(-1_000_000_000,true);
        for(int i=0;i<3;i++) g.submitted(-900_000_000+i,1_000_000,BUDGET);
        assertFalse(g.allow(-500_000_001,false)); assertTrue(g.allow(-500_000_000,false));
        g.allow(0,true); g.reset(); assertTrue(g.allow(1,false));
    }
}
