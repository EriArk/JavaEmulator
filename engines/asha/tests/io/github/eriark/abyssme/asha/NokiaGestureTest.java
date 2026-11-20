/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.asha;

import android.os.SystemClock;
import androidx.test.platform.app.InstrumentationRegistry;
import com.nokia.mid.ui.gestures.*;
import javax.microedition.lcdui.*;
import javax.microedition.lcdui.event.CanvasEvent;
import org.junit.*;
import java.util.*;
import java.util.List;
import static com.nokia.mid.ui.gestures.GestureInteractiveZone.*;
import static org.junit.Assert.*;

public class NokiaGestureTest {
    private final CanvasLifecycleTest fixture = new CanvasLifecycleTest();
    private Canvas canvas;
    @Before public void setup() throws Exception {
        fixture.setup();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> canvas = new Canvas() {
            protected void paint(Graphics g) {}
            public boolean isShown() { return true; }
        });
    }
    @After public void cleanup() throws Exception {
        if (canvas != null) GestureRegistrationManager.unregisterAll(canvas);
        fixture.cleanup();
    }

    @Test public void clipsStartCoordinatesAndCancelsBeforeRelease() {
        GestureInteractiveZone zone = new GestureInteractiveZone(GESTURE_TAP | GESTURE_FLICK);
        zone.setRectangle(10,10,30,30);
        GestureRegistrationManager.register(canvas,zone);
        List<GestureEvent> events = new ArrayList<>();
        GestureRegistrationManager.setListener(canvas,(owner,z,e) -> events.add(e));
        Canvas.GestureInput input = canvas.gestureInput;
        long t = SystemClock.uptimeMillis();
        input.pointer(CanvasEvent.POINTER_PRESSED,9,12,t);
        input.pointer(CanvasEvent.POINTER_RELEASED,20,20,t+20);
        input.pointer(CanvasEvent.POINTER_PRESSED,10,10,t);
        input.cancel();
        input.pointer(CanvasEvent.POINTER_RELEASED,10,10,t+20);
        assertTrue(events.isEmpty());
        input.pointer(CanvasEvent.POINTER_PRESSED,10,10,t);
        input.pointer(CanvasEvent.POINTER_RELEASED,60,10,t+100);
        assertEquals(1,events.size());
        assertEquals(GESTURE_FLICK,events.get(0).getType());
        assertEquals(500,events.get(0).getFlickSpeedX());
        assertEquals(10,events.get(0).getStartX());
        input.pointer(CanvasEvent.POINTER_PRESSED,14,14,t);
        input.pointer(CanvasEvent.POINTER_RELEASED,15,14,t+50);
        assertEquals(GESTURE_TAP,events.get(1).getType());
    }

    @Test public void longHoldFiresBeforeReleaseAndDoesNotBecomeTap() throws Exception {
        GestureInteractiveZone zone = new GestureInteractiveZone(GESTURE_TAP | GESTURE_LONG_PRESS,50);
        GestureRegistrationManager.register(canvas,zone);
        java.util.concurrent.CountDownLatch hold = new java.util.concurrent.CountDownLatch(1);
        List<Integer> events = Collections.synchronizedList(new ArrayList<>());
        GestureRegistrationManager.setListener(canvas,(owner,z,e)->{ events.add(e.getType()); hold.countDown(); });
        canvas.gestureInput.pointer(CanvasEvent.POINTER_PRESSED,20,20,SystemClock.uptimeMillis());
        assertTrue(hold.await(3,java.util.concurrent.TimeUnit.SECONDS));
        canvas.gestureInput.pointer(CanvasEvent.POINTER_RELEASED,20,20,SystemClock.uptimeMillis());
        assertEquals(Collections.singletonList(GESTURE_LONG_PRESS),events);
    }

    @Test public void hardwareBackDeliversOnlyTheHighestPriorityBackCommand() throws Exception {
        Command help = new Command("Help", Command.HELP, 0);
        Command back = new Command("Return", Command.BACK, 1);
        canvas.addCommand(help);
        canvas.addCommand(new Command("Other back", Command.BACK, 4));
        canvas.addCommand(back);
        assertFalse(canvas.fireBackCommand());
        java.util.concurrent.LinkedBlockingQueue<Command> received = new java.util.concurrent.LinkedBlockingQueue<>();
        canvas.setCommandListener((command, screen) -> received.add(command));
        assertTrue(canvas.fireBackCommand());
        assertSame(back, received.poll(2, java.util.concurrent.TimeUnit.SECONDS));
    }

    @Test public void phoneIdentityBelongsToTheSelectedGame() {
        ru.playsoftware.j2meloader.config.ProfileModel profile = new ru.playsoftware.j2meloader.config.ProfileModel();
        profile.systemProperties = "custom.key: retained\n";
        ru.playsoftware.j2meloader.config.GamePresets.phone(profile,"Nokia Asha 311 (S40 full touch)");
        assertTrue(profile.systemProperties.contains("com.nokia.keyboard.type: None"));
        ru.playsoftware.j2meloader.config.GamePresets.phone(profile,"Nokia S40");
        assertFalse(profile.systemProperties.contains("com.nokia.keyboard.type"));
        assertTrue(profile.systemProperties.contains("custom.key: retained"));
    }
}
