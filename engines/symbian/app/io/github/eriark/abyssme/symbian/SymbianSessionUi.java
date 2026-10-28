package io.github.eriark.abyssme.symbian;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.*;
import android.os.*;
import android.util.SparseIntArray;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import ru.playsoftware.j2meloader.BuildConfig;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.config.*;
import ru.playsoftware.j2meloader.input.*;
import ru.playsoftware.j2meloader.util.Screenshots;
import javax.microedition.lcdui.keyboard.KeyMapper;

/** App-owned chrome and phone controls; the native surface remains engine-owned. */
final class SymbianSessionUi {
    interface Host extends SymbianInput.NativeKeys, SymbianTouch.Sink {
        RectF touchBounds();
        default int[] displayGeometry() { return null; }
        void pause(boolean paused);
        void display(int scale);
        void frameRate(int fps);
        default void speed(int percent) { }
        void leave();
        void restart();
        Bitmap capture() throws IOException;
    }
    private final Activity activity;
    private final Host host;
    private final String title;
    private final TextureView surface;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService disk = Executors.newSingleThreadExecutor();
    final FrameLayout root;
    final ProfileModel profile;
    private final SymbianInput keys;
    private final ControllerInput input;
    private SparseIntArray mappings;
    private final DeckView deckView;
    private final SettingsPreviewBar preview;
    private final ImageButton camera;
    private final GameChrome gameChrome;
    private final FastForwardControl fastForward;
    private final DeviceControls devices;
    private GameMenuDialog menu;
    private MapperDialog mapper;
    private boolean closed, visible = true, focused = true, selectDown, screenshotPending;
    private int selectDevice;
    private int[] lcdGeometry;
    private long nextLcdGeometry;
    void frameAvailable() {
        if (closed || profile.screenEffect == 0) return;
        long now = SystemClock.uptimeMillis();
        if (now < nextLcdGeometry) return;
        nextLcdGeometry = now + 200;
        int[] geometry = host.displayGeometry();
        if (!java.util.Arrays.equals(geometry, lcdGeometry)) {
            lcdGeometry = geometry; deckView.invalidate();
        }
    }
    private final Runnable holdSelect = this::heldSelect;
    private void heldSelect() { if (selectDown) { selectDown = false; showMapping(profile); } }

