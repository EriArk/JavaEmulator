/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.gamate;

import android.os.Bundle;
import io.github.eriark.abyssme.storage.StoragePaths;
import java.io.*;
import java.nio.file.Files;
import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.handheld.HandheldActivity;
import ru.playsoftware.j2meloader.input.ControllerInput;
import ru.playsoftware.j2meloader.storage.StorageActivity;

public final class GamateGameActivity extends HandheldActivity {
    private File saves;
    private boolean saving;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (StorageActivity.guard(this)) return;
        try {
            String id = getIntent().getStringExtra("installed");
            File game = new GamateLibrary().installed(this,id);
            saves = new File(StoragePaths.library(this),"gamate/data/" + id);
            Files.createDirectories(saves.toPath());
            prepareSession(new File(Config.getConfigsDir(),"gamate-" + id));
            File bios = GamateLibrary.systemBios(this);
            if (game.length() == 4096) {
                GamateLibrary.selectBios(this, game);
                android.widget.Toast.makeText(this, "Gamate BIOS selected", android.widget.Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            if (!bios.isFile()) throw new IOException("Import the 4 KiB Gamate BIOS (.bin or gamate.zip) from the Gamate tab, then open this game again.");
            if (!GamateCore.start(game.getPath(),saves.getPath())) throw new IOException("Could not start the Gamate cartridge.");
            sessionStarted(getIntent().getStringExtra("title"));
        } catch (IOException | LinkageError e) { fail(e.toString()); }
    }
    @Override protected void more(ru.playsoftware.j2meloader.input.GameMenuDialog menu) {
        menu.action("Save state", android.R.drawable.ic_menu_save, () -> checkpoint(menu,true));
        if (new File(saves,"slot.gamatestate").isFile())
            menu.action("Load state", android.R.drawable.ic_menu_revert, () -> checkpoint(menu,false));
    }

    private void checkpoint(ru.playsoftware.j2meloader.input.GameMenuDialog menu, boolean save) {
        if (saving) return;
        saving = true;
        new Thread(() -> {
            boolean ok = GamateCore.checkpoint(saves.getPath(),save);
            runOnUiThread(() -> {
                saving = false;
                if (isDestroyed() || isFinishing()) return;
                android.widget.Toast.makeText(this, ok ? (save ? "State saved" : "State loaded")
                        : "Could not " + (save ? "save" : "load") + " state", android.widget.Toast.LENGTH_SHORT).show();
                if (ok && menu.isShowing()) menu.dismiss();
            });
        }, "Gamate save state").start();
    }
    @Override protected ControllerInput.Sink keys() { return new ru.playsoftware.j2meloader.handheld.LibretroKeys() {
        @Override protected void button(int id, boolean down) { GamateCore.button(id, down); }
    }; }
    @Override protected void configureProfile(ru.playsoftware.j2meloader.config.ProfileModel profile) {
        ru.playsoftware.j2meloader.handheld.LibretroKeys.defaultMappings(profile);
    }
    @Override protected void setSpeed(int percent) { GamateCore.speed(percent); }
    @Override protected void pauseCore(boolean paused) { GamateCore.pause(paused); }
    @Override protected void stopCore() { GamateCore.stop(); }
    @Override protected boolean frame(int[] pixels) { return GamateCore.frame(pixels); }
    @Override protected int width() { return 160; }
    @Override protected int height() { return 150; }
    @Override protected boolean exited() { return GamateCore.exited(); }
}
