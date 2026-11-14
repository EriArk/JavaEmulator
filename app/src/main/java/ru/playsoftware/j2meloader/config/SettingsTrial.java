/* Copyright 2026. Licensed under the Apache License, Version 2.0. */
package ru.playsoftware.j2meloader.config;

import android.util.AtomicFile;
import com.google.gson.Gson;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** A one-launch draft. The confirmed config and game saves are never replaced. */
public final class SettingsTrial {
    private static AtomicFile file(File dir) { return new AtomicFile(new File(dir, "settings-trial.json")); }
    public static void discard(File dir) { file(dir).delete(); }

    /** Keep the running trial intact until the next-process handoff is accepted. */
    public static void prepareRestart(ProfileModel current, ProfileModel draft, Runnable handoff) throws IOException {
        if (draft != null) launch(draft, handoff);
        else {
            handoff.run();
            ProfilesManager.undoPreview(current);
        }
    }

    /** A rejected launch must not leave a trial for an unrelated later Play action. */
    public static void launch(ProfileModel draft, Runnable handoff) throws IOException {
        try {
            stage(draft);
            handoff.run();
        } catch (IOException | RuntimeException error) {
            discard(draft.dir);
            throw error;
        }
    }

    public static void stage(ProfileModel draft) throws IOException {
        SharedProfiles.capture(draft);
        AtomicFile file = file(draft.dir);
        FileOutputStream out = null;
        try {
            out = file.startWrite();
            out.write(new Gson().toJson(draft).getBytes(StandardCharsets.UTF_8));
            file.finishWrite(out);
        } catch (IOException e) { file.failWrite(out); throw e; }
    }

    public static void claim(ProfileModel confirmed) throws IOException {
        AtomicFile file = file(confirmed.dir);
        if (!file.getBaseFile().exists()) return;
        if (file.getBaseFile().length() > 1024 * 1024) { file.delete(); throw new IOException("Settings draft is too large"); }
        ProfileModel draft;
        try {
            draft = new Gson().fromJson(new String(file.readFully(), StandardCharsets.UTF_8), ProfileModel.class);
        } catch (RuntimeException e) { throw new IOException("Invalid settings draft", e); }
        finally { file.delete(); }
        if (draft == null || draft.screenWidth <= 0 || draft.screenHeight <= 0)
            throw new IOException("Invalid settings draft");
        // Consume before engine initialization: a failed launch cannot replay the draft.
        ProfilesManager.beginPreview(confirmed);
        ProfilesManager.copyInto(draft, confirmed);
        confirmed.sharedSnapshot = SharedProfiles.values(confirmed);
    }

    private SettingsTrial() { }
}
