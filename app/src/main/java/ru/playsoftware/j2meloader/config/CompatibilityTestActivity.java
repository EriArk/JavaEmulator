/*
 * Copyright 2026
 *
 * Licensed under the Apache License, Version 2.0
 */

package ru.playsoftware.j2meloader.config;

import static ru.playsoftware.j2meloader.util.Constants.KEY_MIDLET_NAME;
import static ru.playsoftware.j2meloader.util.Constants.KEY_START_ARGUMENTS;
import static ru.playsoftware.j2meloader.util.Constants.PREF_DEFAULT_PROFILE;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import ru.playsoftware.j2meloader.input.GameMenuDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CancellationException;

import javax.microedition.shell.MicroActivity;

import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.databinding.ActivityCompatibilityTestBinding;
import ru.playsoftware.j2meloader.diagnostics.LaunchDiagnostics;
import ru.playsoftware.j2meloader.util.FileUtils;

public class CompatibilityTestActivity extends AppCompatActivity {
	private ActivityCompatibilityTestBinding binding;
	private ExecutorService executor;
	private File appDir;
	private File configDir;
	private String appName;
	private String arguments;
	private volatile boolean destroyed;
	private boolean ready;
	private boolean launched;
	private GameMenuDialog errorDialog;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		binding = ActivityCompatibilityTestBinding.inflate(getLayoutInflater());
		setContentView(binding.getRoot());
		appName = getIntent().getStringExtra(KEY_MIDLET_NAME);
		arguments = getIntent().getStringExtra(KEY_START_ARGUMENTS);
		setTitle(appName);
		binding.compatibilityLaunchNow.setOnClickListener(v -> launchMidlet());
		if (!preparePaths()) {
			return;
		}
		startTesting();
	}

	private boolean preparePaths() {
		String path = getIntent().getDataString();
		if (path == null) {
			showError();
			return false;
		}
		appDir = new File(path);
		File convertedDir = appDir.getParentFile();
		String workDir = convertedDir == null ? null : convertedDir.getParent();
		if (!appDir.isDirectory() || workDir == null) {
			showError();
			return false;
		}
		new File(workDir + Config.MIDLET_DATA_DIR + appDir.getName()).mkdirs();
		configDir = new File(workDir + Config.MIDLET_CONFIGS_DIR + appDir.getName());
		configDir.mkdirs();
		return true;
	}

	private void showError() {
		showError(getString(R.string.err_missing_app, ""), false);
	}

	private void showError(String message, boolean retryable) {
		errorDialog = new GameMenuDialog(this, appName);
		GameMenuDialog dialog = errorDialog;
		boolean[] retry = {false};
		dialog.show(); dialog.page("Could not prepare game");
		dialog.setCanceledOnTouchOutside(false);
		dialog.message(message);
		dialog.setOnDismissListener(d -> { if (!retry[0] && !destroyed) finish(); });
		if (retryable) dialog.action("Retry", android.R.drawable.ic_menu_rotate, () -> {
			retry[0] = true; dialog.dismiss(); startTesting();
		});
		dialog.action("Exit", android.R.drawable.ic_menu_close_clear_cancel, dialog::dismiss);
	}

	private void startTesting() {
		if (executor == null) executor = Executors.newSingleThreadExecutor();
		executor.execute(() -> {
			try {
				ProfileModel params = loadOrCreateProfile();
				updateProgress(0, getString(R.string.compatibility_test_starting));
				CompatibilityProfileTester.Result result = CompatibilityProfileTester.run(
						this, appDir, params, this::updateProgress);
				LaunchDiagnostics.record(this, "profile_selected", appName,
						result.profileName + ":" + result.confidence + ":" + result.reasons);
				updateProgress(100, getString(R.string.compatibility_test_done));
				runOnUiThread(() -> {
					if (destroyed) return;
					ready = true;
					binding.compatibilityLaunchNow.setVisibility(View.VISIBLE);
					binding.compatibilityLaunchNow.postDelayed(this::launchMidlet, 450);
				});
			} catch (CancellationException ignored) {
				// Leaving preparation must not launch a game from a stopped activity.
			} catch (Exception error) {
				LaunchDiagnostics.record(this, "preparation_failed", appName, error.getClass().getSimpleName());
				runOnUiThread(() -> {
					if (destroyed) return;
					showError(getString(R.string.compatibility_test_failed), true);
				});
			}
		});
	}

	private ProfileModel loadOrCreateProfile() {
		ProfileModel params = ProfilesManager.loadConfig(configDir);
		if (params != null) {
			return params;
		}
		String defProfile = PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
				.getString(PREF_DEFAULT_PROFILE, null);
		if (defProfile != null) {
			FileUtils.copyFiles(new File(Config.getProfilesDir(), defProfile), configDir, null);
			params = ProfilesManager.loadConfig(configDir);
			if (params != null) {
				return params;
			}
		}
		return new ProfileModel(configDir);
	}

	private void updateProgress(int progress, String message) {
		runOnUiThread(() -> {
			if (destroyed || binding == null) {
				return;
			}
			binding.compatibilityProgress.setProgress(progress);
			binding.compatibilityStatus.setText(message);
		});
	}

	private void launchMidlet() {
		if (destroyed || !ready || launched || appDir == null) {
			return;
		}
		launched = true;
		Intent intent = new Intent(Intent.ACTION_DEFAULT, Uri.parse(appDir.getPath()), this,
				getIntent().getBooleanExtra("open_settings", false) ? GameSettingsActivity.class : MicroActivity.class);
		intent.putExtra(KEY_MIDLET_NAME, appName);
		intent.putExtra(KEY_START_ARGUMENTS, arguments);
		startActivity(intent);
		finish();
	}

	@Override
	protected void onDestroy() {
		destroyed = true;
		if (errorDialog != null) errorDialog.dismiss();
		if (executor != null) {
			executor.shutdownNow();
		}
		binding = null;
		super.onDestroy();
	}
}
