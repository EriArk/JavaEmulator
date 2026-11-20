/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.applist;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import androidx.appcompat.widget.SwitchCompat;
import androidx.preference.PreferenceManager;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.settings.CompactSettingsActivity;
import ru.playsoftware.j2meloader.ui.AppPalette;

public final class PlatformLauncherActivity extends CompactSettingsActivity {
    static final String STARTUP = "pref_platform_launcher";
    private static final int[] ART = {0, R.drawable.sticker_java, R.drawable.sticker_mophun,
            R.drawable.sticker_symbian, R.drawable.sticker_ngage, R.drawable.sticker_wipi};
    private static final String[] DEVICES = {"", "Nokia 6230", "Sony Ericsson T610", "Nokia 6600",
            "Nokia N-Gage", "Anycall Yuna’s Haptic"};
    private static final int[] COLORS = {0, 0xff244f77, 0xff32665c, 0xff544869, 0xff52643d, 0xff805145};

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        screenTitle(getString(R.string.platform_launcher));
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        screenContent.addView(body);
        ScrollView scroll = new ScrollView(this);
        body.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        GridLayout grid = new GridLayout(this);
        grid.setPadding(dp(8), dp(4), dp(8), dp(4));
        scroll.addView(grid);
        for (int id = 1; id < ART.length; id++) {
            if (LibraryNavigation.available(id)) grid.addView(card(id));
        }
        scroll.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> {
            if (r - l == or - ol && b - t == ob - ot) return;
            int width = r - l - grid.getPaddingLeft() - grid.getPaddingRight();
            int columns = Math.min(grid.getChildCount(), Math.max(1, width / dp(146)));
            if (columns == 4 && grid.getChildCount() == 5) columns = 3;
            int rows = (grid.getChildCount() + columns - 1) / columns;
            int height = Math.max(dp(136), Math.min(dp(214), (b - t - dp(8)) / rows));
            // Clear previous column assignments before reducing the number of columns.
            grid.setColumnCount(GridLayout.UNDEFINED);
            for (int i = 0; i < grid.getChildCount(); i++) {
                GridLayout.LayoutParams lp = new GridLayout.LayoutParams(GridLayout.spec(i / columns), GridLayout.spec(i % columns));
                lp.setMargins(dp(4), dp(4), dp(4), dp(4));
                lp.width = width / columns - dp(8); lp.height = height - dp(8);
                grid.getChildAt(i).setLayoutParams(lp);
            }
            grid.setColumnCount(columns);
        });
        SwitchCompat startup = new SwitchCompat(this);
        startup.setText(R.string.platform_show_startup);
        startup.setTextColor(AppPalette.color(this, R.color.text_primary));
        startup.setPadding(dp(16), 0, dp(16), 0);
        startup.setMinHeight(dp(48));
        startup.setChecked(PreferenceManager.getDefaultSharedPreferences(this).getBoolean(STARTUP, true));
        startup.setOnCheckedChangeListener((v,on) -> PreferenceManager.getDefaultSharedPreferences(this)
                .edit().putBoolean(STARTUP, on).apply());
        body.addView(startup);
    }

    private View card(int id) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(9), dp(6), dp(9), dp(8));
        card.setGravity(Gravity.CENTER);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, face(id, true));
        states.addState(new int[]{android.R.attr.state_pressed}, face(id, true));
        states.addState(new int[]{}, face(id, false));
        card.setBackground(states);
        card.setFocusable(true);
        card.setContentDescription(LibraryQuery.PLATFORMS[id] + ", " + DEVICES[id]);
        card.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        card.setOnClickListener(v -> {
            setResult(RESULT_OK, new Intent().putExtra("platform", id));
            finish();
        });
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setImageResource(ART[id]);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        card.addView(image, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView title = new TextView(this);
        title.setText(LibraryQuery.PLATFORMS[id]);
        title.setTextSize(17); title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER); title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        card.addView(title, new LinearLayout.LayoutParams(-1,-2));
        TextView device = new TextView(this);
        device.setText(DEVICES[id]); device.setTextSize(12); device.setTextColor(0xffe1e6e8);
        device.setGravity(Gravity.CENTER);
        device.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        card.addView(device, new LinearLayout.LayoutParams(-1, -2));
        return card;
    }

    private GradientDrawable face(int id, boolean focused) {
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{COLORS[id], 0xff18252c});
        bg.setCornerRadius(dp(17));
        bg.setStroke(dp(focused ? 3 : 1), focused ? 0xffffc650 : 0xff55636b);
        return bg;
    }
}
