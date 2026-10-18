package javax.microedition.shell;

import android.content.Context;
import android.os.Handler;
import android.view.LayoutInflater;
import androidx.test.platform.app.InstrumentationRegistry;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.event.EventQueue;
import javax.microedition.midlet.MIDlet;
import javax.microedition.midlet.MIDletStateChangeException;
import javax.microedition.util.ContextHolder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.databinding.ActivityMicroBinding;
import static org.junit.Assert.*;

/** Exercises the real lifecycle handler, without a commercial JAR or Android Surface. */
public class MidletStartupTest {
    private MicroActivity previousHost;
    private Object previousThread, previousDisplay, previousProperties;
    private boolean previousImmediate;
    private Host host;
    private MidletThread thread;
    private Guest guest;
    private final CountDownLatch release = new CountDownLatch(1);

    private static class Host extends MicroActivity {
        volatile boolean visible = true;
        volatile Displayable selected;
        Host(Context context) {
            attachBaseContext(new android.view.ContextThemeWrapper(context, R.style.AppTheme));
            binding = ActivityMicroBinding.inflate(LayoutInflater.from(getBaseContext()));
        }
        @Override public boolean isVisible() { return visible; }
        @Override public void setCurrent(Displayable value) { selected = value; }
        @Override public Displayable getCurrent() { return selected; }
    }

    private static class Screen extends Canvas {
        Screen() { super(true); }
        volatile int paints;
        @Override protected void paint(Graphics g) { paints++; g.setColor(0x12AB34); g.fillRect(0, 0, getWidth(), getHeight()); }
    }

    private class Guest extends MIDlet {
        final Screen screen = new Screen();
        final CountDownLatch entered = new CountDownLatch(1), finished = new CountDownLatch(1);
        final CountDownLatch paused = new CountDownLatch(1);
        boolean sawVisible, sawFrame, constructorWasHidden;
        boolean refuse, selfPause, hold;
        Guest() {
            Display.getDisplay(this).setCurrent(screen);
            constructorWasHidden = !screen.isShown();
        }
        @Override public void startApp() throws MIDletStateChangeException {
            entered.countDown();
            try {
                long deadline = android.os.SystemClock.uptimeMillis() + 2000;
                while (!screen.isShown() && android.os.SystemClock.uptimeMillis() < deadline)
                    android.os.SystemClock.sleep(5);
                sawVisible = screen.isShown();
                screen.repaint(); screen.serviceRepaints(); sawFrame = screen.paints > 0;
                if (hold) release.await(5, TimeUnit.SECONDS);
                if (selfPause) notifyPaused();
                if (refuse) throw new MIDletStateChangeException("Startup refusal fixture");
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            finally { finished.countDown(); }
        }
        @Override public void pauseApp() { paused.countDown(); }
        @Override public void destroyApp(boolean unconditional) { }
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        Field value = owner.getDeclaredField(name); value.setAccessible(true); return value;
    }

