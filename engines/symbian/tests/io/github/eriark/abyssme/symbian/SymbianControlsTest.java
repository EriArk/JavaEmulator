package io.github.eriark.abyssme.symbian;

import java.util.*;
import org.junit.Test;
import ru.playsoftware.j2meloader.input.ControllerInput;
import static org.junit.Assert.*;

public final class SymbianControlsTest {
    @Test public void phoneKeysTranslateToScanCodes() {
        int[] input = {-1,-2,-3,-4,-5,-6,-7,-8,-10,-11,'0','5','9','*','#'};
        int[] expected = {16,17,14,15,167,164,165,1,196,197,48,53,57,42,35};
        for (int i=0;i<input.length;i++) assertEquals(expected[i],SymbianInput.scan(input[i]));
        assertEquals(0,SymbianInput.scan(ControllerInput.UNBOUND));
        assertEquals(0,SymbianInput.scan(0));
    }
    @Test public void touchAndControllerShareKeyWithoutPrematureRelease() {
        List<String> events = new ArrayList<>();
        SymbianInput keys = new SymbianInput((code,down) -> events.add(code+":"+down));
        ControllerInput.Sink pad = keys.source(), touch = keys.source();
        pad.press('5'); pad.press('5'); touch.press('5'); pad.release('5');
        assertEquals(Collections.singletonList("53:true"),events);
        touch.repeat('5'); touch.release('5'); touch.release('5');
        assertEquals(Arrays.asList("53:true","53:false"),events);
        pad.press(-1); touch.press(-1); keys.clear(); keys.clear(); touch.release(-1);
        assertEquals(Arrays.asList("53:true","53:false","16:true","16:false"),events);
    }
    @Test public void stickAndDpadDiagonalsMapToOneDigitAndClear() {
        Set<Integer> held = new HashSet<>();
        SymbianInput keys = new SymbianInput((code,down) -> { if (down) held.add(code); else held.remove(code); });
        ControllerInput pad = new ControllerInput(code -> {
            if (code == ControllerInput.DPAD_DL || code == ControllerInput.STICK_DL) return '7';
            if (code == 21 || code == ControllerInput.STICK_LEFT) return -3;
            if (code == 20 || code == ControllerInput.STICK_DOWN) return -2;
            return 0;
        },keys.source());
        pad.key(1,21,true,false); pad.key(1,20,true,false);
        assertEquals(Collections.singleton((int)'7'),held);
        pad.clear(); assertTrue(held.isEmpty());
        pad.axes(1,-1,1,0,0,0,0);
        assertEquals(Collections.singleton((int)'7'),held);
        pad.clear(); assertTrue(held.isEmpty());
    }
    @Test public void requiredFilesDistinguishGenerationsAndPlatformFromFirmware() {
        List<SystemRequirements.Row> empty = SystemRequirements.rows(Collections.emptyList(),false);
        assertEquals("No extra files",empty.get(0).status);
        for (int i=1;i<empty.size();i++) assertFalse(empty.get(i).phoneReady);
        SystemFileCatalog.Entry ngage = new SystemFileCatalog.Entry("a","N-Gage","RH-29","epoc6");
        List<SystemRequirements.Row> legacy = SystemRequirements.rows(Collections.singletonList(ngage),true);
        assertTrue(legacy.get(1).phoneReady);
        assertFalse("Saved installer cannot replace modern firmware",legacy.get(4).phoneReady);
        SystemFileCatalog.Entry n95 = new SystemFileCatalog.Entry("b","N95","RM-320","epoc93fp1");
        List<SystemRequirements.Row> modern = SystemRequirements.rows(Collections.singletonList(n95),false);
        assertFalse(modern.get(1).phoneReady); assertTrue(modern.get(4).phoneReady);
        assertEquals("Checked on import",modern.get(4).status);
        assertEquals("Setup files added",SystemRequirements.rows(Collections.singletonList(n95),true).get(4).status);
        assertTrue(SystemRequirements.formats().contains("same phone dump"));
        assertTrue(empty.get(4).description.contains("not a .n-gage game"));
    }
}
