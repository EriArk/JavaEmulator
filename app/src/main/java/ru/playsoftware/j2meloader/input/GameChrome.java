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
    private final View display, screenshot, menu, fastForward;
    private boolean available, expanded, displayEnabled, screenshotEnabled;
    private final Runnable timeout = this::collapse;

    @SuppressLint("ClickableViewAccessibility")
    public GameChrome(FrameLayout root, ProfileModel profile) {
        this.root = root; this.profile = profile;
        display = root.findViewById(R.id.game_display_button);
        screenshot = root.findViewById(R.id.game_screenshot_button);
        menu = root.findViewById(R.id.game_menu_button);
        fastForward = root.findViewById(R.id.game_fast_forward_button);
        handle = new ImageButton(root.getContext());
        handle.setId(R.id.game_controls_handle);
        handle.setImageResource(R.drawable.ic_arrow_down);
        handle.setPadding(dp(16), dp(16), dp(16), dp(16));
        handle.setContentDescription("Show game controls");
        handle.setTooltipText("Show game controls");
        handle.setOnClickListener(view -> reveal());
        root.addView(handle, new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP | Gravity.END));
        root.getViewTreeObserver().addOnGlobalLayoutListener(this::render);
        root.addOnLayoutChangeListener((view, l, t, r, b, ol, ot, or, ob) -> positionHandle());
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(handle, (view, insets) -> {
            handle.post(this::positionHandle);
            return insets;
        });
        for (View button : new View[]{display, screenshot, menu, fastForward}) {
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

    public void render() {
        positionHandle();
        visible(handle, available && !expanded && profile.gameHandleVisible && !previewCoversHandle());
        visible(display, available && expanded && displayEnabled);
        visible(screenshot, available && expanded && screenshotEnabled);
        visible(menu, available && expanded);
        visible(fastForward, available && profile.fastForwardOnScreen
                && (expanded || fastForward != null && fastForward.isSelected()));
    }

    private boolean previewCoversHandle() {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) handle.getLayoutParams();
        android.graphics.Rect bounds = new android.graphics.Rect(params.leftMargin, params.topMargin,
                params.leftMargin + params.width, params.topMargin + params.height);
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (child instanceof SettingsPreviewBar && child.getVisibility() == View.VISIBLE) {
                android.graphics.Rect preview = new android.graphics.Rect(); child.getHitRect(preview);
                if (android.graphics.Rect.intersects(bounds, preview)) return true;
            }
        }
        return false;
    }

    private void positionHandle() {
        if (root.getWidth() == 0 || root.getHeight() == 0) return;
        android.graphics.RectF safe = new android.graphics.RectF(root.getPaddingLeft(), root.getPaddingTop(),
                root.getWidth() - root.getPaddingRight(), root.getHeight() - root.getPaddingBottom());
        androidx.core.view.WindowInsetsCompat insets = androidx.core.view.ViewCompat.getRootWindowInsets(root);
        if (insets != null) {
            androidx.core.graphics.Insets edges = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()
                    | androidx.core.view.WindowInsetsCompat.Type.displayCutout()
                    | androidx.core.view.WindowInsetsCompat.Type.mandatorySystemGestures());
            int[] origin = new int[2]; root.getLocationInWindow(origin);
            safe.left = Math.max(safe.left, edges.left - origin[0]);
            safe.top = Math.max(safe.top, edges.top - origin[1]);
            safe.right = Math.min(safe.right, root.getRootView().getWidth() - edges.right - origin[0]);
            safe.bottom = Math.min(safe.bottom, root.getRootView().getHeight() - edges.bottom - origin[1]);
        }
        safe.right = Math.max(safe.left, safe.right); safe.bottom = Math.max(safe.top, safe.bottom);
        android.graphics.RectF rect = GameHandlePlacement.bounds(profile, root.getWidth() > root.getHeight(), safe, dp(48));
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) handle.getLayoutParams();
        int left = Math.round(rect.left), top = Math.round(rect.top);
        int width = Math.round(rect.width()), height = Math.round(rect.height());
        if (params.gravity != (Gravity.TOP | Gravity.LEFT) || params.leftMargin != left
                || params.topMargin != top || params.width != width || params.height != height) {
            params.gravity = Gravity.TOP | Gravity.LEFT;
            params.leftMargin = left; params.topMargin = top; params.width = width; params.height = height;
            handle.setLayoutParams(params);
        }
    }

    private static void visible(View view, boolean visible) {
        if (view != null) view.setVisibility(visible ? View.VISIBLE : View.GONE);
    }
}
