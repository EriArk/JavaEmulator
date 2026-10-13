/* Copyright 2026. Licensed under the Apache License, Version 2.0. */
package ru.playsoftware.j2meloader.config;

import android.app.Activity;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.Toast;
import java.io.File;
import static ru.playsoftware.j2meloader.util.Constants.*;

/** Lives outside :midlet so the next game starts only after the old VM is gone. */
public final class GameRestartActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean launched;
    private long deadline;

    public static void prepare(Activity activity, String name, String path, String arguments, File configDir) {
        Intent intent = new Intent(activity, GameRestartActivity.class);
        intent.setData(android.net.Uri.parse(path));
        intent.putExtra(KEY_MIDLET_NAME, name);
        intent.putExtra(KEY_START_ARGUMENTS, arguments);
        intent.putExtra("config_dir", configDir.getPath());
        intent.putExtra("orientation", activity.getRequestedOrientation());
        Bundle token = new Bundle();
        token.putBinder("old_vm", new Binder());
        intent.putExtra("vm", token);
        activity.startActivity(intent);
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setRequestedOrientation(getIntent().getIntExtra("orientation", -1));
        getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                | android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        getWindow().getDecorView().setBackgroundColor(0xff131c21);
        deadline = state == null ? SystemClock.elapsedRealtime() + 10000 : state.getLong("deadline");
    }
    @Override protected void onResume() { super.onResume(); handler.post(check); }
    @Override protected void onPause() { handler.removeCallbacks(check); super.onPause(); }
    private final Runnable check = new Runnable() {
        @Override public void run() {
            if (launched || isFinishing()) return;
            Bundle token = getIntent().getBundleExtra("vm");
            IBinder vm = token == null ? null : token.getBinder("old_vm");
            String path = getIntent().getDataString();
            if (vm == null || path == null) { onBackPressed(); return; }
            if (!vm.pingBinder()) {
                launched = true;
                try {
                    Config.startApp(GameRestartActivity.this, getIntent().getStringExtra(KEY_MIDLET_NAME),
                            path, false, getIntent().getStringExtra(KEY_START_ARGUMENTS));
                } catch (RuntimeException e) {
                    Toast.makeText(GameRestartActivity.this, "Could not launch the game. Previous settings kept.", Toast.LENGTH_LONG).show();
                    onBackPressed();
                }
                finish();
            } else if (SystemClock.elapsedRealtime() >= deadline) {
                Toast.makeText(GameRestartActivity.this, "Could not restart the game. Previous settings kept.", Toast.LENGTH_LONG).show();
                onBackPressed();
            } else handler.postDelayed(this, 100);
        }
    };
    @Override public void onBackPressed() {
        String dir = getIntent().getStringExtra("config_dir");
        if (dir != null) SettingsTrial.discard(new File(dir));
        finish();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putLong("deadline", deadline);
        super.onSaveInstanceState(state);
    }
}