    @Before public void setup() throws Exception {
        previousHost = ContextHolder.getActivity();
        previousThread = field(MidletThread.class, "instance").get(null);
        previousDisplay = field(Display.class, "instance").get(null);
        previousProperties = field(MIDlet.class, "properties").get(null);
        previousImmediate = EventQueue.isImmediate(); EventQueue.setImmediate(false);
        Display.initDisplay(); MIDlet.initProps(Collections.emptyMap());
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            host = new Host(InstrumentationRegistry.getInstrumentation().getTargetContext());
            ContextHolder.setCurrentActivity(host);
            guest = new Guest();
        });
    }

    private void create() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        MicroLoader loader = new MicroLoader(context, context.getCacheDir() + "/startup-fixture/app") {
            @Override MIDlet loadMIDlet(String name) { return guest; }
        };
        MidletThread.create(loader, "fixture");
        thread = (MidletThread) field(MidletThread.class, "instance").get(null);
        barrier();
    }

    private void barrier() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        new Handler(thread.getLooper()).post(done::countDown);
        assertTrue("Lifecycle handler must not remain blocked in startApp", done.await(5, TimeUnit.SECONDS));
    }

    @After public void cleanup() throws Exception {
        release.countDown();
        if (thread != null) { barrier(); thread.quitSafely(); thread.join(5000); assertFalse(thread.isAlive()); }
        Display.setForeground(false);
        field(MidletThread.class, "instance").set(null, previousThread);
        field(Display.class, "instance").set(null, previousDisplay);
        field(MIDlet.class, "properties").set(null, previousProperties);
        ContextHolder.setCurrentActivity(previousHost); EventQueue.setImmediate(previousImmediate);
    }

    @Test public void startAppCanWaitForVisibilityAndServiceAFrame() throws Exception {
        create(); MidletThread.resumeApp();
        assertTrue(guest.finished.await(5, TimeUnit.SECONDS)); barrier();
        assertTrue("Construction must not publish a partially initialized MIDlet", guest.constructorWasHidden);
        assertTrue("isShown must be reachable before startApp returns", guest.sawVisible);
        assertTrue("Startup repaint must not depend on an Android Surface", guest.sawFrame);
        assertTrue(guest.screen.isShown());
    }

    @Test public void declinedStartWithdrawsTheDisplay() throws Exception {
        guest.refuse = true; create(); MidletThread.resumeApp();
        assertTrue(guest.finished.await(5, TimeUnit.SECONDS)); barrier();
        assertTrue(guest.sawVisible); assertFalse(guest.screen.isShown());
    }

    @Test public void immediateEventsStillAllowStartupToObserveAFrame() throws Exception {
        EventQueue.setImmediate(true);
        startAppCanWaitForVisibilityAndServiceAFrame();
    }

    @Test public void queuedStartCannotPublishAfterHostWasPaused() throws Exception {
        create();
        CountDownLatch blocked = new CountDownLatch(1);
        new Handler(thread.getLooper()).post(() -> {
            blocked.countDown();
            try { release.await(5, TimeUnit.SECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        assertTrue(blocked.await(5, TimeUnit.SECONDS));
        MidletThread.resumeApp(); host.visible = false; MidletThread.pauseApp();
        release.countDown(); barrier();
        assertEquals("Obsolete start must not call the guest", 1, guest.entered.getCount());
        assertFalse(guest.screen.isShown());
        host.visible = true; MidletThread.resumeApp();
        assertTrue(guest.finished.await(5, TimeUnit.SECONDS)); barrier();
        assertTrue(guest.sawVisible); assertTrue(guest.screen.isShown());
    }

    @Test public void notifyPausedDuringStartIsNotOverriddenByCompletion() throws Exception {
        guest.selfPause = true; create(); MidletThread.resumeApp();
        assertTrue(guest.finished.await(5, TimeUnit.SECONDS)); barrier();
        assertTrue(guest.sawVisible); assertFalse(guest.screen.isShown());
    }

    @Test public void backgroundDuringBlockedStartHidesImmediatelyAndResumeRecovers() throws Exception {
        guest.hold = true; create(); MidletThread.resumeApp();
        assertTrue(guest.entered.await(5, TimeUnit.SECONDS));
        long deadline = android.os.SystemClock.uptimeMillis() + 2500;
        while (!guest.screen.isShown() && android.os.SystemClock.uptimeMillis() < deadline)
            android.os.SystemClock.sleep(5);
        assertTrue(guest.screen.isShown());
        host.visible = false; MidletThread.pauseApp();
        assertFalse("Pause must not wait behind a guest startup loop", guest.screen.isShown());
        host.visible = true; MidletThread.resumeApp();
        deadline = android.os.SystemClock.uptimeMillis() + 2000;
        while (!guest.screen.isShown() && android.os.SystemClock.uptimeMillis() < deadline)
            android.os.SystemClock.sleep(5);
        assertTrue("Returning to a still-starting game must restore visibility", guest.screen.isShown());
        release.countDown(); barrier();
        assertTrue(guest.paused.await(5, TimeUnit.SECONDS));
        assertTrue(guest.screen.isShown());
    }
}
