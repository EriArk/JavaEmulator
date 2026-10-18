package ru.playsoftware.j2meloader.input;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.config.ProfileModel;

/** Phone-only transient shortcuts. Only the explicit handle reveals the row. */
public final class GameChrome {
    static final long HIDE_DELAY_MS = 5000;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final FrameLayout root;
    private final ProfileModel profile;
    private final ImageButton handle;
    private final View display, screenshot, menu;
    private boolean available, expanded, displayEnabled, screenshotEnabled;
    private final Runnable timeout = this::collapse;

    @SuppressLint("ClickableViewAccessibility")
    public GameChrome(FrameLayout root, ProfileModel profile) {
        this.root = root; this.profile = profile;
        display = root.findViewById(R.id.game_display_button);
        screenshot = root.findViewById(R.id.game_screenshot_button);
        menu = root.findViewById(R.id.game_menu_button);
        handle = new ImageButton(root.getContext());
        handle.setId(R.id.game_controls_handle);
        handle.setImageResource(R.drawable.ic_arrow_down);
        handle.setPadding(dp(16), dp(16), dp(16), dp(16));
        handle.setContentDescription("Show game controls");
        handle.setTooltipText("Show game controls");
        handle.setOnClickListener(view -> reveal());
        root.addView(handle, new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP | Gravity.END));
        for (View button : new View[]{display, screenshot, menu}) {
            if (button == null) continue;
            button.setOnTouchListener((view, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) handler.removeCallbacks(timeout);
                else if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) schedule();
                return false;
            });
        }
        root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            public void onViewAttachedToWindow(View view) { }
            public void onViewDetachedFromWindow(View view) { collapse(); }
        });
        render();
    }

    private int dp(int n) { return Math.round(n * root.getResources().getDisplayMetrics().density); }

    public void update(boolean available, boolean displayEnabled, boolean screenshotEnabled) {
        this.available = available;
        this.displayEnabled = displayEnabled;
        this.screenshotEnabled = screenshotEnabled;
        if (!available) { expanded = false; handler.removeCallbacks(timeout); }
        TouchAppearance.shortcuts(root, profile);
        render();
    }

    private void reveal() {
        if (!available) return;
        expanded = true; render(); schedule();
    }

    public void collapse() {
        handler.removeCallbacks(timeout); expanded = false; render();
    }

    private void schedule() {
        handler.removeCallbacks(timeout);
        if (available && expanded) handler.postDelayed(timeout, HIDE_DELAY_MS);
    }

    private void render() {
        visible(handle, available && !expanded);
        visible(display, available && expanded && displayEnabled);
        visible(screenshot, available && expanded && screenshotEnabled);
        visible(menu, available && expanded);
    }

    private static void visible(View view, boolean visible) {
        if (view != null) view.setVisibility(visible ? View.VISIBLE : View.GONE);
    }
}
