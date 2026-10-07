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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.applist.AppItem;
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
		foldersPreference = findPreference("pref_game_folders");
		foldersPreference.setOnPreferenceClickListener(preference -> {
			folderLauncher.launch(null);
			return true;
		});
		updateFolderSummary();
		findPreference("pref_import_library").setIntent(new Intent(requireActivity(),
				ru.playsoftware.j2meloader.catalog.LibraryImportActivity.class));
		findPreference("pref_expert").setIntent(new Intent(requireActivity(), ProfilesActivity.class));
		findPreference("pref_local_diagnostics").setOnPreferenceClickListener(preference -> {
			try {
				LogUtils.writeLog();
				Toast.makeText(requireContext(), R.string.log_saved, Toast.LENGTH_SHORT).show();
			} catch (IOException e) {
				Toast.makeText(requireContext(), R.string.error, Toast.LENGTH_SHORT).show();
			}
			return true;
		});
		findPreference("pref_about").setOnPreferenceClickListener(preference -> {
			new AboutDialogFragment().show(getChildFragmentManager(), "about");
			return true;
		});
	}

	private void onFolderSelected(Uri uri) {
		if (uri == null) return;
		try {
			requireContext().getContentResolver().takePersistableUriPermission(uri,
					Intent.FLAG_GRANT_READ_URI_PERMISSION);
		} catch (SecurityException ignored) {
		}
		Set<String> folders = new HashSet<>(getPreferenceManager().getSharedPreferences()
				.getStringSet("pref_game_folders", Collections.emptySet()));
		folders.add(uri.toString());
		getPreferenceManager().getSharedPreferences().edit()
				.putStringSet("pref_game_folders", folders).apply();
		updateFolderSummary();
		AppListModel model = new ViewModelProvider(requireActivity()).get(AppListModel.class);
		executor.execute(() -> {
			List<AppItem> games = GameFolderIndexer.scan(requireContext().getApplicationContext(), uri);
			model.getAppRepository().insert(games);
			requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(),
					getString(R.string.folder_indexed, games.size()), Toast.LENGTH_SHORT).show());
		});
	}

	private void updateFolderSummary() {
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
