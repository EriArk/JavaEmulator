package ru.playsoftware.j2meloader.input;

import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.View;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import ru.playsoftware.j2meloader.config.ProfileModel;
import static org.junit.Assert.*;

public class TouchFeedbackTest {
    private final AtomicInteger pulses = new AtomicInteger(), repeats = new AtomicInteger();
    private TouchDeck deck(ProfileModel p) {
        TouchDeck deck = new TouchDeck(p, 1, new TouchDeck.Sink() {
            public void press(int key) { }
            public void release(int key) { }
            public void repeat(int key) { repeats.incrementAndGet(); }
            public void invalidate() { }
            public void feedback() { pulses.incrementAndGet(); }
        });
        deck.layout(360, 640); return deck;
    }
    private TouchDeck.Key key(TouchDeck deck, String label) {
        for (TouchDeck.Key key : deck.getKeys()) if (label.equals(key.label)) return key;
        throw new AssertionError(label);
    }
    private void down(TouchDeck deck, int id, String label) {
        TouchDeck.Key key = key(deck, label); deck.down(id, key.bounds.centerX(), key.bounds.centerY());
    }
    private void move(TouchDeck deck, int id, String label) {
        TouchDeck.Key key = key(deck, label); deck.move(id, key.bounds.centerX(), key.bounds.centerY());
    }

    @Test public void onePulsePerNewKeyNotPerFingerOrDiagonalCode() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            ProfileModel p = new ProfileModel(); p.touchLayout = TouchDeck.GAMEPAD; p.vkFeedback = true;
            TouchDeck deck = deck(p);
            try {
                down(deck, 0, "\u2196"); assertEquals(1, pulses.get());
                down(deck, 1, "\u2196"); move(deck, 0, "\u2196"); assertEquals(1, pulses.get());
                down(deck, 2, "5"); assertEquals(2, pulses.get());
                move(deck, 0, "\u2190"); assertEquals(3, pulses.get());
                move(deck, 1, "\u2190"); assertEquals(3, pulses.get());
                deck.move(0, 200, 100); assertEquals(3, pulses.get());
                deck.up(1); move(deck, 0, "\u2190"); assertEquals(4, pulses.get());
                down(deck, 3, "123"); assertEquals(5, pulses.get());
                deck.up(3); deck.cancel(); assertEquals(5, pulses.get());
                down(deck, 4, "7"); assertEquals(6, pulses.get());
                deck.layout(640, 360); assertEquals(6, pulses.get());
            } finally { deck.cancel(); }
        });
    }

    @Test public void disabledFeedbackAndGameAreaAreSilentForBothLayouts() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (int style : new int[]{TouchDeck.PHONE, TouchDeck.GAMEPAD}) {
                ProfileModel p = new ProfileModel(); p.touchLayout = style;
                TouchDeck deck = deck(p);
                try {
                    down(deck, 0, "5"); deck.up(0); assertEquals(0, pulses.get());
                    p.vkFeedback = true;
                    assertFalse(deck.down(1, 200, 100)); assertEquals(0, pulses.get());
                    down(deck, 0, "5"); assertEquals(1, pulses.get());
                    p.vkFeedback = false; move(deck, 0, "1"); assertEquals(1, pulses.get());
                    deck.up(0); pulses.set(0);
                } finally { deck.cancel(); }
            }
        });
    }

    @Test public void heldKeyAutoRepeatDoesNotRepeatFeedback() {
        TouchDeck[] current = new TouchDeck[1];
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                ProfileModel p = new ProfileModel(); p.vkFeedback = true;
                current[0] = deck(p); down(current[0], 0, "5");
            });
            SystemClock.sleep(700);
            assertTrue(repeats.get() > 0); assertEquals(1, pulses.get());
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> { if (current[0] != null) current[0].cancel(); });
        }
    }

    @Test public void platformRequestUsesVirtualKeyWithoutOverridingSystemPolicy() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            View view = new View(InstrumentationRegistry.getInstrumentation().getTargetContext()) {
                @Override public boolean performHapticFeedback(int constant) {
                    assertEquals(HapticFeedbackConstants.VIRTUAL_KEY, constant);
                    pulses.incrementAndGet(); return true;
                }
            };
            assertFalse(TouchFeedback.perform(null, true));
            assertFalse(TouchFeedback.perform(view, false)); assertEquals(0, pulses.get());
            assertTrue(TouchFeedback.perform(view, true)); assertEquals(1, pulses.get());
        });
    }

    @Test public void javaKeyboardRoutesPhoneAndGamepadFeedbackToItsOverlay() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            android.content.Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
            View overlay = new View(context) {
                @Override public boolean performHapticFeedback(int constant) { pulses.incrementAndGet(); return true; }
            };
            for (int style : new int[]{TouchDeck.PHONE, TouchDeck.GAMEPAD}) {
                ProfileModel p = new ProfileModel(context.getCacheDir()); p.touchLayout = style; p.vkFeedback = true;
                javax.microedition.lcdui.keyboard.VirtualKeyboard keyboard = new javax.microedition.lcdui.keyboard.VirtualKeyboard(p);
                keyboard.setView(overlay);
                try {
                    keyboard.resize(new android.graphics.RectF(0, 0, 1080, 1920), 0, 0, 1080, 1000);
                    java.lang.reflect.Field field = keyboard.getClass().getDeclaredField("touchDeck"); field.setAccessible(true);
                    TouchDeck.Key five = key((TouchDeck) field.get(keyboard), "5");
                    pulses.set(0);
                    keyboard.pointerPressed(0, five.bounds.centerX(), five.bounds.centerY());
                    assertEquals(1, pulses.get());
                    keyboard.pointerReleased(0, five.bounds.centerX(), five.bounds.centerY());
                    p.vkFeedback = false;
                    keyboard.pointerPressed(0, five.bounds.centerX(), five.bounds.centerY());
                    assertEquals(1, pulses.get());
                } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
                finally { keyboard.cancel(); keyboard.setView(null); }
            }
        });
    }
}
