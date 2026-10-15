package javax.microedition.lcdui;

import android.content.Context;
import android.view.LayoutInflater;
import androidx.test.platform.app.InstrumentationRegistry;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.microedition.lcdui.event.EventQueue;
import javax.microedition.lcdui.event.RunnableEvent;
import javax.microedition.shell.MicroActivity;
import javax.microedition.util.ContextHolder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.databinding.ActivityMicroBinding;
import static org.junit.Assert.*;

/** Guest display lifecycle with a host that deliberately never creates an Android Surface. */
public class CanvasLifecycleTest {
    private Display display, oldDisplay;
    private MicroActivity oldActivity;
    private javax.microedition.lcdui.keyboard.VirtualKeyboard oldKeyboard;
    private Field instance;
    private boolean oldImmediate;
    private Host host;

    private static class Host extends MicroActivity {
        private volatile Displayable selected;
        Host(Context context) {
            attachBaseContext(new android.view.ContextThemeWrapper(context, R.style.AppTheme));
            binding = ActivityMicroBinding.inflate(LayoutInflater.from(getBaseContext()));
        }
        @Override public void setCurrent(Displayable value) { selected = value; }
        @Override public Displayable getCurrent() { return selected; }
    }

    private static class Screen extends Canvas {
        Screen() { super(true); }
        final List<String> events = Collections.synchronizedList(new ArrayList<>());
        volatile int frame, painted = -1, keys;
        boolean failShow;
        Runnable onSize, onShow;
        protected void sizeChanged(int w, int h) { events.add("size"); if (onSize != null) onSize.run(); }
        protected void showNotify() {
            events.add("show");
            if (onShow != null) onShow.run();
            if (failShow) throw new IllegalStateException("test show");
        }
        protected void hideNotify() { events.add("hide"); }
        protected void keyPressed(int key) { keys++; }
        protected void paint(Graphics g) {
            events.add("paint"); painted = frame;
            g.setColor(0x00FF00); g.fillRect(0, 0, getWidth(), getHeight());
        }
    }

    private static class TestAlert extends Alert {
        int shows;
        TestAlert() { super("Test alert"); }
        @Override boolean isDialogShowing() { return shows > 0; }
        @Override public androidx.appcompat.app.AlertDialog prepareDialog() {
            return new androidx.appcompat.app.AlertDialog(ContextHolder.getActivity().getBaseContext()) {
                @Override public void show() { shows++; }
            };
        }
    }

