/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.mre;

import android.os.Bundle;
import io.github.eriark.abyssme.storage.StoragePaths;
import java.io.*;
import java.nio.file.Files;
import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.handheld.HandheldActivity;
import ru.playsoftware.j2meloader.input.ControllerInput;
import ru.playsoftware.j2meloader.storage.StorageActivity;

public final class MreGameActivity extends HandheldActivity {
    private File saves;
    private boolean saving;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (StorageActivity.guard(this)) return;
        try {
            String id = getIntent().getStringExtra("installed");
            File game = new MreLibrary().installed(this,id);
            saves = new File(StoragePaths.library(this),"mre/data/" + id);
            for (String drive : new String[] {"c", "d", "e"})
                Files.createDirectories(new File(saves, "fs/" + drive).toPath());
            prepareSession(new File(Config.getConfigsDir(),"mre-" + id));
            MreCore.assets(getApplicationContext().getAssets());
            if (!MreCore.start(game.getPath(),saves.getPath())) throw new IOException("Could not start the MediaTek MRE game.");
            sessionStarted(getIntent().getStringExtra("title"));
        } catch (IOException | LinkageError e) { fail(e.toString()); }
    }
    @Override protected void more(ru.playsoftware.j2meloader.input.GameMenuDialog menu) {
        menu.action("Save state", android.R.drawable.ic_menu_save, () -> checkpoint(menu,true));
        if (new File(saves,"slot.mrestate").isFile())
            menu.action("Load state", android.R.drawable.ic_menu_revert, () -> checkpoint(menu,false));
    }

    private void checkpoint(ru.playsoftware.j2meloader.input.GameMenuDialog menu, boolean save) {
        if (saving) return;
        saving = true;
        new Thread(() -> {
            boolean ok = MreCore.checkpoint(saves.getPath(),save);
            runOnUiThread(() -> {
                saving = false;
                if (isDestroyed() || isFinishing()) return;
                android.widget.Toast.makeText(this, ok ? (save ? "State saved" : "State loaded")
                        : "Could not " + (save ? "save" : "load") + " state", android.widget.Toast.LENGTH_SHORT).show();
                if (ok && menu.isShowing()) menu.dismiss();
            });
        }, "Mre save state").start();
    }
    @Override protected ControllerInput.Sink keys() { return new ControllerInput.Sink() {
        public void press(int key) { MreCore.key(key, true); }
        public void release(int key) { MreCore.key(key, false); }
        public void repeat(int key) {}
    }; }
    @Override protected void configureProfile(ru.playsoftware.j2meloader.config.ProfileModel profile) {
        ru.playsoftware.j2meloader.handheld.LibretroKeys.defaultMappings(profile);
    }
    private boolean penDown;
    private int penX, penY;
    @Override protected boolean usesStylus() { return true; }
    @Override protected void stylus(float x, float y, boolean down) {
        if (down) { penX = (int)x; penY = (int)y; }
        MreCore.pen(down ? (penDown ? 3 : 1) : 2, penX, penY);
        penDown = down;
    }
    @Override protected void setSpeed(int percent) { MreCore.speed(percent); }
    @Override protected void pauseCore(boolean paused) { MreCore.pause(paused); }
    @Override protected void stopCore() { MreCore.stop(); }
    @Override protected boolean frame(int[] pixels) { return MreCore.frame(pixels); }
    @Override protected int width() { return 240; }
    @Override protected int height() { return 320; }
    @Override protected boolean exited() { return MreCore.exited(); }
}
