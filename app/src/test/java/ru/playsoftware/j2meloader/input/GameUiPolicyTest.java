package ru.playsoftware.j2meloader.input;

import org.junit.Test;
import static org.junit.Assert.*;

public class GameUiPolicyTest {
    @Test public void soloAlwaysKeepsExistingPauseOwnership() {
        for (boolean visible : new boolean[]{false,true})
            for (boolean panel : new boolean[]{false,true})
                for (boolean social : new boolean[]{false,true})
                    assertFalse(GameUiPolicy.keepNetworkRunning(false, visible, panel, social));
    }
    @Test public void networkLocalPanelsDoNotPauseTheOtherPlayer() {
        assertTrue(GameUiPolicy.keepNetworkRunning(true, true, true, false));
        assertTrue(GameUiPolicy.keepNetworkRunning(true, false, true, true));
        assertFalse(GameUiPolicy.keepNetworkRunning(true, false, true, false));
        assertFalse(GameUiPolicy.keepNetworkRunning(true, true, false, false));
    }
}
