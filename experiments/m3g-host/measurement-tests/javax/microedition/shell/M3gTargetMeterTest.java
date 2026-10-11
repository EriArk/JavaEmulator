package javax.microedition.shell;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.lang.reflect.Field;
import javax.microedition.lcdui.Image;
import javax.microedition.m3g.Graphics3D;
import org.abyssme.research.FrameMeter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Checks generated hooks on both engines, not a separate fake timer. */
@RunWith(AndroidJUnit4.class)
public class M3gTargetMeterTest {
    private Graphics3D renderer;

    @Before public void setup() throws Exception {
        Graphics3D.initGraphics3D();
        renderer = Graphics3D.getInstance();
        field("targetCount").setInt(null, 0);
    }

    @After public void release() { renderer.releaseTarget(); }

    @Test public void successfulTargetSessionIncludesHeldInterval() throws Exception {
        renderer.bindTarget(Image.createImage(16, 16).getGraphics());
        android.os.SystemClock.sleep(10);
        renderer.releaseTarget();
        assertEquals(1, field("targetCount").getInt(null));
        assertTrue(((long[]) field("targets").get(null))[0] >= 5_000_000L);
    }

    @Test public void repeatedReleaseDoesNotRecordAnotherSession() throws Exception {
        renderer.bindTarget(Image.createImage(16, 16).getGraphics());
        renderer.releaseTarget();
        renderer.releaseTarget();
        assertEquals(1, field("targetCount").getInt(null));
    }

    @Test public void failedBindingDoesNotRecordSession() throws Exception {
        try {
            renderer.bindTarget(null);
            fail("Null target accepted");
        } catch (NullPointerException expected) {
            renderer.releaseTarget();
            assertEquals(0, field("targetCount").getInt(null));
        }
    }

    private Field field(String name) throws Exception {
        Field field = FrameMeter.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
