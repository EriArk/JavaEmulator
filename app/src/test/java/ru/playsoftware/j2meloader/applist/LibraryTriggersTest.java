package ru.playsoftware.j2meloader.applist;

import org.junit.Test;
import static org.junit.Assert.*;

public class LibraryTriggersTest {
    @Test public void mirroredKeyAndAxisProduceOneStepUntilBothRelease() {
        LibraryTriggers gate = new LibraryTriggers();
        assertEquals(1, gate.key(1, true, true));
        assertEquals(0, gate.axis(1, true, 1));
        assertEquals(0, gate.key(1, true, true));
        assertEquals(0, gate.key(1, true, false));
        assertEquals(0, gate.axis(1, true, .7f));
        assertEquals(0, gate.axis(1, true, 0));
        assertEquals(1, gate.axis(1, true, 1));
        assertEquals(0, gate.key(1, true, true));
    }
    @Test public void analogHasHysteresisAndSidesAreIndependent() {
        LibraryTriggers gate = new LibraryTriggers();
        assertEquals(0, gate.axis(1, false, .6f));
        assertEquals(-1, gate.axis(1, false, .7f));
        assertEquals(0, gate.axis(1, false, .5f));
        assertEquals(0, gate.axis(1, false, .8f));
        assertEquals(1, gate.axis(1, true, 1));
        assertEquals(-1, gate.key(2, false, true));
        assertEquals(0, gate.axis(1, false, .2f));
        assertEquals(-1, gate.axis(1, false, 1));
        gate.reset();
        assertEquals(-1, gate.key(2, false, true));
    }
}
