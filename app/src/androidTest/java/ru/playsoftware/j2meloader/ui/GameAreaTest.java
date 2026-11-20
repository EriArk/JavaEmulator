/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import org.junit.Test;
import static org.junit.Assert.*;

public class GameAreaTest {
    private Button button(View view, String title) {
        if (view instanceof Button && title.contentEquals(((Button)view).getText())) return (Button)view;
        if (view instanceof ViewGroup) {
            ViewGroup g = (ViewGroup)view;
            for (int i=0; i<g.getChildCount(); i++) {
                Button b = button(g.getChildAt(i), title);
                if (b != null) return b;
            }
        }
        return null;
    }
    private void touch(View v, int type, int x, int y) {
        MotionEvent e = MotionEvent.obtain(1, 2, type, x, y, 0);
        try {v.dispatchTouchEvent(e);} finally {e.recycle();}
    }
    @Test public void drawSaveReopenAndResetPortrait() throws Exception {
        Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue(c.getPackageName().endsWith(".debug"));
        int[] before = GameArea.read(c,true), landscape = GameArea.read(c,false);
        Intent intent = new Intent(c, GameAreaActivity.class).putExtra("portrait",true);
        try {
            try (ActivityScenario<GameAreaActivity> scenario = ActivityScenario.launch(intent)) {
                scenario.onActivity(a -> {
                    FrameLayout content = a.findViewById(android.R.id.content);
                    FrameLayout root = (FrameLayout)content.getChildAt(0);
                    View preview = root.getChildAt(0);
                    assertTrue(preview.getHeight()>340);
                    button(root,"Draw").performClick();
                    touch(preview, MotionEvent.ACTION_DOWN,31,71);
                    touch(preview, MotionEvent.ACTION_MOVE,283,331);
                    touch(preview, MotionEvent.ACTION_UP,283,331);
                    button(root,"Save").performClick();
                    int[] data = GameArea.read(c,true);
                    assertArrayEquals(new int[]{preview.getWidth(),preview.getHeight(),31,71,283,331},data);
                    assertEquals(new Rect(62,142,566,662),GameArea.bounds(data,data[0]*2,data[1]*2));
                });
            }
            try (ActivityScenario<GameAreaActivity> scenario = ActivityScenario.launch(intent)) {
                scenario.onActivity(a -> {
                    View root = a.findViewById(android.R.id.content);
                    button(root,"Pixels").performClick();
                    View dialog = null;
                    for (View window : android.view.inspector.WindowInspector.getGlobalWindowViews()) {
                        if (button(window, "Preview") != null) dialog = window;
                    }
                    assertNotNull(dialog);
                    java.util.ArrayList<View> fields = new java.util.ArrayList<>();
                    for (String name : new String[]{"X from left","Y from top","Width","Height"}) {
                        dialog.findViewsWithText(fields,name,View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);
                    }
                    assertEquals(4,fields.size());
                    int[] values = {37,59,241,319};
                    for (int i=0;i<4;i++) ((android.widget.EditText)fields.get(i)).setText(String.valueOf(values[i]));
                    button(dialog,"Preview").performClick();
                    button(root,"Save").performClick();
                    int[] data = GameArea.read(c,true);
                    assertEquals(new Rect(37,59,278,378),GameArea.bounds(data,data[0],data[1]));
                });
            }
            try (ActivityScenario<GameAreaActivity> scenario = ActivityScenario.launch(intent)) {
                scenario.onActivity(a -> {
                    View root = a.findViewById(android.R.id.content);
                    button(root,"Reset").performClick();
                    button(root,"Save").performClick();
                    int[] data = GameArea.read(c,true);
                    assertEquals(new Rect(0,0,640,480),GameArea.bounds(data,640,480));
                });
            }
            assertArrayEquals(landscape,GameArea.read(c,false));
        } finally {
            if (before == null) new android.util.AtomicFile(new File(c.getFilesDir(),"game-area-portrait")).delete();
            else GameArea.save(c,true,before[0],before[1],new Rect(before[2],before[3],before[4],before[5]));
        }
    }
}
