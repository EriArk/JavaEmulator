/* Copyright 2026. Licensed under the Apache License, Version 2.0. */
package ru.playsoftware.j2meloader.config;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.io.File;
import ru.playsoftware.j2meloader.BuildConfig;
import ru.playsoftware.j2meloader.input.GameMenuDialog;
import ru.playsoftware.j2meloader.input.MapperDialog;
import static ru.playsoftware.j2meloader.util.Constants.*;

public final class GameSettingsActivity extends AppCompatActivity {
    private GameMenuDialog menu;
    private MapperDialog mapper;
    private GameSettingsPages pages;
    private File app;
    private String title;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (BuildConfig.HANDHELD_MODE) setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        String path = getIntent().getDataString();
        if (path == null) { finish(); return; }
        app = new File(path);
        if (!app.isDirectory() || app.getParentFile() == null || app.getParentFile().getParentFile() == null) { finish(); return; }
        title = getIntent().getStringExtra(KEY_MIDLET_NAME);
        if (title == null) title = app.getName();
        File dir = new File(app.getParentFile().getParentFile(), "configs/" + app.getName());
        if (!dir.isDirectory() && !dir.mkdirs()) { finish(); return; }
        ProfileModel profile = ProfilesManager.loadConfig(dir);
        if (profile == null) {
            profile = new ProfileModel(dir);
            if (!ProfilesManager.saveConfig(profile)) { finish(); return; }
        }
        menu = new GameMenuDialog(this, title);
        menu.setOnCancelListener(d -> finish());
        menu.setOnDismissListener(d -> finish());
        menu.show();
        pages = new GameSettingsPages(menu, profile, false, !getIntent().getBooleanExtra("native_settings", false), new GameSettingsPages.Host() {
            public void preview(ProfileModel draft) {
                try {
                    SettingsTrial.stage(draft);
                    Config.startApp(GameSettingsActivity.this, title, app.getPath(), false,
                            getIntent().getStringExtra(KEY_START_ARGUMENTS));
                    finish();
                } catch (Exception e) { Toast.makeText(GameSettingsActivity.this, "Could not prepare settings", Toast.LENGTH_LONG).show(); }
            }
            public void mapping(ProfileModel draft, Runnable back) {
                menu.hide();
                mapper = new MapperDialog(GameSettingsActivity.this, title, draft, () -> {
                    if (!isFinishing()) { menu.show(); back.run(); }
                });
                mapper.show();
            }
            public void advanced() {
                Intent intent = new Intent(getIntent()).setClass(GameSettingsActivity.this, ConfigActivity.class);
                startActivity(intent); finish();
            }
            public void back() { pages.home(); }
        });
        if (state != null && state.containsKey("draft")) {
            try {
                ProfileModel restored = new com.google.gson.Gson().fromJson(state.getString("draft"), ProfileModel.class);
                if (restored != null) ProfilesManager.copyInto(restored, pages.draft());
            } catch (RuntimeException ignored) { }
        }
        pages.home();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        if (pages != null) state.putString("draft", new com.google.gson.Gson().toJson(pages.draft()));
        super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy() {
        if (mapper != null) { mapper.setOnDismissListener(null); mapper.dismiss(); }
        if (menu != null) { menu.setOnDismissListener(null); menu.dismiss(); }
        super.onDestroy();
    }
}
