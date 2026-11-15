/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.symbian;
import org.junit.Test;
import java.util.*;
import ru.playsoftware.j2meloader.input.ControllerInput;
import static org.junit.Assert.*;
public class RightStickNativeKeysTest {
    @Test public void rightStickAndLeftStickReachSeparateNativeKeys() {
        List<String> events=new ArrayList<>();
        SymbianInput nativeInput=new SymbianInput((scan,down)->events.add((down?"+":"-")+scan));
        ControllerInput input=new ControllerInput(k->k==ControllerInput.STICK_UP?-1:k==ControllerInput.RIGHT_STICK_RIGHT?-7:0,nativeInput.source());
        input.axes(1,0,-1,0,0,0,0,1,0);
        assertEquals(Arrays.asList("+16","+165"),events);events.clear();
        input.axes(1,0,-1,0,0,0,0,0,0);assertEquals(Arrays.asList("-165"),events);events.clear();
        input.removeDevice(1);assertEquals(Arrays.asList("-16"),events);
    }
}
