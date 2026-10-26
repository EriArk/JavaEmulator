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
    @Test public void socialUsesExistingDestinationsAndBackKeepsThePanelAlive() {
        withActivity((instrumentation, activity) -> instrumentation.runOnMainSync(() -> {
            GameMenuDialog menu = new GameMenuDialog(activity, "Fixture"); menu.useEdgePanel(); menu.show();
            java.util.List<Intent> launches = new java.util.ArrayList<>();
            try {
                SocialMenuPages.show(menu, activity, () -> menu.page("Game menu"), launches::add);
                View root = menu.getWindow().getDecorView();
                for (String label : new String[]{"Lobbies", "Friends", "Chats", "Fluxer account"}) findText(root,label).performClick();
                assertEquals(4, launches.size()); assertTrue(menu.isShowing());
                assertEquals("ru.playsoftware.j2meloader.multiplayer.LobbyDirectoryActivity", launches.get(0).getComponent().getClassName());
                assertTrue(launches.get(1).getBooleanExtra("friends",false));
                assertFalse(launches.get(2).getBooleanExtra("friends",false));
                find(root,"Back").performClick(); assertNotNull(findText(root,"Game menu"));
            } finally { menu.dismiss(); }
        }));
    }

    @Test public void pageReturnRestoresFocusedActionInsteadOfFirstRow() {
        withActivity((instrumentation, activity) -> {
            GameMenuDialog[] menu = new GameMenuDialog[1];
            Runnable[] home = new Runnable[1];
            try {
                instrumentation.runOnMainSync(() -> {
                    menu[0] = new GameMenuDialog(activity,"Fixture"); menu[0].useEdgePanel(); menu[0].show();
                    home[0] = () -> {
                        menu[0].page("Parent"); menu[0].action("Play",0,()->{});
                        menu[0].action("More",0,()->{ menu[0].page("Child"); menu[0].action("Item",0,()->{}); menu[0].setBackAction(home[0]); });
                    };
                    home[0].run();
                });
                instrumentation.waitForIdleSync(); android.os.SystemClock.sleep(100);
                instrumentation.runOnMainSync(() -> {
                    View more=findText(menu[0].getWindow().getDecorView(),"More");
                    more.requestFocusFromTouch(); more.performClick();
                });
                instrumentation.waitForIdleSync();
                instrumentation.runOnMainSync(() -> find(menu[0].getWindow().getDecorView(),"Back").performClick());
                instrumentation.waitForIdleSync(); android.os.SystemClock.sleep(100);
                instrumentation.runOnMainSync(() -> assertEquals("More",((Button)menu[0].getCurrentFocus()).getText().toString()));
            } finally { instrumentation.runOnMainSync(() -> {if(menu[0]!=null)menu[0].dismiss();}); }
        });
    }

    @Test public void dialogPreservesFullscreenGameWindowOnBothVariants() {
        withActivity((instrumentation, activity) -> instrumentation.runOnMainSync(() -> {
            int before = activity.getWindow().getAttributes().flags;
            activity.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
            GameMenuDialog menu = new GameMenuDialog(activity, "Fullscreen fixture");
            try {
                menu.show(); menu.page("Game menu");
                menu.action("Resume", 0, menu::dismiss);
                assertTrue((menu.getWindow().getAttributes().flags
                        & android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0);
            } finally {
                menu.dismiss();
                activity.getWindow().setFlags(before, android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
            }
        }));
    }

    @Test public void commonGamePagesKeepPauseDuringExitAndResumeFirstDuringPreview() {
        withActivity((instrumentation, activity) -> instrumentation.runOnMainSync(() -> {
            GameMenuDialog menu = new GameMenuDialog(activity, "Fixture");
            AtomicInteger exits = new AtomicInteger();
            AtomicInteger screenshots = new AtomicInteger();
            AtomicInteger restarts = new AtomicInteger();
            GameMenuPages pages = new GameMenuPages(menu, new GameMenuPages.Host() {
                public void display() { menu.page("Display"); }
                public void controls() { menu.page("Controls"); }
                public void more() { menu.action("Engine setting", 0, () -> {}); }
                public void exit() { exits.incrementAndGet(); }
                public void restart() { restarts.incrementAndGet(); }
                public boolean hasPreview() { return true; }
                public void keep() { }
                public void undo() { }
            }, screenshots::incrementAndGet);
            menu.show();
            try {
                pages.home();
                View root = menu.getWindow().getDecorView();
                java.util.List<String> actions = new java.util.ArrayList<>();
                collectButtons(root, actions);
                assertEquals(java.util.Arrays.asList("Resume", "Display", "Controls",
                        "Keep changes", "Undo changes", "More", "Exit game"), actions);
                findText(root, "More").performClick();
                assertNotNull(findText(root, "Engine setting"));
                findText(root, "Restart game").performClick();
                assertTrue(menu.isShowing()); assertEquals(0, restarts.get());
                findText(root, "Cancel").performClick();
                assertNotNull(findText(root, "Restart game"));
                pages.confirmRestart();
                menu.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_B));
                assertEquals(0, restarts.get()); assertNotNull(findText(root, "Restart game"));
                pages.confirmRestart(); findText(root, "Restart").performClick();
                assertEquals(1, restarts.get()); assertTrue(menu.isShowing());
                pages.home();
                findText(root, "Exit game").performClick();
                assertTrue(menu.isShowing()); assertEquals(0, exits.get());
                menu.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_B));
                assertNotNull(findText(root, "Display")); assertTrue(menu.isShowing());
                pages.confirmExit();
                findText(root, "Leave without saving").performClick();
                assertEquals(1, exits.get());
                assertTrue("Host owns teardown; confirmation must not resume first", menu.isShowing());
                pages.more(); findText(root, "Screenshot").performClick();
                assertFalse(menu.isShowing()); assertEquals(1, screenshots.get());
            } finally { menu.dismiss(); }
        }));
    }

    @Test public void unsupportedScreenshotIsNotOfferedAndResumeClosesMenu() {
        withActivity((instrumentation, activity) -> instrumentation.runOnMainSync(() -> {
            GameMenuDialog menu = new GameMenuDialog(activity, "Form fixture");
            menu.show();
            try {
                new GameMenuPages(menu, new GameMenuPages.Host() {
                    public void display() { }
                    public void controls() { }
                    public void more() { }
                    public void exit() { fail("Resume must not exit"); }
                    public void restart() { fail("Resume must not restart"); }
                    public boolean hasPreview() { return false; }
                    public void keep() { }
                    public void undo() { }
                }, null).home();
                View root = menu.getWindow().getDecorView();
                assertNull(findText(root, "Screenshot")); assertNull(findText(root, "Keep changes"));
                findText(root, "Resume").performClick(); assertFalse(menu.isShowing());
            } finally { menu.dismiss(); }
        }));
    }

    private void collectButtons(View view, java.util.List<String> labels) {
        if (view instanceof Button) labels.add(((Button) view).getText().toString());
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
            collectButtons(((ViewGroup) view).getChildAt(i), labels);
    }
    private View findText(View view, String text) {
        if (view instanceof android.widget.TextView && text.contentEquals(((android.widget.TextView) view).getText())) return view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            View found = findText(((ViewGroup) view).getChildAt(i), text);
            if (found != null) return found;
        }
        return null;
    }

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
            android.graphics.Point displaySize = new android.graphics.Point();
            activity.getWindowManager().getDefaultDisplay().getRealSize(displaySize);
            org.junit.Assume.assumeTrue("A square display has no distinct portrait/landscape dimensions",
                    displaySize.x != displaySize.y);
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
                awaitGravity(instrumentation, dialog[0], Gravity.CENTER);
                instrumentation.runOnMainSync(() -> {
                    assertTrue(dialog[0].isShowing()); assertFalse(activity.isDestroyed());
                    assertEquals(Gravity.CENTER, dialog[0].getWindow().getAttributes().gravity);
                });
                rotate(instrumentation, activity, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                        Configuration.ORIENTATION_PORTRAIT);
                awaitGravity(instrumentation, dialog[0], Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
                instrumentation.runOnMainSync(() -> {
                    assertTrue(dialog[0].isShowing());
                    assertEquals(Gravity.BOTTOM, dialog[0].getWindow().getAttributes().gravity & Gravity.VERTICAL_GRAVITY_MASK);
                });
            } finally { instrumentation.runOnMainSync(() -> { if (dialog[0] != null) dialog[0].dismiss(); }); }
        });
    }

    private void awaitGravity(Instrumentation instrumentation, GameMenuDialog dialog, int expected) {
        // Resources update before the dialog receives its configuration/layout traversal.
        AtomicInteger actual = new AtomicInteger();
        long deadline = android.os.SystemClock.uptimeMillis() + 5000;
        do {
            instrumentation.runOnMainSync(() -> actual.set(dialog.getWindow().getAttributes().gravity));
            if (actual.get() == expected) return;
            android.os.SystemClock.sleep(50);
        } while (android.os.SystemClock.uptimeMillis() < deadline);
        assertEquals(expected, actual.get());
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