    SymbianSessionUi(Activity activity, TextureView surface, String system, long uid, String title, Host host) throws IOException {
        this.activity = activity; this.surface = surface; this.title = title; this.host = host;
        profile = SymbianGameSettings.load(activity.getFilesDir(), system, uid);
        try { SettingsTrial.claim(profile); }
        catch (IOException error) {
            android.util.Log.w("AbyssSymbian", "Could not apply settings draft", error);
            Toast.makeText(activity, "Could not apply settings. Using saved settings.", Toast.LENGTH_LONG).show();
        }
        mappings = KeyMapper.resolveMappings(profile.getActiveKeyMappings());
        keys = new SymbianInput(host); input = new ControllerInput(key -> mappings.get(key, 0), keys.source());
        root = new FrameLayout(activity) {
            @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) { super.onSizeChanged(w,h,oldw,oldh); post(() -> SymbianSessionUi.this.layout()); }
        };
        root.setBackgroundColor(Color.BLACK); root.addView(surface);
        deckView = new DeckView(keys.source()); root.addView(deckView, new FrameLayout.LayoutParams(-1,-1));
        ImageButton settings = icon(activity.getString(R.string.game_menu_title), R.drawable.ic_baseline_tune_24, this::showMenu);
        settings.setId(R.id.game_menu_button);
        root.addView(settings, new FrameLayout.LayoutParams(dp(48),dp(48), Gravity.TOP | Gravity.END));
        camera = icon(activity.getString(R.string.game_screenshot), R.drawable.ic_action_screenshot, this::screenshot);
        camera.setId(R.id.game_screenshot_button);
        root.addView(camera, new FrameLayout.LayoutParams(dp(48),dp(48), Gravity.TOP | Gravity.START));
        preview = new SettingsPreviewBar(activity, this::keep, this::undo);
        preview.attachTo(root);
        fastForward = new FastForwardControl(root, profile, () -> true, host::speed, host::frameRate, false);
        input.shortcut(fastForward::binding, fastForward::button);
        gameChrome = BuildConfig.HANDHELD_MODE ? null : new GameChrome(root, profile);
        fastForward.changed(() -> { if (gameChrome != null) gameChrome.render(); });
        devices = new DeviceControls(activity, () -> { fastForward.cancel(); clear(); }, this::layout, () -> {
            if (visible) showMenu();
            pause();
        });
        apply();
        if (ProfilesManager.isPreview(profile)) preview.start();
    }
    private int dp(int n) { return Math.round(n * activity.getResources().getDisplayMetrics().density); }
    private ImageButton icon(String label, int resource, Runnable action) {
        ImageButton button = new ImageButton(activity); button.setImageResource(resource);
        button.setBackgroundResource(R.drawable.bg_quick_setting_button);
        button.setImageTintList(android.content.res.ColorStateList.valueOf(ru.playsoftware.j2meloader.ui.AppPalette.color(activity, ru.playsoftware.j2meloader.R.color.accent)));
        button.setPadding(dp(12),dp(12),dp(12),dp(12)); button.setContentDescription(label); button.setTooltipText(label);
        button.setOnClickListener(v -> action.run()); return button;
    }
    int scale() { return Math.max(0, Math.min(2, profile.screenScaleType)); }
    void apply() {
        fastForward.configure();
        lcdGeometry = null; nextLcdGeometry = 0;
        TouchAppearance.shortcuts(root, profile);
        clear(); mappings = KeyMapper.resolveMappings(profile.getActiveKeyMappings());
        activity.setRequestedOrientation(BuildConfig.HANDHELD_MODE || profile.orientation == 3 ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                : profile.orientation == 2 ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
        host.speed(profile.getEmulationSpeed());
        host.display(scale()); host.frameRate(profile.getNativeFpsLimit()); layout(); updateShortcuts();
    }
    private void layout() {
        deckView.deck.setEnabled(DeviceControls.showTouch(profile, devices != null && devices.connected()));
        if (closed || root.getWidth() == 0 || root.getHeight() == 0) return;
        deckView.layoutDeck(root.getWidth(), root.getHeight());
        RectF area = BuildConfig.HANDHELD_MODE ? new RectF(0,0,root.getWidth(),root.getHeight())
                : deckView.deck.gameArea(root.getWidth(), root.getHeight());
        boolean quarter = Math.floorMod(profile.screenRotation / 90, 2) != 0;
        int width = Math.max(1, Math.round(quarter ? area.height() : area.width()));
        int height = Math.max(1, Math.round(quarter ? area.width() : area.height()));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, height);
        params.leftMargin = Math.round(area.centerX() - width / 2f); params.topMargin = Math.round(area.centerY() - height / 2f);
        surface.setLayoutParams(params); surface.setPivotX(width / 2f); surface.setPivotY(height / 2f);
        surface.setRotation(Math.floorMod(profile.screenRotation / 90, 4) * 90); deckView.invalidate();
    }
    void clear() {
        if (fastForward != null) fastForward.stop();
        handler.removeCallbacks(holdSelect); selectDown = false;
        input.clear(); if (deckView != null) deckView.cancel(); keys.clear();
    }
    void visible(boolean value) {
        if (!value) fastForward.cancel();
        visible = value;
        if (!value && menu != null) menu.cancelPreviewPage();
        if (value && devices.resumeRequired()) showMenu();
        if (value) updateShortcuts(); pause();
    }
    private void updateShortcuts() {
        camera.setVisibility(Screenshots.isButtonVisible(activity) ? View.VISIBLE : View.GONE);
        if (gameChrome != null) gameChrome.update(!closed && visible && focused && menu == null && mapper == null,
                false, Screenshots.isButtonVisible(activity));
    }
    void focused(boolean value) { if (!value) fastForward.cancel(); focused = value; pause(); }
    private void pause() {
        updateShortcuts();
        boolean paused = closed || !visible || !focused || menu != null || mapper != null || devices.resumeRequired();
        if (paused) clear(); host.pause(paused);
        if (paused) preview.suspend(); else preview.resume();
    }
    boolean key(KeyEvent event) {
        if (closed || !visible || !focused || menu != null || mapper != null) return false;
        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_MENU) {
            if (event.getAction() == KeyEvent.ACTION_UP) showMenu(); return true;
        }
        if (code == KeyEvent.KEYCODE_BUTTON_SELECT) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                selectDown = true; selectDevice = event.getDeviceId(); handler.postDelayed(holdSelect, 450);
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                handler.removeCallbacks(holdSelect);
                if (selectDown && selectDevice == event.getDeviceId()) {
                    int device = selectDevice; input.key(device, code, true, false);
                    handler.postDelayed(() -> input.key(device, code, false, false), 80);
                }
                selectDown = false;
            }
            return true;
        }
        if (mappings.get(code, 0) == 0 && fastForward.binding() != code) return false;
        input.key(event.getDeviceId(), code, event.getAction() == KeyEvent.ACTION_DOWN, event.getRepeatCount() > 0); return true;
    }
    boolean motion(MotionEvent event) {
        if (closed || !visible || !focused || menu != null || mapper != null || !event.isFromSource(InputDevice.SOURCE_JOYSTICK)) return false;
        input.axes(event.getDeviceId(), event.getAxisValue(MotionEvent.AXIS_X), event.getAxisValue(MotionEvent.AXIS_Y),
                event.getAxisValue(MotionEvent.AXIS_HAT_X), event.getAxisValue(MotionEvent.AXIS_HAT_Y),
                Math.max(event.getAxisValue(MotionEvent.AXIS_LTRIGGER), event.getAxisValue(MotionEvent.AXIS_BRAKE)),
                Math.max(event.getAxisValue(MotionEvent.AXIS_RTRIGGER), event.getAxisValue(MotionEvent.AXIS_GAS)));
        return true;
    }
    void showMenu() {
        if (closed || menu != null || mapper != null) return;
        fastForward.cancel();
        menu = new GameMenuDialog(activity, title);
        menu.useEdgePanel();
        menu.setOnDismissListener(d -> { menu = null; devices.resume(); if (!closed) pause(); }); menu.show(); pause(); home();
    }
    private void home() {
        menuPages().home();
    }
    private GameMenuPages menuPages() {
        return new GameMenuPages(menu, new GameMenuPages.Host() {
            public void display() { displayPage(); }
            public void controls() { pages().controls(SymbianSessionUi.this::home); }
            public void more() { populateMorePage(); }
            public boolean hasSocial() { return true; }
            public void social() {
                ru.playsoftware.j2meloader.input.SocialMenuPages.show(menu, activity,
                        SymbianSessionUi.this::home, activity::startActivity);
            }
            public boolean hasPreview() { return ProfilesManager.isPreview(profile); }
            public void keep() { SymbianSessionUi.this.keep(); }
            public void undo() { SymbianSessionUi.this.undo(); }
            public void exit() { close(); host.leave(); }
            public void restart() {
                if (ProfilesManager.isPreview(profile)) undo();
                host.restart();
            }
        }, this::screenshot, fastForward);
    }
    private void morePage() {
        menuPages().more();
    }
    private void populateMorePage() {
        menu.action(activity.getString(R.string.game_fast_forward_settings), android.R.drawable.ic_media_ff,
                () -> pages().fastForward(this::morePage));
        menu.action(activity.getString(R.string.game_speed_value, profile.getEmulationSpeed()),
                R.drawable.ic_baseline_tune_24, () -> pages().speed(this::morePage));
        menu.setBackAction(this::home);
        menu.action(activity.getString(R.string.game_fps_limit_value, GameSettingsPages.frameRateLabel(activity, profile.getNativeFpsLimit())),
                R.drawable.ic_baseline_tune_24, () -> pages().frameRate(this::morePage));
        menu.action(activity.getString(R.string.on_screen_shortcuts), R.drawable.ic_baseline_tune_24,
                () -> ru.playsoftware.j2meloader.util.GameShortcuts.page(activity, menu, false,
                        this::updateShortcuts, this::morePage));
    }
    private void displayPage() {
        new ru.playsoftware.j2meloader.config.DisplayPanel(menu, profile, false, true,
                this::previewPicture, this::home, this::advancedDisplayPage).show();
    }
    void previewPicture() {
        // LCD is a host overlay: keep the paused native surface and guest timing untouched.
        lcdGeometry = host.displayGeometry();
        nextLcdGeometry = 0;
        deckView.invalidate();
    }
    private void advancedDisplayPage() {
        pages().nativePhoneDisplay();
    }
    private GameSettingsPages pages() {
        return new GameSettingsPages(menu, profile, true, false, new GameSettingsPages.Host() {
            public void preview(ProfileModel draft) { trySettings(draft); }
            public void mapping(ProfileModel draft, Runnable back) { showMapping(draft); }
            public void advanced() { }
            public void back() { home(); }
        });
    }
    private void showMapping(ProfileModel source) {
        if (closed || mapper != null) return;
        ProfileModel draft = ProfilesManager.copy(source);
        if (menu != null) menu.hide();
        mapper = new MapperDialog(activity, title, draft, () -> {
            boolean applied = mapper.wasApplied();
            mapper = null;
            if (closed) return;
            if (applied) trySettings(draft);
            if (menu != null) menu.show(); pause();
            if (visible && devices.resumeRequired()) showMenu();
        });
        mapper.show(); pause();
    }
    private void trySettings(ProfileModel draft) {
        ProfilesManager.beginPreview(profile); ProfilesManager.copyInto(draft,profile); apply();
        if (menu != null) menu.dismiss(); preview.start(); pause();
    }
    private void keep() {
        if (!ProfilesManager.keepPreview(profile)) { Toast.makeText(activity,activity.getString(R.string.game_save_settings_failed),Toast.LENGTH_LONG).show(); return; }
        preview.stop(); if (menu != null) home();
    }
    private void undo() { ProfilesManager.undoPreview(profile); apply(); preview.stop(); if (menu != null) home(); }
    void screenshot() {
        if (closed || screenshotPending) return;
        screenshotPending = true; camera.setEnabled(false);
        // Wait for menu dismissal/resume before asking the engine for its frame.
        handler.post(() -> copyScreenshot(10));
    }
    private void retryScreenshot(int remaining) {
        if (remaining > 0 && !closed) handler.postDelayed(() -> copyScreenshot(remaining-1),100);
        else screenshotDone(false);
    }
    private void copyScreenshot(int remaining) {
        if (closed) { screenshotDone(false); return; }
        if (!visible || !focused || menu != null || mapper != null || !surface.isAvailable()
                || surface.getWidth() == 0 || surface.getHeight() == 0) { retryScreenshot(remaining); return; }
        Bitmap bitmap;
        try { bitmap = host.capture(); }
        catch (IOException | RuntimeException error) {
            android.util.Log.e("AbyssSymbian","Cannot capture game frame",error); screenshotDone(false); return;
        }
        if (bitmap == null) { retryScreenshot(remaining); return; }
        int rotation = Math.floorMod(profile.screenRotation / 90,4)*90;
        ru.playsoftware.j2meloader.util.ScreenshotEffect effect = new ru.playsoftware.j2meloader.util.ScreenshotEffect(profile);
        int[] geometry = host.displayGeometry();
        int guestWidth = geometry != null && geometry.length == 6 ? geometry[4] : bitmap.getWidth();
        int guestHeight = geometry != null && geometry.length == 6 ? geometry[5] : bitmap.getHeight();
        disk.execute(() -> {
            Bitmap output = bitmap; boolean saved = false;
            try {
                if (rotation != 0) { Matrix matrix = new Matrix(); matrix.postRotate(rotation);
                    output = Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),matrix,false); }
                boolean quarter = rotation % 180 != 0;
                Screenshots.save(activity.getApplicationContext(),output,effect,
                        quarter ? guestHeight : guestWidth, quarter ? guestWidth : guestHeight); saved = true;
            } catch (Exception error) { android.util.Log.e("AbyssSymbian","Screenshot",error); }
            finally { if (output != bitmap) output.recycle(); bitmap.recycle(); }
            boolean success = saved; handler.post(() -> screenshotDone(success));
        });
    }
    private void screenshotDone(boolean success) {
        screenshotPending = false; camera.setEnabled(true);
        if (!success && !closed) android.util.Log.e("AbyssSymbian","Screenshot failed: visible="+visible+", focused="+focused
                +", menu="+(menu!=null)+", mapper="+(mapper!=null)+", surface="+surface.isAvailable()
                +", size="+surface.getWidth()+"x"+surface.getHeight());
        if (!closed) Toast.makeText(activity, success ? R.string.screenshot_gallery_saved : R.string.screenshot_failed, Toast.LENGTH_SHORT).show();
    }
    void close() {
        fastForward.cancel();
        if (closed) return; closed = true; clear(); preview.stop();
        devices.close();
        updateShortcuts();
        // A dismissed dialog may deliver its callback after the next session starts.
        if (mapper != null) { mapper.setOnDismissListener(null); mapper.dismiss(); mapper = null; }
        if (menu != null) { menu.setOnDismissListener(null); menu.dismiss(); menu = null; }
        disk.shutdown();
    }
    private final class DeckView extends View {
        private final ru.playsoftware.j2meloader.ui.LcdEffect lcdEffect = new ru.playsoftware.j2meloader.ui.LcdEffect();
        private final RectF lcdBounds = new RectF();
        final TouchDeck deck;
        final SymbianTouch touch = new SymbianTouch(host);
        private final Matrix inverse = new Matrix();
        private final float[] point = new float[2];
        DeckView(ControllerInput.Sink sink) {
            super(activity);
            deck = new TouchDeck(profile, getResources().getDisplayMetrics().density, new TouchDeck.Sink() {
                public void press(int key) { sink.press(key); }
                public void release(int key) { sink.release(key); }
                public void repeat(int key) { sink.repeat(key); }
                public void feedback() { TouchFeedback.perform(DeckView.this, profile.vkFeedback); }
                public void invalidate() { DeckView.this.invalidate(); }
            });
            deck.setTheme(activity);
        }
        void layoutDeck(int w,int h) { cancel(); if (!BuildConfig.HANDHELD_MODE) deck.layout(w,h); }
        void cancel() { touch.cancel(); deck.cancel(); }
        private void point(MotionEvent event, int index) {
            point[0] = event.getX(index) - surface.getLeft();
            point[1] = event.getY(index) - surface.getTop();
            surface.getMatrix().invert(inverse);
            inverse.mapPoints(point);
        }
        private void down(MotionEvent event, int index) {
            int id = event.getPointerId(index);
            if (!BuildConfig.HANDHELD_MODE && deck.down(id,event.getX(index),event.getY(index))) return;
            point(event,index);
            if (point[0] < 0 || point[1] < 0 || point[0] >= surface.getWidth() || point[1] >= surface.getHeight()) return;
            touch.down(id,point[0],point[1],event.getPressure(index),host.touchBounds());
        }
        @Override protected void onDraw(Canvas canvas) {
            if (lcdGeometry != null && lcdGeometry.length == 6) {
                int save = canvas.save();
                canvas.clipRect(BuildConfig.HANDHELD_MODE ? new RectF(0,0,getWidth(),getHeight()) : deck.gameArea(getWidth(),getHeight()));
                canvas.translate(surface.getLeft(), surface.getTop());
                canvas.concat(surface.getMatrix());
                canvas.clipRect(0, 0, surface.getWidth(), surface.getHeight());
                lcdBounds.set(lcdGeometry[0], lcdGeometry[1], lcdGeometry[2], lcdGeometry[3]);
                lcdEffect.draw(canvas, lcdBounds, lcdGeometry[4], lcdGeometry[5], 0,
                        profile.screenEffect, profile.screenEffectIntensity);
                canvas.restoreToCount(save);
            }
            if (!BuildConfig.HANDHELD_MODE) deck.draw(canvas);
        }
        @Override public boolean onTouchEvent(MotionEvent event) {
            if (closed || !visible || !focused || menu != null || mapper != null) { cancel(); return true; }
            int index = event.getActionIndex(), id = event.getPointerId(index);
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    cancel(); down(event,index); break;
                case MotionEvent.ACTION_POINTER_DOWN: down(event,index); break;
                case MotionEvent.ACTION_MOVE:
                    for (int i=0;i<event.getPointerCount();i++) {
                        if (!BuildConfig.HANDHELD_MODE) deck.move(event.getPointerId(i),event.getX(i),event.getY(i));
                        point(event,i);
                        touch.move(event.getPointerId(i),point[0],point[1],event.getPressure(i));
                    }
                    break;
                case MotionEvent.ACTION_POINTER_UP: touch.up(id); deck.up(id); break;
                case MotionEvent.ACTION_UP: touch.up(id); deck.up(id); cancel(); break;
                case MotionEvent.ACTION_CANCEL: cancel(); break;
            }
            return true;
        }
    }
}
