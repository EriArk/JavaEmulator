/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.wipi;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.AtomicFile;
import com.parkjeongseop.wipi.WipiNative;
import io.github.eriark.abyssme.storage.StoragePaths;
import java.io.*;
import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.handheld.HandheldActivity;
import ru.playsoftware.j2meloader.input.ControllerInput;
import ru.playsoftware.j2meloader.storage.StorageActivity;

public final class WipiGameActivity extends HandheldActivity {
    private Uri selected;
    private final int[] errorKind = new int[1];
    @Override protected ControllerInput.Sink keys() { return new WipiKeys(); }
    @Override protected void setSpeed(int percent) { WipiNative.nativeSetSpeed(percent); }
    @Override protected void pauseCore(boolean paused) {
        WipiNative.nativeSetPaused(paused);
        WipiNative.nativeSetVolume(paused ? 0 : 1, paused ? 0 : 1);
    }
    @Override protected void stopCore() { WipiNative.nativeStop(); }
    @Override protected boolean frame(int[] pixels) { return WipiNative.nativeGetFrame(pixels); }
    @Override protected int width() { return 240; }
    @Override protected int height() { return 320; }
    @Override protected boolean exited() { return WipiNative.nativePollExit(); }
    @Override protected String error() { return WipiNative.nativeGetError(errorKind); }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (StorageActivity.guard(this)) return;
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
                        prepareSession(config);
                        WipiNative.initialize(this);
                        if (!WipiNative.nativeStart(game.bytes, "game.zip", saves.getPath(), bank))
                            throw new IOException("Could not start " + game.carrier + " game");
                        sessionStarted(getIntent().hasExtra("title") ? getIntent().getStringExtra("title") : game.carrier + " game");
                    } catch (Exception | LinkageError e) { fail(e.toString()); }
                });
            } catch (Exception e) { runOnUiThread(() -> fail(e.toString())); }
        }, "WIPI import").start();
    }

}
