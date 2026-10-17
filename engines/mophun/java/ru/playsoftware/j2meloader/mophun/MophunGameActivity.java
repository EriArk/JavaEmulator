package ru.playsoftware.j2meloader.mophun;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.input.InputManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.AtomicFile;
import android.util.SparseIntArray;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Gravity;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.Toast;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;
import ru.playsoftware.j2meloader.applist.AppItem;
import ru.playsoftware.j2meloader.applist.IconArtUtils;
import ru.playsoftware.j2meloader.appsdb.AppDatabase;
import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.config.ProfileModel;
import ru.playsoftware.j2meloader.config.ProfilesManager;
import ru.playsoftware.j2meloader.input.ControllerInput;
import ru.playsoftware.j2meloader.input.ControllerMapperView;
import ru.playsoftware.j2meloader.input.GameMenuDialog;
import ru.playsoftware.j2meloader.input.TouchDeck;
import ru.playsoftware.j2meloader.BuildConfig;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.util.Screenshots;
import javax.microedition.lcdui.keyboard.KeyMapper;
import wolphun.Screen;

public final class MophunGameActivity extends Activity implements InputManager.InputDeviceListener {
    @Override protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(ru.playsoftware.j2meloader.util.AppearanceContext.wrap(base));
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService disk = Executors.newSingleThreadExecutor();
    private MophunSession session;
    private ControllerInput input;
    private SparseIntArray mappings;
    private ProfileModel profile;
    private File directory;
    private File libraryRoot;
    private String title;
    private FrameLayout root;
    private FrameView frameView;
    private MophunTouchControls touch;
    private ImageButton screenshotButton;
    private boolean screenshotPending;
    private ControllerMapperView mapper;
    private GameMenuDialog menu;
    private ru.playsoftware.j2meloader.input.MapperDialog settingsMapper;
    private ru.playsoftware.j2meloader.input.SettingsPreviewBar previewBar;
    private InputManager inputManager;
    private boolean visible, selectDown, selectHeld, captured;
    private final Runnable holdSelect = () -> { if (selectDown) { selectHeld = true; showControls(); } };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(5894);
        try {
            libraryRoot = new File(Config.getEmulatorDir());
            directory = MophunStore.directory(libraryRoot, getIntent().getStringExtra("id"));
            JSONObject metadata = MophunStore.metadata(directory);
            title = getIntent().getStringExtra("title");
            if (title == null) title = metadata.optString("title", "Mophun game");
            File configDir = new File(Config.getConfigsDir(), directory.getName());
            if (!configDir.isDirectory() && !configDir.mkdirs()) throw new java.io.IOException("Cannot create controls directory");
            profile = ProfilesManager.loadConfig(configDir);
            if (profile == null) profile = new ProfileModel(configDir);
            ru.playsoftware.j2meloader.config.SettingsTrial.claim(profile);
            if (profile.touchLayout == null || profile.touchLayout != TouchDeck.GAMEPAD) profile.touchLayout = TouchDeck.PHONE;
            applyOrientation();
            profile.ensureKeyMappingProfiles();
            mappings = KeyMapper.resolveMappings(profile.getActiveKeyMappings());
            int model = MophunCompatibility.model(metadata);
            if (model != 0 && model != 2 && model != 5) throw new java.io.IOException("Invalid phone model");
            session = new MophunSession(new File(directory, MophunStore.EXECUTABLE),
                    new File(Config.getDataDir(), directory.getName()), model,
                    model == 2 ? 128 : 101, model == 2 ? 160 : 80, getCacheDir());
            MophunInput sink = new MophunInput(session::screen);
            input = new ControllerInput(key -> mappings.get(key, 0), sink);
            inputManager = getSystemService(InputManager.class);
            inputManager.registerInputDeviceListener(this, handler);
            root = new FrameLayout(this);
            frameView = new FrameView();
            root.addView(frameView);
            if (!BuildConfig.HANDHELD_MODE) {
                touch = new MophunTouchControls(profile, getResources().getDisplayMetrics().density,
                        ViewConfiguration.get(this).getScaledTouchSlop(), sink.newSource(),
                        () -> frameView.invalidate(), () -> frameView.performClick(),
                        () -> ru.playsoftware.j2meloader.input.TouchFeedback.perform(frameView, profile.vkFeedback));
                touch.deck.setTheme(this);
                ImageButton menuButton = new ImageButton(this);
                menuButton.setId(R.id.game_menu_button);
                menuButton.setImageResource(R.drawable.ic_baseline_tune_24);
                menuButton.setImageTintList(android.content.res.ColorStateList.valueOf(getColor(ru.playsoftware.j2meloader.R.color.text_primary)));
                menuButton.setBackgroundResource(R.drawable.bg_quick_setting_button);
                menuButton.setPadding(dp(12), dp(12), dp(12), dp(12));
                menuButton.setContentDescription("Game menu");
                menuButton.setTooltipText("Game menu");
                menuButton.setOnClickListener(v -> showMenu());
                root.addView(menuButton, new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP | Gravity.END));
            }
            screenshotButton = new ImageButton(this);
            screenshotButton.setId(R.id.game_screenshot_button);
            screenshotButton.setImageResource(R.drawable.ic_action_screenshot);
            screenshotButton.setImageTintList(android.content.res.ColorStateList.valueOf(getColor(ru.playsoftware.j2meloader.R.color.text_primary)));
            screenshotButton.setBackgroundResource(R.drawable.bg_quick_setting_button);
            screenshotButton.setPadding(dp(12), dp(12), dp(12), dp(12));
            screenshotButton.setContentDescription(getString(R.string.capture_screenshot));
            screenshotButton.setTooltipText(getString(R.string.capture_screenshot));
            screenshotButton.setOnClickListener(v -> takeScreenshot());
            root.addView(screenshotButton, new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP | Gravity.START));
            updateScreenshotButton();
            setContentView(root);
            previewBar = new ru.playsoftware.j2meloader.input.SettingsPreviewBar(this, this::keepPreview, this::undoPreview);
            FrameLayout.LayoutParams previewLayout = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
            previewLayout.topMargin = dp(54);
            root.addView(previewBar, previewLayout);
            if (ProfilesManager.isPreview(profile)) previewBar.start();
            if (getIntent().getBooleanExtra("controls", false)) showControls();
            session.start();
        } catch (Exception failure) {
            Toast.makeText(this, failure.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void updateScreenshotButton() {
        if (screenshotButton == null) return;
        screenshotButton.setVisibility(Screenshots.isButtonVisible(this) && mapper == null ? View.VISIBLE : View.GONE);
        screenshotButton.setEnabled(!screenshotPending);
    }

    private void takeScreenshot() {
        if (screenshotPending || !visible || isFinishing() || session == null) return;
        Screen screen = session.screen();
        if (screen == null) return;
        int[] pixels = new int[screen.pw * screen.ph];
        if (!screen.copyPresented(pixels)) return;
        screenshotPending = true;
        updateScreenshotButton();
        // Copy the presented frame before encoding; gameplay continues on its worker.
        disk.execute(() -> {
            Bitmap bitmap = Bitmap.createBitmap(pixels, screen.pw, screen.ph, Bitmap.Config.ARGB_8888);
            int result = R.string.screenshot_gallery_saved;
            try { Screenshots.save(getApplicationContext(), bitmap); }
            catch (Exception error) {
                android.util.Log.w("Mophun", "Screenshot", error);
                result = R.string.screenshot_failed;
            } finally { bitmap.recycle(); }
            int message = result;
            handler.post(() -> {
                screenshotPending = false;
                if (isDestroyed()) return;
                updateScreenshotButton();
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void applyOrientation() {
        setRequestedOrientation(BuildConfig.HANDHELD_MODE || profile.orientation == 3
                ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE : profile.orientation == 2
                ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
    }

    private void clearInput() {
        if (touch != null) touch.cancel();
        if (input != null) input.clear();
    }

    private void pauseState() {
        boolean paused = !visible || isFinishing() || mapper != null || menu != null
                || (settingsMapper != null && settingsMapper.isShowing()) || !hasWindowFocus();
        if (paused) clearInput();
        if (session != null) session.pause(paused);
        if (previewBar != null) { if (paused) previewBar.suspend(); else previewBar.resume(); }
    }

    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (!focused) cancelSelect();
        else getWindow().getDecorView().setSystemUiVisibility(5894);
        pauseState();
    }

    @Override public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        cancelSelect();
        clearInput();
        if (menu != null) menu.dismiss();
        if (frameView != null) frameView.requestLayout();
    }

    private boolean saveProfile() {
        if (ProfilesManager.isPreview(profile)) return true;
        AtomicFile file = new AtomicFile(new File(profile.dir, "config.json"));
        FileOutputStream stream = null;
        try {
            stream = file.startWrite();
            stream.write(new GsonBuilder().setPrettyPrinting().create().toJson(profile).getBytes(StandardCharsets.UTF_8));
            file.finishWrite(stream);
            return true;
        } catch (Exception failure) {
            file.failWrite(stream);
            Toast.makeText(this, "Could not save controls", Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private void showControls() {
        if (mapper != null || session == null) return;
        input.clear();
        mapper = new ControllerMapperView(this, title, profile.keyMappingProfiles, profile.activeKeyMappingProfile,
                new ControllerMapperView.Listener() {
                    public void save(ArrayList<ProfileModel.KeyMappingProfile> profiles, int active) {
                        ProfilesManager.beginPreview(profile);
                        profile.keyMappingProfiles = profiles;
                        profile.setActiveKeyMappingProfile(active);
                        applyPreview();
                        closeControls();
                        previewBar.start();
                    }
                    public void cancel() { closeControls(); }
                });
        mapper.setSaveLabel("Try changes");
        root.addView(mapper, new FrameLayout.LayoutParams(-1, -1));
        updateScreenshotButton();
        pauseState();
    }

    private void closeControls() {
        input.clear();
        root.removeView(mapper);
        mapper = null;
        updateScreenshotButton();
        pauseState();
    }

    private void showMenu() {
        if (menu != null || mapper != null || session == null) return;
        cancelSelect();
        input.clear();
        menu = new GameMenuDialog(this, title);
        menu.setOnDismissListener(d -> { menu = null; pauseState(); });
        pauseState();
        menu.show();
        showMenuPage();
    }

    private void showMenuPage() {
        new ru.playsoftware.j2meloader.input.GameMenuPages(menu,
                new ru.playsoftware.j2meloader.input.GameMenuPages.Host() {
            public void display() { settingsPages().display(); }
            public void controls() { settingsPages().controls(); }
            public void more() { showMorePage(); }
            public boolean hasPreview() { return ProfilesManager.isPreview(profile); }
            public void keep() { keepPreview(); }
            public void undo() { undoPreview(); }
            public void restart() { restartGame(); }
            public void exit() { leaveGame(); }
        }, this::takeScreenshot).home();
    }

    private void showMorePage() {
        menu.page("More");
        menu.setBackAction(this::showMenuPage);
        menu.action(getString(R.string.on_screen_shortcuts), R.drawable.ic_baseline_tune_24,
                () -> ru.playsoftware.j2meloader.util.GameShortcuts.page(this, menu, false,
                        this::updateScreenshotButton, this::showMorePage));
    }

    private ru.playsoftware.j2meloader.config.GameSettingsPages settingsPages() {
        return new ru.playsoftware.j2meloader.config.GameSettingsPages(menu, profile, true, false,
                new ru.playsoftware.j2meloader.config.GameSettingsPages.Host() {
                    public void preview(ProfileModel draft) {
                        ProfilesManager.beginPreview(profile);
                        ProfilesManager.copyInto(draft, profile);
                        applyPreview();
                        if (menu != null) menu.dismiss();
                        previewBar.start();
                    }
                    public void mapping(ProfileModel draft, Runnable back) {
                        menu.hide();
                        settingsMapper = new ru.playsoftware.j2meloader.input.MapperDialog(MophunGameActivity.this, title, draft, () -> {
                            if (!isFinishing() && menu != null) { menu.show(); back.run(); }
                        });
                        settingsMapper.show();
                    }
                    public void advanced() { }
                    public void back() { showMenuPage(); }
                });
    }
    private void applyPreview() {
        clearInput(); mappings = KeyMapper.resolveMappings(profile.getActiveKeyMappings());
        applyOrientation();
        if (touch != null) touch.layout(frameView.getWidth(), frameView.getHeight());
        frameView.invalidate();
    }
    private void keepPreview() {
        if (!ProfilesManager.keepPreview(profile)) {
            Toast.makeText(this, "Could not save settings", Toast.LENGTH_LONG).show(); return;
        }
        previewBar.stop();
        if (menu != null) showMenuPage();
    }
    private void undoPreview() {
        ProfilesManager.undoPreview(profile); applyPreview(); previewBar.stop();
        if (menu != null) showMenuPage();
    }

    private void changeTouch(Runnable change, Runnable rollback) {
        clearInput();
        change.run();
        if (!saveProfile()) rollback.run();
        touch.layout(frameView.getWidth(), frameView.getHeight());
        frameView.invalidate();
        showTouchPage();
    }

    private void showTouchPage() {
        menu.page("Touch controls");
        menu.choice("Layout", new String[]{"Phone", "Gamepad"}, profile.touchLayout, index -> {
            int before = profile.touchLayout;
            changeTouch(() -> profile.touchLayout = index, () -> profile.touchLayout = before);
        });
        menu.choice("Size", new String[]{"Standard", "Large", "Extra large"}, Math.max(0, Math.min(2, profile.touchSize)), index -> {
            int before = profile.touchSize;
            changeTouch(() -> profile.touchSize = index, () -> profile.touchSize = before);
        });
        menu.opacity(Math.max(80, Math.min(100, profile.touchOpacity)), value -> {
            int before = profile.touchOpacity;
            changeTouch(() -> profile.touchOpacity = value, () -> profile.touchOpacity = before);
        });
        boolean landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        float reach = landscape ? profile.touchLandscapeReach : profile.touchPortraitReach;
        menu.choice("Position", new String[]{"Bottom", "Raised"}, reach >= .5f ? 1 : 0, index -> {
            float before = landscape ? profile.touchLandscapeReach : profile.touchPortraitReach;
            changeTouch(() -> { if (landscape) profile.touchLandscapeReach = index; else profile.touchPortraitReach = index; },
                    () -> { if (landscape) profile.touchLandscapeReach = before; else profile.touchPortraitReach = before; });
        });
        menu.action("Back", android.R.drawable.ic_media_previous, this::showMenuPage);
    }

    private boolean restarting;
    private void restartGame() {
        if (restarting || session == null || isFinishing()) return;
        restarting = true;
        if (ProfilesManager.isPreview(profile)) undoPreview();
        clearInput(); previewBar.stop(); visible = false;
        menu.setOnDismissListener(null); menu.page("Restarting game");
        menu.setCancelable(false);
        session.stop();
        disk.execute(() -> {
            try {
                session.close();
                runOnUiThread(() -> { if (!isFinishing() && !isDestroyed()) recreate(); });
            } catch (Exception error) {
                android.util.Log.e("Mophun", "Restart", error);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    menu.page("Could not restart game");
                    menu.message("The previous session did not stop. Exit before trying again.");
                    menu.action("Exit game", android.R.drawable.ic_menu_close_clear_cancel, this::leaveGame);
                });
            }
        });
    }

    private void leaveGame() {
            visible = false;
            session.stop();
            if (!getIntent().getBooleanExtra("externalLaunch", false))
                startActivity(android.content.Intent.makeRestartActivityTask(new android.content.ComponentName(
                        this, ru.playsoftware.j2meloader.MainActivity.class)));
            finish();
            menu.dismiss();
    }

    private void cancelSelect() {
        handler.removeCallbacks(holdSelect);
        selectDown = false;
        selectHeld = false;
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int key = event.getKeyCode();
        if (!visible || isFinishing() || menu != null) return super.dispatchKeyEvent(event);
        if (key == KeyEvent.KEYCODE_BUTTON_SELECT && selectDown && event.getAction() == KeyEvent.ACTION_UP) {
            handler.removeCallbacks(holdSelect);
            if (!selectHeld && mapper == null && menu == null && input != null) {
                int device = event.getDeviceId();
                input.key(device, key, true, false);
                handler.postDelayed(() -> { if (input != null) input.key(device, key, false, false); }, 80);
            }
            selectDown = false;
            selectHeld = false;
            return true;
        }
        if (mapper != null) {
            if (!mapper.handleKey(event)) super.dispatchKeyEvent(event);
            return true;
        }
        if (key == KeyEvent.KEYCODE_BACK || key == KeyEvent.KEYCODE_MENU) {
            if (event.getAction() == KeyEvent.ACTION_UP) showMenu();
            return true;
        }
        if (key == KeyEvent.KEYCODE_BUTTON_SELECT && input != null) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                selectDown = true;
                handler.postDelayed(holdSelect, 350);
            }
            return true;
        }
        if (input != null && mappings.get(key, 0) != 0) {
            input.key(event.getDeviceId(), key, event.getAction() == KeyEvent.ACTION_DOWN, event.getRepeatCount() > 0);
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (!visible || isFinishing() || menu != null) return super.dispatchGenericMotionEvent(event);
        if (mapper != null) { mapper.captureAxes(event); return true; }
        if (input != null && event.isFromSource(InputDevice.SOURCE_JOYSTICK)) {
            input.axes(event.getDeviceId(), event.getAxisValue(MotionEvent.AXIS_X), event.getAxisValue(MotionEvent.AXIS_Y),
                    event.getAxisValue(MotionEvent.AXIS_HAT_X), event.getAxisValue(MotionEvent.AXIS_HAT_Y),
                    event.getAxisValue(MotionEvent.AXIS_LTRIGGER), event.getAxisValue(MotionEvent.AXIS_RTRIGGER));
            return true;
        }
        return super.dispatchGenericMotionEvent(event);
    }

    @Override protected void onPause() {
        visible = false;
        cancelSelect();
        if (input != null) input.clear();
        pauseState();
        captureArtwork();
        super.onPause();
    }
    @Override protected void onResume() {
        super.onResume();
        visible = true;
        updateScreenshotButton();
        pauseState();
        if (frameView != null) frameView.postInvalidateOnAnimation();
    }
    @Override protected void onDestroy() {
        if (previewBar != null) previewBar.stop();
        if (settingsMapper != null) { settingsMapper.setOnDismissListener(null); settingsMapper.dismiss(); }
        visible = false;
        if (menu != null) {
            menu.setOnDismissListener(null);
            menu.dismiss();
            menu = null;
        }
        handler.removeCallbacksAndMessages(null);
        if (touch != null) touch.cancel();
        if (inputManager != null) inputManager.unregisterInputDeviceListener(this);
        if (input != null) input.clear();
        if (session != null) {
            session.stop();
            disk.execute(() -> { try { session.close(); } catch (Exception error) { android.util.Log.w("Mophun", "Shutdown", error); } });
        }
        disk.shutdown();
        super.onDestroy();
    }
    public void onInputDeviceAdded(int id) { }
    public void onInputDeviceChanged(int id) { if (input != null) input.removeDevice(id); }
    public void onInputDeviceRemoved(int id) { if (input != null) input.removeDevice(id); }

    private void captureArtwork() {
        if (session == null || directory == null || captured || new File(directory, "cover.png").exists()) return;
        Screen screen = session.screen();
        if (screen == null || screen.flips < 20) return;
        int[] pixels = new int[screen.pw * screen.ph];
        if (!screen.copyPresented(pixels)) return;
        captured = true;
        File gameDir = directory;
        disk.execute(() -> {
            Bitmap bitmap = Bitmap.createBitmap(pixels, screen.pw, screen.ph, Bitmap.Config.ARGB_8888);
            Bitmap icon = IconArtUtils.createFallback(title, 128, 128, bitmap);
            try {
                synchronized (ru.playsoftware.j2meloader.catalog.LibraryImporter.class) {
                    if (!gameDir.isDirectory()) return;
                    try (AppDatabase db = AppDatabase.open(getApplicationContext(), libraryRoot.getAbsolutePath())) {
                        AppItem item = db.appItemDao().getByPath(gameDir.getName());
                        if (item == null) return;
                        if (!new File(gameDir, "cover.png").exists()) {
                            writePng(new File(gameDir, "cover.png"), bitmap);
                            item.setCoverPathExt("cover.png");
                        }
                        if (!new File(gameDir, "icon.png").exists()) {
                            writePng(new File(gameDir, "icon.png"), icon);
                            item.setImagePathExt("icon.png");
                        }
                        db.appItemDao().update(item);
                    }
                }
            } catch (Exception failure) { android.util.Log.w("Mophun", "Artwork capture", failure); }
            finally { bitmap.recycle(); icon.recycle(); }
        });
    }

    private static void writePng(File destination, Bitmap bitmap) throws Exception {
        AtomicFile file = new AtomicFile(destination);
        FileOutputStream stream = file.startWrite();
        try {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) throw new java.io.IOException("PNG encoding failed");
            file.finishWrite(stream);
        } catch (Exception error) { file.failWrite(stream); throw error; }
    }

    private final class FrameView extends View {
        private final Paint paint = new Paint();
        private final Rect destination = new Rect();
        private Bitmap bitmap;
        private int[] pixels;
        private boolean errorShown;
        FrameView() {
            super(MophunGameActivity.this);
            paint.setFilterBitmap(false);
            setOnClickListener(v -> showMenu());
        }
        @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            if (touch != null) touch.layout(w, h);
        }
        @Override public boolean onTouchEvent(MotionEvent event) {
            if (!visible || !hasWindowFocus() || mapper != null || menu != null || isFinishing()) return true;
            if (touch != null) return touch.touch(event);
            if (event.getAction() == MotionEvent.ACTION_UP) performClick();
            return true;
        }
        @Override protected void onDraw(Canvas canvas) {
            canvas.drawColor(0xff101417);
            Screen screen = session.screen();
            if (screen != null) {
                if (bitmap == null) {
                    bitmap = Bitmap.createBitmap(screen.pw, screen.ph, Bitmap.Config.ARGB_8888);
                    pixels = new int[screen.pw * screen.ph];
                }
                if (screen.copyPresented(pixels)) {
                    bitmap.setPixels(pixels, 0, screen.pw, 0, 0, screen.pw, screen.ph);
                    RectF area = touch == null ? new RectF(0, 0, getWidth(), getHeight()) : touch.gameArea();
                    int rotation = Math.floorMod(profile.screenRotation, 360);
                    boolean quarter = rotation == 90 || rotation == 270;
                    float availableW = quarter ? area.height() : area.width();
                    float availableH = quarter ? area.width() : area.height();
                    float scale = profile.screenScaleType == 0 ? 1 : Math.min(availableW / screen.pw, availableH / screen.ph);
                    int width = Math.round(profile.screenScaleType == 2 ? availableW : screen.pw * scale);
                    int height = Math.round(profile.screenScaleType == 2 ? availableH : screen.ph * scale);
                    canvas.save(); canvas.clipRect(area);
                    canvas.translate(area.centerX(), area.centerY()); canvas.rotate(rotation);
                    destination.set(-width / 2, -height / 2, width - width / 2, height - height / 2);
                    paint.setFilterBitmap(profile.screenFilter);
                    canvas.drawBitmap(bitmap, null, destination, paint);
                    canvas.restore();
                }
            }
            if (touch != null) touch.draw(canvas);
            if (session.error() != null && !errorShown) {
                errorShown = true;
                Toast.makeText(getContext(), session.error().toString(), Toast.LENGTH_LONG).show();
            }
            if (visible && !session.finished()) postInvalidateOnAnimation();
        }
        @Override protected void onWindowVisibilityChanged(int visibility) {
            super.onWindowVisibilityChanged(visibility);
            if (visibility == VISIBLE) postInvalidateOnAnimation();
        }
        @Override protected void onDetachedFromWindow() {
            if (touch != null) touch.cancel();
            super.onDetachedFromWindow();
        }
    }
}
