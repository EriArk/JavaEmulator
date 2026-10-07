package ru.playsoftware.j2meloader.input;

import android.content.Context;
import android.content.Intent;
import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.util.SparseIntArray;
import android.view.ContextThemeWrapper;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.gson.Gson;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.keyboard.KeyMapper;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.MainActivity;
import ru.playsoftware.j2meloader.config.ProfileModel;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ControllerMapperTest {
    private Context context() {
        return new ContextThemeWrapper(InstrumentationRegistry.getInstrumentation().getTargetContext(), R.style.AppTheme);
    }

    @Test public void oldProfileAndSyntheticInputsRoundTrip() {
        ProfileModel legacy = new ProfileModel();
        legacy.keyMappings = new SparseIntArray();
        legacy.keyMappings.put(96, 53);
        legacy.ensureKeyMappingProfiles();
        assertEquals("Custom", legacy.keyMappingProfiles.get(legacy.activeKeyMappingProfile).name);
        assertEquals(53, legacy.getActiveKeyMappings().get(96));
        legacy.getActiveKeyMappings().put(ControllerInput.DPAD_DL, 55);
        legacy.getActiveKeyMappings().put(ControllerInput.STICK_UL, ControllerInput.UNBOUND);
        Gson gson = new Gson();
        ProfileModel loaded = gson.fromJson(gson.toJson(legacy), ProfileModel.class);
        assertEquals(55, loaded.getActiveKeyMappings().get(ControllerInput.DPAD_DL));
        assertEquals(ControllerInput.UNBOUND, loaded.getActiveKeyMappings().get(ControllerInput.STICK_UL));
        assertEquals(53, loaded.keyMappings.get(96));
        assertEquals(55, KeyMapper.getEightWayKeyMap().get(ControllerInput.DPAD_DL));
        assertEquals(49, KeyMapper.getEightWayKeyMap().get(ControllerInput.STICK_UL));
    }

    @Test public void editIsADraftAndDuplicateTargetsArePreserved() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            ArrayList<ProfileModel.KeyMappingProfile> original = KeyMapper.createBuiltInProfiles();
            AtomicInteger saved = new AtomicInteger();
            ControllerMapperView editor = new ControllerMapperView(context(), "Test", original, 0,
                    new ControllerMapperView.Listener() {
                        public void cancel() { fail("Unexpected cancel"); }
                        public void save(ArrayList<ProfileModel.KeyMappingProfile> draft, int active) {
                            SparseIntArray mappings = draft.get(active).mappings;
                            assertEquals(53, mappings.get(96));
                            assertEquals(53, mappings.get(100));
                            assertNull(draft.get(0).mappings);
                            saved.incrementAndGet();
                        }
                    });
            find(editor, "Phone key 5").performClick();
            assertEquals(4, original.size());
            assertNull(original.get(0).mappings);
            assertEquals(Canvas.KEY_FIRE, KeyMapper.resolveMappings(original.get(0).mappings).get(96));
            findText(editor, context().getString(R.string.mapper_save)).performClick();
            assertEquals(1, saved.get());
        });
    }

    @Test public void cancelDoesNotSaveAndUnbindDoesNotFallBackToDefault() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            AtomicInteger cancelled = new AtomicInteger();
            ControllerMapperView editor = new ControllerMapperView(context(), "Test", KeyMapper.createBuiltInProfiles(), 0,
                    new ControllerMapperView.Listener() {
                        public void cancel() { cancelled.incrementAndGet(); }
                        public void save(ArrayList<ProfileModel.KeyMappingProfile> draft, int active) {
                            assertEquals(ControllerInput.UNBOUND, KeyMapper.resolveMappings(draft.get(active).mappings).get(96));
                        }
                    });
            find(editor, context().getString(R.string.mapper_unbind)).performClick();
            findText(editor, context().getString(R.string.mapper_save)).performClick();
            editor.handleKey(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK));
            assertEquals(1, cancelled.get());
        });
    }

    @Test public void controlsFitHandheldAndPhoneWidths() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context c = context();
            float density = c.getResources().getDisplayMetrics().density;
            for (int[] size : new int[][]{{620, 540}, {640, 360}, {360, 640}}) {
                ControllerMapperView editor = new ControllerMapperView(c, "A very long game title to check truncation",
                        KeyMapper.createBuiltInProfiles(), 0, new ControllerMapperView.Listener() {
                    public void cancel() { }
                    public void save(ArrayList<ProfileModel.KeyMappingProfile> draft, int active) { }
                });
                int w = Math.round(size[0] * density), h = Math.round(size[1] * density);
                editor.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
                editor.layout(0, 0, w, h);
                assertButtonGeometry(editor, density);
                Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                editor.draw(new android.graphics.Canvas(bitmap));
                File output = new File(c.getExternalFilesDir(null), "mapper-layout-" + size[0] + "x" + size[1] + ".png");
                try (FileOutputStream stream = new FileOutputStream(output)) {
                    assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
                } catch (IOException e) { throw new AssertionError(e); }
                finally { bitmap.recycle(); }
            }
        });
    }

    @Test public void controllerCanSelectAndBindWithoutTouch() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Activity activity = instrumentation.startActivitySync(new Intent(context(), MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        ControllerMapperView[] host = new ControllerMapperView[1];
        AtomicInteger saved = new AtomicInteger();
        try {
            instrumentation.runOnMainSync(() -> {
                ControllerMapperView editor = new ControllerMapperView(activity, "Test", KeyMapper.createBuiltInProfiles(), 0,
                        new ControllerMapperView.Listener() {
                            public void cancel() { fail("Unexpected cancel"); }
                            public void save(ArrayList<ProfileModel.KeyMappingProfile> draft, int active) {
                                assertEquals(50, draft.get(active).mappings.get(96));
                                assertEquals(55, draft.get(active).mappings.get(97));
                                saved.incrementAndGet();
                            }
                        });
                host[0] = editor;
                activity.setContentView(editor);
            });
            instrumentation.waitForIdleSync();
            instrumentation.runOnMainSync(() -> {
                ControllerMapperView editor = host[0];
                find(editor, "A").requestFocusFromTouch();
                editor.handleKey(new KeyEvent(KeyEvent.ACTION_DOWN, 96));
                assertEquals("Phone key OK / Fire", editor.findFocus().getContentDescription());
                int[] firePosition = new int[2], digitPosition = new int[2];
                find(editor,"Phone key OK / Fire").getLocationOnScreen(firePosition);
                find(editor,"Phone key 2").getLocationOnScreen(digitPosition);
                int[] path = digitPosition[0] > firePosition[0] + 20
                        ? new int[]{19,22,22,22} : new int[]{20,20};
                for (int direction : path) editor.handleKey(new KeyEvent(KeyEvent.ACTION_DOWN,direction));
                assertEquals("Phone key 2", editor.findFocus().getContentDescription());
                editor.handleKey(new KeyEvent(KeyEvent.ACTION_DOWN, 96));
                editor.handleKey(new KeyEvent(KeyEvent.ACTION_DOWN, 97));
                assertEquals("A", editor.findFocus().getContentDescription());
            });
            tap(instrumentation, host[0], "B");
            tap(instrumentation, host[0], "Phone key 7");
            instrumentation.runOnMainSync(() -> findText(host[0], context().getString(R.string.mapper_save)).performClick());
            assertEquals(1, saved.get());
        } finally { instrumentation.runOnMainSync(activity::finish); }
    }

    private void tap(Instrumentation instrumentation, View root, String description) {
        int[] point = new int[2];
        instrumentation.runOnMainSync(() -> {
            View view = find(root,description);
            view.requestRectangleOnScreen(new android.graphics.Rect(0,0,view.getWidth(),view.getHeight()),true);
        });
        instrumentation.waitForIdleSync();
        instrumentation.runOnMainSync(() -> {
            View view = find(root, description);
            view.getLocationOnScreen(point);
            point[0] += view.getWidth() / 2;
            point[1] += view.getHeight() / 2;
            android.graphics.Rect visible = new android.graphics.Rect();
            assertTrue("Not visible: " + description,view.getLocalVisibleRect(visible));
            assertTrue("Tap outside visible target: " + description + " " + visible,
                    visible.contains(view.getWidth()/2,view.getHeight()/2));
        });
        long now = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, point[0], point[1], 0);
        MotionEvent up = MotionEvent.obtain(now, now + 30, MotionEvent.ACTION_UP, point[0], point[1], 0);
        try { instrumentation.sendPointerSync(down); instrumentation.sendPointerSync(up); }
        finally { down.recycle(); up.recycle(); }
        instrumentation.waitForIdleSync();
    }

    @Test public void identifyPairAndUndoKeepsOriginalProfile() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        ControllerMapperView[] host = new ControllerMapperView[1];
        instrumentation.runOnMainSync(() -> {
            host[0] = new ControllerMapperView(context(), "Test", KeyMapper.createBuiltInProfiles(), 0,
                    new ControllerMapperView.Listener() {
                        public void cancel() { }
                        public void save(ArrayList<ProfileModel.KeyMappingProfile> profiles,int active) {
                            assertEquals(0,active);
                            assertNull(profiles.get(active).mappings);
                        }
                    });
            findText(host[0],context().getString(R.string.mapper_detect)).performClick();
            host[0].handleKey(new KeyEvent(KeyEvent.ACTION_DOWN,21));
            host[0].handleKey(new KeyEvent(KeyEvent.ACTION_DOWN,20));
        });
        SystemClock.sleep(180);
        instrumentation.runOnMainSync(() -> {
            assertTrue(find(host[0],"D-pad Down + left").isSelected());
            find(host[0],"Phone key 7").performClick();
            find(host[0],context().getString(R.string.mapper_undo)).performClick();
            findText(host[0],context().getString(R.string.mapper_save)).performClick();
        });
    }

    @Test public void gameMenuAcceptsControllerAfterTouch() {
        Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
        Activity activity=instrumentation.startActivitySync(new Intent(context(),MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        AtomicInteger clicked=new AtomicInteger();
        try {
            instrumentation.runOnMainSync(() -> {
                GameMenuDialog dialog=new GameMenuDialog(activity,"Test");
                dialog.show(); dialog.page("Game menu");
                dialog.action("Resume",android.R.drawable.ic_media_play,clicked::incrementAndGet);
                dialog.action("Other",0,()->fail("Wrong action"));
                if (dialog.getCurrentFocus() != null) dialog.getCurrentFocus().clearFocus();
                dialog.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,96));
                dialog.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,96));
                assertEquals(1,clicked.get());
                dialog.dismiss();
            });
        } finally { instrumentation.runOnMainSync(activity::finish); }
    }

    private void assertButtonGeometry(View view, float density) {
        if (view instanceof Button) {
            assertTrue("Too short: " + ((Button) view).getText(), view.getHeight() >= 28 * density);
            assertTrue("Too narrow: " + ((Button) view).getText(), view.getWidth() >= 30 * density);
            View parent = (View) view.getParent();
            assertTrue("Overflows parent", view.getRight() <= parent.getWidth());
        }
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
            assertButtonGeometry(((ViewGroup) view).getChildAt(i), density);
    }

    private View find(View view, String description) {
        if (description.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription())) return view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            View found = find(((ViewGroup) view).getChildAt(i), description);
            if (found != null) return found;
        }
        return null;
    }

    private View findText(View view, String text) {
        if (view instanceof Button && text.contentEquals(((Button) view).getText())) return view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            View found = findText(((ViewGroup) view).getChildAt(i), text);
            if (found != null) return found;
        }
        return null;
    }
}
