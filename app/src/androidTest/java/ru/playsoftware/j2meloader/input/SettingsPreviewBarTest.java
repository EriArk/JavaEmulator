package ru.playsoftware.j2meloader.input;

import android.content.Context;
import android.content.res.Configuration;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import static org.junit.Assert.*;

public class SettingsPreviewBarTest {
    private void main(Runnable action) { InstrumentationRegistry.getInstrumentation().runOnMainSync(action); }
    private int dp(Context context, int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
    private Context context(boolean dark, float fontScale) {
        Context base = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | (dark ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        config.fontScale = fontScale;
        return base.createConfigurationContext(config);
    }
    private void layout(View root, int width, int height) {
        root.measure(View.MeasureSpec.makeMeasureSpec(dp(root.getContext(), width), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(dp(root.getContext(), height), View.MeasureSpec.EXACTLY));
        root.layout(0, 0, root.getMeasuredWidth(), root.getMeasuredHeight());
    }

    @Test public void compactOverlayFitsSmallAndPhoneScreensWithoutMovingGame() {
        main(() -> {
            for (boolean dark : new boolean[]{false, true}) for (float font : new float[]{1, 1.5f, 2}) {
                Context context = context(dark, font);
                FrameLayout root = new FrameLayout(context);
                View game = new View(context); root.addView(game, new FrameLayout.LayoutParams(-1, -1));
                SettingsPreviewBar bar = new SettingsPreviewBar(context, () -> {}, () -> {}); bar.attachTo(root);
                try {
                    bar.start();
                    for (int[] size : new int[][]{{240,240},{320,240},{360,640},{640,360},{480,320}}) {
                        layout(root, size[0], size[1]);
                        assertEquals(0, game.getTop()); assertEquals(root.getHeight(), game.getHeight());
                        assertEquals(dp(context, 64) + 2 * dp(context, 48) + 4 * dp(context, 4), bar.getWidth());
                        assertEquals(dp(context, 48) + 2 * dp(context, 2), bar.getHeight());
                        assertTrue(bar.getRight() < root.getWidth()); assertTrue(bar.getBottom() < root.getHeight());
                        assertTrue("Shortcut row stays clear", bar.getTop() >= dp(context, 48));
                        TextView timer = (TextView) bar.getChildAt(0);
                        assertNotNull(timer.getLayout());
                        assertEquals(0, timer.getLayout().getEllipsisCount(0));
                        assertTrue(timer.getPaint().measureText(timer.getText().toString()) <= timer.getWidth());
                        for (int i = 1; i < 3; i++) {
                            View button = bar.getChildAt(i);
                            assertTrue(button instanceof ImageButton);
                            assertEquals(dp(context, 48), button.getWidth()); assertEquals(dp(context, 48), button.getHeight());
                            assertTrue(button.getRight() <= bar.getWidth());
                            assertNotNull(button.getContentDescription()); assertNotNull(button.getTooltipText());
                        }
                    }
                } finally { bar.stop(); }
            }
        });
    }

    @Test public void buttonsStayLightOnDarkInBothAppThemes() {
        main(() -> {
            for (boolean dark : new boolean[]{false, true}) {
                SettingsPreviewBar bar = new SettingsPreviewBar(context(dark, 1), () -> {}, () -> {});
                for (int i = 1; i < 3; i++) {
                    ImageButton button = (ImageButton) bar.getChildAt(i);
                    assertTrue(androidx.core.graphics.ColorUtils.calculateLuminance(button.getImageTintList().getDefaultColor()) > .7);
                    android.graphics.Bitmap image = android.graphics.Bitmap.createBitmap(48, 48, android.graphics.Bitmap.Config.ARGB_8888);
                    try {
                        button.getBackground().setBounds(0, 0, 48, 48);
                        button.getBackground().draw(new android.graphics.Canvas(image));
                        assertTrue(androidx.core.graphics.ColorUtils.calculateLuminance(image.getPixel(24, 24)) < .1);
                    } finally { image.recycle(); }
                }
            }
        });
    }

    @Test public void actionsAndControllerFocusRemainSeparateFromGameInput() {
        try (androidx.test.core.app.ActivityScenario<ru.playsoftware.j2meloader.MainActivity> scenario =
                     androidx.test.core.app.ActivityScenario.launch(ru.playsoftware.j2meloader.MainActivity.class)) {
        scenario.onActivity(activity -> {
            Context context = activity;
            int[] calls = new int[2];
            FrameLayout root = new FrameLayout(context);
            View game = new View(context); game.setFocusableInTouchMode(true);
            root.addView(game, new FrameLayout.LayoutParams(-1, -1)); game.requestFocus();
            SettingsPreviewBar bar = new SettingsPreviewBar(context, () -> calls[0]++, () -> calls[1]++);
            bar.attachTo(root);
            activity.setContentView(root); game.requestFocus();
            try {
                bar.start(); layout(root, 320, 240);
                assertTrue("Showing preview does not steal game focus", game.hasFocus());
                View keep = bar.getChildAt(1), undo = bar.getChildAt(2);
                assertTrue(keep.requestFocusFromTouch()); assertSame(undo, keep.focusSearch(View.FOCUS_RIGHT));
                assertSame(keep, undo.focusSearch(View.FOCUS_LEFT));
                keep.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER));
                keep.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER));
                undo.performClick();
                assertArrayEquals(new int[]{1,1}, calls);
            } finally { bar.stop(); }
        }); }
    }

    @Test public void touchesOutsideCompactPanelReachGameAndPanelTouchesDoNot() {
        main(() -> {
            Context context = context(false, 1); int[] touches = {0};
            FrameLayout root = new FrameLayout(context); View game = new View(context);
            game.setOnTouchListener((v, event) -> { if (event.getAction() == MotionEvent.ACTION_UP) touches[0]++; return true; });
            root.addView(game, new FrameLayout.LayoutParams(-1, -1));
            SettingsPreviewBar bar = new SettingsPreviewBar(context, () -> {}, () -> {}); bar.attachTo(root);
            try {
                bar.start(); layout(root, 320, 240);
                tap(root, dp(context, 280), dp(context, 70)); assertEquals(1, touches[0]);
                tap(root, bar.getLeft()+dp(context, 20), bar.getTop()+dp(context, 20)); assertEquals(1, touches[0]);
            } finally { bar.stop(); }
        });
    }
    private void tap(View root, int x, int y) {
        long time = SystemClock.uptimeMillis();
        for (int action : new int[]{MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP}) {
            MotionEvent event = MotionEvent.obtain(time, time, action, x, y, 0);
            try { root.dispatchTouchEvent(event); } finally { event.recycle(); }
        }
    }

    @Test public void suspendResumeAndStopKeepPreviewVisibilityContract() {
        main(() -> {
            SettingsPreviewBar bar = new SettingsPreviewBar(context(false, 1), () -> {}, () -> {});
            try {
                assertEquals(View.GONE, bar.getVisibility()); bar.resume(); assertEquals(View.GONE, bar.getVisibility());
                bar.start(); assertEquals(View.VISIBLE, bar.getVisibility());
                assertEquals("20s", ((TextView) bar.getChildAt(0)).getText().toString());
                bar.suspend(); assertEquals(View.GONE, bar.getVisibility());
                bar.resume(); assertEquals(View.VISIBLE, bar.getVisibility());
                bar.stop(); bar.resume(); assertEquals(View.GONE, bar.getVisibility());
            } finally { bar.stop(); }
        });
    }
}