    @Before public void setup() throws Exception {
        oldActivity = ContextHolder.getActivity(); oldKeyboard = ContextHolder.getVk();
        oldImmediate = EventQueue.isImmediate(); EventQueue.setImmediate(false);
        drain();
        instance = Display.class.getDeclaredField("instance"); instance.setAccessible(true);
        oldDisplay = (Display) instance.get(null);
        java.lang.reflect.Constructor<Display> constructor = Display.class.getDeclaredConstructor();
        constructor.setAccessible(true); display = constructor.newInstance(); instance.set(null, display);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            host = new Host(InstrumentationRegistry.getInstrumentation().getTargetContext());
            ContextHolder.setCurrentActivity(host);
            ContextHolder.setVk(null);
        });
    }

    @After public void cleanup() throws Exception {
        if (instance != null) { Display.setForeground(false); drain(); instance.set(null, oldDisplay); }
        ContextHolder.setCurrentActivity(oldActivity); ContextHolder.setVk(oldKeyboard);
        EventQueue.setImmediate(oldImmediate);
    }

    private Screen screen() {
        Screen[] result = new Screen[1];
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> result[0] = new Screen());
        return result[0];
    }

    private static void drain() throws Exception {
        // A show callback posts a paint; the second barrier drains that follow-up too.
        for (int i = 0; i < 2; i++) {
            CountDownLatch done = new CountDownLatch(1);
            Display.getEventQueue().postEventQueued(RunnableEvent.getInstance(done::countDown));
            assertTrue("Event queue must remain responsive", done.await(5, TimeUnit.SECONDS));
        }
    }

    @Test public void startCompletionPublishesSizeShowAndPaintWithoutSurface() throws Exception {
        Screen canvas = screen(); display.setCurrent(canvas); canvas.repaint(); canvas.serviceRepaints(); drain();
        assertFalse(canvas.isShown()); assertTrue(canvas.events.isEmpty());
        Display.setForeground(true); drain();
        assertTrue(canvas.isShown());
        assertEquals(java.util.Arrays.asList("size", "show", "paint"), canvas.events);
        assertEquals(0, canvas.painted);
        Field image = Canvas.class.getDeclaredField("offscreenCopy"); image.setAccessible(true);
        assertEquals(0xFF00FF00, ((Image) image.get(canvas)).getBitmap().getPixel(0, 0));
    }

    @Test public void synchronousFramesAreNotDiscardedWhileHostSurfaceIsMissing() throws Exception {
        Screen canvas = screen(); display.setCurrent(canvas); Display.setForeground(true); drain();
        for (int frame = 1; frame <= 30; frame++) {
            canvas.frame = frame; canvas.repaint(); canvas.serviceRepaints();
            assertEquals("Frame-dependent initialization must not be skipped", frame, canvas.painted);
        }
    }

    @Test public void pauseResumeAndRepeatedResumeHaveBalancedNotifications() throws Exception {
        Screen canvas = screen(); display.setCurrent(canvas); Display.setForeground(true); drain();
        Display.setForeground(false); drain();
        canvas.frame = 1; canvas.repaint(); canvas.serviceRepaints(); drain();
        assertFalse(canvas.isShown()); assertEquals(0, canvas.painted);
        Display.setForeground(true); Display.setForeground(true); drain();
        assertEquals(1, canvas.painted);
        assertEquals(2, Collections.frequency(canvas.events, "show"));
        assertEquals(1, Collections.frequency(canvas.events, "hide"));
    }

    @Test public void switchingScreensSuppressesOldPaintAndInput() throws Exception {
        Screen first = screen(), second = screen();
        display.setCurrent(first); Display.setForeground(true); drain();
        display.setCurrent(second); drain();
        first.frame = 7; first.repaint(); first.serviceRepaints(); first.postKeyPressed(Canvas.KEY_NUM5);
        second.postKeyPressed(Canvas.KEY_NUM5); drain();
        assertFalse(first.isShown()); assertTrue(second.isShown());
        assertEquals(0, first.painted); assertEquals(0, first.keys); assertEquals(1, second.keys);
        assertEquals(1, Collections.frequency(first.events, "hide"));
    }

    @Test public void hiddenRequestsCannotWedgeLaterRepaints() throws Exception {
        Screen canvas = screen();
        for (int i = 0; i < 20; i++) canvas.repaint();
        display.setCurrent(canvas); Display.setForeground(true); drain();
        canvas.frame = 2; canvas.repaint(); canvas.serviceRepaints();
        assertEquals(2, canvas.painted);
        display.setCurrent(null); drain(); assertFalse(canvas.isShown());
    }

    @Test public void rapidSelectionDoesNotResurrectAnObsoleteCanvas() throws Exception {
        Screen first = screen(), second = screen();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        Display.postEvent(RunnableEvent.getInstance(() -> {
            entered.countDown();
            try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }));
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        try {
            Display.setForeground(true); display.setCurrent(first); display.setCurrent(second);
        } finally { release.countDown(); }
        drain(); assertFalse(first.isShown()); assertTrue(second.isShown());
        assertFalse(first.events.contains("paint"));
    }

    @Test public void failingShowCallbackDoesNotLoseAutomaticRepaint() throws Exception {
        Screen canvas = screen(); canvas.failShow = true;
        display.setCurrent(canvas); Display.setForeground(true); drain();
        assertEquals(0, canvas.painted);
    }

    @Test public void sizeCallbackCanSelectAnotherScreenWithoutShowingOldOne() throws Exception {
        Screen first = screen(), second = screen();
        first.onSize = () -> display.setCurrent(second);
        display.setCurrent(first); Display.setForeground(true); drain(); drain();
        assertFalse(first.isShown()); assertTrue(second.isShown());
        assertFalse(first.events.contains("show")); assertFalse(first.events.contains("paint"));
        assertSame(second, host.getCurrent());
    }

    @Test public void showCallbackCanSelectAnotherScreen() throws Exception {
        Screen first = screen(), second = screen();
        first.onShow = () -> display.setCurrent(second);
        display.setCurrent(first); Display.setForeground(true); drain(); drain();
        assertFalse(first.isShown()); assertTrue(second.isShown());
        assertFalse(first.events.contains("paint"));
        assertEquals(1, Collections.frequency(first.events, "hide"));
        assertSame(second, host.getCurrent());
    }

    @Test public void formRoundTripRestoresCanvasWithoutASecondSizeNotification() throws Exception {
        Screen canvas = screen(); display.setCurrent(canvas); Display.setForeground(true); drain();
        Form form = new Form("Test form"); display.setCurrent(form); drain();
        assertFalse(canvas.isShown()); assertSame(form, host.getCurrent());
        display.setCurrent(canvas); drain();
        assertTrue(canvas.isShown()); assertSame(canvas, host.getCurrent());
        assertEquals(1, Collections.frequency(canvas.events, "size"));
        assertEquals(2, Collections.frequency(canvas.events, "show"));
    }

    @Test public void immediateModeDoesNotRunLifecycleWhileDisplayIsLocked() throws Exception {
        Screen first = screen(), second = screen();
        EventQueue.setImmediate(true);
        CountDownLatch changed = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean completedInsideCallback = new java.util.concurrent.atomic.AtomicBoolean();
        first.onShow = () -> {
            Thread worker = new Thread(() -> { display.setCurrent(second); changed.countDown(); });
            worker.start();
            try { completedInsideCallback.set(changed.await(3, TimeUnit.SECONDS)); }
            catch (InterruptedException e) { throw new AssertionError(e); }
        };
        display.setCurrent(first); Display.setForeground(true); drain(); drain();
        assertTrue("Guest callbacks must not hold the Display monitor", completedInsideCallback.get());
        assertEquals(0, changed.getCount()); assertTrue(second.isShown());
        assertFalse(first.isShown()); assertSame(second, host.getCurrent());
    }

    @Test public void gameCanvasFlushAndKeyPollingSurvivePauseWithoutSurface() throws Exception {
        Field fullscreen = Canvas.class.getDeclaredField("forceFullscreen"); fullscreen.setAccessible(true);
        boolean oldFullscreen = fullscreen.getBoolean(null);
        javax.microedition.lcdui.game.GameCanvas[] result = new javax.microedition.lcdui.game.GameCanvas[1];
        try {
            fullscreen.setBoolean(null, true);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                    result[0] = new javax.microedition.lcdui.game.GameCanvas(true));
        } finally { fullscreen.setBoolean(null, oldFullscreen); }
        javax.microedition.lcdui.game.GameCanvas canvas = result[0];
        display.setCurrent(canvas); Display.setForeground(true); drain();
        Graphics g = canvas.getGraphics(); g.setColor(0xFF0000);
        g.fillRect(0, 0, canvas.getWidth(), canvas.getHeight()); canvas.flushGraphics();
        Field image = Canvas.class.getDeclaredField("offscreenCopy"); image.setAccessible(true);
        assertEquals(0xFFFF0000, ((Image) image.get(canvas)).getBitmap().getPixel(0, 0));
        canvas.postKeyPressed(Canvas.KEY_NUM5);
        assertEquals(javax.microedition.lcdui.game.GameCanvas.FIRE_PRESSED, canvas.getKeyStates());
        Display.setForeground(false); drain(); Display.setForeground(true); drain();
        assertEquals(0, canvas.getKeyStates()); assertTrue(canvas.isShown());
        assertEquals(0xFFFF0000, ((Image) image.get(canvas)).getBitmap().getPixel(0, 0));
    }

    @Test public void alertDeferredByPauseAppearsOnResumeOnlyOnce() throws Exception {
        TestAlert alert = new TestAlert();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        ViewHandler.postEvent(() -> {
            entered.countDown();
            try { release.await(5, TimeUnit.SECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        try {
            display.setCurrent(alert); Display.setForeground(true); drain();
            Display.setForeground(false); drain();
        } finally { release.countDown(); }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertEquals(0, alert.shows);
        Display.setForeground(true); drain();
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertEquals(1, alert.shows);
        Display.setForeground(false); drain(); Display.setForeground(true); drain();
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertEquals(1, alert.shows);
    }

    @Test public void fullscreenChangeWhileHiddenDeliversSizeBeforeNextPaint() throws Exception {
        Field ratio = Canvas.class.getDeclaredField("scaleRatio"); ratio.setAccessible(true);
        int oldRatio = ratio.getInt(null);
        try {
            ratio.setInt(null, 100);
            Screen canvas = screen(); display.setCurrent(canvas); Display.setForeground(true); drain();
            Display.setForeground(false); drain(); canvas.events.clear();
            canvas.setFullScreenMode(false); drain();
            assertTrue(canvas.events.isEmpty());
            Display.setForeground(true); drain();
            assertEquals(java.util.Arrays.asList("size", "show", "paint"), canvas.events);
        } finally { ratio.setInt(null, oldRatio); }
    }

    @Test public void queuedPaintDiscardedWhileHiddenDoesNotBlockNextFrame() throws Exception {
        Screen canvas = screen(); display.setCurrent(canvas); Display.setForeground(true); drain();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        Display.getEventQueue().postEventQueued(RunnableEvent.getInstance(() -> {
            entered.countDown();
            try { release.await(5, TimeUnit.SECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }));
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        try {
            canvas.frame = 1; canvas.repaint(); Display.setForeground(false);
        } finally { release.countDown(); }
        drain(); assertEquals(0, canvas.painted);
        Display.setForeground(true); drain();
        canvas.frame = 2; canvas.repaint(); canvas.serviceRepaints();
        assertEquals(2, canvas.painted);
    }
}
