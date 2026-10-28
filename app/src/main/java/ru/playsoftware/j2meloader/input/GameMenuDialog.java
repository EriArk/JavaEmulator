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
    private LinearLayout decisions;
    private TextView heading;
    private TextView subtitle;
    private ImageButton navigation;
    private ScrollView scroll;
    private View defaultAction;
    private View pendingDefaultFocus;
    private Runnable backAction;
    private final String game;
    private Dialog editor;
    private boolean edgePanel;
    private android.widget.ImageView artwork;
    private String pageTitle;
    private final java.util.Map<String, String> pageFocus = new java.util.HashMap<>();
    private final java.util.Map<String, Integer> pageScroll = new java.util.HashMap<>();
    private String restoreAction;
    private int restoreScroll;
    private Runnable pageCleanup;
    private boolean picturePreview;
    private boolean confirmation;
    private int layoutOrientation = -1;
    private int layoutWidth = -1;
    private int layoutHeight = -1;
    private float layoutDensity;
    private final androidx.lifecycle.Lifecycle hostLifecycle;
    private final androidx.lifecycle.LifecycleEventObserver hostObserver = (source, event) -> {
        if (event == androidx.lifecycle.Lifecycle.Event.ON_DESTROY) dismiss();
    };
    public void setPageCleanup(Runnable cleanup) {
        pageCleanup = cleanup; picturePreview = cleanup != null;
        if (isShowing()) { updateWindowLayout(); getWindow().getDecorView().requestLayout(); }
    }
    public void cancelPreviewPage() { if (pageCleanup != null) dismiss(); }
    public void finishPagePreview() {
        Runnable cleanup = pageCleanup; pageCleanup = null;
        if (cleanup != null) cleanup.run();
        picturePreview = false;
        if (isShowing()) updateWindowLayout();
    }
    /** Opt in screen by screen; does not change emulator pause/input ownership. */
    public void useEdgePanel() { edgePanel = true; if (isShowing()) updateWindowLayout(); }
    public void editTouchLayout(ru.playsoftware.j2meloader.config.ProfileModel draft, Runnable done) {
        editTouchLayout(draft, done, false);
    }
    public void editGameHandle(ru.playsoftware.j2meloader.config.ProfileModel draft, Runnable done) {
        editTouchLayout(draft, done, true);
    }
    private void editTouchLayout(ru.playsoftware.j2meloader.config.ProfileModel draft, Runnable done, boolean handle) {
        if (editor != null) editor.dismiss();
        editor = new TouchLayoutEditor(getContext(), draft, done, handle);
        editor.setOnDismissListener(d -> editor = null);
        editor.show();
    }
    public Bundle saveTouchEditor() { return editor == null ? null : editor.onSaveInstanceState(); }
    public void restoreTouchEditor(Bundle state, ru.playsoftware.j2meloader.config.ProfileModel draft, Runnable done) {
        if (state == null) return;
        editTouchLayout(draft, done, state.getBoolean("handleMode")); editor.onRestoreInstanceState(state);
    }
    @Override public void dismiss() {
        finishPagePreview();
        if (editor != null) { editor.dismiss(); editor = null; }
        super.dismiss();
    }
    public GameMenuDialog(Context context, String game) {
        super(context); this.game = game;
        Context host = context;
        while (host instanceof android.content.ContextWrapper
                && !(host instanceof androidx.lifecycle.LifecycleOwner)) {
            host = ((android.content.ContextWrapper) host).getBaseContext();
        }
        hostLifecycle = host instanceof androidx.lifecycle.LifecycleOwner
                ? ((androidx.lifecycle.LifecycleOwner) host).getLifecycle() : null;
        ru.playsoftware.j2meloader.ui.AppPalette.apply(getContext());
        requestWindowFeature(Window.FEATURE_NO_TITLE);
    }
    @Override protected void onStart() {
        super.onStart();
        if (hostLifecycle != null) hostLifecycle.addObserver(hostObserver);
    }
    @Override protected void onStop() {
        if (hostLifecycle != null) hostLifecycle.removeObserver(hostObserver);
        super.onStop();
    }
    public String text(@androidx.annotation.StringRes int id, Object... args) {
        return getContext().getString(id, args);
    }
    public String[] labels(@androidx.annotation.ArrayRes int id) {
        return getContext().getResources().getStringArray(id);
    }
    @Override public void show() {
        boolean fullscreen = isFullscreenHost();
        // Set this before attaching the dialog: a transient system-bar resize
        // can trigger the game's configuration handler and close its menu.
        if (fullscreen) getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        super.show();
        if (fullscreen) {
            androidx.core.view.WindowInsetsControllerCompat bars =
                    androidx.core.view.WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
            bars.setSystemBarsBehavior(androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            bars.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars());
        }
    }
    private boolean isFullscreenHost() {
        if (DeviceControls.connected(getContext())) return true;
        Context context = getContext();
        while (context instanceof android.content.ContextWrapper) {
            if (context instanceof android.app.Activity) {
                Window host = ((android.app.Activity) context).getWindow();
                return (host.getAttributes().flags & WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0
                        || (host.getDecorView().getSystemUiVisibility() & View.SYSTEM_UI_FLAG_FULLSCREEN) != 0;
            }
            context = ((android.content.ContextWrapper) context).getBaseContext();
        }
        return false;
    }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout panel = new LinearLayout(getContext()) {
            @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                // Configuration callbacks can precede updated dialog resources.
                // Reconcile once at traversal, without requesting another unchanged layout.
                android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
                if (layoutOrientation != getResources().getConfiguration().orientation
                        || layoutWidth != metrics.widthPixels || layoutHeight != metrics.heightPixels
                        || layoutDensity != metrics.density) updateWindowLayout();
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
                android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
                if (picturePreview && MenuPlacement.resolve(metrics.widthPixels / metrics.density,
                        metrics.heightPixels / metrics.density, DeviceControls.connected(getContext()),
                        confirmation, true) == MenuPlacement.Position.BOTTOM)
                    max = Math.min(max, Math.round(getResources().getDisplayMetrics().heightPixels * .62f));
                if (View.MeasureSpec.getMode(heightSpec) != View.MeasureSpec.UNSPECIFIED) {
                    max = Math.min(max, View.MeasureSpec.getSize(heightSpec));
                }
                super.onMeasure(widthSpec,View.MeasureSpec.makeMeasureSpec(max,View.MeasureSpec.AT_MOST));
            }
        };
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(ru.playsoftware.j2meloader.ui.AppPalette.color(getContext(), ru.playsoftware.j2meloader.R.color.menu_surface));
        if (edgePanel) {
            panel.setBackground(new ru.playsoftware.j2meloader.ui.ConsoleSurface(getContext(),
                    ru.playsoftware.j2meloader.ui.ConsoleSurface.Kind.PANEL));
            panel.setPadding(dp(3), dp(4), dp(3), dp(4));
        }
        LinearLayout header = new LinearLayout(getContext());
        header.setPadding(dp(16),dp(6),dp(8),dp(6));
        header.setGravity(Gravity.CENTER_VERTICAL);
        artwork = new android.widget.ImageView(getContext());
        artwork.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        artwork.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        artwork.setVisibility(View.GONE);
        LinearLayout.LayoutParams artParams = new LinearLayout.LayoutParams(dp(44), dp(44));
        artParams.rightMargin = dp(10);
        header.addView(artwork, artParams);
        LinearLayout titles = new LinearLayout(getContext());
        titles.setOrientation(LinearLayout.VERTICAL);
        heading = new TextView(getContext());
        heading.setTextColor(ru.playsoftware.j2meloader.ui.AppPalette.color(getContext(), ru.playsoftware.j2meloader.R.color.accent)); heading.setTextSize(18);
        titles.addView(heading);
        subtitle = new TextView(getContext());
        subtitle.setText(game); subtitle.setTextColor(ru.playsoftware.j2meloader.ui.AppPalette.color(getContext(), ru.playsoftware.j2meloader.R.color.text_secondary)); subtitle.setTextSize(13);
        subtitle.setSingleLine(true); subtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titles.addView(subtitle);
        header.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        navigation = new ImageButton(getContext());
        navigation.setBackgroundResource(R.drawable.bg_quick_setting_button);
        navigation.setBackgroundTintList(null);
        if (edgePanel) {
            navigation.setDefaultFocusHighlightEnabled(false);
            navigation.setBackground(new ru.playsoftware.j2meloader.ui.ConsoleSurface(getContext(),
                    ru.playsoftware.j2meloader.ui.ConsoleSurface.Kind.KEY));
        }
        navigation.setImageTintList(android.content.res.ColorStateList.valueOf(ru.playsoftware.j2meloader.ui.AppPalette.color(getContext(), ru.playsoftware.j2meloader.R.color.accent)));
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
        decisions = new LinearLayout(getContext()); decisions.setPadding(dp(12), dp(4), dp(12), dp(8));
        decisions.setVisibility(View.GONE);
        panel.addView(decisions, new LinearLayout.LayoutParams(-1, -2));
        setContentView(panel);
        Window window = getWindow();
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setDimAmount(edgePanel ? .4f : .75f);
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
    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateWindowLayout();
    }
    private void updateWindowLayout() {
        // Keep pending geometry, but do not resize a detached window during Activity recreation.
        Window current = getWindow();
        if (current == null) return;
        android.util.DisplayMetrics metrics = getContext().getResources().getDisplayMetrics();
        layoutOrientation = getContext().getResources().getConfiguration().orientation;
        layoutWidth = metrics.widthPixels; layoutHeight = metrics.heightPixels; layoutDensity = metrics.density;
        boolean canApply = !isShowing() || current.getDecorView().isAttachedToWindow();
        if (edgePanel || confirmation) {
            Window window = getWindow();
            WindowManager.LayoutParams attributes = window.getAttributes();
            int width = getContext().getResources().getDisplayMetrics().widthPixels;
            int height = getContext().getResources().getDisplayMetrics().heightPixels;
            float density = getContext().getResources().getDisplayMetrics().density;
            MenuPlacement.Position position = MenuPlacement.resolve(width / density, height / density,
                    DeviceControls.connected(getContext()), confirmation, picturePreview);
            attributes.gravity = position == MenuPlacement.Position.SIDE ? Gravity.END | Gravity.CENTER_VERTICAL
                    : position == MenuPlacement.Position.BOTTOM ? Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL : Gravity.CENTER;
            int limit = confirmation ? 380 : position == MenuPlacement.Position.SIDE ? (picturePreview ? 300 : 380)
                    : picturePreview ? 380 : 440;
            attributes.width = position == MenuPlacement.Position.BOTTOM ? Math.min(dp(600), width)
                    : Math.min(dp(limit), width - dp(24));
            attributes.dimAmount = picturePreview ? 0 : .4f;
            attributes.height = WindowManager.LayoutParams.WRAP_CONTENT;
            attributes.y = 0;
            if (canApply) window.setAttributes(attributes);
            return;
        }
        boolean bottom = !ru.playsoftware.j2meloader.BuildConfig.HANDHELD_MODE
                && getContext().getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_PORTRAIT;
        Window window = getWindow();
        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.gravity = bottom ? Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL : Gravity.CENTER;
        attributes.y = bottom ? dp(12) : 0;
        attributes.width = Math.min(dp(440), getContext().getResources().getDisplayMetrics().widthPixels-dp(24));
        attributes.height = WindowManager.LayoutParams.WRAP_CONTENT;
        if (canApply) window.setAttributes(attributes);
    }
    public void page(String title) {
        finishPagePreview();
        confirmation = false;
        if (pageTitle != null) {
            View focus = getCurrentFocus();
            if (focus instanceof Button) pageFocus.put(pageTitle, ((Button) focus).getText().toString());
            pageScroll.put(pageTitle, scroll.getScrollY());
        }
        pageTitle = title;
        restoreAction = pageFocus.get(title);
        restoreScroll = pageScroll.getOrDefault(title, 0);
        content.removeAllViews();
        decisions.removeAllViews(); decisions.setVisibility(View.GONE);
        defaultAction = null;
        pendingDefaultFocus = null;
        setBackAction(null);
        heading.setText(title);
        subtitle.setVisibility(game == null || game.isEmpty() || game.equals(title) ? View.GONE : View.VISIBLE);
        scroll.scrollTo(0,0);
        if (isShowing()) updateWindowLayout();
    }
    public void confirmationPage(String title) {
        page(title);
        confirmation = true;
        if (isShowing()) updateWindowLayout();
    }
    public void artwork(String... paths) {
        artwork.setImageDrawable(null);
        artwork.setVisibility(View.GONE);
        for (String path : paths) {
            if (path == null || path.isEmpty()) continue;
            android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeFile(path, options);
            if (options.outWidth <= 0 || options.outHeight <= 0) continue;
            options.inSampleSize = 1;
            while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > dp(88)) options.inSampleSize *= 2;
            options.inJustDecodeBounds = false;
            android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(path, options);
            if (bitmap == null) continue;
            artwork.setImageBitmap(bitmap);
            artwork.setVisibility(View.VISIBLE);
            return;
        }
    }
    public void setBackAction(Runnable action) {
        backAction = action;
        navigation.setImageResource(action == null ? android.R.drawable.ic_menu_close_clear_cancel
                : androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        navigation.setContentDescription(getContext().getString(action == null ? R.string.menu_close : R.string.menu_back));
        navigation.setTooltipText(navigation.getContentDescription());
    }
    public void presetTiles(String[] names, int[] swatches, int selected, java.util.function.IntConsumer change) {
        if (names.length != swatches.length) throw new IllegalArgumentException("Preset labels/styles mismatch");
        LinearLayout row = null;
        java.util.ArrayList<Button> buttons = new java.util.ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(getContext());
                content.addView(row, new LinearLayout.LayoutParams(-1, -2));
            }
            final int index = i;
            Button tile = new Button(getContext());
            tile.setText(names[i]); tile.setAllCaps(false); tile.setTextSize(14);
            tile.setTextColor(ru.playsoftware.j2meloader.ui.AppPalette.color(getContext(), R.color.text_primary));
            tile.setMinHeight(dp(88)); tile.setMinimumWidth(0); tile.setMinWidth(0);
            tile.setPadding(dp(6), dp(8), dp(6), dp(8));
            tile.setBackground(new ru.playsoftware.j2meloader.ui.ConsoleSurface(getContext(),
                    ru.playsoftware.j2meloader.ui.ConsoleSurface.Kind.KEY));
            tile.setSelected(i == selected); tile.setFocusable(true);
            tile.setDefaultFocusHighlightEnabled(false); tile.setStateListAnimator(null);
            tile.setCompoundDrawablesWithIntrinsicBounds(null,
                    new PresetSwatch(getContext(), swatches[i]), null, null);
            tile.setContentDescription(getContext().getString(R.string.menu_picture_preset, names[i]));
            tile.setOnClickListener(v -> {
                tile.requestFocusFromTouch();
                change.accept(index);
                for (int n = 0; n < buttons.size(); n++) buttons.get(n).setSelected(n == index);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
            lp.setMargins(dp(3), dp(3), dp(3), dp(3)); row.addView(tile, lp);
            buttons.add(tile); focusDefault(tile);
            if (names[i].equals(restoreAction)) {
                defaultAction = tile; pendingDefaultFocus = tile;
                tile.post(this::focusPendingAction);
            }
        }
        if (names.length % 2 != 0) row.addView(new View(getContext()), new LinearLayout.LayoutParams(0, 1, 1));
    }
    public void previewDecisions(Runnable keep, Runnable undo) {
        decisionButtons(new String[]{getContext().getString(R.string.menu_keep), getContext().getString(R.string.menu_undo)}, new Runnable[]{keep, undo});
    }
    public void trialDecision(String label, Runnable action) {
        if (edgePanel) decisionButtons(new String[]{label}, new Runnable[]{action});
        else action(label, android.R.drawable.ic_media_play, action);
    }
    private void decisionButtons(String[] names, Runnable[] actions) {
        decisions.removeAllViews(); decisions.setVisibility(View.VISIBLE);
        for (int i = 0; i < names.length; i++) {
            Button button = new Button(getContext()); button.setText(names[i]);
            button.setAllCaps(false); button.setTextSize(15); button.setMinHeight(dp(48));
            button.setMinWidth(0); button.setMinimumWidth(0); button.setPadding(dp(8), dp(6), dp(8), dp(6));
            button.setTextColor(ru.playsoftware.j2meloader.ui.AppPalette.color(getContext(), R.color.text_primary));
            button.setBackground(new ru.playsoftware.j2meloader.ui.ConsoleSurface(getContext(),
                    ru.playsoftware.j2meloader.ui.ConsoleSurface.Kind.KEY));
            button.setDefaultFocusHighlightEnabled(false); button.setStateListAnimator(null);
            final Runnable action = actions[i]; button.setOnClickListener(v -> action.run());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
            lp.setMargins(dp(3), 0, dp(3), 0); decisions.addView(button, lp);
        }
    }
    private void navigateBack() { if (backAction != null) backAction.run(); else dismiss(); }
    public android.widget.EditText textInput(String label, String value) {
        android.widget.EditText input = new android.widget.EditText(getContext());
        input.setSingleLine(true);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(160)});
        input.setHint(label); input.setContentDescription(label); input.setText(value);
        input.setTextSize(16);
        input.setTextColor(ru.playsoftware.j2meloader.ui.AppPalette.color(getContext(), R.color.text_primary));
        input.setHintTextColor(ru.playsoftware.j2meloader.ui.AppPalette.color(getContext(), R.color.text_secondary));
        input.setBackgroundResource(R.drawable.bg_phone_input);
        input.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(8); input.setMinHeight(dp(48));
        content.addView(input, params);
        return input;
    }
    public void message(String value) {
        TextView text = new TextView(getContext());
        text.setText(value); text.setTextSize(14); text.setTextColor(ru.playsoftware.j2meloader.ui.AppPalette.color(getContext(), ru.playsoftware.j2meloader.R.color.text_secondary));
        text.setPadding(dp(4), dp(6), dp(4), dp(10));
        content.addView(text, new LinearLayout.LayoutParams(-1, -2));
    }
    public Button action(String label, int icon, Runnable run) {
        return action(label, icon, run, false);
    }
    public Button primaryAction(String label, int icon, Runnable run) {
        return action(label, icon, run, true);
    }
    public Button selectionAction(String label, boolean selected, Runnable run) {
        Button button = action(label, R.drawable.ic_check, run);
        button.getCompoundDrawablesRelative()[0].setAlpha(selected ? 255 : 0);
        button.setSelected(selected);
        androidx.core.view.ViewCompat.setAccessibilityDelegate(button, new androidx.core.view.AccessibilityDelegateCompat() {
            @Override public void onInitializeAccessibilityNodeInfo(View host,
                    androidx.core.view.accessibility.AccessibilityNodeInfoCompat info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(android.widget.RadioButton.class.getName());
                info.setCheckable(true); info.setChecked(selected);
            }
        });
        return button;
    }
    private Button action(String label, int icon, Runnable run, boolean primary) {
        Button button = new Button(getContext());
        button.setText(label); button.setTextSize(15); button.setAllCaps(false);
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setTextColor(ru.playsoftware.j2meloader.ui.AppPalette.color(getContext(), ru.playsoftware.j2meloader.R.color.text_primary));
        button.setBackgroundResource(R.drawable.bg_quick_setting_button);
        button.setBackgroundTintList(null);
        if (edgePanel) {
            button.setStateListAnimator(null);
            button.setDefaultFocusHighlightEnabled(false);
            button.setElevation(0);
            button.setBackground(new ru.playsoftware.j2meloader.ui.ConsoleSurface(getContext(),
                    primary ? ru.playsoftware.j2meloader.ui.ConsoleSurface.Kind.KEY
                            : ru.playsoftware.j2meloader.ui.ConsoleSurface.Kind.ROW));
        }
        button.setPadding(dp(14),dp(8),dp(14),dp(8));
        button.setMinHeight(dp(48));
        button.setMinimumHeight(dp(48));
        if (icon != 0) {
            android.graphics.drawable.Drawable drawable = getContext().getDrawable(icon).mutate();
            drawable.setTint(ru.playsoftware.j2meloader.ui.AppPalette.color(getContext(), ru.playsoftware.j2meloader.R.color.accent)); drawable.setBounds(0,0,dp(22),dp(22));
            button.setCompoundDrawablesRelative(drawable,null,null,null); button.setCompoundDrawablePadding(dp(12));
        }
        button.setOnClickListener(v -> run.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
        lp.topMargin=dp(4); content.addView(button,lp);
        focusDefault(button);
        if (label.equals(restoreAction)) {
            defaultAction = button;
            pendingDefaultFocus = button;
            button.post(this::focusPendingAction);
        }
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
            if (restoreScroll > 0) scroll.scrollTo(0, restoreScroll);
            restoreScroll = 0;
        }
    }
    public void choice(String label, String[] options, int selected, java.util.function.IntConsumer change) {
        LinearLayout row = new LinearLayout(getContext()) {
            @Override protected void onMeasure(int widthSpec, int heightSpec) {
                TextView name = (TextView) getChildAt(0);
                android.content.res.Configuration config = getResources().getConfiguration();
                java.text.BreakIterator breaks = java.text.BreakIterator.getLineInstance(config.getLocales().get(0));
                breaks.setText(label);
                float longest = 0;
                for (int start = breaks.first(), end = breaks.next(); end != java.text.BreakIterator.DONE;
                        start = end, end = breaks.next()) {
                    longest = Math.max(longest, name.getPaint().measureText(label.substring(start, end).trim()));
                }
                int width = MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight();
                boolean vertical = config.screenWidthDp < 384 || config.fontScale >= 1.3f
                        || (MeasureSpec.getMode(widthSpec) != MeasureSpec.UNSPECIFIED && longest + dp(8) > width / 2.3f);
                int orientation = vertical ? VERTICAL : HORIZONTAL;
                if (getOrientation() != orientation) {
                    setOrientation(orientation);
                    for (int i = 0; i < getChildCount(); i++) {
                        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) getChildAt(i).getLayoutParams();
                        params.width = vertical ? LayoutParams.MATCH_PARENT : 0;
                        params.weight = vertical ? 0 : i == 0 ? 1 : 1.3f;
                    }
                }
                super.onMeasure(widthSpec, heightSpec);
            }
        };
        android.content.res.Configuration configuration = getContext().getResources().getConfiguration();
        boolean stacked = configuration.screenWidthDp < 384 || configuration.fontScale >= 1.3f;
        row.setOrientation(stacked ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = new TextView(getContext());
        name.setText(label); name.setTextSize(15); name.setTextColor(ru.playsoftware.j2meloader.ui.AppPalette.color(getContext(), ru.playsoftware.j2meloader.R.color.text_primary));
        name.setBreakStrategy(android.graphics.text.LineBreaker.BREAK_STRATEGY_HIGH_QUALITY);
        name.setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_NORMAL);
        name.setMinHeight(dp(48));
        row.addView(name, stacked ? new LinearLayout.LayoutParams(-1, -2) : new LinearLayout.LayoutParams(0,-2,1));
        name.setGravity(Gravity.CENTER_VERTICAL);
        android.widget.Spinner spinner = new android.widget.Spinner(getContext());
        spinner.setForeground(getContext().getDrawable(R.drawable.bg_settings_focus));
        spinner.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ru.playsoftware.j2meloader.ui.AppPalette.color(getContext(), ru.playsoftware.j2meloader.R.color.accent)));
        spinner.setContentDescription(label);
        if (edgePanel) {
            spinner.setBackgroundTintList(null);
            spinner.setBackground(new ru.playsoftware.j2meloader.ui.ConsoleSurface(getContext(),
                    ru.playsoftware.j2meloader.ui.ConsoleSurface.Kind.KEY));
            spinner.setForeground(null);
            spinner.setDefaultFocusHighlightEnabled(false);
            spinner.setPadding(dp(4), dp(2), dp(4), dp(4));
        }
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<String>(
                getContext(),android.R.layout.simple_spinner_item,options) {
            private View label(int position, boolean dropdown) {
                TextView text = new TextView(getContext());
                text.setText(getItem(position)); text.setTextSize(15); text.setTextColor(ru.playsoftware.j2meloader.ui.AppPalette.color(getContext(), ru.playsoftware.j2meloader.R.color.accent));
                if (!edgePanel || dropdown) text.setBackgroundColor(ru.playsoftware.j2meloader.ui.AppPalette.color(getContext(),
                        edgePanel ? R.color.background : R.color.phone_panel_alt));
                text.setGravity(Gravity.CENTER_VERTICAL);
                text.setPadding(dp(12),dp(6),dp(12),dp(6)); text.setMinHeight(dp(48));
                if (edgePanel && !dropdown) {
                    android.graphics.drawable.Drawable arrow = getContext().getDrawable(R.drawable.ic_arrow_down).mutate();
                    arrow.setTint(ru.playsoftware.j2meloader.ui.AppPalette.color(getContext(), R.color.accent));
                    arrow.setBounds(0, 0, dp(20), dp(20));
                    text.setCompoundDrawablesRelative(null, null, arrow, null);
                    text.setCompoundDrawablePadding(dp(4));
                }
                return text;
            }
            @Override public View getView(int p,View v,android.view.ViewGroup parent) { return label(p, false); }
            @Override public View getDropDownView(int p,View v,android.view.ViewGroup parent) { return label(p, true); }
        };
        spinner.setAdapter(adapter); spinner.setSelection(selected);
        name.setOnClickListener(v -> spinner.performClick());
        name.setFocusable(false);
        int[] current = {selected};
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(android.widget.AdapterView<?> parent) { }
            public void onItemSelected(android.widget.AdapterView<?> parent,View v,int index,long id) {
                if (current[0] != index) { current[0]=index; change.accept(index); }
            }
        });
        row.addView(spinner, stacked ? new LinearLayout.LayoutParams(-1, -2) : new LinearLayout.LayoutParams(0,-2,1.3f));
        row.setPadding(0,dp(3),0,dp(3));
        content.addView(row,new LinearLayout.LayoutParams(-1,-2));
        focusDefault(spinner);
    }
    public void toggle(String label, boolean checked, java.util.function.Consumer<Boolean> change) {
        android.widget.Switch toggle = new android.widget.Switch(getContext());
        toggle.setForeground(getContext().getDrawable(R.drawable.bg_settings_focus));
        toggle.setText(label); toggle.setTextSize(15); toggle.setTextColor(ru.playsoftware.j2meloader.ui.AppPalette.color(getContext(), ru.playsoftware.j2meloader.R.color.text_primary));
        toggle.setChecked(checked);
        toggle.setThumbTintList(new android.content.res.ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{ru.playsoftware.j2meloader.ui.AppPalette.color(getContext(), ru.playsoftware.j2meloader.R.color.accent), ru.playsoftware.j2meloader.ui.AppPalette.color(getContext(), ru.playsoftware.j2meloader.R.color.text_secondary)}));
        toggle.setTrackTintList(android.content.res.ColorStateList.valueOf(ru.playsoftware.j2meloader.ui.AppPalette.color(getContext(), ru.playsoftware.j2meloader.R.color.phone_line)));
        toggle.setOnCheckedChangeListener((button, value) -> change.accept(value));
        toggle.setMinHeight(dp(52));
        content.addView(toggle, new LinearLayout.LayoutParams(-1, -2));
        focusDefault(toggle);
    }
    public void opacity(int value, java.util.function.IntConsumer change) {
        percentage(getContext().getString(R.string.menu_opacity), value, 80, change);
    }
    public void percentage(String title, int value, int minimum, java.util.function.IntConsumer change) {
        TextView label = new TextView(getContext());
        label.setText(getContext().getString(R.string.menu_percentage, title, value)); label.setTextSize(15); label.setTextColor(ru.playsoftware.j2meloader.ui.AppPalette.color(getContext(), ru.playsoftware.j2meloader.R.color.text_primary));
        label.setPadding(0,dp(12),0,0); content.addView(label);
        android.widget.SeekBar slider = new android.widget.SeekBar(getContext());
        slider.setForeground(getContext().getDrawable(R.drawable.bg_settings_focus));
        slider.setContentDescription(title);
        slider.setMax(100-minimum); slider.setProgress(value-minimum);
        slider.setProgressTintList(android.content.res.ColorStateList.valueOf(ru.playsoftware.j2meloader.ui.AppPalette.color(getContext(), ru.playsoftware.j2meloader.R.color.accent)));
        slider.setThumbTintList(android.content.res.ColorStateList.valueOf(ru.playsoftware.j2meloader.ui.AppPalette.color(getContext(), ru.playsoftware.j2meloader.R.color.accent)));
        content.addView(slider,new LinearLayout.LayoutParams(-1,dp(48)));
        slider.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            public void onStartTrackingTouch(android.widget.SeekBar view) { }
            public void onStopTrackingTouch(android.widget.SeekBar view) { change.accept(view.getProgress()+minimum); }
            public void onProgressChanged(android.widget.SeekBar view,int progress,boolean fromUser) {
                label.setText(getContext().getString(R.string.menu_percentage, title, progress+minimum));
                if (fromUser && !view.isPressed()) change.accept(progress+minimum);
            }
        });
    }
    private int dp(int value) { return Math.round(value*getContext().getResources().getDisplayMetrics().density); }
}
