package ru.playsoftware.j2meloader.runtime;

import org.junit.Test;
import static org.junit.Assert.*;

public class EmulationClockTest {
    @Test public void continuousAcrossRatesAndPause() {
        long[] now = {1000};
        EmulationClock clock = new EmulationClock(() -> now[0]);
        now[0] += 100; assertEquals(100, clock.getAsLong());
        clock.speed(50); assertEquals(100, clock.getAsLong());
        now[0] += 100; assertEquals(150, clock.getAsLong());
        clock.pause(true); now[0] += 900; clock.speed(200);
        assertEquals(150, clock.getAsLong());
        clock.pause(false); now[0] += 100; assertEquals(350, clock.getAsLong());
        clock.speed(100); now[0] += 100; assertEquals(450, clock.getAsLong());
    }
    @Test public void fractionalTimeIsNotLostOnFrequentReads() {
        long[] now = {0}; EmulationClock clock = new EmulationClock(() -> now[0]);
        clock.speed(75);
        for (int i = 0; i < 100; i++) { now[0]++; clock.getAsLong(); }
        assertEquals(75, clock.getAsLong());
    }
    @Test public void waitsAndInvalidSettings() {
        EmulationClock clock = new EmulationClock(() -> 0);
        for (int speed : new int[]{50,75,100,125,150,200}) {
            clock.speed(speed); assertEquals(1000, clock.hostDelay(speed * 10));
        }
        clock.speed(0); assertEquals(100, clock.speed());
        clock.speed(500); assertEquals(200, clock.speed());
        clock.speed(1); assertEquals(50, clock.speed());
        assertEquals(0, clock.hostDelay(0));
    }
}
