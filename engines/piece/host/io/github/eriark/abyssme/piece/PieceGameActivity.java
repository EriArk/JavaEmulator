/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.piece;

import android.os.Bundle;
import android.widget.Toast;
import java.io.*;
import java.nio.file.Files;
import io.github.eriark.abyssme.storage.StoragePaths;
import ru.playsoftware.j2meloader.config.*;
import ru.playsoftware.j2meloader.handheld.*;
import ru.playsoftware.j2meloader.input.ControllerInput;
import ru.playsoftware.j2meloader.storage.StorageActivity;

public final class PieceGameActivity extends HandheldActivity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (StorageActivity.guard(this)) return;
        try {
            String id = getIntent().getStringExtra("installed");
            File installed = new PieceLibrary().installed(this, id);
            File saves = new File(StoragePaths.library(this), "piece/data/" + id);
            Files.createDirectories(saves.toPath());
            File flash = new File(saves, "flash.pfi");
            File image = flash.isFile() ? flash : installed;
            new PieceImage(image);
            prepareSession(new File(Config.getConfigsDir(), "piece-" + id));
            if (!PieceCore.start(image.getPath(), saves.getPath())) throw new IOException("Could not start P/ECE image");
            sessionStarted(getIntent().getStringExtra("title"));
        } catch (IOException | LinkageError e) { fail(e.toString()); }
    }
    @Override protected ControllerInput.Sink keys() {
        return new LibretroKeys() {
            @Override protected void button(int id, boolean down) { PieceCore.button(id, down); }
        };
    }
    @Override protected void configureProfile(ProfileModel profile) { LibretroKeys.defaultMappings(profile); }
    @Override protected void pauseCore(boolean paused) {
        PieceCore.pause(paused);
        if (paused && !PieceCore.flush()) Toast.makeText(this, "Could not save P/ECE flash", Toast.LENGTH_LONG).show();
    }
    @Override protected void setSpeed(int percent) { PieceCore.speed(percent); }
    @Override protected void stopCore() { PieceCore.stop(); }
    @Override protected boolean frame(int[] pixels) { return PieceCore.frame(pixels); }
    @Override protected int width() { return 128; }
    @Override protected int height() { return 88; }
    @Override protected boolean exited() { return PieceCore.exited(); }
}
