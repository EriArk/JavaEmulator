/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.gameking;

import android.os.Bundle;
import io.github.eriark.abyssme.storage.StoragePaths;
import java.io.*;
import java.nio.file.Files;
import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.handheld.HandheldActivity;
import ru.playsoftware.j2meloader.input.ControllerInput;
import ru.playsoftware.j2meloader.storage.StorageActivity;

public class GameKingGameActivity extends HandheldActivity {
    private File saves;
    private boolean saving;
    private boolean color;
    private int drawnFrames;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (StorageActivity.guard(this)) return;
        try {
            String id = getIntent().getStringExtra("installed");
            saves = new File(StoragePaths.library(this), engine() + "/data/" + id);
            Files.createDirectories(saves.toPath());
            prepareSession(new File(Config.getConfigsDir(), engine() + "-" + id));
            File command = prepareCommand(id);
            if (command == null) return;
            if (!GameKingCore.start(command.getPath(), saves.getPath())) throw new IOException("Could not start the emulation core.");
            sessionStarted(getIntent().getStringExtra("title"));
        } catch (IOException | LinkageError e) { fail(e.toString()); }
    }
    protected String engine() { return "gameking"; }

    protected File prepareCommand(String id) throws IOException {
        File game = new GameKingLibrary().installed(this, id);
        boolean colorBios = GameKingLibrary.COLOR_BIOS.equals(id);
        color = colorBios || GameKingLibrary.colorCartridge(game);
        File bios = GameKingLibrary.systemBios(this, color);
        boolean builtIn = colorBios || GameKingLibrary.BUILT_IN.equals(id);
        if (builtIn && !bios.isFile()) GameKingLibrary.selectBios(this, game, color);
        if (!bios.isFile()) throw new IOException(color ? "Import the GameKing 3 GM-220 BIOS (gm220.bin or gamekin3.zip), then open this game again." : "Import the GM-218 BIOS (gm218.bin or gameking.zip), then open this game again.");
        return writeCommand(color ? "gamekin3" : "gameking", bios, builtIn ? null : game);
    }

    protected File writeCommand(String driver, File bios, File cartridge) throws IOException {
        File command = new File(getFilesDir(), driver + ".cmd");
        String options = driver + " -skip_gameinfo -nothrottle -rompath " + quoted(bios.getParentFile().getParentFile());
        if (cartridge != null) options += " -cart " + quoted(cartridge);
        Files.write(command.toPath(), options.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return command;
    }

    private static String quoted(File file) throws IOException {
        String path = file.getCanonicalPath();
        if (path.contains("\"") || path.contains("\n") || path.contains("\r")
                || path.getBytes(java.nio.charset.StandardCharsets.UTF_8).length >= 1000)
            throw new IOException("The GameKing core cannot read this library path");
        return "\"" + path + "\"";
    }

    @Override protected void more(ru.playsoftware.j2meloader.input.GameMenuDialog menu) {
        menu.action("Save state", android.R.drawable.ic_menu_save, () -> checkpoint(menu,true));
        if (new File(saves,"slot.gamekingstate").isFile())
            menu.action("Load state", android.R.drawable.ic_menu_revert, () -> checkpoint(menu,false));
    }

    private void checkpoint(ru.playsoftware.j2meloader.input.GameMenuDialog menu, boolean save) {
        if (saving) return;
        saving = true;
        new Thread(() -> {
            boolean ok = GameKingCore.checkpoint(saves.getPath(),save);
            runOnUiThread(() -> {
                saving = false;
                if (isDestroyed() || isFinishing()) return;
                android.widget.Toast.makeText(this, ok ? (save ? "State saved" : "State loaded")
                        : "Could not " + (save ? "save" : "load") + " state", android.widget.Toast.LENGTH_SHORT).show();
                if (ok && menu.isShowing()) menu.dismiss();
            });
        }, "GameKing save state").start();
    }
    protected void sendButton(int id, boolean down) { GameKingCore.button(id, down); }
    @Override protected ControllerInput.Sink keys() { return new ru.playsoftware.j2meloader.handheld.LibretroKeys() {
        @Override protected void button(int id, boolean down) { sendButton(id, down); }
    }; }
    @Override protected void configureProfile(ru.playsoftware.j2meloader.config.ProfileModel profile) {
        ru.playsoftware.j2meloader.handheld.LibretroKeys.defaultMappings(profile);
    }
    @Override protected void setSpeed(int percent) { GameKingCore.speed(percent); }
    @Override protected void pauseCore(boolean paused) { GameKingCore.pause(paused); }
    @Override protected void stopCore() { GameKingCore.stop(); }
    @Override protected boolean frame(int[] pixels) {
        if (!GameKingCore.frame(pixels)) return false;
        drawnFrames++;
        return true;
    }
    @Override protected boolean artworkReady() {
        // Both boot ROMs animate the manufacturer logo before the cartridge title.
        return drawnFrames >= (color ? 480 : 720);
    }
    @Override protected int width() { return color ? 160 : 48; }
    // GM-220 has a 160 × 160 color-filter raster on a 3:2 LCD.
    @Override protected int displayWidth() { return color ? 240 : 48; }
    @Override protected int height() { return color ? 160 : 32; }
    @Override protected boolean exited() { return GameKingCore.exited(); }
}
