/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.arduboy;

import android.os.Bundle;
import io.github.eriark.abyssme.storage.StoragePaths;
import java.io.*;
import java.nio.file.Files;
import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.handheld.HandheldActivity;
import ru.playsoftware.j2meloader.input.ControllerInput;
import ru.playsoftware.j2meloader.storage.StorageActivity;

public final class ArduboyGameActivity extends HandheldActivity {
    private File saves;
    private boolean saving;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (StorageActivity.guard(this)) return;
        try {
            String id = getIntent().getStringExtra("installed");
            File game = new ArduboyLibrary().installed(this,id);
            saves = new File(StoragePaths.library(this),"arduboy/data/" + id);
            Files.createDirectories(saves.toPath());
            prepareSession(new File(Config.getConfigsDir(),"arduboy-" + id));
            if (!ArduboyCore.start(game.getPath(),saves.getPath())) throw new IOException("Could not start the Arduboy game.");
            sessionStarted(getIntent().getStringExtra("title"));
        } catch (IOException | LinkageError e) { fail(e.toString()); }
    }
    @Override protected void more(ru.playsoftware.j2meloader.input.GameMenuDialog menu) {
        menu.action("Save state", android.R.drawable.ic_menu_save, () -> checkpoint(menu,true));
        if (new File(saves,"slot.ardstate").isFile())
            menu.action("Load state", android.R.drawable.ic_menu_revert, () -> checkpoint(menu,false));
    }

    private void checkpoint(ru.playsoftware.j2meloader.input.GameMenuDialog menu, boolean save) {
        if (saving) return;
        saving = true;
        new Thread(() -> {
            boolean ok = ArduboyCore.checkpoint(save);
            runOnUiThread(() -> {
                saving = false;
                if (isDestroyed() || isFinishing()) return;
                android.widget.Toast.makeText(this, ok ? (save ? "State saved" : "State loaded")
                        : "Could not " + (save ? "save" : "load") + " state", android.widget.Toast.LENGTH_SHORT).show();
                if (ok && menu.isShowing()) menu.dismiss();
            });
        }, "Arduboy save state").start();
    }
    @Override protected ControllerInput.Sink keys() { return new ru.playsoftware.j2meloader.handheld.LibretroKeys() {
        @Override protected void button(int id, boolean down) { ArduboyCore.button(id, down); }
    }; }
    @Override protected void configureProfile(ru.playsoftware.j2meloader.config.ProfileModel profile) {
        ru.playsoftware.j2meloader.handheld.LibretroKeys.defaultMappings(profile);
    }
    @Override protected void setSpeed(int percent) { ArduboyCore.speed(percent); }
    @Override protected void pauseCore(boolean paused) { ArduboyCore.pause(paused); }
    @Override protected void stopCore() { ArduboyCore.stop(); }
    @Override protected boolean frame(int[] pixels) { return ArduboyCore.frame(pixels); }
    @Override protected int width() { return 128; }
    @Override protected int height() { return 64; }
    @Override protected boolean exited() { return ArduboyCore.exited(); }
}
