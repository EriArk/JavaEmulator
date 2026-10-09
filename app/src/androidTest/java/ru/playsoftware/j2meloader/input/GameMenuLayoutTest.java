package ru.playsoftware.j2meloader.input;

import android.app.Instrumentation;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.view.ContextThemeWrapper;
import android.view.KeyEvent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import java.util.concurrent.atomic.AtomicInteger;
import ru.playsoftware.j2meloader.MainActivity;
import ru.playsoftware.j2meloader.BuildConfig;
import ru.playsoftware.j2meloader.R;
import static org.junit.Assert.*;

public class GameMenuLayoutTest {
    @Test public void firstControllerPressAfterLayoutChoosesActionNotClose() {
        withActivity((instrumentation, activity) -> {
            GameMenuDialog[] dialog = new GameMenuDialog[1];
            AtomicInteger clicks = new AtomicInteger();
            try {
                instrumentation.runOnMainSync(() -> {
                    dialog[0] = new GameMenuDialog(activity, "Fixture");
                    dialog[0].show(); dialog[0].page("Library menu");
                    dialog[0].action("View", 0, clicks::incrementAndGet);
                    dialog[0].action("Sort", 0, () -> fail("Wrong initial focus"));
                });
                instrumentation.waitForIdleSync();
                android.os.SystemClock.sleep(150);
                instrumentation.runOnMainSync(() -> {
                    assertTrue("First action must receive focus", dialog[0].getCurrentFocus() instanceof Button);
                    assertEquals("View", ((Button) dialog[0].getCurrentFocus()).getText().toString());
                    dialog[0].dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A));
                    dialog[0].dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_A));
                    assertTrue("A must not close the menu", dialog[0].isShowing());
                    assertEquals(1, clicks.get());
                });
            } finally { instrumentation.runOnMainSync(() -> { if (dialog[0] != null) dialog[0].dismiss(); }); }
        });
    }

    @Test public void phoneMenuSurvivesRotationAndChangesPlacement() {
        org.junit.Assume.assumeFalse(BuildConfig.HANDHELD_MODE);
        withActivity((instrumentation, activity) -> {
            GameMenuDialog[] dialog = new GameMenuDialog[1];
            try {
                rotate(instrumentation, activity, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                        Configuration.ORIENTATION_PORTRAIT);
                instrumentation.runOnMainSync(() -> {
                    dialog[0] = new GameMenuDialog(activity, "Fixture");
                    dialog[0].show(); dialog[0].page("View");
                    dialog[0].action("Grid", 0, () -> {});
                    assertEquals(Gravity.BOTTOM, dialog[0].getWindow().getAttributes().gravity & Gravity.VERTICAL_GRAVITY_MASK);
                });
                rotate(instrumentation, activity, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
                        Configuration.ORIENTATION_LANDSCAPE);
                instrumentation.runOnMainSync(() -> {
                    assertTrue(dialog[0].isShowing()); assertFalse(activity.isDestroyed());
                    assertEquals(Gravity.CENTER, dialog[0].getWindow().getAttributes().gravity);
                });
                rotate(instrumentation, activity, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                        Configuration.ORIENTATION_PORTRAIT);
                instrumentation.runOnMainSync(() -> {
                    assertTrue(dialog[0].isShowing());
                    assertEquals(Gravity.BOTTOM, dialog[0].getWindow().getAttributes().gravity & Gravity.VERTICAL_GRAVITY_MASK);
                });
            } finally { instrumentation.runOnMainSync(() -> { if (dialog[0] != null) dialog[0].dismiss(); }); }
        });
    }

    private void rotate(Instrumentation instrumentation, MainActivity activity, int requested, int expected) {
        instrumentation.runOnMainSync(() -> activity.setRequestedOrientation(requested));
        long deadline = android.os.SystemClock.uptimeMillis() + 5000;
        while (activity.getResources().getConfiguration().orientation != expected
                && android.os.SystemClock.uptimeMillis() < deadline) android.os.SystemClock.sleep(50);
        instrumentation.waitForIdleSync();
        assertEquals(expected, activity.getResources().getConfiguration().orientation);
    }

    @Test public void headerProvidesTouchBackAndCloseAndControllerBack() {
        withActivity((instrumentation, activity) -> instrumentation.runOnMainSync(() -> {
            GameMenuDialog menu = new GameMenuDialog(activity, "Fixture");
            menu.show();
            try {
                AtomicInteger backs = new AtomicInteger();
                menu.page("Parent"); menu.action("Item", 0, () -> {});
                assertNotNull(find(menu.getWindow().getDecorView(), "Close"));
                menu.page("Child");
                menu.setBackAction(() -> { backs.incrementAndGet(); menu.page("Parent"); });
                find(menu.getWindow().getDecorView(), "Back").performClick();
                assertEquals(1, backs.get()); assertTrue(menu.isShowing());
                menu.setBackAction(backs::incrementAndGet);
                menu.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_B));
                menu.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_B));
                assertEquals(2, backs.get());
                menu.page("Parent");
                find(menu.getWindow().getDecorView(), "Close").performClick();
                assertFalse(menu.isShowing());
            } finally { menu.dismiss(); }
        }));
    }

    @Test public void largeTextWrapsAndHeaderStaysOutsideScrollingBody() {
        withActivity((instrumentation, activity) -> instrumentation.runOnMainSync(() -> {
            ContextThemeWrapper context = new ContextThemeWrapper(activity, R.style.AppTheme);
            Configuration config = new Configuration(activity.getResources().getConfiguration());
            config.fontScale = 1.5f;
            context.applyOverrideConfiguration(config);
            GameMenuDialog menu = new GameMenuDialog(context, "A long game title that should stay on one line");
            menu.show();
            try {
                menu.page("Library menu");
                Button longAction = menu.action("Replace legacy art, keep custom images", 0, () -> {});
                for (int i = 0; i < 10; i++) menu.action("Action " + i, 0, () -> {});
                ViewGroup panel = (ViewGroup) ((FrameLayout) menu.findViewById(android.R.id.content)).getChildAt(0);
                float density = context.getResources().getDisplayMetrics().density;
                for (int width : new int[]{296, 416}) {
                    panel.measure(View.MeasureSpec.makeMeasureSpec(Math.round(width * density), View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(Math.round(240 * density), View.MeasureSpec.AT_MOST));
                    panel.layout(0, 0, panel.getMeasuredWidth(), panel.getMeasuredHeight());
                    assertTrue("Menu exceeds available height", panel.getHeight() <= 240 * density);
                    assertTrue("Touch target too small", longAction.getHeight() >= 48 * density);
                    assertTrue("Long action text clipped", longAction.getLayout().getHeight()
                            <= longAction.getHeight() - longAction.getCompoundPaddingTop() - longAction.getCompoundPaddingBottom());
                    View close = find(panel, "Close");
                    assertTrue(close.getWidth() >= 48 * density);
                    ScrollView body = (ScrollView) panel.getChildAt(1);
                    body.scrollTo(0, 0);
                    assertTrue("Scrolling body has no height", body.getHeight() > 0);
                    assertTrue("Long menu must scroll", body.canScrollVertically(1));
                    int headerTop = panel.getChildAt(0).getTop();
                    body.scrollTo(0, 1000);
                    assertEquals(headerTop, panel.getChildAt(0).getTop());
                    assertNotEquals(body, close.getParent());
                }
            } finally { menu.dismiss(); }
        }));
    }

    private interface Check { void run(Instrumentation instrumentation, MainActivity activity); }
    private void withActivity(Check check) {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        MainActivity activity = (MainActivity) instrumentation.startActivitySync(new Intent(
                instrumentation.getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try { check.run(instrumentation, activity); }
        finally { instrumentation.runOnMainSync(activity::finish); }
    }
    private View find(View view, String description) {
        if (description.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription())) return view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            View match = find(((ViewGroup) view).getChildAt(i), description);
            if (match != null) return match;
        }
        return null;
    }
}
