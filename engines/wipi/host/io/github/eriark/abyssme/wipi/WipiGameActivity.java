/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.wipi;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.util.AtomicFile;
import android.util.SparseIntArray;
import android.view.*;
import android.widget.*;
import com.parkjeongseop.wipi.WipiNative;
import io.github.eriark.abyssme.storage.StoragePaths;
import java.io.*;
import javax.microedition.lcdui.keyboard.KeyMapper;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.config.*;
import ru.playsoftware.j2meloader.input.*;
import ru.playsoftware.j2meloader.mophun.MophunGpuView;
import ru.playsoftware.j2meloader.storage.StorageActivity;
import ru.playsoftware.j2meloader.ui.*;
import ru.playsoftware.j2meloader.util.AppearanceContext;

public final class WipiGameActivity extends Activity {
    private static WipiGameActivity activeSession;
    private boolean started, resumed, failed;
    private Uri selected;
    private ProfileModel profile;
    private FrameLayout root;
    private WipiScreen screen;
    private ControllerInput physical;
    private SparseIntArray mappings;
    private DeviceControls devices;
    private GameMenuDialog menu;
    private SettingsPreviewBar preview;
    private FastForwardControl fast;
    private GameChrome chrome;

    @Override protected void attachBaseContext(Context base) { super.attachBaseContext(AppearanceContext.wrap(base)); }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (StorageActivity.guard(this)) return;
        AppPalette.apply(this);
        getWindow().getDecorView().setSystemUiVisibility(5894);
        selected = state == null ? getIntent().getData() : state.getParcelable("game");
        String installed = getIntent().getStringExtra("installed");
        if (installed != null && installed.matches("[a-f0-9]{64}"))
            selected = Uri.fromFile(new File(StoragePaths.library(this), "wipi/packages/" + installed + "/game.zip"));
        if (selected != null) load();
        else if (state == null) startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .setType("*/*").addCategory(Intent.CATEGORY_OPENABLE), 1);
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putParcelable("game", selected);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != 1) return;
        if (result != RESULT_OK || data == null || data.getData() == null) { finish(); return; }
        selected = data.getData();
        load();
    }

    private String soundfont() throws IOException {
        // rustysynth needs a seekable file. Invalidate the cache on APK replacement.
        File bank = new File(getCacheDir(), "wipi-" + new File(getApplicationInfo().sourceDir).lastModified() + ".sf2");
        if (bank.isFile() && bank.length() > 0) return bank.getPath();
        AtomicFile atomic = new AtomicFile(bank);
        try (InputStream in = getAssets().open("resources/generaluser-gs.sf2")) {
            FileOutputStream out = atomic.startWrite();
            try {
                byte[] buffer = new byte[65536];
                for (int n; (n = in.read(buffer)) != -1;) out.write(buffer, 0, n);
                atomic.finishWrite(out);
            } catch (IOException e) { atomic.failWrite(out); throw e; }
        }
        return bank.getPath();
    }

    private void load() {
        new Thread(() -> {
            try (InputStream in = getContentResolver().openInputStream(selected)) {
                WipiPackage game = WipiPackage.read(in);
                String bank = soundfont();
                runOnUiThread(() -> {
                    if (isDestroyed() || isFinishing() || StorageActivity.guard(this)) return;
                    try {
                        File saves = new File(StoragePaths.library(this), "wipi/data/" + game.id);
                        File config = new File(Config.getConfigsDir(), "wipi-" + game.id);
                        for (File dir : new File[]{saves, config}) {
                            if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot open " + dir.getName());
                        }
                        profile = ProfilesManager.loadConfig(config);
                        if (profile == null) profile = new ProfileModel(config);
                        WipiNative.initialize(this);
                        if (activeSession != null && activeSession != this) {
                            WipiGameActivity previous = activeSession;
                            previous.closeSession();
                            previous.finish();
                        }
                        started = WipiNative.nativeStart(game.bytes, "game.zip", saves.getPath(), bank);
                        if (!started) throw new IOException("Could not start " + game.carrier + " game");
                        activeSession = this;
                        setTitle(getIntent().hasExtra("title") ? getIntent().getStringExtra("title") : game.carrier + " game");
                        setup();
                    } catch (Exception | LinkageError e) { fail(e.toString()); }
                });
            } catch (Exception e) { runOnUiThread(() -> fail(e.toString())); }
        }, "WIPI import").start();
    }

    private void setup() {
        WipiKeys keys = new WipiKeys();
        physical = new ControllerInput(code -> mappings.get(code, 0), keys);
        root = new FrameLayout(this);
        screen = new WipiScreen(this, profile, keys);
        root.addView(screen);
        ImageButton button = new ImageButton(this);
        button.setId(R.id.game_menu_button);
        button.setImageResource(R.drawable.ic_baseline_tune_24);
        button.setContentDescription(getString(R.string.game_menu_title));
        button.setBackgroundResource(R.drawable.bg_quick_setting_button);
        button.setOnClickListener(v -> openMenu());
        int size = Math.round(48 * getResources().getDisplayMetrics().density);
        root.addView(button, new FrameLayout.LayoutParams(size, size, Gravity.TOP | Gravity.END));
        fast = new FastForwardControl(root, profile, () -> true, WipiNative::nativeSetSpeed, fps -> screen.fps = fps, false);
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
        mappings = KeyMapper.resolveMappings(profile.getActiveKeyMappings());
        if (mappings.indexOfKey(KeyEvent.KEYCODE_DEL) < 0) mappings.put(KeyEvent.KEYCODE_DEL, javax.microedition.lcdui.Canvas.KEY_CLEAR);
        setRequestedOrientation(profile.orientation == 3 ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                : profile.orientation == 2 ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
        screen.layoutControls(devices.connected());
        fast.configure();
        WipiNative.nativeSetSpeed(profile.getEmulationSpeed());
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
                    WipiGameActivity.this::applySettings, WipiGameActivity.this::home, () -> settings().display()).show(); }
            public void more() {}
            public void restart() { menu.dismiss(); recreate(); }
            public void exit() { finish(); }
            public boolean hasPreview() { return ProfilesManager.isPreview(profile); }
            public void keep() { WipiGameActivity.this.keep(); home(); }
            public void undo() { WipiGameActivity.this.undo(); home(); }
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

    void fail(String message) {
        if (isFinishing() || isDestroyed() || failed) return;
        failed = true;
        syncPause();
        android.util.Log.e("AbyssWipi", message);
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
        WipiNative.nativeSetPaused(paused);
        WipiNative.nativeSetVolume(paused ? 0 : 1, paused ? 0 : 1);
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
        if (started) WipiNative.nativeStop();
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
