/*
 * Copyright 2026
 * Licensed under the Apache License, Version 2.0
 */

package ru.playsoftware.j2meloader.settings;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.applist.AppListModel;
import ru.playsoftware.j2meloader.catalog.GameFolderIndexer;
import ru.playsoftware.j2meloader.config.ProfilesActivity;
import ru.playsoftware.j2meloader.info.AboutDialogFragment;
import ru.playsoftware.j2meloader.util.LogUtils;

public class SettingsFragment extends PreferenceFragmentCompat {
	private final ExecutorService executor = Executors.newSingleThreadExecutor();
	private Preference foldersPreference;
	private final ActivityResultLauncher<Uri> folderLauncher = registerForActivityResult(
			new ActivityResultContracts.OpenDocumentTree(), this::onFolderSelected);

	@Override
	public void onCreatePreferences(Bundle bundle, String rootKey) {
		setPreferencesFromResource(R.xml.preferences, rootKey);
		styleRows(getPreferenceScreen());
		if (ru.playsoftware.j2meloader.BuildConfig.SYMBIAN_EXPERIMENT && rootKey == null) {
			Preference systems = new Preference(requireContext()); systems.setKey("pref_system_files");
			systems.setTitle("System files"); systems.setSummary("Required files for Symbian and N-Gage");
			systems.setIcon(R.drawable.ic_setting_folder); systems.setLayoutResource(R.layout.preference_compact);
			systems.setOrder(0); systems.setIntent(new Intent().setClassName(requireContext(), "io.github.eriark.abyssme.symbian.SymbianActivity"));
			getPreferenceScreen().addPreference(systems);
		}
		androidx.preference.SwitchPreferenceCompat screenshot = findPreference(
				ru.playsoftware.j2meloader.util.Screenshots.BUTTON_PREFERENCE);
		if (screenshot != null) {
			screenshot.setChecked(ru.playsoftware.j2meloader.util.Screenshots.isButtonVisible(requireContext()));
			screenshot.setOnPreferenceChangeListener((preference, value) -> {
				ru.playsoftware.j2meloader.util.Screenshots.setButtonVisible(requireContext(), (Boolean) value);
				return true;
			});
		}
		foldersPreference = findPreference("pref_game_folders");
		androidx.preference.SwitchPreferenceCompat display = findPreference(
				ru.playsoftware.j2meloader.util.GameShortcuts.DISPLAY_PREFERENCE);
		if (display != null) {
			display.setVisible(!ru.playsoftware.j2meloader.BuildConfig.HANDHELD_MODE);
			display.setOnPreferenceChangeListener((preference, value) -> {
				ru.playsoftware.j2meloader.util.GameShortcuts.setDisplayVisible(requireContext(), (Boolean) value);
				return true;
			});
		}
		if (foldersPreference != null) foldersPreference.setOnPreferenceClickListener(preference -> {
			folderLauncher.launch(null);
			return true;
		});
		updateFolderSummary();
		if (findPreference("pref_import_library") != null) findPreference("pref_import_library").setIntent(new Intent(requireActivity(),
				ru.playsoftware.j2meloader.catalog.LibraryImportActivity.class));
		if (findPreference("pref_expert") != null) findPreference("pref_expert").setIntent(new Intent(requireActivity(), ProfilesActivity.class));
		if (findPreference("pref_local_diagnostics") != null) findPreference("pref_local_diagnostics").setOnPreferenceClickListener(preference -> {
			try {
				LogUtils.writeLog();
				Toast.makeText(requireContext(), R.string.log_saved, Toast.LENGTH_SHORT).show();
			} catch (IOException e) {
				Toast.makeText(requireContext(), R.string.error, Toast.LENGTH_SHORT).show();
			}
			return true;
		});
		if (findPreference("pref_about") != null) findPreference("pref_about").setOnPreferenceClickListener(preference -> {
			new AboutDialogFragment().show(getChildFragmentManager(), "about");
			return true;
		});
	}

	private void styleRows(androidx.preference.PreferenceGroup group) {
		for (int i = 0; i < group.getPreferenceCount(); i++) {
			Preference preference = group.getPreference(i);
			preference.setLayoutResource(R.layout.preference_compact);
			preference.setIconSpaceReserved(true);
			if (preference instanceof androidx.preference.PreferenceGroup) styleRows((androidx.preference.PreferenceGroup) preference);
		}
	}

	@Override public void onResume() {
		super.onResume();
		androidx.preference.SwitchPreferenceCompat display = findPreference(
				ru.playsoftware.j2meloader.util.GameShortcuts.DISPLAY_PREFERENCE);
		if (display != null) display.setChecked(ru.playsoftware.j2meloader.util.GameShortcuts.isDisplayVisible(requireContext()));
		androidx.preference.SwitchPreferenceCompat screenshot = findPreference(
				ru.playsoftware.j2meloader.util.Screenshots.BUTTON_PREFERENCE);
		if (screenshot != null) screenshot.setChecked(ru.playsoftware.j2meloader.util.Screenshots.isButtonVisible(requireContext()));
		((SettingsActivity) requireActivity()).screenTitle(getPreferenceScreen().getTitle());
		((SettingsActivity) requireActivity()).focusContentWhenReady();
	}

	private void onFolderSelected(Uri uri) {
		if (uri == null) return;
		try {
			requireContext().getContentResolver().takePersistableUriPermission(uri,
					Intent.FLAG_GRANT_READ_URI_PERMISSION);
		} catch (SecurityException ignored) {
		}
		AppListModel model = new ViewModelProvider(requireActivity()).get(AppListModel.class);
		android.content.Context context = requireContext().getApplicationContext();
		Toast.makeText(context, "Scanning folder...", Toast.LENGTH_SHORT).show();
		executor.execute(() -> {
			String message;
			try {
				GameFolderIndexer.Result result = GameFolderIndexer.scan(context, uri);
				int added = model.getAppRepository().indexGames(result.games);
				GameFolderIndexer.rememberFolder(context, uri);
				message = result.summary(added);
			} catch (Exception error) {
				if (Thread.currentThread().isInterrupted()) return;
				android.util.Log.e("GameFolders", "Folder scan failed", error);
				message = "Could not add folder: " + error.getMessage();
			}
			String status = message;
			new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
				Toast.makeText(context, status, Toast.LENGTH_LONG).show();
				if (isAdded()) updateFolderSummary();
			});
		});
	}

	private void updateFolderSummary() {
		if (foldersPreference == null) return;
		int count = getPreferenceManager().getSharedPreferences()
				.getStringSet("pref_game_folders", Collections.emptySet()).size();
		foldersPreference.setSummary(count == 0 ? "No folders selected" : count + " folder(s)");
	}

	@Override
	public void onDestroy() {
		executor.shutdownNow();
		super.onDestroy();
	}
}
