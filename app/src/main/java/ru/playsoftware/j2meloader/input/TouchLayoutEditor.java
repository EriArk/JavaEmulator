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
    private ScrollView settings;
    private SeekBar scale;
    private TextView scaleLabel;
    private boolean landscape;
    private int selected;
    private int tab;

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
        icon(header, "Cancel", androidx.appcompat.R.drawable.abc_ic_ab_back_material, this::dismiss);
        TextView title = label(handleMode ? getContext().getString(R.string.game_handle_editor) : "Touch layout", 18); title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setSingleLine(true); title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        icon(header, "Reset layout", R.drawable.ic_restore, () -> {
            if (handleMode) {
                if (landscape) draft.gameHandleLandscape = null; else draft.gameHandlePortrait = null;
                preview.invalidate(); return;
            }
            if (draft.touchPlacements != null) for (int i=0;i<2;i++) draft.touchPlacements.remove(preview.deck.placementId(i));
            draft.touchSize = 0;
            if (landscape) draft.touchLandscapeReach = 0; else draft.touchPortraitReach = 0;
            preview.rebuild(); buildControls();
        });
        icon(header, "Use layout", R.drawable.ic_check, () -> {
            if (handleMode) copyHandle(draft, target); else copyLayout(draft, target);
            dismiss(); done.run();
        });
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(48)));
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
        landscape=state.getBoolean("landscape",landscape); tab=Math.max(0,Math.min(2,state.getInt("tab")));
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
        controls.removeAllViews();
        scale = null;
        if (handleMode) {
            choice("Preview", new String[]{"Portrait", "Landscape"}, landscape ? 1 : 0, i -> {
                landscape = i == 1; preview.rebuild(); buildControls();
            });
            addMoveButtons();
            return;
        }
        LinearLayout tabs = new LinearLayout(getContext());
        String[] names = {"Layout", "Position", "Style"};
        for (int i=0;i<names.length;i++) {
            final int index=i;
            TextView button=label(names[i],14); button.setGravity(Gravity.CENTER);
            button.setSingleLine(true); button.setEllipsize(android.text.TextUtils.TruncateAt.END);
            button.setBackgroundResource(R.drawable.bg_phone_mode); button.setSelected(tab==i);
            button.setFocusable(true); button.setClickable(true); button.setContentDescription(names[i]+" tab");
            button.setOnClickListener(v->{tab=index; buildControls();});
            tabs.addView(button,new LinearLayout.LayoutParams(0,dp(48),1));
        }
        controls.addView(tabs);
        if (tab == 0) {
        choice("Layout", new String[]{"Phone", "Gamepad"}, draft.touchLayout == null ? 0 : Math.min(1,draft.touchLayout), i -> {
            draft.touchLayout=i; selected=0; preview.rebuild(); buildControls();
        });
        if (draft.touchLayout == null || draft.touchLayout == TouchDeck.PHONE)
            choice("Keypad placement", new String[]{"Separate", "Overlay"}, draft.touchKeypadOverlay ? 1 : 0,
                    i -> { draft.touchKeypadOverlay = i == 1; preview.rebuild(); buildControls(); });
        choice("Preview", new String[]{"Portrait", "Landscape"}, landscape ? 1 : 0, i -> {
            landscape=i==1; selected=0; preview.rebuild(); buildControls();
        });
        choice("Button size", new String[]{"Standard", "Large", "Extra large"}, Math.max(0,Math.min(2,draft.touchSize)), i -> {
            draft.touchSize=i; preview.rebuild(); updateScale();
        });
        } else if (tab == 1) {
        choice("Block", preview.deck.groupCount()==1 ? new String[]{"Keypad"} : new String[]{"Directions", "Actions"}, selected, i -> {
            selected=i; preview.invalidate(); updateScale();
        });
        scaleLabel=label("Block size",14); controls.addView(scaleLabel);
        scale = slider("Block size", 50, 30, value -> {
            preview.deck.placement(selected).scale = (value+70)/100f;
            preview.rebuild(); scaleLabel.setText("Block size: " + (value+70) + "%");
        }); updateScale();
        addMoveButtons();
        } else {
        choice("Button theme",new String[]{"Follow app","Dark","Light"},Math.max(0,Math.min(2,draft.touchTheme)), i->{draft.touchTheme=i; preview.rebuild();});
        TextView opacity=label(getContext().getString(R.string.touch_button_opacity_value, draft.touchOpacity),14); controls.addView(opacity);
        int minimum = 20;
        slider(getContext().getString(R.string.touch_button_opacity),100-minimum,Math.max(0,Math.min(100-minimum,draft.touchOpacity-minimum)),v->{draft.touchOpacity=v+minimum; opacity.setText(getContext().getString(R.string.touch_button_opacity_value,v+minimum)); preview.invalidate();});
        if ("contrast".equals(AppPalette.selected(getContext()))) controls.addView(label(getContext().getString(R.string.touch_contrast_opacity),14));
        }
    }
    private void addMoveButtons() {
        LinearLayout move = new LinearLayout(getContext()); move.setGravity(Gravity.CENTER);
        icon(move,"Move left",androidx.appcompat.R.drawable.abc_ic_ab_back_material,()->move(-8,0));
        icon(move,"Move up",R.drawable.ic_arrow_up,()->move(0,-8));
        icon(move,"Move down",R.drawable.ic_arrow_down,()->move(0,8));
        icon(move,"Move right",androidx.appcompat.R.drawable.abc_ic_ab_back_material,()->move(8,0));
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
        scale.setProgress(value-70); scaleLabel.setText("Block size: "+value+"%");
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
    private void icon(LinearLayout parent,String name,int resource,Runnable action) {
        ImageButton button=new ImageButton(getContext()); button.setImageResource(resource);
        button.setImageTintList(android.content.res.ColorStateList.valueOf(color(R.color.accent)));
        button.setBackgroundResource(R.drawable.bg_library_tab); button.setPadding(dp(12),dp(12),dp(12),dp(12));
        button.setContentDescription(name); button.setTooltipText(name); button.setOnClickListener(v->action.run());
        parent.addView(button,new LinearLayout.LayoutParams(dp(48),dp(48)));
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
            super(context); setContentDescription("Touch layout preview");
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
            canvas.drawRoundRect(handleMode ? handleBounds() : deck.groupBounds(selected),5,5,paint); paint.setStyle(Paint.Style.FILL);
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
