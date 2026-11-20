/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.dingoo;

import android.os.Bundle;
import android.view.KeyEvent;
import android.util.SparseIntArray;
import io.github.eriark.abyssme.storage.StoragePaths;
import java.io.*;
import java.nio.file.Files;
import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.handheld.HandheldActivity;
import ru.playsoftware.j2meloader.input.ControllerInput;
import ru.playsoftware.j2meloader.storage.StorageActivity;

public final class DingooGameActivity extends HandheldActivity {
    private File saves;
    private boolean saving;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (StorageActivity.guard(this)) return;
        try {
            String id = getIntent().getStringExtra("installed");
            File game = new DingooLibrary().installed(this,id);
            saves = new File(StoragePaths.library(this),"dingoo/data/" + id);
            Files.createDirectories(saves.toPath());
            prepareSession(new File(Config.getConfigsDir(),"dingoo-" + id));
            if (!DingooCore.start(game.getPath(),saves.getPath())) throw new IOException("DingooEmu could not load this package");
            sessionStarted(getIntent().getStringExtra("title"));
        } catch (IOException | LinkageError e) { fail(e.toString()); }
    }
    @Override protected void more(ru.playsoftware.j2meloader.input.GameMenuDialog menu) {
        menu.action("Save state", android.R.drawable.ic_menu_save, () -> checkpoint(menu,true));
        if (new File(saves,"slot.dingstate").isFile())
            menu.action("Load state", android.R.drawable.ic_menu_revert, () -> checkpoint(menu,false));
    }

    private void checkpoint(ru.playsoftware.j2meloader.input.GameMenuDialog menu, boolean save) {
        if (saving) return;
        saving = true;
        new Thread(() -> {
            boolean ok = DingooCore.checkpoint(saves.getPath(),save);
            runOnUiThread(() -> {
                saving = false;
                if (isDestroyed() || isFinishing()) return;
                android.widget.Toast.makeText(this, ok ? (save ? "State saved" : "State loaded")
                        : "Could not " + (save ? "save" : "load") + " state", android.widget.Toast.LENGTH_SHORT).show();
                if (ok && menu.isShowing()) menu.dismiss();
            });
        }, "Dingoo save state").start();
    }
    @Override protected ControllerInput.Sink keys() { return new DingooKeys(); }
    @Override protected void configureProfile(ru.playsoftware.j2meloader.config.ProfileModel profile) {
        DingooLibrary.defaultMappings(profile);
    }
    @Override protected void setSpeed(int percent) { DingooCore.speed(percent); }
    @Override protected void pauseCore(boolean paused) { DingooCore.pause(paused); }
    @Override protected void stopCore() { DingooCore.stop(); }
    @Override protected boolean frame(int[] pixels) { return DingooCore.frame(pixels); }
    @Override protected int width() { return 320; }
    @Override protected int height() { return 240; }
    @Override protected boolean exited() { return DingooCore.exited(); }
}
