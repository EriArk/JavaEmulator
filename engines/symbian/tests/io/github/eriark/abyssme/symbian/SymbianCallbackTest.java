package io.github.eriark.abyssme.symbian;

import androidx.test.platform.app.InstrumentationRegistry;
import java.lang.reflect.Field;
import org.junit.Test;
import static org.junit.Assert.*;

public class SymbianCallbackTest {
    @Test public void queuedGuestExitCannotCloseReplacementSession() throws Exception {
        Field running = SymbianActivity.class.getDeclaredField("running"); running.setAccessible(true);
        Field generation = SymbianActivity.class.getDeclaredField("sessionGeneration"); generation.setAccessible(true);
        SymbianActivity[] activity = new SymbianActivity[1];
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            try {
                activity[0] = new SymbianActivity();
                running.setBoolean(activity[0], true); generation.setLong(activity[0], 1);
                Thread callback = new Thread(activity[0]::exited);
                callback.start(); callback.join(2000);
                assertFalse(callback.isAlive());
                generation.setLong(activity[0], 2);
            } catch (Exception e) { throw new AssertionError(e); }
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertTrue(running.getBoolean(activity[0]));
        assertEquals(2, generation.getLong(activity[0]));
    }
}
