package ru.playsoftware.j2meloader.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.preference.PreferenceManager;
import ru.playsoftware.j2meloader.R;

public final class PaletteDialog extends Dialog {
    private final Activity activity;
    public PaletteDialog(Activity activity) {
        super(activity); this.activity = activity; AppPalette.apply(getContext());
    }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle("Visual style");
        LinearLayout rows = new LinearLayout(getContext()); rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(dp(12), dp(8), dp(12), dp(12));
        int night = getContext().getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        for (int i = 0; i < AppPalette.VALUES.length; i++) {
            final String value = AppPalette.VALUES[i];
            Context palette = AppPalette.preview(getContext(), value, night);
            LinearLayout row = new LinearLayout(palette); row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(12), dp(12), dp(12));
            GradientDrawable background = new GradientDrawable();
            background.setColor(AppPalette.color(palette, R.color.phone_panel));
            background.setCornerRadius(dp("retro".equals(value) ? 7 : 3));
            background.setStroke(dp("contrast".equals(value) ? 2 : 1), AppPalette.color(palette, R.color.phone_line));
            row.setBackground(background); row.setFocusable(true); row.setClickable(true);
            row.setForeground(palette.getDrawable(R.drawable.bg_settings_focus));
            row.setContentDescription(AppPalette.NAMES[i]);
            TextView title = new TextView(palette); title.setText(AppPalette.NAMES[i]);
            title.setTextColor(AppPalette.color(palette, R.color.text_primary)); title.setTextSize(17);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            LinearLayout description = new LinearLayout(palette);
            description.setOrientation(LinearLayout.VERTICAL);
            description.addView(title, new LinearLayout.LayoutParams(-1, -2));
            LinearLayout swatches = new LinearLayout(palette);
            for (int color : new int[]{R.color.accent, R.color.phone_cyan, R.color.text_primary}) {
                View swatch = new View(palette); GradientDrawable shape = new GradientDrawable();
                shape.setColor(AppPalette.color(palette, color)); shape.setCornerRadius(dp(3)); swatch.setBackground(shape);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(28), dp(10)); lp.setMarginEnd(dp(4));
                swatches.addView(swatch, lp);
            }
            LinearLayout.LayoutParams swatchRow = new LinearLayout.LayoutParams(-1, -2); swatchRow.topMargin=dp(8);
            description.addView(swatches, swatchRow);
            row.addView(description, new LinearLayout.LayoutParams(0, -2, 1));
            RadioButton selected = new RadioButton(palette); selected.setClickable(false); selected.setFocusable(false);
            selected.setChecked(value.equals(AppPalette.selected(activity)));
            selected.setButtonTintList(android.content.res.ColorStateList.valueOf(AppPalette.color(palette, R.color.accent)));
            row.addView(selected, new LinearLayout.LayoutParams(dp(44), dp(48)));
            row.setOnClickListener(v -> {
                PreferenceManager.getDefaultSharedPreferences(activity).edit().putString(AppPalette.PREFERENCE, value).apply();
                dismiss(); activity.recreate();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(8); rows.addView(row, lp);
        }
        ScrollView scroll = new ScrollView(getContext()); scroll.addView(rows); setContentView(scroll);
    }
    @Override public void show() {
        super.show(); getWindow().setLayout(Math.min(dp(440), getContext().getResources().getDisplayMetrics().widthPixels - dp(24)), -2);
    }
    @Override public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        if (event.getKeyCode()==android.view.KeyEvent.KEYCODE_BUTTON_B) {
            if (event.getAction()==android.view.KeyEvent.ACTION_UP) dismiss(); return true;
        }
        if (event.getKeyCode()==android.view.KeyEvent.KEYCODE_BUTTON_A && getCurrentFocus()!=null && getCurrentFocus().isClickable()) {
            if (event.getAction()==android.view.KeyEvent.ACTION_UP) getCurrentFocus().performClick(); return true;
        }
        return super.dispatchKeyEvent(event);
    }
    private int dp(int value) { return Math.round(value * getContext().getResources().getDisplayMetrics().density); }
}
