/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.asha;

import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import androidx.test.platform.app.InstrumentationRegistry;
import com.nokia.mid.ui.TextEditor;
import com.nokia.mid.ui.TextEditorListener;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.CanvasLifecycleTest;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.TextField;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class NokiaTextEditorTest {
    private final CanvasLifecycleTest fixture = new CanvasLifecycleTest();
    private Canvas canvas;
    private TextEditor editor;

    @Before public void setup() throws Exception {
        fixture.setup();
        main(() -> canvas = new Canvas() { protected void paint(Graphics g) {} });
        editor = TextEditor.createTextEditor("", 8, TextField.ANY, 100, 30);
    }
    @After public void cleanup() throws Exception {
        if (editor != null) editor.setParent(null);
        main(() -> canvas.clearDisplayableView());
        fixture.cleanup();
    }

    private static void main(Runnable action) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action);
    }

    private static EditText field(View view) {
        if (view instanceof EditText) return (EditText) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                EditText child = field(group.getChildAt(i));
                if (child != null) return child;
            }
        }
        return null;
    }

    @Test public void offsetsAndLimitsUseUnicodeCodePoints() {
        editor.setContent("a😀bc");
        editor.setSelection(1, 1);
        assertEquals("😀", editor.getSelection());
        editor.insert("é", 2);
        assertEquals(3, editor.getCaretPosition());
        editor.delete(0, 1);
        editor.setMaxSize(2);
        assertEquals("😀é", editor.getContent());
        assertEquals(2, editor.size());
        assertThrows(StringIndexOutOfBoundsException.class, () -> editor.setCaret(3));
        assertThrows(IllegalArgumentException.class, () -> editor.insert("x", 1));
        assertEquals("😀é", editor.getContent());
    }

    @Test public void nullTextClampedInsertionAndConstraintChanges() {
        editor.setContent(null);
        editor.insert("12", 10);
        editor.insert("-", -1);
        editor.setConstraints(TextField.NUMERIC);
        assertEquals("-12", editor.getContent());
        assertThrows(IllegalArgumentException.class, () -> editor.setMaxSize(1));
        assertEquals(8, editor.getMaxSize());
        assertThrows(IllegalArgumentException.class, () -> editor.insert("x", 2));
        editor.setConstraints(TextField.ANY);
        editor.setContent("Asha");
        editor.setConstraints(TextField.DECIMAL);
        assertEquals("", editor.getContent());
        assertThrows(IllegalStateException.class, () -> editor.setFocus(true));
    }

    @Test public void androidInputUpdatesGuestAndSurvivesCanvasRecreation() throws Exception {
        CountDownLatch change = new CountDownLatch(1);
        editor.setTextEditorListener((source, action) -> {
            if ((action & TextEditorListener.ACTION_CONTENT_CHANGE) != 0) change.countDown();
        });
        editor.setParent(canvas);
        editor.setSize(5, 7, 112, 41);
        editor.setMaxSize(3);
        editor.setVisible(true);
        main(() -> {
            EditText item = field(canvas.getDisplayableView());
            assertEquals(5f, item.getTranslationX(), 0f);
            assertEquals(41, item.getLayoutParams().height);
            assertTrue(item.onCreateInputConnection(new EditorInfo()).commitText("a😀cd", 1));
        });
        assertTrue(change.await(3, TimeUnit.SECONDS));
        assertEquals("a😀c", editor.getContent());
        main(() -> {
            canvas.clearDisplayableView();
            assertEquals("a😀c", field(canvas.getDisplayableView()).getText().toString());
        });
        editor.setParent(null);
        main(() -> assertNull(field(canvas.getDisplayableView())));
        assertFalse(editor.hasFocus());
    }
}
