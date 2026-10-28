package ru.playsoftware.j2meloader.input;

import java.util.HashMap;
import org.junit.Test;
import static org.junit.Assert.*;

public class TouchMappingsTest {
    @Test public void absentAndInvalidMappingsKeepDefaults() {
        assertArrayEquals(new int[]{-1}, TouchMappings.resolve(null, "up"));
        HashMap<String, Integer> map = new HashMap<>();
        map.put("up", 10000); map.put("left", null); map.put("down", 0);
        assertArrayEquals(new int[]{-1,-3}, TouchMappings.resolve(map, "up_left"));
        assertArrayEquals(new int[]{-2}, TouchMappings.resolve(map, "down"));
        assertEquals(-1, TouchMappings.index("literal:50"));
    }
    @Test public void numpadDiagonalsFollowDirectionsUnlessExplicitlyOverridden() {
        HashMap<String,Integer> map = TouchMappings.template(true);
        assertArrayEquals(new int[]{50,52}, TouchMappings.resolve(map,"up_left"));
        map.put("up_left", 55);
        assertArrayEquals(new int[]{55}, TouchMappings.resolve(map,"up_left"));
        map.remove("up_left"); map.put("left",50);
        assertArrayEquals(new int[]{50}, TouchMappings.resolve(map,"up_left"));
        assertArrayEquals(new int[]{53}, TouchMappings.resolve(map,"fire"));
    }
    @Test public void templatesAreDetachedAndNeverMapAppCommands() {
        HashMap<String,Integer> first = TouchMappings.template(true);
        first.put("up",-6);
        assertEquals(Integer.valueOf(50),TouchMappings.template(true).get("up"));
        assertTrue(TouchMappings.template(false).isEmpty());
        for (int code : new int[]{0,4,82,10000,-8,Integer.MAX_VALUE}) assertFalse(TouchMappings.validCode(code));
        for (int code : new int[]{-7,-1,48,57,35,42}) assertTrue(TouchMappings.validCode(code));
    }
}
