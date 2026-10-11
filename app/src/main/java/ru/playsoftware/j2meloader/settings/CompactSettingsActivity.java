package ru.playsoftware.j2meloader.settings;

import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import ru.playsoftware.j2meloader.BuildConfig;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.base.BaseActivity;

/** Shared compact chrome for settings and library transfer. */
public abstract class CompactSettingsActivity extends BaseActivity {
    protected FrameLayout screenContent;
    protected LinearLayout screenHeader;
    private TextView screenTitle;
    private boolean initialFocus = true;

    @Override protected void onCreate(Bundle state) {
        setTheme(R.style.AppTheme_NoActionBar);
        super.onCreate(state);
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        if (BuildConfig.HANDHELD_MODE) setRequestedOrientation(
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getColor(R.color.background));
        screenHeader = new LinearLayout(this);
        screenHeader.setGravity(Gravity.CENTER_VERTICAL);
        screenHeader.setBackgroundColor(getColor(R.color.phone_panel));
        ImageButton back = headerButton("Back", androidx.appcompat.R.drawable.abc_ic_ab_back_material, this::onBackPressed);
        back.setId(R.id.settings_back);
        screenTitle = new TextView(this);
        screenTitle.setTextSize(18); screenTitle.setTextColor(getColor(R.color.text_primary));
        screenTitle.setSingleLine(true); screenTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        screenHeader.addView(screenTitle, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(screenHeader, new LinearLayout.LayoutParams(-1, dp(48)));
        screenContent = new FrameLayout(this); screenContent.setId(R.id.settings_content);
        root.addView(screenContent, new LinearLayout.LayoutParams(-1, 0, 1));
        root.getViewTreeObserver().addOnGlobalLayoutListener(this::focusInitialAction);
        setContentView(root);
    }

    protected void screenTitle(CharSequence title) { screenTitle.setText(title); setTitle(title); }
    protected void focusContentWhenReady() { initialFocus = true; screenContent.requestLayout(); }
    protected ImageButton headerButton(String label, int icon, Runnable action) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(icon); button.setColorFilter(getColor(R.color.accent));
        button.setBackgroundResource(R.drawable.bg_library_tab);
        button.setPadding(dp(12), dp(12), dp(12), dp(12));
        button.setContentDescription(label); button.setTooltipText(label);
        button.setOnClickListener(v -> action.run());
        screenHeader.addView(button, new LinearLayout.LayoutParams(dp(48), dp(48)));
        return button;
    }
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (focused && BuildConfig.HANDHELD_MODE) {
            WindowInsetsControllerCompat bars = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
            bars.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            bars.hide(WindowInsetsCompat.Type.systemBars());
        }
        if (focused) focusInitialAction();
    }
    private void focusInitialAction() {
        if (!initialFocus || !BuildConfig.HANDHELD_MODE || screenContent == null || !screenContent.hasWindowFocus()) return;
        for (View view : screenContent.getFocusables(View.FOCUS_FORWARD)) {
            if (view.isShown() && view.isEnabled() && view.isClickable() && view.isLaidOut()) {
                view.requestFocusFromTouch(); initialFocus = false; break;
            }
        }
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_B) {
            if (event.getAction() == KeyEvent.ACTION_UP) onBackPressed();
            return true;
        }
        if (event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_A && getCurrentFocus() != null
                && getCurrentFocus().isClickable()) {
            if (event.getAction() == KeyEvent.ACTION_UP) getCurrentFocus().performClick();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
    protected int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
