/* Copyright 2026. Licensed under the Apache License, Version 2.0. */
package ru.playsoftware.j2meloader.input;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Button;
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
            label.setText("Preview " + seconds-- + "s");
            handler.postDelayed(this, 1000);
        }
    };
    public SettingsPreviewBar(Context context, Runnable keep, Runnable undo) {
        super(context); this.undo = undo;
        setOrientation(HORIZONTAL); setGravity(Gravity.CENTER_VERTICAL);
        setBackgroundColor(0xff131c21); setPadding(dp(8), dp(4), dp(8), dp(4));
        label = new TextView(context); label.setTextColor(0xffffc15a); label.setTextSize(14);
        addView(label, new LayoutParams(0, dp(48), 1)); label.setGravity(Gravity.CENTER_VERTICAL);
        button("Keep", keep); button("Undo", undo);
        setVisibility(GONE);
    }
    private void button(String name, Runnable action) {
        Button button = new Button(getContext()); button.setText(name); button.setAllCaps(false);
        button.setTextColor(0xffedf1ee); button.setBackgroundResource(R.drawable.bg_quick_setting_button);
        button.setBackgroundTintList(null); button.setOnClickListener(v -> action.run());
        LayoutParams lp = new LayoutParams(dp(80), dp(48)); lp.leftMargin = dp(4); addView(button, lp);
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
