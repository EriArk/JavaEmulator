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

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.microedition.shell.MicroActivity;

import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.databinding.ActivityCompatibilityTestBinding;
import ru.playsoftware.j2meloader.util.FileUtils;

public class CompatibilityTestActivity extends AppCompatActivity {
	private ActivityCompatibilityTestBinding binding;
	private ExecutorService executor;
	private File appDir;
	private File configDir;
	private String appName;
	private String arguments;
	private volatile boolean destroyed;

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
		new AlertDialog.Builder(this)
				.setTitle(R.string.error)
				.setMessage(getString(R.string.err_missing_app, ""))
				.setPositiveButton(R.string.exit, (dialog, which) -> finish())
				.setCancelable(false)
				.show();
	}

	private void startTesting() {
		executor = Executors.newSingleThreadExecutor();
		executor.execute(() -> {
			ProfileModel params = loadOrCreateProfile();
			updateProgress(0, getString(R.string.compatibility_test_starting));
			CompatibilityProfileTester.Result result = CompatibilityProfileTester.run(
					this, appDir, params, this::updateProgress);
			updateProgress(100, getString(R.string.compatibility_test_done, result.profileName));
			runOnUiThread(() -> {
				if (destroyed) {
					return;
				}
				binding.compatibilityLaunchNow.setVisibility(View.VISIBLE);
				binding.compatibilityLaunchNow.postDelayed(this::launchMidlet, 450);
			});
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
		if (destroyed || appDir == null) {
			return;
		}
		Intent intent = new Intent(Intent.ACTION_DEFAULT, Uri.parse(appDir.getPath()), this, MicroActivity.class);
		intent.putExtra(KEY_MIDLET_NAME, appName);
		intent.putExtra(KEY_START_ARGUMENTS, arguments);
		startActivity(intent);
		finish();
	}

	@Override
	protected void onDestroy() {
		destroyed = true;
		if (executor != null) {
			executor.shutdownNow();
		}
		binding = null;
		super.onDestroy();
	}
}
