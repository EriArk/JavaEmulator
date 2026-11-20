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
        input.pointer(CanvasEvent.POINTER_PRESSED,0,9,12,t);
        input.pointer(CanvasEvent.POINTER_RELEASED,0,20,20,t+20);
        input.pointer(CanvasEvent.POINTER_PRESSED,0,10,10,t);
        input.cancel();
        input.pointer(CanvasEvent.POINTER_RELEASED,0,10,10,t+20);
        assertTrue(events.isEmpty());
        input.pointer(CanvasEvent.POINTER_PRESSED,0,10,10,t);
        input.pointer(CanvasEvent.POINTER_RELEASED,0,60,10,t+100);
        assertEquals(1,events.size());
        assertEquals(GESTURE_FLICK,events.get(0).getType());
        assertEquals(500,events.get(0).getFlickSpeedX());
        assertEquals(10,events.get(0).getStartX());
        input.pointer(CanvasEvent.POINTER_PRESSED,0,14,14,t);
        input.pointer(CanvasEvent.POINTER_RELEASED,0,15,14,t+50);
        assertEquals(GESTURE_TAP,events.get(1).getType());
    }

    @Test public void longHoldFiresBeforeReleaseAndDoesNotBecomeTap() throws Exception {
        GestureInteractiveZone zone = new GestureInteractiveZone(GESTURE_TAP | GESTURE_LONG_PRESS,50);
        GestureRegistrationManager.register(canvas,zone);
        java.util.concurrent.CountDownLatch hold = new java.util.concurrent.CountDownLatch(1);
        List<Integer> events = Collections.synchronizedList(new ArrayList<>());
        GestureRegistrationManager.setListener(canvas,(owner,z,e)->{ events.add(e.getType()); hold.countDown(); });
        canvas.gestureInput.pointer(CanvasEvent.POINTER_PRESSED,0,20,20,SystemClock.uptimeMillis());
        assertTrue(hold.await(3,java.util.concurrent.TimeUnit.SECONDS));
        canvas.gestureInput.pointer(CanvasEvent.POINTER_RELEASED,0,20,20,SystemClock.uptimeMillis());
        assertEquals(Collections.singletonList(GESTURE_LONG_PRESS),events);
    }

    private List<GestureEvent> listen(int mask) {
        GestureRegistrationManager.register(canvas, new GestureInteractiveZone(mask));
        List<GestureEvent> events = Collections.synchronizedList(new ArrayList<>());
        GestureRegistrationManager.setListener(canvas, (owner, zone, event) -> events.add(event));
        return events;
    }

    private void pointer(int type, int id, int x, int y, long time) {
        javax.microedition.lcdui.event.Event event = CanvasEvent.getInstance(canvas, type, id, x, y, time);
        try { event.process(); } finally { event.recycle(); }
    }

    private void tap(int id, int x, long t) {
        pointer(CanvasEvent.POINTER_PRESSED, id, x, 20, t);
        pointer(CanvasEvent.POINTER_RELEASED, id, x, 20, t + 20);
    }

    @Test public void closeTapsBecomeOneDoubleTapInsideRecognitionBoundaries() {
        assertTrue(GestureInteractiveZone.isSupported(GESTURE_PINCH | GESTURE_DOUBLE_TAP));
        List<GestureEvent> events = listen(GESTURE_ALL);
        long t = SystemClock.uptimeMillis();
        tap(3, 20, t);
        tap(8, 21, t + 90);
        List<Integer> types = new ArrayList<>();
        for (GestureEvent event : events) types.add(event.getType());
        assertEquals(Arrays.asList(GESTURE_RECOGNITION_START, GESTURE_RECOGNITION_END,
                GESTURE_RECOGNITION_START, GESTURE_DOUBLE_TAP, GESTURE_RECOGNITION_END), types);
    }

    @Test public void isolatedAndDistantTapsAreDeliveredAndCancelDiscardsPendingTap() throws Exception {
        GestureRegistrationManager.register(canvas, new GestureInteractiveZone(GESTURE_TAP | GESTURE_DOUBLE_TAP));
        java.util.concurrent.LinkedBlockingQueue<GestureEvent> events = new java.util.concurrent.LinkedBlockingQueue<>();
        GestureRegistrationManager.setListener(canvas, (owner, zone, event) -> events.add(event));
        long t = SystemClock.uptimeMillis();
        tap(1, 20, t);
        tap(1, 100, t + 60);
        assertEquals(20, events.remove().getStartX());
        GestureEvent single = events.poll(2, java.util.concurrent.TimeUnit.SECONDS);
        assertNotNull(single);
        assertEquals(GESTURE_TAP, single.getType());
        assertEquals(100, single.getStartX());
        tap(2, 70, SystemClock.uptimeMillis());
        canvas.gestureInput.cancel();
        assertNull(events.poll(android.view.ViewConfiguration.getDoubleTapTimeout() + 100,
                java.util.concurrent.TimeUnit.MILLISECONDS));
    }

    @Test public void holdingTheSecondPressDoesNotSwallowTheFirstTap() throws Exception {
        GestureRegistrationManager.register(canvas, new GestureInteractiveZone(GESTURE_TAP | GESTURE_DOUBLE_TAP));
        java.util.concurrent.LinkedBlockingQueue<GestureEvent> events = new java.util.concurrent.LinkedBlockingQueue<>();
        GestureRegistrationManager.setListener(canvas, (owner, zone, event) -> events.add(event));
        long t = SystemClock.uptimeMillis();
        tap(0, 20, t);
        pointer(CanvasEvent.POINTER_PRESSED, 0, 20, 20, t + 60);
        GestureEvent first = events.poll(2, java.util.concurrent.TimeUnit.SECONDS);
        assertNotNull(first);
        assertEquals(GESTURE_TAP, first.getType());
        pointer(CanvasEvent.POINTER_RELEASED, 0, 20, 20, SystemClock.uptimeMillis());
        assertEquals(GESTURE_TAP, events.remove().getType());
    }

    @Test public void keypadDoubleTapUsesItsOwnTapHistory() {
        List<GestureEvent> events = listen(GESTURE_DOUBLE_TAP);
        long t = SystemClock.uptimeMillis();
        int x = canvas.getWidth() / 2, y = canvas.getHeight() / 2;
        pointer(CanvasEvent.POINTER_PRESSED, 1, x, y, t);
        pointer(CanvasEvent.POINTER_RELEASED, 1, x, y, t + 20);
        assertTrue(canvas.gestureInput.acceptsKey(Canvas.KEY_FIRE));
        canvas.gestureInput.key(Canvas.KEY_FIRE, true, t + 55);
        canvas.gestureInput.key(Canvas.KEY_FIRE, false, t + 75);
        assertTrue(events.isEmpty());
        canvas.gestureInput.key(Canvas.KEY_FIRE, true, t + 100);
        canvas.gestureInput.key(Canvas.KEY_FIRE, false, t + 130);
        assertEquals(1, events.size());
        assertEquals(GESTURE_DOUBLE_TAP, events.get(0).getType());
    }

    @Test public void pinchReportsGeometryAndTheSurvivingFingerCanDrag() {
        List<GestureEvent> events = listen(GESTURE_ALL);
        long t = SystemClock.uptimeMillis();
        pointer(CanvasEvent.POINTER_PRESSED, 7, 10, 20, t);
        pointer(CanvasEvent.POINTER_PRESSED, 2, 30, 20, t + 10);
        pointer(CanvasEvent.POINTER_PRESSED, 5, 70, 10, t + 15);
        pointer(CanvasEvent.POINTER_DRAGGED, 2, 50, 20, t + 20);
        GestureEvent spread = events.get(2);
        assertEquals(GESTURE_PINCH, spread.getType());
        assertEquals(10, spread.getStartX());
        assertEquals(20, spread.getPinchDistanceStarting());
        assertEquals(40, spread.getPinchDistanceCurrent());
        assertEquals(20, spread.getPinchDistanceChange());
        assertEquals(30, spread.getPinchCenterX());
        assertEquals(10, spread.getPinchCenterChangeX());
        pointer(CanvasEvent.POINTER_DRAGGED, 7, 50, 40, t + 30);
        GestureEvent inward = events.get(3);
        assertEquals(-20, inward.getPinchDistanceChange());
        assertEquals(30, inward.getPinchCenterY());
        assertEquals(10, inward.getPinchCenterChangeY());
        pointer(CanvasEvent.POINTER_RELEASED, 7, 50, 40, t + 35);
        pointer(CanvasEvent.POINTER_DRAGGED, 2, 53, 22, t + 40);
        assertEquals(GESTURE_DRAG, events.get(5).getType());
        assertEquals(3, events.get(5).getDragDistanceX());
        pointer(CanvasEvent.POINTER_RELEASED, 2, 53, 22, t + 50);
        pointer(CanvasEvent.POINTER_RELEASED, 5, 70, 10, t + 55);
        assertEquals(8, events.size());
        assertEquals(GESTURE_RECOGNITION_END, events.get(7).getType());
    }

    @Test public void twoContactsNeverTurnIntoASingleTapInATapOnlyZone() {
        List<GestureEvent> events = listen(GESTURE_TAP | GESTURE_RECOGNITION_START | GESTURE_RECOGNITION_END);
        long t = SystemClock.uptimeMillis();
        pointer(CanvasEvent.POINTER_PRESSED, 0, 20, 20, t);
        pointer(CanvasEvent.POINTER_PRESSED, 1, 70, 20, t + 10);
        pointer(CanvasEvent.POINTER_RELEASED, 1, 70, 20, t + 20);
        pointer(CanvasEvent.POINTER_RELEASED, 0, 20, 20, t + 25);
        List<Integer> types = new ArrayList<>();
        for (GestureEvent e : events) types.add(e.getType());
        assertEquals(Arrays.asList(GESTURE_RECOGNITION_START, GESTURE_RECOGNITION_START,
                GESTURE_RECOGNITION_END, GESTURE_RECOGNITION_END), types);
    }

    @Test public void pinchReleaseAndCancellationDoNotProduceTaps() {
        List<GestureEvent> events = listen(GESTURE_PINCH | GESTURE_TAP | GESTURE_FLICK);
        long t = SystemClock.uptimeMillis();
        pointer(CanvasEvent.POINTER_PRESSED, 4, 20, 20, t);
        pointer(CanvasEvent.POINTER_PRESSED, 8, 20, 60, t + 5);
        pointer(CanvasEvent.POINTER_RELEASED, 8, 20, 60, t + 10);
        pointer(CanvasEvent.POINTER_RELEASED, 4, 20, 20, t + 15);
        pointer(CanvasEvent.POINTER_PRESSED, 4, 20, 20, t + 20);
        pointer(CanvasEvent.POINTER_PRESSED, 5, 24, 40, t + 25);
        canvas.gestureInput.cancel();
        pointer(CanvasEvent.POINTER_RELEASED, 5, 24, 40, t + 30);
        pointer(CanvasEvent.POINTER_RELEASED, 4, 20, 20, t + 35);
        assertTrue(events.isEmpty());
        tap(4, 10, t + 40);
        assertEquals(GESTURE_TAP, events.get(0).getType());
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
