/* SPDX-License-Identifier: GPL-3.0-only */
package javax.microedition.lcdui;

import android.graphics.Rect;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class KeyboardViewportTest {
    private static Rect project(int rotation) {
        Rect bounds = new Rect(0, 700, 1200, 950);
        KeyboardViewport.toGuest(bounds, 100, 100, 600, 800, 240, 320, rotation);
        return bounds;
    }

    @Test public void bottomInsetUsesScaledOffsetCanvasAndClipsLetterbox() {
        assertEquals(new Rect(0, 240, 240, 320), project(0));
    }

    @Test public void rotationMovesKeyboardOverlapToTheCorrespondingGuestEdge() {
        assertEquals(new Rect(180, 0, 240, 320), project(90));
        assertEquals(new Rect(0, 0, 240, 80), project(180));
        assertEquals(new Rect(0, 0, 60, 320), project(270));
    }

    @Test public void keyboardOutsideCanvasOrWithoutDockedInsetsCoversNoPixels() {
        Rect bounds = new Rect(0, 900, 1200, 1000);
        KeyboardViewport.toGuest(bounds, 100, 100, 600, 800, 240, 320, 0);
        assertTrue(bounds.isEmpty());
        bounds.set(0, 725, 1200, 725);
        KeyboardViewport.toGuest(bounds, 100, 100, 600, 800, 240, 320, 0);
        assertTrue(bounds.isEmpty());
    }
}
