/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.handheld;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.util.SparseIntArray;
import android.view.*;
import android.widget.*;
import java.io.*;
import javax.microedition.lcdui.keyboard.KeyMapper;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.config.*;
import ru.playsoftware.j2meloader.input.*;
import ru.playsoftware.j2meloader.mophun.MophunGpuView;
import ru.playsoftware.j2meloader.ui.*;
import ru.playsoftware.j2meloader.util.AppearanceContext;

public abstract class HandheldActivity extends Activity {
    private static HandheldActivity activeSession;
    private boolean started, resumed, failed;
    private ProfileModel profile;
    private FrameLayout root;
    private HandheldScreen screen;
    private ControllerInput physical;
    private SparseIntArray mappings;
    private DeviceControls devices;
    private GameMenuDialog menu;
    private SettingsPreviewBar preview;
    private FastForwardControl fast;
    private GameChrome chrome;


    protected abstract ControllerInput.Sink keys();
    protected abstract void setSpeed(int percent);
    protected abstract void pauseCore(boolean paused);
    protected abstract void stopCore();
    protected void more(GameMenuDialog dialog) {}
    protected abstract boolean frame(int[] pixels);
    protected boolean artworkReady() { return true; }
    protected void configureProfile(ProfileModel profile) {}
    protected abstract int width();
    protected int displayWidth() { return width(); }
    protected abstract int height();
    protected abstract boolean exited();
    protected void gameTap(float x,float y) {}
    protected String error() { return null; }

