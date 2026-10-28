package ru.playsoftware.j2meloader.input;

import android.content.Context;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.config.ProfileModel;
import static org.junit.Assert.*;

public class GameChromeTest {
    private FrameLayout root;
    private GameChrome chrome;
    private View game, handle, menu, camera;
    private int presses;
    private ProfileModel profile;

    private void main(Runnable action) { InstrumentationRegistry.getInstrumentation().runOnMainSync(action); }
    private void setup() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        root = new FrameLayout(context);
        game = new View(context);
        game.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_UP) presses++;
            return true;
        });
        root.addView(game, new FrameLayout.LayoutParams(-1, -1));
        for (int id : new int[]{R.id.game_menu_button, R.id.game_screenshot_button, R.id.game_display_button}) {
            ImageButton button = new ImageButton(context); button.setId(id);
            root.addView(button, new FrameLayout.LayoutParams(48, 48, Gravity.TOP | Gravity.START));
        }
        profile = new ProfileModel();
        chrome = new GameChrome(root, profile);
        chrome.update(true, true, true);
        handle = root.findViewById(R.id.game_controls_handle);
        menu = root.findViewById(R.id.game_menu_button);
        camera = root.findViewById(R.id.game_screenshot_button);
        root.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 600, 900);
    }
    private void event(View view, int action, float x, float y) {
        long now = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(now, now, action, x, y, 0);
        try { view.dispatchTouchEvent(event); } finally { event.recycle(); }
    }

    @Test public void normalGameTouchesDoNotRevealChromeOrResizeGame() {
        main(() -> {
            setup();
            try {
                assertEquals(View.GONE, menu.getVisibility());
                assertEquals(View.VISIBLE, handle.getVisibility());
                event(root, MotionEvent.ACTION_DOWN, 150, 200);
                event(root, MotionEvent.ACTION_UP, 150, 200);
                assertEquals(1, presses); assertEquals(View.GONE, menu.getVisibility());
                handle.performClick();
                assertEquals(View.VISIBLE, menu.getVisibility());
                assertEquals(View.GONE, handle.getVisibility());
                assertEquals(0, game.getTop()); assertEquals(900, game.getHeight());
                chrome.collapse();
                assertEquals(900, game.getHeight());
            } finally { chrome.collapse(); }
        });
    }

    @Test public void rowHidesAfterFiveSeconds() {
        main(() -> { setup(); handle.performClick(); });
        try {
            SystemClock.sleep(GameChrome.HIDE_DELAY_MS + 250);
            main(() -> {
                assertEquals(View.GONE, menu.getVisibility());
                assertEquals(View.VISIBLE, handle.getVisibility());
            });
        } finally { main(() -> chrome.collapse()); }
    }

    @Test public void holdingShortcutDelaysHideAndBackgroundCancelsIt() {
        main(() -> { setup(); handle.performClick(); event(camera, MotionEvent.ACTION_DOWN, 20, 20); });
        try {
            SystemClock.sleep(GameChrome.HIDE_DELAY_MS + 250);
            main(() -> {
                assertEquals(View.VISIBLE, camera.getVisibility());
                event(camera, MotionEvent.ACTION_CANCEL, 20, 20);
                chrome.update(false, true, true);
                assertEquals(View.GONE, menu.getVisibility()); assertEquals(View.GONE, handle.getVisibility());
                chrome.update(true, false, false);
                assertEquals(View.VISIBLE, handle.getVisibility()); handle.performClick();
                assertEquals(View.GONE, camera.getVisibility());
                assertEquals(View.GONE, root.findViewById(R.id.game_display_button).getVisibility());
                assertEquals(View.VISIBLE, menu.getVisibility());
            });
        } finally { main(() -> chrome.collapse()); }
    }

    @Test public void hiddenHandleDoesNotConsumeTouchesAndCanBeRestored() {
        main(() -> {
            setup();
            try {
                profile.gameHandleVisible = false; chrome.update(true, true, true);
                assertEquals(View.GONE, handle.getVisibility());
                event(root, MotionEvent.ACTION_DOWN, 590, 10);
                event(root, MotionEvent.ACTION_UP, 590, 10);
                assertEquals(1, presses); assertEquals(View.GONE, menu.getVisibility());
                profile.gameHandleVisible = true; chrome.update(true, true, true);
                assertEquals(View.VISIBLE, handle.getVisibility());
                handle.performClick(); assertEquals(View.VISIBLE, menu.getVisibility());
            } finally { chrome.collapse(); }
        });
    }

    @Test public void placementAndOpacityAreIndependentOfExpandedStateAndGame() {
        main(() -> {
            setup();
            try {
                GameHandlePlacement.move(profile, false, new android.graphics.RectF(0, 0, 600, 900),
                        handle.getLayoutParams().width, -9999, 9999);
                profile.gameShortcutOpacity = 35; chrome.update(true, true, true);
                FrameLayout.LayoutParams before = (FrameLayout.LayoutParams) handle.getLayoutParams();
                int x = before.leftMargin, y = before.topMargin;
                assertEquals(0, x); assertTrue(y > 0);
                assertEquals(.35f, menu.getAlpha(), .001f); assertEquals(.35f, handle.getAlpha(), .001f);
                assertEquals(1, game.getAlpha(), 0);
                handle.performClick(); chrome.collapse();
                FrameLayout.LayoutParams after = (FrameLayout.LayoutParams) handle.getLayoutParams();
                assertEquals(x, after.leftMargin); assertEquals(y, after.topMargin);
                root.measure(View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY));
                root.layout(0, 0, 900, 600);
                after = (FrameLayout.LayoutParams) handle.getLayoutParams();
                assertEquals(0, after.topMargin); assertEquals(900 - after.width, after.leftMargin);
            } finally { chrome.collapse(); }
        });
    }

    @Test public void movedHandleNeverCoversKeepUndoAndReturnsAfterPreview() {
        main(() -> {
            setup();
            SettingsPreviewBar preview = new SettingsPreviewBar(root.getContext(), () -> {}, () -> {});
            preview.attachTo(root);
            try {
                GameHandlePlacement.move(profile, false, new android.graphics.RectF(0, 0, 600, 900),
                        handle.getLayoutParams().width, -9999, 120);
                preview.start();
                root.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY));
                root.layout(0, 0, 600, 900);
                // Place the handle in the actual preview panel, independent of device density.
                profile.gameHandlePortrait.x = preview.getLeft() / (float)(600 - handle.getLayoutParams().width);
                profile.gameHandlePortrait.y = preview.getTop() / (float)(900 - handle.getLayoutParams().height);
                chrome.render(); assertEquals(View.GONE, handle.getVisibility());
                preview.stop(); chrome.render(); assertEquals(View.VISIBLE, handle.getVisibility());
            } finally { preview.stop(); chrome.collapse(); }
        });
    }
}
