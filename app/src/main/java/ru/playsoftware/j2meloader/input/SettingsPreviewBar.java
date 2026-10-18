/* Copyright 2026. Licensed under the Apache License, Version 2.0. */
package ru.playsoftware.j2meloader.input;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import ru.playsoftware.j2meloader.R;

public final class SettingsPreviewBar extends LinearLayout {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final TextView label;
    private final Runnable undo;
    private int seconds;
    private boolean active;
    private boolean suspended;
    private final Runnable tick = new Runnable() {
        public void run() {
            if (seconds <= 0) { undo.run(); return; }
            label.setText(seconds + "s");
            label.setContentDescription("Settings preview: " + seconds-- + " seconds remaining");
            handler.postDelayed(this, 1000);
        }
    };
    public SettingsPreviewBar(Context context, Runnable keep, Runnable undo) {
        super(context); this.undo = undo;
        setOrientation(HORIZONTAL); setGravity(Gravity.CENTER_VERTICAL);
        Configuration config = new Configuration(getResources().getConfiguration());
        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_YES;
        Context palette = ru.playsoftware.j2meloader.ui.AppPalette.preview(context,
                ru.playsoftware.j2meloader.ui.AppPalette.selected(context), Configuration.UI_MODE_NIGHT_YES);
        setBackground(palette.getDrawable(R.drawable.bg_quick_map_panel));
        setPadding(dp(4), dp(2), dp(4), dp(2));
        setClickable(true); setFocusable(false);
        label = new TextView(context); label.setTextColor(ru.playsoftware.j2meloader.ui.AppPalette.color(palette, R.color.accent));
        label.setTextSize(14); label.setSingleLine(true); label.setGravity(Gravity.CENTER);
        label.setAutoSizeTextTypeUniformWithConfiguration(10, 14, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        addView(label, new LayoutParams(dp(64), dp(48)));
        button(palette, "Keep", android.R.drawable.ic_menu_save, keep);
        button(palette, "Undo", android.R.drawable.ic_menu_revert, undo);
        setVisibility(GONE);
    }
    /** Overlay only: leave the game bounds and the shortcut row untouched in every engine. */
    public void attachTo(FrameLayout parent) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        lp.setMargins(dp(8), dp(54), dp(8), 0);
        parent.addView(this, lp);
    }
    private void button(Context palette, String name, int icon, Runnable action) {
        ImageButton button = new ImageButton(getContext()); button.setImageResource(icon);
        button.setFocusable(true);
        button.setContentDescription(name); button.setTooltipText(name + " changes");
        button.setImageTintList(ColorStateList.valueOf(ru.playsoftware.j2meloader.ui.AppPalette.color(palette, R.color.text_primary)));
        button.setBackground(palette.getDrawable(R.drawable.bg_touch_shortcut));
        button.setPadding(dp(12), dp(12), dp(12), dp(12));
        button.setBackgroundTintList(null); button.setOnClickListener(v -> action.run());
        LayoutParams lp = new LayoutParams(dp(48), dp(48)); lp.setMarginStart(dp(4)); addView(button, lp);
    }
    public void start() { stop(); active = true; seconds = 20; resume(); }
    public void suspend() { suspended = true; handler.removeCallbacks(tick); setVisibility(GONE); }
    public void resume() {
        if (!active || (!suspended && getVisibility() == VISIBLE)) return;
        suspended = false; setVisibility(VISIBLE); tick.run();
    }
    public void stop() { active = false; suspended = false; handler.removeCallbacks(tick); setVisibility(GONE); }
    @Override protected void onDetachedFromWindow() { handler.removeCallbacks(tick); super.onDetachedFromWindow(); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
