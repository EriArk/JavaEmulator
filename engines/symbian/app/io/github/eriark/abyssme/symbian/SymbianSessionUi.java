package io.github.eriark.abyssme.symbian;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.*;
import android.os.*;
import android.util.SparseIntArray;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.file.Files;
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
        void pause(boolean paused);
        void display(int scale);
        void frameRate(int fps);
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
    private GameMenuDialog menu;
    private MapperDialog mapper;
    private boolean closed, visible = true, focused = true, selectDown, screenshotPending;
    private int selectDevice;
    private final Runnable holdSelect = this::heldSelect;
    private void heldSelect() { if (selectDown) { selectDown = false; showMapping(profile); } }

    SymbianSessionUi(Activity activity, TextureView surface, String system, long uid, String title, Host host) throws IOException {
        this.activity = activity; this.surface = surface; this.title = title; this.host = host;
        if (!system.matches("[0-9a-f]{64}|legacy") || uid <= 0 || uid > 0xffffffffL) throw new IOException("Invalid game identity");
        File directory = new File(PhoneBackup.profiles(activity.getFilesDir(), system), Long.toString(uid));
        Files.createDirectories(directory.toPath());
        ProfileModel loaded = ProfilesManager.loadConfig(directory);
        profile = loaded == null ? new ProfileModel(directory) : loaded;
        if (loaded == null) {
            SparseIntArray defaults = KeyMapper.getDefaultKeyMap();
            defaults.put(KeyEvent.KEYCODE_BUTTON_X, '5'); defaults.put(KeyEvent.KEYCODE_BUTTON_Y, '0');
            defaults.put(KeyEvent.KEYCODE_BUTTON_B, -7); defaults.put(KeyEvent.KEYCODE_BUTTON_START, -6);
            profile.keyMappingProfiles.add(0, new ProfileModel.KeyMappingProfile("Symbian", defaults));
            profile.activeKeyMappingProfile = 0;
        }
        profile.ensureKeyMappingProfiles();
        if (profile.touchLayout == null || profile.touchLayout != TouchDeck.GAMEPAD) profile.touchLayout = TouchDeck.PHONE;
        mappings = KeyMapper.resolveMappings(profile.getActiveKeyMappings());
        keys = new SymbianInput(host); input = new ControllerInput(key -> mappings.get(key, 0), keys.source());
        root = new FrameLayout(activity) {
            @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) { super.onSizeChanged(w,h,oldw,oldh); post(() -> SymbianSessionUi.this.layout()); }
        };
        root.setBackgroundColor(Color.BLACK); root.addView(surface);
        deckView = new DeckView(keys.source()); root.addView(deckView, new FrameLayout.LayoutParams(-1,-1));
        ImageButton settings = icon("Game menu", R.drawable.ic_baseline_tune_24, this::showMenu);
        settings.setId(R.id.game_menu_button);
        root.addView(settings, new FrameLayout.LayoutParams(dp(48),dp(48), Gravity.TOP | Gravity.END));
        camera = icon("Screenshot", R.drawable.ic_action_screenshot, this::screenshot);
        camera.setId(R.id.game_screenshot_button);
        root.addView(camera, new FrameLayout.LayoutParams(dp(48),dp(48), Gravity.TOP | Gravity.START));
        preview = new SettingsPreviewBar(activity, this::keep, this::undo);
        FrameLayout.LayoutParams previewLayout = new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);
        previewLayout.topMargin = dp(50); root.addView(preview, previewLayout);
        gameChrome = BuildConfig.HANDHELD_MODE ? null : new GameChrome(root, profile);
        apply();
    }
    private int dp(int n) { return Math.round(n * activity.getResources().getDisplayMetrics().density); }
    private ImageButton icon(String label, int resource, Runnable action) {
        ImageButton button = new ImageButton(activity); button.setImageResource(resource);
        button.setBackgroundResource(R.drawable.bg_quick_setting_button);
        button.setImageTintList(android.content.res.ColorStateList.valueOf(activity.getColor(ru.playsoftware.j2meloader.R.color.accent)));
        button.setPadding(dp(12),dp(12),dp(12),dp(12)); button.setContentDescription(label); button.setTooltipText(label);
        button.setOnClickListener(v -> action.run()); return button;
    }
    int scale() { return Math.max(0, Math.min(2, profile.screenScaleType)); }
    void apply() {
        TouchAppearance.shortcuts(root, profile);
        clear(); mappings = KeyMapper.resolveMappings(profile.getActiveKeyMappings());
        activity.setRequestedOrientation(BuildConfig.HANDHELD_MODE || profile.orientation == 3 ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                : profile.orientation == 2 ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
        host.display(scale()); host.frameRate(profile.getNativeFpsLimit()); layout(); updateShortcuts();
    }
    private void layout() {
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
        handler.removeCallbacks(holdSelect); selectDown = false;
        input.clear(); if (deckView != null) deckView.cancel(); keys.clear();
    }
    void visible(boolean value) { visible = value; if (value) updateShortcuts(); pause(); }
    private void updateShortcuts() {
        camera.setVisibility(Screenshots.isButtonVisible(activity) ? View.VISIBLE : View.GONE);
        if (gameChrome != null) gameChrome.update(!closed && visible && focused && menu == null && mapper == null,
                false, Screenshots.isButtonVisible(activity));
    }
    void focused(boolean value) { focused = value; pause(); }
    private void pause() {
        updateShortcuts();
        boolean paused = closed || !visible || !focused || menu != null || mapper != null;
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
        if (mappings.get(code, 0) == 0) return false;
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
        menu = new GameMenuDialog(activity, title);
        menu.setOnDismissListener(d -> { menu = null; if (!closed) pause(); }); menu.show(); pause(); home();
    }
    private void home() {
        new GameMenuPages(menu, new GameMenuPages.Host() {
            public void display() { displayPage(); }
            public void controls() { pages().controls(SymbianSessionUi.this::home); }
            public void more() { morePage(); }
            public boolean hasPreview() { return ProfilesManager.isPreview(profile); }
            public void keep() { SymbianSessionUi.this.keep(); }
            public void undo() { SymbianSessionUi.this.undo(); }
            public void exit() { close(); host.leave(); }
            public void restart() {
                if (ProfilesManager.isPreview(profile)) undo();
                host.restart();
            }
        }, this::screenshot).home();
    }
    private void morePage() {
        menu.page("More");
        menu.setBackAction(this::home);
        menu.action("Frame rate limit: " + GameSettingsPages.frameRateLabel(profile.getNativeFpsLimit()),
                R.drawable.ic_baseline_tune_24, () -> pages().frameRate(this::morePage));
        menu.action(activity.getString(R.string.on_screen_shortcuts), R.drawable.ic_baseline_tune_24,
                () -> ru.playsoftware.j2meloader.util.GameShortcuts.page(activity, menu, false,
                        this::updateShortcuts, this::morePage));
    }
    private void displayPage() {
        ProfileModel draft = ProfilesManager.copy(profile); menu.page("Display");
        GameSettingsPages.frameRateChoice(menu, draft, false);
        menu.choice("Picture size", new String[]{"Original", "Fit", "Stretch"}, scale(), i -> draft.screenScaleType = i);
        menu.choice("Image rotation", new String[]{"0\u00b0","90\u00b0","180\u00b0","270\u00b0"},
                Math.floorMod(draft.screenRotation / 90, 4), i -> draft.screenRotation = i * 90);
        if (!BuildConfig.HANDHELD_MODE) menu.choice("Orientation", new String[]{"Auto","Portrait","Landscape"},
                Math.max(0,Math.min(2,draft.orientation - 1)), i -> draft.orientation = i + 1);
        menu.action("Try changes", android.R.drawable.ic_media_play, () -> trySettings(draft)); menu.setBackAction(this::home);
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
        });
        mapper.show(); pause();
    }
    private void trySettings(ProfileModel draft) {
        ProfilesManager.beginPreview(profile); ProfilesManager.copyInto(draft,profile); apply();
        if (menu != null) menu.dismiss(); preview.start(); pause();
    }
    private void keep() {
        if (!ProfilesManager.keepPreview(profile)) { Toast.makeText(activity,"Could not save settings",Toast.LENGTH_LONG).show(); return; }
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
        disk.execute(() -> {
            Bitmap output = bitmap; boolean saved = false;
            try {
                if (rotation != 0) { Matrix matrix = new Matrix(); matrix.postRotate(rotation);
                    output = Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),matrix,false); }
                Screenshots.save(activity.getApplicationContext(),output); saved = true;
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
        if (closed) return; closed = true; clear(); preview.stop();
        updateShortcuts();
        // A dismissed dialog may deliver its callback after the next session starts.
        if (mapper != null) { mapper.setOnDismissListener(null); mapper.dismiss(); mapper = null; }
        if (menu != null) { menu.setOnDismissListener(null); menu.dismiss(); menu = null; }
        disk.shutdown();
    }
    private final class DeckView extends View {
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
        @Override protected void onDraw(Canvas canvas) { if (!BuildConfig.HANDHELD_MODE) deck.draw(canvas); }
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
