/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.pokitto;

import android.os.Bundle;
import io.github.eriark.abyssme.storage.StoragePaths;
import java.io.*;
import java.nio.file.Files;
import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.handheld.HandheldActivity;
import ru.playsoftware.j2meloader.input.ControllerInput;
import ru.playsoftware.j2meloader.storage.StorageActivity;

public final class PokittoGameActivity extends HandheldActivity {
    private File saves;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (StorageActivity.guard(this)) return;
        try {
            String id = getIntent().getStringExtra("installed");
            File game = new PokittoLibrary().installed(this,id);
            saves = new File(StoragePaths.library(this),"pokitto/data/" + id);
            Files.createDirectories(saves.toPath());
            prepareSession(new File(Config.getConfigsDir(),"pokitto-" + id));
            if (!PokittoCore.start(game.getPath(),saves.getPath())) throw new IOException("Could not start the Pokitto game.");
            sessionStarted(getIntent().getStringExtra("title"));
        } catch (IOException | LinkageError e) { fail(e.toString()); }
    }
    @Override protected void more(ru.playsoftware.j2meloader.input.GameMenuDialog menu) {
        menu.action("Mount SD image", android.R.drawable.ic_menu_upload, () ->
            new android.app.AlertDialog.Builder(this).setMessage("Replace this game's SD card and restart the game?")
                .setPositiveButton("Choose image", (dialog, which) -> startActivityForResult(
                        new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT)
                                .addCategory(android.content.Intent.CATEGORY_OPENABLE).setType("*/*"), 27))
                .setNegativeButton(android.R.string.cancel, null).show());
    }
    @Override protected void onActivityResult(int request, int result, android.content.Intent data) {
        super.onActivityResult(request, result, data);
        if (request != 27 || result != RESULT_OK || data == null || data.getData() == null) return;
        new Thread(() -> {
            File tmp = null;
            try (InputStream in = getContentResolver().openInputStream(data.getData())) {
                if (in == null) throw new IOException("Cannot open SD image");
                tmp = File.createTempFile("mount-", ".img", saves);
                long size = Files.copy(in, tmp.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                if (size < 512 || size % 512 != 0) throw new IOException("Choose a disk image with complete SD sectors");
                Files.move(tmp.toPath(), new File(saves, "sd.img").toPath(),
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                runOnUiThread(this::recreate);
            } catch (IOException e) { runOnUiThread(() -> fail(e.getMessage())); }
            finally { if (tmp != null) tmp.delete(); }
        }, "Mount Pokitto card").start();
    }

    @Override protected ControllerInput.Sink keys() { return new ru.playsoftware.j2meloader.handheld.LibretroKeys() {
        @Override protected void button(int id, boolean down) { PokittoCore.button(id, down); }
    }; }
    @Override protected void configureProfile(ru.playsoftware.j2meloader.config.ProfileModel profile) {
        ru.playsoftware.j2meloader.handheld.LibretroKeys.defaultMappings(profile);
    }
    @Override protected void setSpeed(int percent) { PokittoCore.speed(percent); }
    @Override protected void pauseCore(boolean paused) { PokittoCore.pause(paused); }
    @Override protected void stopCore() { PokittoCore.stop(); }
    @Override protected boolean frame(int[] pixels) { return PokittoCore.frame(pixels); }
    @Override protected int width() { return 220; }
    @Override protected int height() { return 176; }
    @Override protected boolean exited() { return PokittoCore.exited(); }
}
