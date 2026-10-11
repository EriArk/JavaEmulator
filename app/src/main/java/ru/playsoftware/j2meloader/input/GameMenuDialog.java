package ru.playsoftware.j2meloader.input;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import ru.playsoftware.j2meloader.R;

/** A single controller- and touch-accessible entry point for in-game options. */
public final class GameMenuDialog extends Dialog {
    private LinearLayout content;
    private TextView heading;
    private TextView subtitle;
    private ImageButton navigation;
    private ScrollView scroll;
    private View defaultAction;
    private View pendingDefaultFocus;
    private Runnable backAction;
    private final String game;
    public GameMenuDialog(Context context, String game) {
        super(context); this.game = game;
        requestWindowFeature(Window.FEATURE_NO_TITLE);
    }
    @Override public void show() {
        super.show();
        if (ru.playsoftware.j2meloader.BuildConfig.HANDHELD_MODE) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            androidx.core.view.WindowInsetsControllerCompat bars =
                    androidx.core.view.WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
            bars.setSystemBarsBehavior(androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            bars.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars());
        }
    }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout panel = new LinearLayout(getContext()) {
            @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                focusPendingAction();
            }
            @Override public void onWindowFocusChanged(boolean hasFocus) {
                super.onWindowFocusChanged(hasFocus);
                if (hasFocus) focusPendingAction();
            }
            @Override protected void onConfigurationChanged(android.content.res.Configuration config) {
                super.onConfigurationChanged(config);
                updateWindowLayout();
            }
            @Override protected void onMeasure(int widthSpec,int heightSpec) {
                int max = getContext().getResources().getDisplayMetrics().heightPixels-dp(24);
                if (View.MeasureSpec.getMode(heightSpec) != View.MeasureSpec.UNSPECIFIED) {
                    max = Math.min(max, View.MeasureSpec.getSize(heightSpec));
                }
                super.onMeasure(widthSpec,View.MeasureSpec.makeMeasureSpec(max,View.MeasureSpec.AT_MOST));
            }
        };
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(0xff131c21);
        LinearLayout header = new LinearLayout(getContext());
        header.setPadding(dp(16),dp(6),dp(8),dp(6));
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = new LinearLayout(getContext());
        titles.setOrientation(LinearLayout.VERTICAL);
        heading = new TextView(getContext());
        heading.setTextColor(0xffffc15a); heading.setTextSize(18);
        heading.setMaxLines(2); heading.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titles.addView(heading);
        subtitle = new TextView(getContext());
        subtitle.setText(game); subtitle.setTextColor(0xffa9b9bd); subtitle.setTextSize(13);
        subtitle.setSingleLine(true); subtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titles.addView(subtitle);
        header.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        navigation = new ImageButton(getContext());
        navigation.setBackgroundResource(R.drawable.bg_quick_setting_button);
        navigation.setBackgroundTintList(null);
        navigation.setImageTintList(android.content.res.ColorStateList.valueOf(0xffffc15a));
        navigation.setPadding(dp(12),dp(12),dp(12),dp(12));
        navigation.setOnClickListener(v -> navigateBack());
        header.addView(navigation,new LinearLayout.LayoutParams(dp(48),dp(48)));
        panel.addView(header,new LinearLayout.LayoutParams(-1,-2));
        scroll = new ScrollView(getContext());
        content = new LinearLayout(getContext());
        content.setPadding(dp(12),0,dp(12),dp(12));
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        panel.addView(scroll,new LinearLayout.LayoutParams(-1,-2,1));
        setContentView(panel);
        Window window = getWindow();
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setDimAmount(.75f);
        updateWindowLayout();
        setOnKeyListener((d,key,event) -> {
            if (key == KeyEvent.KEYCODE_BUTTON_B || key == KeyEvent.KEYCODE_BACK) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    navigateBack();
                }
                return true;
            }
            if (key == KeyEvent.KEYCODE_BUTTON_A) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    View focus = getCurrentFocus();
                    if (pendingDefaultFocus != null || focus == null || !focus.isClickable()) focus = defaultAction;
                    if (focus != null) focus.performClick();
                }
                return true;
            }
            return false;
        });
    }
    private void updateWindowLayout() {
        boolean bottom = !ru.playsoftware.j2meloader.BuildConfig.HANDHELD_MODE
                && getContext().getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_PORTRAIT;
        Window window = getWindow();
        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.gravity = bottom ? Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL : Gravity.CENTER;
        attributes.y = bottom ? dp(12) : 0;
        attributes.width = Math.min(dp(440), getContext().getResources().getDisplayMetrics().widthPixels-dp(24));
        attributes.height = WindowManager.LayoutParams.WRAP_CONTENT;
        window.setAttributes(attributes);
    }
    public void page(String title) {
        content.removeAllViews();
        defaultAction = null;
        pendingDefaultFocus = null;
        setBackAction(null);
        heading.setText(title);
        subtitle.setVisibility(game == null || game.isEmpty() || game.equals(title) ? View.GONE : View.VISIBLE);
        scroll.scrollTo(0,0);
    }
    public void setBackAction(Runnable action) {
        backAction = action;
        navigation.setImageResource(action == null ? android.R.drawable.ic_menu_close_clear_cancel
                : androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        navigation.setContentDescription(action == null ? "Close" : "Back");
        navigation.setTooltipText(navigation.getContentDescription());
    }
    private void navigateBack() { if (backAction != null) backAction.run(); else dismiss(); }
    public void message(String value) {
        TextView text = new TextView(getContext());
        text.setText(value); text.setTextSize(14); text.setTextColor(0xffa9b9bd);
        text.setPadding(dp(4), dp(6), dp(4), dp(10));
        content.addView(text, new LinearLayout.LayoutParams(-1, -2));
    }
    public Button action(String label, int icon, Runnable run) {
        Button button = new Button(getContext());
        button.setText(label); button.setTextSize(15); button.setAllCaps(false);
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setTextColor(0xffedf1ee);
        button.setBackgroundResource(R.drawable.bg_quick_setting_button);
        button.setBackgroundTintList(null);
        button.setPadding(dp(14),dp(8),dp(14),dp(8));
        button.setMinHeight(dp(48));
        button.setMinimumHeight(dp(48));
        if (icon != 0) {
            android.graphics.drawable.Drawable drawable = getContext().getDrawable(icon).mutate();
            drawable.setTint(0xffffc15a); drawable.setBounds(0,0,dp(22),dp(22));
            button.setCompoundDrawables(drawable,null,null,null); button.setCompoundDrawablePadding(dp(12));
        }
        button.setOnClickListener(v -> run.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
        lp.topMargin=dp(4); content.addView(button,lp);
        focusDefault(button);
        return button;
    }
    private void focusDefault(View view) {
        if (defaultAction != null) return;
        defaultAction = view;
        pendingDefaultFocus = view;
        view.requestFocusFromTouch();
        view.post(this::focusPendingAction);
    }
    private void focusPendingAction() {
        // Wait for both layout and window focus, then leave subsequent navigation alone.
        View view = pendingDefaultFocus;
        if (view != null && view.isLaidOut() && view.hasWindowFocus()) {
            pendingDefaultFocus = null;
            view.requestFocusFromTouch();
        }
    }
    public void choice(String label, String[] options, int selected, java.util.function.IntConsumer change) {
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = new TextView(getContext());
        name.setText(label); name.setTextSize(15); name.setTextColor(0xffedf1ee);
        name.setMinHeight(dp(48));
        row.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        name.setGravity(Gravity.CENTER_VERTICAL);
        android.widget.Spinner spinner = new android.widget.Spinner(getContext());
        spinner.setForeground(getContext().getDrawable(R.drawable.bg_settings_focus));
        spinner.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xffffc15a));
        spinner.setContentDescription(label);
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<String>(
                getContext(),android.R.layout.simple_spinner_item,options) {
            private View label(int position) {
                TextView text = new TextView(getContext());
                text.setText(getItem(position)); text.setTextSize(15); text.setTextColor(0xffffc15a);
                text.setBackgroundColor(0xff26343c); text.setGravity(Gravity.CENTER_VERTICAL);
                text.setPadding(dp(12),dp(6),dp(12),dp(6)); text.setMinHeight(dp(48));
                return text;
            }
            @Override public View getView(int p,View v,android.view.ViewGroup parent) { return label(p); }
            @Override public View getDropDownView(int p,View v,android.view.ViewGroup parent) { return label(p); }
        };
        spinner.setAdapter(adapter); spinner.setSelection(selected);
        int[] current = {selected};
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(android.widget.AdapterView<?> parent) { }
            public void onItemSelected(android.widget.AdapterView<?> parent,View v,int index,long id) {
                if (current[0] != index) { current[0]=index; change.accept(index); }
            }
        });
        row.addView(spinner,new LinearLayout.LayoutParams(0,-2,1.3f));
        row.setPadding(0,dp(3),0,dp(3));
        content.addView(row,new LinearLayout.LayoutParams(-1,-2));
        focusDefault(spinner);
    }
    public void toggle(String label, boolean checked, java.util.function.Consumer<Boolean> change) {
        android.widget.Switch toggle = new android.widget.Switch(getContext());
        toggle.setForeground(getContext().getDrawable(R.drawable.bg_settings_focus));
        toggle.setText(label); toggle.setTextSize(15); toggle.setTextColor(0xffedf1ee);
        toggle.setChecked(checked);
        toggle.setThumbTintList(new android.content.res.ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{0xffffc15a, 0xffa9b9bd}));
        toggle.setTrackTintList(android.content.res.ColorStateList.valueOf(0xff526873));
        toggle.setOnCheckedChangeListener((button, value) -> change.accept(value));
        toggle.setMinHeight(dp(52));
        content.addView(toggle, new LinearLayout.LayoutParams(-1, -2));
        focusDefault(toggle);
    }
    public void opacity(int value, java.util.function.IntConsumer change) {
        TextView label = new TextView(getContext());
        label.setText("Opacity: " + value + "%"); label.setTextSize(15); label.setTextColor(0xffedf1ee);
        label.setPadding(0,dp(12),0,0); content.addView(label);
        android.widget.SeekBar slider = new android.widget.SeekBar(getContext());
        slider.setForeground(getContext().getDrawable(R.drawable.bg_settings_focus));
        slider.setContentDescription("Opacity");
        slider.setMax(20); slider.setProgress(value-80);
        slider.setProgressTintList(android.content.res.ColorStateList.valueOf(0xffffc15a));
        slider.setThumbTintList(android.content.res.ColorStateList.valueOf(0xffffc15a));
        content.addView(slider,new LinearLayout.LayoutParams(-1,dp(48)));
        slider.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            public void onStartTrackingTouch(android.widget.SeekBar view) { }
            public void onStopTrackingTouch(android.widget.SeekBar view) { change.accept(view.getProgress()+80); }
            public void onProgressChanged(android.widget.SeekBar view,int progress,boolean fromUser) {
                label.setText("Opacity: " + (progress+80) + "%");
                if (fromUser && !view.isPressed()) change.accept(progress+80);
            }
        });
    }
    private int dp(int value) { return Math.round(value*getContext().getResources().getDisplayMetrics().density); }
}
