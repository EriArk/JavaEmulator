/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.input;
import org.junit.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;
import static ru.playsoftware.j2meloader.input.RightStickAxes.*;

public class RightStickAxesTest {
    private final Map<Integer, Range> ranges = new HashMap<>();
    private void pair(int x, int y, float min) {
        ranges.put(x, new Range(min, 1, .1f)); ranges.put(y, new Range(min, 1, .1f));
    }
    @Test public void standardAndroidPairWinsWhenBothPairsExist() {
        pair(Z, RZ, -1); pair(RX, RY, -1);
        assertEquals(Z, select(ranges::get).x); assertEquals(RZ, select(ranges::get).y);
    }
    @Test public void unipolarZTriggersUseAlternateStick() {
        pair(Z, RZ, 0); pair(RX, RY, -1);
        assertEquals(RX, select(ranges::get).x); assertEquals(RY, select(ranges::get).y);
    }
    @Test public void absentIncompleteAndTriggerOnlyMetadataDoNotInventAStick() {
        assertEquals(-1, select(ranges::get).x);
        ranges.put(Z, new Range(-1,1,0)); assertEquals(-1, select(ranges::get).x);
        pair(Z,RZ,0); pair(17,18,0); pair(22,23,0); assertEquals(-1, select(ranges::get).x);
    }
    @Test public void invalidRangeDoesNotSelectPair() {
        pair(Z,RZ,-1); ranges.put(RZ,new Range(-1,Float.NaN,0));
        assertEquals(-1,select(ranges::get).x);
    }
    @Test public void normalizationRespectsFlatAndBothRangeHalves() {
        Range r=new Range(-100,200,10);
        assertEquals(0,r.value(9),0); assertEquals(-.5f,r.value(-50),0);
        assertEquals(.5f,r.value(100),0); assertEquals(1,r.value(250),0);
        assertEquals(0,r.value(Float.NaN),0); assertEquals(0,r.value(Float.POSITIVE_INFINITY),0);
    }
}