    @Override protected void attachBaseContext(Context base) { super.attachBaseContext(AppearanceContext.wrap(base)); }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        AppPalette.apply(this);
        getWindow().getDecorView().setSystemUiVisibility(5894);
    }
    protected void prepareSession(File config) throws IOException {
        if (!config.isDirectory() && !config.mkdirs()) throw new IOException("Cannot create game configuration");
        if (activeSession != null && activeSession != this) {
            HandheldActivity previous = activeSession;
            previous.closeSession();
            previous.finish();
        }
        profile = ProfilesManager.loadConfig(config);
        if (profile == null) profile = new ProfileModel(config);
        configureProfile(profile);
    }
    protected void sessionStarted(String title) {
        started = true;
        activeSession = this;
        setTitle(title);
        setup();
    }
    private void setup() {
        ControllerInput.Sink keys = keys();
        physical = new ControllerInput(code -> mappings.get(code, 0), keys);
        root = new FrameLayout(this);
        screen = new HandheldScreen(this, profile, keys);
        root.addView(screen);
        ImageButton button = new ImageButton(this);
        button.setId(R.id.game_menu_button);
        button.setImageResource(R.drawable.ic_baseline_tune_24);
        button.setContentDescription(getString(R.string.game_menu_title));
        button.setBackgroundResource(R.drawable.bg_quick_setting_button);
        button.setOnClickListener(v -> openMenu());
        int size = Math.round(48 * getResources().getDisplayMetrics().density);
        root.addView(button, new FrameLayout.LayoutParams(size, size, Gravity.TOP | Gravity.END));
        fast = new FastForwardControl(root, profile, () -> true, this::setSpeed, fps -> screen.fps = fps, false);
        physical.shortcut(fast::binding, fast::button);
        chrome = new GameChrome(root, profile);
        fast.changed(chrome::render);
        preview = new SettingsPreviewBar(this, this::keep, this::undo);
        preview.attachTo(root);
        devices = new DeviceControls(this, this::releaseKeys, () -> screen.layoutControls(devices.connected()), this::openMenu);
        setContentView(GameArea.wrap(root));
        applySettings();
        syncPause();
    }

    private void applySettings() {
        releaseKeys();
        profile.ensureKeyMappingProfiles();
        SparseIntArray overrides = profile.getActiveKeyMappings();
        mappings = KeyMapper.resolveMappings(overrides);
        if (mappings.indexOfKey(KeyEvent.KEYCODE_DEL) < 0) mappings.put(KeyEvent.KEYCODE_DEL, javax.microedition.lcdui.Canvas.KEY_CLEAR);
        setRequestedOrientation(profile.orientation == 3 ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                : profile.orientation == 2 ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
        screen.layoutControls(devices.connected());
        fast.configure();
        setSpeed(profile.getEmulationSpeed());
        screen.fps = profile.getNativeFpsLimit();
        boolean enhanced = MophunGpuView.enabled(profile);
        if (enhanced && screen.gpu == null) {
            screen.gpu = new MophunGpuView(this, screen::invalidate, () -> {
                screen.invalidate();
                Toast.makeText(this, R.string.game_enhancements_unavailable, Toast.LENGTH_LONG).show();
            });
            root.addView(screen.gpu, 0, new FrameLayout.LayoutParams(-1, -1));
        }
        if (screen.gpu != null) {
            screen.gpu.active(enhanced && resumed);
            screen.gpu.setVisibility(enhanced ? View.VISIBLE : View.GONE);
        }
        chrome.update(menu == null, false, false);
        screen.invalidate();
    }

    private void openMenu() {
        if (!started || failed || menu != null || isFinishing()) return;
        menu = new GameMenuDialog(this, getTitle().toString());
        menu.useEdgePanel();
        menu.setOnDismissListener(d -> {
            menu = null;
            devices.resume();
            syncPause();
        });
        syncPause();
        menu.show();
        home();
    }
    private GameSettingsPages settings() {
        return new GameSettingsPages(menu, profile, true, false, new GameSettingsPages.Host() {
            public void preview(ProfileModel draft) {
                ProfilesManager.beginPreview(profile);
                ProfilesManager.copyInto(draft, profile);
                applySettings();
                menu.dismiss();
                preview.start();
            }
            public void mapping(ProfileModel draft, Runnable back) { menu.editPhysicalMapping(draft, back); }
            public boolean mophunTuning() { return false; }
            public void advanced() { home(); }
            public void back() { home(); }
        });
    }
    private void home() {
        new GameMenuPages(menu, new GameMenuPages.Host() {
            public void controls() { settings().controls(); }
            public void display() { new DisplayPanel(menu, profile, true, false,
                    HandheldActivity.this::applySettings, HandheldActivity.this::home, () -> settings().display()).show(); }
            public void more() { HandheldActivity.this.more(menu); }
            public void restart() { menu.dismiss(); recreate(); }
            public void exit() { finish(); }
            public boolean hasPreview() { return ProfilesManager.isPreview(profile); }
            public void keep() { HandheldActivity.this.keep(); home(); }
            public void undo() { HandheldActivity.this.undo(); home(); }
        }, null, fast).home();
    }
    private void keep() {
        if (ProfilesManager.keepPreview(profile)) preview.stop();
        else Toast.makeText(this, R.string.game_save_settings_failed, Toast.LENGTH_LONG).show();
    }
    private void undo() {
        ProfilesManager.undoPreview(profile);
        applySettings();
        preview.stop();
    }

    protected void fail(String message) {
        if (isFinishing() || isDestroyed() || failed) return;
        failed = true;
        syncPause();
        android.util.Log.e("AbyssHandheld", message);
        new AlertDialog.Builder(this).setMessage(message)
                .setPositiveButton("Close", (d, w) -> finish()).setOnCancelListener(d -> finish()).show();
    }
    private void releaseKeys() {
        if (screen != null) screen.release();
        if (physical != null) physical.clear();
        if (fast != null) fast.cancel();
    }
    private void syncPause() {
        if (!started || screen == null) return;
        boolean paused = failed || !resumed || !hasWindowFocus() || menu != null || isFinishing();
        if (paused) releaseKeys();
        pauseCore(paused);
        screen.paused = paused;
        if (preview != null) { if (paused) preview.suspend(); else preview.resume(); }
        if (chrome != null) chrome.update(!paused, false, false);
        screen.invalidate();
    }
    @Override public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        syncPause();
    }
    @Override public void onResume() {
        super.onResume();
        resumed = true;
        if (started && screen != null && screen.gpu != null) screen.gpu.active(MophunGpuView.enabled(profile));
        syncPause();
    }
    @Override public void onPause() {
        resumed = false;
        if (menu != null) menu.cancelPreviewPage();
        syncPause();
        if (screen != null && screen.gpu != null) screen.gpu.active(false);
        super.onPause();
    }
    private void closeSession() {
        if (activeSession != this) return;
        activeSession = null;
        if (screen != null) screen.paused = true;
        releaseKeys();
        if (menu != null) { menu.setOnDismissListener(null); menu.dismiss(); }
        if (preview != null) preview.stop();
        if (devices != null) devices.close();
        if (started) stopCore();
        started = false;
    }
    @Override public void onDestroy() {
        closeSession();
        super.onDestroy();
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (screen == null || screen.paused) return super.dispatchKeyEvent(event);
        int key = event.getKeyCode();
        if (key == KeyEvent.KEYCODE_BACK || key == KeyEvent.KEYCODE_MENU) {
            if (event.getAction() == KeyEvent.ACTION_UP) openMenu();
            return true;
        }
        if (mappings.get(key, 0) != 0 || key == fast.binding()) {
            physical.key(event.getDeviceId(), key, event.getAction() == KeyEvent.ACTION_DOWN, event.getRepeatCount() > 0);
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
    @Override public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (screen != null && !screen.paused && event.isFromSource(InputDevice.SOURCE_JOYSTICK)) {
            ControllerMotion.update(physical, event);
            return true;
        }
        return super.dispatchGenericMotionEvent(event);
    }
}
