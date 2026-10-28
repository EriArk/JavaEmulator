package ru.playsoftware.j2meloader.input;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.config.ProfileModel;
import ru.playsoftware.j2meloader.config.ProfilesManager;
import ru.playsoftware.j2meloader.ui.AppPalette;

/** Edits a detached draft. No preview touch can reach a running guest. */
public final class TouchLayoutEditor extends Dialog {
    private final ProfileModel target, draft;
    private final Runnable done;
    private final boolean handleMode;
    private Preview preview;
    private LinearLayout controls, body;
    private LinearLayout tabs;
    private ScrollView settings;
    private SeekBar scale;
    private TextView scaleLabel;
    private boolean landscape;
    private int selected;
    private int tab;
    private String selectedButton = "up";
    private ImageButton reset;

    public TouchLayoutEditor(Context context, ProfileModel target, Runnable done) {
        this(context, target, done, false);
    }
    public TouchLayoutEditor(Context context, ProfileModel target, Runnable done, boolean handleMode) {
        super(context, R.style.AppTheme_NoActionBar);
        this.target = target; this.draft = ProfilesManager.copy(target); this.done = done;
        this.handleMode = handleMode;
        landscape = context.getResources().getConfiguration().orientation == 2;
        AppPalette.apply(getContext());
    }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(getContext()) {
            @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
                super.onSizeChanged(w,h,oldw,oldh);
                float font=Math.max(1,getResources().getConfiguration().fontScale);
                post(() -> arrange(w >= dp(480)*font || w > h * 1.4f && w >= dp(360)*font));
            }
        };
        root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(color(R.color.background));
        LinearLayout header = new LinearLayout(getContext()); header.setGravity(Gravity.CENTER_VERTICAL);
        icon(header, getContext().getString(R.string.game_cancel), androidx.appcompat.R.drawable.abc_ic_ab_back_material, this::dismiss);
        TextView title = label(handleMode ? getContext().getString(R.string.game_handle_editor) : getContext().getString(R.string.game_touch_layout), 18); title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setSingleLine(true); title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        reset = icon(header, getContext().getString(R.string.controls_reset_layout), R.drawable.ic_restore, () -> {
            if (handleMode) {
                if (landscape) draft.gameHandleLandscape = null; else draft.gameHandlePortrait = null;
                preview.invalidate(); return;
            }
            if (tab == 3) {
                draft.touchGamepadMappings = null;
                preview.rebuild(); buildControls(); return;
            }
            if (draft.touchPlacements != null) for (int i=0;i<2;i++) draft.touchPlacements.remove(preview.deck.placementId(i));
            draft.touchSize = 0;
            if (landscape) draft.touchLandscapeReach = 0; else draft.touchPortraitReach = 0;
            preview.rebuild(); buildControls();
        });
        icon(header, getContext().getString(R.string.controls_use_layout), R.drawable.ic_check, () -> {
            if (handleMode) copyHandle(draft, target); else copyLayout(draft, target);
            dismiss(); done.run();
        });
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(48)));
        tabs = new LinearLayout(getContext());
        if (!handleMode) root.addView(tabs, new LinearLayout.LayoutParams(-1,dp(48)));
        body = new LinearLayout(getContext()); body.setOrientation(LinearLayout.VERTICAL);
        preview = new Preview(getContext()); body.addView(preview, new LinearLayout.LayoutParams(-1, 0, 2));
        settings = new ScrollView(getContext()); controls = new LinearLayout(getContext());
        controls.setOrientation(LinearLayout.VERTICAL); controls.setPadding(dp(12), dp(4), dp(12), dp(12));
        settings.addView(controls); body.addView(settings, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(body, new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root); buildControls();
    }
    private void arrange(boolean side) {
        body.setOrientation(side ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        preview.setLayoutParams(new LinearLayout.LayoutParams(side ? 0 : -1, side ? -1 : 0, side ? 1 : 2));
        float font=Math.max(1,getContext().getResources().getConfiguration().fontScale);
        int panel=Math.min(Math.round(dp(280)*font),Math.max(Math.round(dp(192)*font),body.getWidth()/2));
        settings.setLayoutParams(new LinearLayout.LayoutParams(side ? panel : -1, side ? -1 : 0, side ? 0 : 1));
    }
    public static void copyLayout(ProfileModel from, ProfileModel to) {
        ProfileModel copy = ProfilesManager.copy(from);
        to.touchLayout = copy.touchLayout; to.touchSize = copy.touchSize;
        to.touchKeypadOverlay = copy.touchKeypadOverlay;
        to.touchTheme = copy.touchTheme; to.touchOpacity = copy.touchOpacity;
        to.touchPortraitReach = copy.touchPortraitReach; to.touchLandscapeReach = copy.touchLandscapeReach;
        to.touchPlacements = copy.touchPlacements;
        to.touchGamepadMappings = copy.touchGamepadMappings;
    }
    public static void copyHandle(ProfileModel from, ProfileModel to) {
        ProfileModel copy = ProfilesManager.copy(from);
        to.gameHandlePortrait = copy.gameHandlePortrait;
        to.gameHandleLandscape = copy.gameHandleLandscape;
    }
    @Override public void show() {
        super.show(); getWindow().setLayout(-1,-1);
        androidx.core.view.WindowInsetsControllerCompat bars = androidx.core.view.WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        bars.setSystemBarsBehavior(androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        bars.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars());
    }
    @Override public Bundle onSaveInstanceState() {
        Bundle state=super.onSaveInstanceState();
        state.putString("touchDraft",new com.google.gson.Gson().toJson(draft));
        state.putBoolean("landscape",landscape); state.putInt("tab",tab); state.putInt("block",selected);
        state.putBoolean("handleMode", handleMode);
        state.putString("touchButton", selectedButton);
        return state;
    }
    @Override public void onRestoreInstanceState(Bundle state) {
        super.onRestoreInstanceState(state);
        String json=state.getString("touchDraft");
        if(json!=null) {
            try { ProfileModel restored=new com.google.gson.Gson().fromJson(json,ProfileModel.class);
                if(restored!=null) { copyLayout(restored,draft); copyHandle(restored,draft); }
            } catch(RuntimeException ignored) { }
        }
        landscape=state.getBoolean("landscape",landscape); tab=Math.max(0,Math.min(3,state.getInt("tab")));
        String button = state.getString("touchButton", "up");
        selectedButton = TouchMappings.index(button) >= 0 ? button : "up";
        preview.rebuild(); selected=Math.max(0,Math.min(preview.deck.groupCount()-1,state.getInt("block")));
        buildControls();
    }
    @Override public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        if (event.getKeyCode() == android.view.KeyEvent.KEYCODE_BUTTON_B) {
            if (event.getAction() == android.view.KeyEvent.ACTION_UP) dismiss(); return true;
        }
        if (event.getKeyCode() == android.view.KeyEvent.KEYCODE_BUTTON_A && getCurrentFocus()!=null && getCurrentFocus().isClickable()) {
            if (event.getAction() == android.view.KeyEvent.ACTION_UP) getCurrentFocus().performClick(); return true;
        }
        return super.dispatchKeyEvent(event);
    }
    private void buildControls() {
        int scroll = settings.getScrollY();
        settings.post(() -> settings.scrollTo(0, scroll));
        controls.removeAllViews();
        scale = null;
        if (handleMode) {
            choice(getContext().getString(R.string.controls_preview), getContext().getResources().getStringArray(R.array.controls_preview_orientations), landscape ? 1 : 0, i -> {
                landscape = i == 1; preview.rebuild(); buildControls();
            });
            addMoveButtons();
            return;
        }
        tabs.removeAllViews();
        String resetLabel = getContext().getString(tab == 3 ? R.string.touch_mapping_reset : R.string.touch_positions_reset);
        reset.setContentDescription(resetLabel); reset.setTooltipText(resetLabel);
        String[] names = {getContext().getString(R.string.controls_layout), getContext().getString(R.string.controls_position), getContext().getString(R.string.controls_style), getContext().getString(R.string.touch_mapping_tab)};
        for (int i=0;i<names.length;i++) {
            final int index=i;
            TextView button=label(names[i],14); button.setGravity(Gravity.CENTER);
            button.setSingleLine(true); button.setEllipsize(android.text.TextUtils.TruncateAt.END);
            ru.playsoftware.j2meloader.ui.ConsoleSurface.apply(button, ru.playsoftware.j2meloader.ui.ConsoleSurface.Kind.TAB);
            button.setSelected(tab==i);
            button.setFocusable(true); button.setClickable(true); button.setContentDescription(getContext().getString(R.string.controls_tab, names[i]));
            button.setOnClickListener(v->{tab=index; settings.scrollTo(0,0); buildControls(); preview.invalidate();});
            tabs.addView(button,new LinearLayout.LayoutParams(0,dp(48),1));
        }
        if (tab == 0) {
        choice(getContext().getString(R.string.controls_layout), getContext().getResources().getStringArray(R.array.game_native_touch_layouts), draft.touchLayout == null ? 0 : Math.min(1,draft.touchLayout), i -> {
            draft.touchLayout=i; selected=0; preview.rebuild(); buildControls();
        });
        if (draft.touchLayout == null || draft.touchLayout == TouchDeck.PHONE)
            choice(getContext().getString(R.string.game_keypad_placement), getContext().getResources().getStringArray(R.array.game_keypad_placements), draft.touchKeypadOverlay ? 1 : 0,
                    i -> { draft.touchKeypadOverlay = i == 1; preview.rebuild(); buildControls(); });
        choice(getContext().getString(R.string.controls_preview), getContext().getResources().getStringArray(R.array.controls_preview_orientations), landscape ? 1 : 0, i -> {
            landscape=i==1; selected=0; preview.rebuild(); buildControls();
        });
        choice(getContext().getString(R.string.game_button_size), getContext().getResources().getStringArray(R.array.game_button_sizes), Math.max(0,Math.min(2,draft.touchSize)), i -> {
            draft.touchSize=i; preview.rebuild(); updateScale();
        });
        } else if (tab == 1) {
        choice(getContext().getString(R.string.controls_block), preview.deck.groupCount()==1 ? getContext().getResources().getStringArray(R.array.controls_phone_blocks) : getContext().getResources().getStringArray(R.array.controls_gamepad_blocks), selected, i -> {
            selected=i; preview.invalidate(); updateScale();
        });
        scaleLabel=label(getContext().getString(R.string.controls_block_size),14); controls.addView(scaleLabel);
        scale = slider(getContext().getString(R.string.controls_block_size), 50, 30, value -> {
            preview.deck.placement(selected).scale = (value+70)/100f;
            preview.rebuild(); scaleLabel.setText(getContext().getString(R.string.controls_block_size_value, value + 70));
        }); updateScale();
        addMoveButtons();
        } else if (tab == 3) {
            buildMapping();
        } else {
        choice(getContext().getString(R.string.game_button_theme),getContext().getResources().getStringArray(R.array.game_button_themes),Math.max(0,Math.min(2,draft.touchTheme)), i->{draft.touchTheme=i; preview.rebuild();});
        TextView opacity=label(getContext().getString(R.string.touch_button_opacity_value, draft.touchOpacity),14); controls.addView(opacity);
        int minimum = 20;
        slider(getContext().getString(R.string.touch_button_opacity),100-minimum,Math.max(0,Math.min(100-minimum,draft.touchOpacity-minimum)),v->{draft.touchOpacity=v+minimum; opacity.setText(getContext().getString(R.string.touch_button_opacity_value,v+minimum)); preview.invalidate();});
        if ("contrast".equals(AppPalette.selected(getContext()))) controls.addView(label(getContext().getString(R.string.touch_contrast_opacity),14));
        }
    }
    private void buildMapping() {
        if (draft.touchLayout == null || draft.touchLayout != TouchDeck.GAMEPAD) {
            choice(getContext().getString(R.string.controls_layout), getContext().getResources().getStringArray(R.array.game_native_touch_layouts), 0,
                    i -> { draft.touchLayout = i; preview.rebuild(); buildControls(); });
            return;
        }
        int preset = draft.touchGamepadMappings == null || draft.touchGamepadMappings.isEmpty() ? 0
                : draft.touchGamepadMappings.equals(TouchMappings.template(true)) ? 1 : 2;
        choice(getContext().getString(R.string.touch_mapping_template),
                getContext().getResources().getStringArray(R.array.touch_mapping_templates), preset, i -> {
                    if (i == 2) return;
                    draft.touchGamepadMappings = TouchMappings.template(i == 1);
                    preview.rebuild(); buildControls();
                });
        String[] names = getContext().getResources().getStringArray(R.array.touch_mapping_buttons);
        String[] assignments = new String[names.length];
        for (int i = 0; i < names.length; i++) assignments[i] = names[i] + " : " + assignment(TouchMappings.IDS[i]);
        choice(getContext().getString(R.string.touch_mapping_button), assignments, TouchMappings.index(selectedButton),
                i -> { selectedButton = TouchMappings.IDS[i]; buildControls(); preview.invalidate(); });
        // Keep the digit keys in the familiar telephone order below the navigation pad.
        int[][] rows = {{-6,-1,-7}, {-3,-5,-4}, {-2}, {49,50,51}, {52,53,54}, {55,56,57}, {42,48,35}};
        Integer assigned = draft.touchGamepadMappings == null ? null : draft.touchGamepadMappings.get(selectedButton);
        for (int[] rowCodes : rows) {
            LinearLayout row = new LinearLayout(getContext()); row.setGravity(Gravity.CENTER);
            for (int code : rowCodes) {
                TextView key = mappingButton(TouchMappings.glyph(code), assigned != null && assigned == code, () -> {
                    if (draft.touchGamepadMappings == null) draft.touchGamepadMappings = new java.util.HashMap<>();
                    draft.touchGamepadMappings.put(selectedButton, code);
                    preview.rebuild(); buildControls();
                });
                key.setContentDescription(TouchMappings.glyph(code) + " (" + code + ")");
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(48), 1);
                params.setMargins(dp(2), 0, dp(2), 0); row.addView(key, params);
            }
            if (rowCodes.length == 1) {
                TextView automatic = mappingButton(getContext().getString(R.string.touch_mapping_default),
                        assigned == null, () -> {
                            if (draft.touchGamepadMappings != null) draft.touchGamepadMappings.remove(selectedButton);
                            preview.rebuild(); buildControls();
                        });
                automatic.setContentDescription(getContext().getString(R.string.touch_mapping_default_description));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(48), 2);
                params.setMargins(dp(2), 0, dp(2), 0); row.addView(automatic, params);
            }
            controls.addView(row);
        }
    }
    private String assignment(String id) {
        int[] codes = TouchMappings.resolve(draft.touchGamepadMappings, id);
        StringBuilder text = new StringBuilder();
        for (int code : codes) {
            if (text.length() > 0) text.append(" + ");
            text.append(TouchMappings.glyph(code)).append(" (").append(code).append(")");
        }
        return text.toString();
    }
    private TextView mappingButton(String text, boolean selected, Runnable action) {
        TextView button = label(text, 16); button.setGravity(Gravity.CENTER);
        ru.playsoftware.j2meloader.ui.ConsoleSurface.apply(button, ru.playsoftware.j2meloader.ui.ConsoleSurface.Kind.KEY);
        button.setSelected(selected);
        button.setTextColor(color(selected ? R.color.accent : R.color.text_primary));
        button.setFocusable(true); button.setClickable(true); button.setOnClickListener(v -> action.run());
        return button;
    }
    private void addMoveButtons() {
        LinearLayout move = new LinearLayout(getContext()); move.setGravity(Gravity.CENTER);
        icon(move,getContext().getString(R.string.controls_move_left),androidx.appcompat.R.drawable.abc_ic_ab_back_material,()->move(-8,0));
        icon(move,getContext().getString(R.string.controls_move_up),R.drawable.ic_arrow_up,()->move(0,-8));
        icon(move,getContext().getString(R.string.controls_move_down),R.drawable.ic_arrow_down,()->move(0,8));
        icon(move,getContext().getString(R.string.controls_move_right),androidx.appcompat.R.drawable.abc_ic_ab_back_material,()->move(8,0));
        ((ImageButton) move.getChildAt(3)).setRotation(180);
        controls.addView(move);
    }
    private void move(float x,float y) {
        if (handleMode) GameHandlePlacement.move(draft, landscape, preview.safeArea(), 48, x, y);
        else preview.deck.moveGroup(selected,x,y);
        preview.invalidate();
    }
    private void updateScale() {
        if(scale==null) return;
        TouchPlacement p = draft.touchPlacements == null ? null : draft.touchPlacements.get(preview.deck.placementId(selected));
        int value = p == null ? 100 : Math.round(p.scale()*100);
        scale.setProgress(value-70); scaleLabel.setText(getContext().getString(R.string.controls_block_size_value, value));
    }
    private SeekBar slider(String name,int max,int value,java.util.function.IntConsumer changed) {
        SeekBar bar = new SeekBar(getContext()); bar.setContentDescription(name); bar.setMax(max); bar.setProgress(value);
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(color(R.color.accent)));
        bar.setThumbTintList(android.content.res.ColorStateList.valueOf(color(R.color.accent)));
        controls.addView(bar,new LinearLayout.LayoutParams(-1,dp(48)));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onStartTrackingTouch(SeekBar v) { }
            public void onStopTrackingTouch(SeekBar v) { }
            public void onProgressChanged(SeekBar v,int n,boolean user) { if(user) changed.accept(n); }
        }); return bar;
    }
    private void choice(String name,String[] values,int selected,java.util.function.IntConsumer change) {
        LinearLayout row=new LinearLayout(getContext()); row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(label(name,14),new LinearLayout.LayoutParams(0,-2,1));
        Spinner spinner=new Spinner(getContext()); spinner.setContentDescription(name);
        ArrayAdapter<String> adapter=new ArrayAdapter<String>(getContext(),android.R.layout.simple_spinner_dropdown_item,values) {
            private View item(int index) {
                TextView text=label(getItem(index),14); text.setMaxLines(2); text.setGravity(Gravity.CENTER_VERTICAL);
                text.setMinHeight(dp(48)); text.setPadding(dp(6),dp(4),dp(6),dp(4));
                text.setTextColor(color(R.color.accent)); text.setBackgroundColor(color(R.color.phone_panel_alt));
                return text;
            }
            @Override public View getView(int p,View v,android.view.ViewGroup parent) { return item(p); }
            @Override public View getDropDownView(int p,View v,android.view.ViewGroup parent) { return item(p); }
        };
        spinner.setAdapter(adapter); spinner.setSelection(selected); int[] current={selected};
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(android.widget.AdapterView<?> p) { }
            public void onItemSelected(android.widget.AdapterView<?> p,View v,int index,long id) {
                if(current[0]!=index) {current[0]=index; change.accept(index);}
            }
        }); spinner.setMinimumHeight(dp(48)); row.addView(spinner,new LinearLayout.LayoutParams(0,-2,1.4f)); controls.addView(row);
    }
    private ImageButton icon(LinearLayout parent,String name,int resource,Runnable action) {
        ImageButton button=new ImageButton(getContext()); button.setImageResource(resource);
        button.setImageTintList(android.content.res.ColorStateList.valueOf(color(R.color.accent)));
        button.setBackgroundResource(R.drawable.bg_library_tab); button.setPadding(dp(12),dp(12),dp(12),dp(12));
        button.setContentDescription(name); button.setTooltipText(name); button.setOnClickListener(v->action.run());
        parent.addView(button,new LinearLayout.LayoutParams(dp(48),dp(48)));
        return button;
    }
    private TextView label(String text,int size) {
        TextView label=new TextView(getContext()); label.setText(text); label.setTextSize(size);
        label.setTextColor(color(R.color.text_primary)); return label;
    }
    private int color(int id) { return AppPalette.color(getContext(),id); }
    private int dp(int value) { return Math.round(value*getContext().getResources().getDisplayMetrics().density); }

    private final class Preview extends View {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        final TouchDeck deck;
        float zoom=1, offsetX,offsetY,lastX,lastY;
        int pointer=-1;
        Preview(Context context) {
            super(context); setContentDescription(getContext().getString(R.string.controls_touch_preview));
            deck=new TouchDeck(draft,1,new TouchDeck.Sink() {
                public void press(int key) { } public void release(int key) { } public void repeat(int key) { }
                public void feedback() { } public void invalidate() { Preview.this.invalidate(); }
            }); rebuild();
        }
        private float logicalWidth() {
            android.util.DisplayMetrics metrics=getResources().getDisplayMetrics();
            return (landscape ? Math.max(metrics.widthPixels,metrics.heightPixels) : Math.min(metrics.widthPixels,metrics.heightPixels))/metrics.density;
        }
        private float logicalHeight() {
            android.util.DisplayMetrics metrics=getResources().getDisplayMetrics();
            return (landscape ? Math.min(metrics.widthPixels,metrics.heightPixels) : Math.max(metrics.widthPixels,metrics.heightPixels))/metrics.density;
        }
        void rebuild() { deck.setTheme(getContext()); deck.layout(logicalWidth(),logicalHeight()); invalidate(); }
        private RectF safeArea() { return new RectF(0, 0, logicalWidth(), logicalHeight()); }
        private RectF handleBounds() { return GameHandlePlacement.bounds(draft, landscape, safeArea(), 48); }
        @Override protected void onSizeChanged(int w,int h,int oldw,int oldh) { super.onSizeChanged(w,h,oldw,oldh); rebuild(); }
        @Override protected void onDraw(Canvas canvas) {
            float w=logicalWidth(),h=logicalHeight();
            zoom=Math.max(.01f,Math.min((getWidth()-dp(16))/w,(getHeight()-dp(16))/h));
            offsetX=(getWidth()-w*zoom)/2; offsetY=(getHeight()-h*zoom)/2;
            canvas.save(); canvas.translate(offsetX,offsetY); canvas.scale(zoom,zoom);
            paint.setColor(color(R.color.background)); canvas.drawRect(0,0,w,h,paint);
            RectF game=deck.gameArea(w,h); paint.setColor(color(R.color.mapper_lcd)); canvas.drawRect(game,paint);
            paint.setColor(color(R.color.mapper_lcd_text)); paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(20);
            canvas.drawText("AbyssME",game.centerX(),game.centerY(),paint);
            deck.draw(canvas);
            if (handleMode) {
                RectF handle = handleBounds();
                android.graphics.drawable.Drawable icon = getContext().getDrawable(R.drawable.ic_arrow_down).mutate();
                paint.setColor(color(R.color.touch_button));
                canvas.drawRoundRect(handle, 6, 6, paint);
                icon.setTint(color(R.color.accent));
                icon.setBounds(Math.round(handle.left + 4), Math.round(handle.top + 4),
                        Math.round(handle.right - 4), Math.round(handle.bottom - 4));
                icon.draw(canvas);
            }
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2/zoom); paint.setColor(color(R.color.accent));
            if (!handleMode && tab == 3) {
                for (TouchDeck.Key key : deck.getKeys()) if (selectedButton.equals(key.id)) canvas.drawRoundRect(key.bounds,5,5,paint);
            } else canvas.drawRoundRect(handleMode ? handleBounds() : deck.groupBounds(selected),5,5,paint);
            paint.setStyle(Paint.Style.FILL);
            canvas.restore();
        }
        @Override public boolean onTouchEvent(MotionEvent event) {
            int index=event.findPointerIndex(pointer);
            if(event.getActionMasked()==MotionEvent.ACTION_DOWN) {
                float x=(event.getX()-offsetX)/zoom,y=(event.getY()-offsetY)/zoom;
                if (handleMode) {
                    if (!handleBounds().contains(x, y)) return false;
                    pointer = event.getPointerId(0); lastX = x; lastY = y;
                    getParent().requestDisallowInterceptTouchEvent(true); return true;
                }
                if (tab == 3) {
                    for (TouchDeck.Key key : deck.getKeys()) if (key.bounds.contains(x,y)) {
                        if (key.isLayoutSwitch()) { deck.down(0,x,y); deck.up(0); }
                        else if (key.isMappable()) { selectedButton = key.id; buildControls(); }
                        invalidate(); return true;
                    }
                    return true;
                }
                for(int i=0;i<deck.groupCount();i++) if(deck.groupBounds(i).contains(x,y)) {
                    boolean changed=selected!=i;
                    selected=i; pointer=event.getPointerId(0); lastX=x; lastY=y;
                    if(changed && tab==1) buildControls(); else updateScale();
                    invalidate();
                    getParent().requestDisallowInterceptTouchEvent(true); return true;
                }
                return false;
            }
            if(event.getActionMasked()==MotionEvent.ACTION_MOVE && index>=0) {
                float x=(event.getX(index)-offsetX)/zoom,y=(event.getY(index)-offsetY)/zoom;
                move(x-lastX,y-lastY); lastX=x; lastY=y; return true;
            }
            if(event.getActionMasked()==MotionEvent.ACTION_UP || event.getActionMasked()==MotionEvent.ACTION_CANCEL
                    || event.getActionMasked()==MotionEvent.ACTION_POINTER_UP && event.getPointerId(event.getActionIndex())==pointer) {
                pointer=-1; getParent().requestDisallowInterceptTouchEvent(false); performClick(); return true;
            }
            return pointer>=0;
        }
        @Override public boolean performClick() { super.performClick(); return true; }
        @Override protected void onDetachedFromWindow() { deck.cancel(); super.onDetachedFromWindow(); }
    }
}
