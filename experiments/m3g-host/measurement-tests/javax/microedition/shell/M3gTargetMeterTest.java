package javax.microedition.shell;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.lang.reflect.Field;
import javax.microedition.lcdui.Image;
import javax.microedition.m3g.Graphics3D;
import javax.microedition.m3g.*;
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
        java.util.Arrays.fill((long[]) field("phaseNs").get(null), 0);
        java.util.Arrays.fill((long[]) field("phaseCalls").get(null), 0);
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

    @Test public void phaseTotalsAccumulateWithoutPerDrawStorage() throws Exception {
        FrameMeter.recordPhase(2, 10);
        FrameMeter.recordPhase(2, 20);
        assertEquals(30, ((long[]) field("phaseNs").get(null))[2]);
        assertEquals(2, ((long[]) field("phaseCalls").get(null))[2]);
    }

    @Test public void invalidPhaseSamplesAreIgnored() throws Exception {
        FrameMeter.recordPhase(-1, 10);
        FrameMeter.recordPhase(3, 10);
        FrameMeter.recordPhase(0, -1);
        assertArrayEquals(new long[3], (long[]) field("phaseCalls").get(null));
    }

    @Test public void candidateUnusedTargetAvoidsCopies() throws Exception {
        org.junit.Assume.assumeTrue(InstrumentationRegistryCompat.isCandidate());
        renderer.bindTarget(Image.createImage(16, 16).getGraphics());
        renderer.releaseTarget();
        renderer.releaseTarget();
        assertArrayEquals(new long[]{0, 0, 0}, (long[]) field("phaseCalls").get(null));
    }

    @Test public void candidatePartialClearCopiesAreObservedOncePerTarget() throws Exception {
        org.junit.Assume.assumeTrue(InstrumentationRegistryCompat.isCandidate());
        renderer.bindTarget(Image.createImage(16, 16).getGraphics());
        renderer.setViewport(1, 1, 8, 8);
        renderer.clear(null);
        renderer.releaseTarget();
        renderer.releaseTarget();
        assertArrayEquals(new long[]{1, 1, 0}, (long[]) field("phaseCalls").get(null));
    }

    @Test public void candidateImmediateDrawIsObservedOnce() throws Exception {
        org.junit.Assume.assumeTrue(InstrumentationRegistryCompat.isCandidate());
        renderer.bindTarget(Image.createImage(16, 16).getGraphics());
        Camera camera = new Camera();
        camera.setParallel(2, 1, 1, 10);
        renderer.setCamera(camera, new Transform());
        VertexArray positions = new VertexArray(3, 3, 2);
        positions.set(0, 3, new short[]{-1,-1,-2, 1,-1,-2, 0,1,-2});
        VertexBuffer vertices = new VertexBuffer();
        vertices.setPositions(positions, 1, null);
        renderer.render(vertices, new TriangleStripArray(0, new int[]{3}), new Appearance(), new Transform());
        assertEquals(1, ((long[]) field("phaseCalls").get(null))[2]);
        assertTrue(((long[]) field("phaseNs").get(null))[2] > 0);
    }

    private static final class InstrumentationRegistryCompat {
        static boolean isCandidate() {
            return androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                    .getTargetContext().getPackageName().contains("m3gcandidate");
        }
    }
}
