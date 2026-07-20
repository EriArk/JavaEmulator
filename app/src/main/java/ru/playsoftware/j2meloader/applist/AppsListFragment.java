/*
 * Copyright 2015-2016 Nickolay Savchenko
 * Copyright 2017-2020 Nikita Shakarun
 * Copyright 2018-2022 Yury Kharchenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ru.playsoftware.j2meloader.applist;

import static ru.playsoftware.j2meloader.util.Constants.KEY_APP_URI;
import static ru.playsoftware.j2meloader.util.Constants.KEY_MIDLET_NAME;
import static ru.playsoftware.j2meloader.util.Constants.PREF_APP_SORT;
import static ru.playsoftware.j2meloader.util.Constants.PREF_LAST_PATH;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.sqlite.SQLiteDiskIOException;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;
import androidx.core.widget.TextViewCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.reactivex.Observable;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import ru.playsoftware.j2meloader.BuildConfig;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.appsdb.AppRepository;
import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.config.ConfigActivity;
import ru.playsoftware.j2meloader.config.ProfilesActivity;
import ru.playsoftware.j2meloader.databinding.FragmentAppsListBinding;
import ru.playsoftware.j2meloader.filepicker.FilteredFilePickerFragment;
import ru.playsoftware.j2meloader.info.AboutDialogFragment;
import ru.playsoftware.j2meloader.info.HelpDialogFragment;
import ru.playsoftware.j2meloader.settings.SettingsActivity;
import ru.playsoftware.j2meloader.util.AppUtils;
import ru.playsoftware.j2meloader.util.Constants;
import ru.playsoftware.j2meloader.util.FileUtils;
import ru.playsoftware.j2meloader.util.LogUtils;
import ru.playsoftware.j2meloader.catalog.GameFolderIndexer;
import ru.woesss.j2me.installer.InstallerDialog;

public class AppsListFragment extends Fragment implements AppsListAdapter.Listener {
	private static final String TAG = AppsListFragment.class.getSimpleName();
	private static final String PREF_LIBRARY_VIEW_MODE = "pref_library_view_mode";

	private Uri appUri;
	private SharedPreferences preferences;
	private AppRepository appRepository;
	private AppsListAdapter adapter;
	private Disposable searchViewDisposable;
	private AppItem selectedItem;
	private AppItem artworkTarget;
	private int category = AppsListAdapter.CATEGORY_LIBRARY;

	private FragmentAppsListBinding binding;

	private final ActivityResultLauncher<String> openFileLauncher = registerForActivityResult(
			FileUtils.getFilePicker(),
			this::onPickFileResult);
	private final ActivityResultLauncher<Uri> openFolderLauncher = registerForActivityResult(
			new ActivityResultContracts.OpenDocumentTree(), this::onPickFolderResult);
	private final ActivityResultLauncher<String> artworkLauncher = registerForActivityResult(
			new ActivityResultContracts.GetContent(), this::onArtworkPicked);
	private final ExecutorService folderExecutor = Executors.newSingleThreadExecutor();

	public static AppsListFragment newInstance(Uri data) {
		AppsListFragment fragment = new AppsListFragment();
		Bundle args = new Bundle();
		args.putParcelable(KEY_APP_URI, data);
		fragment.setArguments(args);
		return fragment;
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Bundle args = requireArguments();
		appUri = args.getParcelable(KEY_APP_URI);
		args.remove(KEY_APP_URI);
		preferences = PreferenceManager.getDefaultSharedPreferences(requireActivity());
		adapter = new AppsListAdapter(this);
		adapter.setDisplayMode(preferences.getInt(PREF_LIBRARY_VIEW_MODE, AppsListAdapter.MODE_GALLERY));
		AppListModel appListModel = new ViewModelProvider(requireActivity()).get(AppListModel.class);
		appRepository = appListModel.getAppRepository();
		appRepository.observeErrors(this, this::alertDbError);
		appRepository.observeApps(this, this::onDbUpdated);
	}

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		binding = FragmentAppsListBinding.inflate(inflater, container, false);
		return binding.getRoot();
	}

	@Override
	public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		setHasOptionsMenu(true);
		binding.appsRecycler.setAdapter(adapter);
		binding.appsRecycler.setItemAnimator(null);
		binding.appsRecycler.setHasFixedSize(false);
		adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
			@Override
			public void onChanged() {
				updateEmptyState();
			}
		});
		binding.viewModeGallery.setOnClickListener(v -> setDisplayMode(AppsListAdapter.MODE_GALLERY));
		binding.viewModeList.setOnClickListener(v -> setDisplayMode(AppsListAdapter.MODE_LIST));
		binding.viewModeGrid.setOnClickListener(v -> setDisplayMode(AppsListAdapter.MODE_GRID));
		binding.railLibrary.setSelected(true);
		binding.railLibrary.setOnClickListener(v -> setCategory(AppsListAdapter.CATEGORY_LIBRARY));
		binding.railRecent.setOnClickListener(v -> setCategory(AppsListAdapter.CATEGORY_RECENT));
		binding.railFavorites.setOnClickListener(v -> setCategory(AppsListAdapter.CATEGORY_FAVORITES));
		binding.librarySearch.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				adapter.getFilter().filter(s);
			}

			@Override
			public void afterTextChanged(Editable s) {
			}
		});
		binding.librarySort.setOnClickListener(v -> showSortDialog());
		binding.railAddGame.setOnClickListener(v -> openLastDirectory());
		binding.railFolders.setOnClickListener(v -> openFolderLauncher.launch(null));
		binding.railSettings.setOnClickListener(v ->
				startActivity(new Intent(requireActivity(), SettingsActivity.class)));
		binding.detailPlay.setOnClickListener(v -> {
			if (selectedItem != null) {
				startApp(selectedItem, false);
			}
		});
		binding.detailSettings.setOnClickListener(v -> {
			if (selectedItem != null) {
				startApp(selectedItem, true);
			}
		});
		binding.detailFavorite.setOnClickListener(v -> toggleSelectedFavorite());
		binding.floatingActionButton.setOnClickListener(v -> openLastDirectory());
		updateClock();
		applyDisplayMode();
		updateDetail(adapter.getFirstItem());
	}

	private void openLastDirectory() {
		String path = preferences.getString(PREF_LAST_PATH, null);
		if (path == null) {
			File dir = Environment.getExternalStorageDirectory();
			if (dir.canRead()) {
				path = dir.getAbsolutePath();
			}
		}
		try {
			openFileLauncher.launch(path);
		} catch (ActivityNotFoundException e) {
			Toast.makeText(getContext(), R.string.error_no_picker, Toast.LENGTH_SHORT).show();
			e.printStackTrace();
		}
	}

	private void setDisplayMode(int mode) {
		adapter.setDisplayMode(mode);
		preferences.edit().putInt(PREF_LIBRARY_VIEW_MODE, mode).apply();
		applyDisplayMode();
	}

	private void setCategory(int category) {
		this.category = category;
		adapter.setCategory(category);
		binding.railLibrary.setSelected(category == AppsListAdapter.CATEGORY_LIBRARY);
		binding.railRecent.setSelected(category == AppsListAdapter.CATEGORY_RECENT);
		binding.railFavorites.setSelected(category == AppsListAdapter.CATEGORY_FAVORITES);
		int title = category == AppsListAdapter.CATEGORY_RECENT
				? R.string.launcher_recent
				: category == AppsListAdapter.CATEGORY_FAVORITES
				? R.string.launcher_favorites : R.string.library_title;
		binding.libraryTitle.setText(title);
		updateDetail(adapter.getFirstItem());
	}

	private void applyDisplayMode() {
		int mode = adapter.getDisplayMode();
		if (mode == AppsListAdapter.MODE_LIST) {
			binding.appsRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
		} else {
			binding.appsRecycler.setLayoutManager(new GridLayoutManager(requireContext(), calculateSpanCount(mode)));
		}
		binding.viewModeGallery.setSelected(mode == AppsListAdapter.MODE_GALLERY);
		binding.viewModeList.setSelected(mode == AppsListAdapter.MODE_LIST);
		binding.viewModeGrid.setSelected(mode == AppsListAdapter.MODE_GRID);
		updateDetail(selectedItem == null ? adapter.getFirstItem() : selectedItem);
	}

	private int calculateSpanCount(int mode) {
		int screenWidthDp = getResources().getConfiguration().screenWidthDp;
		if (isCompactPhoneLayout()) {
			int minCardWidth = mode == AppsListAdapter.MODE_GALLERY ? 156 : 124;
			return Math.max(mode == AppsListAdapter.MODE_GALLERY ? 1 : 2,
					Math.max(1, screenWidthDp - 24) / minCardWidth);
		}
		int reserved = 132 + 330 + 32;
		int available = Math.max(320, screenWidthDp - reserved);
		int minCardWidth = mode == AppsListAdapter.MODE_GALLERY ? 150 : 112;
		return Math.max(mode == AppsListAdapter.MODE_GALLERY ? 2 : 3, available / minCardWidth);
	}

	@Override
	public void onConfigurationChanged(@NonNull Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		applyDisplayMode();
	}

	private void alertDbError(Throwable throwable) {
		Activity activity = getActivity();
		if (activity == null) {
			Log.e(TAG, "Db error detected", throwable);
			return;
		}
		if (throwable instanceof SQLiteDiskIOException) {
			Toast.makeText(activity, R.string.error_disk_io, Toast.LENGTH_SHORT).show();
		} else {
			String msg = activity.getString(R.string.error) + ": " + throwable.getMessage();
			Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show();
		}
	}

	private void onPickFileResult(Uri uri) {
		if (uri == null) {
			return;
		}
		preferences.edit()
				.putString(Constants.PREF_LAST_PATH, FilteredFilePickerFragment.getLastPath())
				.apply();
		InstallerDialog.newInstance(uri).show(getParentFragmentManager(), "installer");
	}

	private void onPickFolderResult(Uri uri) {
		if (uri == null) {
			return;
		}
		try {
			requireContext().getContentResolver().takePersistableUriPermission(uri,
					Intent.FLAG_GRANT_READ_URI_PERMISSION);
		} catch (SecurityException ignored) {
		}
		java.util.Set<String> folders = new java.util.HashSet<>(preferences.getStringSet(
				"pref_game_folders", java.util.Collections.emptySet()));
		folders.add(uri.toString());
		preferences.edit().putStringSet("pref_game_folders", folders).apply();
		folderExecutor.execute(() -> {
			List<AppItem> games = GameFolderIndexer.scan(requireContext().getApplicationContext(), uri);
			appRepository.insert(games);
			requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(),
					getString(R.string.folder_indexed, games.size()), Toast.LENGTH_SHORT).show());
		});
	}

	private void onArtworkPicked(Uri uri) {
		AppItem target = artworkTarget;
		artworkTarget = null;
		if (uri == null || target == null || !"ready".equals(target.getPreparationState())) {
			return;
		}
		folderExecutor.execute(() -> {
			File cover = new File(target.getPathExt(), Config.MIDLET_COVER_FILE);
			try (InputStream input = requireContext().getContentResolver().openInputStream(uri);
				 FileOutputStream output = new FileOutputStream(cover)) {
				if (input == null) throw new IOException("Unable to open image");
				byte[] buffer = new byte[32 * 1024];
				int read;
				while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
				target.setCoverPathExt(Config.MIDLET_COVER_FILE);
				appRepository.update(target);
				requireActivity().runOnUiThread(() -> updateDetail(target));
			} catch (IOException e) {
				requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(),
						R.string.error, Toast.LENGTH_SHORT).show());
			}
		});
	}

	private void alertRename(AppItem item) {
		FragmentActivity activity = requireActivity();
		EditText editText = new EditText(activity);
		editText.setText(item.getTitle());
		float density = getResources().getDisplayMetrics().density;
		LinearLayout linearLayout = new LinearLayout(activity);
		LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.WRAP_CONTENT);
		int margin = (int) (density * 20);
		params.setMargins(margin, 0, margin, 0);
		linearLayout.addView(editText, params);
		int paddingVertical = (int) (density * 16);
		int paddingHorizontal = (int) (density * 8);
		editText.setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical);
		AlertDialog.Builder builder = new AlertDialog.Builder(activity)
				.setTitle(R.string.action_context_rename)
				.setView(linearLayout)
				.setPositiveButton(android.R.string.ok, (dialogInterface, i) -> {
					String title = editText.getText().toString().trim();
					if (title.equals("")) {
						Toast.makeText(getActivity(), R.string.error, Toast.LENGTH_SHORT).show();
					} else {
						item.setTitle(title);
						appRepository.update(item);
					}
				})
				.setNegativeButton(android.R.string.cancel, null);
		builder.show();
	}

	private void alertDelete(AppItem item) {
		AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity())
				.setTitle(android.R.string.dialog_alert_title)
				.setMessage(R.string.message_delete)
				.setPositiveButton(android.R.string.ok, (dialogInterface, i) -> {
					AppUtils.deleteApp(item);
					appRepository.delete(item);
				})
				.setNegativeButton(android.R.string.cancel, null);
		builder.show();
	}

	@Override
	public void onAppClicked(AppItem item) {
		startApp(item, false);
	}

	@Override
	public void onAppFocused(AppItem item) {
		updateDetail(item);
	}

	@Override
	public void onAppActionsRequested(View anchor, AppItem item) {
		showActions(anchor, item);
	}

	private void startApp(AppItem item, boolean showSettings) {
		if (!"ready".equals(item.getPreparationState()) && item.getSourceUri() != null) {
			InstallerDialog.newInstance(Uri.parse(item.getSourceUri()), true, false)
					.show(getParentFragmentManager(), "installer");
			return;
		}
		if (!showSettings) {
			appRepository.recordLaunch(item);
		}
		Config.startApp(requireActivity(), item.getTitle(), item.getPathExt(), showSettings);
	}

	private void toggleSelectedFavorite() {
		if (selectedItem == null) {
			return;
		}
		appRepository.toggleFavorite(selectedItem);
		updateDetail(selectedItem);
	}

	public boolean handleControllerKey(int keyCode) {
		if (keyCode == KeyEvent.KEYCODE_BUTTON_Y) {
			toggleSelectedFavorite();
			return true;
		}
		if (keyCode == KeyEvent.KEYCODE_BUTTON_L1 || keyCode == KeyEvent.KEYCODE_BUTTON_R1) {
			int direction = keyCode == KeyEvent.KEYCODE_BUTTON_R1 ? 1 : -1;
			int next = (adapter.getDisplayMode() + direction + 3) % 3;
			setDisplayMode(next);
			return true;
		}
		return false;
	}

	private void showActions(View anchor, AppItem appItem) {
		PopupMenu popup = new PopupMenu(requireContext(), anchor);
		popup.inflate(R.menu.context_main);
		Menu menu = popup.getMenu();
		if (!ShortcutManagerCompat.isRequestPinShortcutSupported(requireContext())) {
			menu.findItem(R.id.action_context_shortcut).setVisible(false);
		}
		if (!new File(appItem.getPathExt() + Config.MIDLET_RES_FILE).exists()) {
			menu.findItem(R.id.action_context_reinstall).setVisible(false);
			menu.findItem(R.id.action_context_artwork).setVisible(false);
		}
		popup.setOnMenuItemClickListener(item -> {
			int itemId = item.getItemId();
			if (itemId == R.id.action_context_shortcut) {
				requestAddShortcut(appItem);
			} else if (itemId == R.id.action_context_rename) {
				alertRename(appItem);
			} else if (itemId == R.id.action_context_settings) {
				startApp(appItem, true);
			} else if (itemId == R.id.action_context_artwork) {
				artworkTarget = appItem;
				artworkLauncher.launch("image/*");
			} else if (itemId == R.id.action_context_reinstall) {
				InstallerDialog.newInstance(appItem.getId()).show(getParentFragmentManager(), "installer");
			} else if (itemId == R.id.action_context_delete) {
				alertDelete(appItem);
			} else {
				return false;
			}
			return true;
		});
		popup.show();
	}

	private void requestAddShortcut(AppItem appItem) {
		FragmentActivity activity = requireActivity();
		Bitmap bitmap = AppUtils.getIconBitmap(appItem);
		IconCompat icon;
		if (bitmap == null) {
			icon = IconCompat.createWithResource(activity, R.mipmap.ic_launcher);
		} else {
			int width = bitmap.getWidth();
			int height = bitmap.getHeight();
			ActivityManager am = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
			int iconSize = am.getLauncherLargeIconSize();
			Rect src;
			if (width > height) {
				int left = (width - height) / 2;
				src = new Rect(left, 0, left + height, height);
			} else if (width < height) {
				int top = (height - width) / 2;
				src = new Rect(0, top, width, top + width);
			} else {
				src = null;
			}
			Bitmap scaled = Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888);
			Canvas canvas = new Canvas(scaled);
			canvas.drawBitmap(bitmap, src, new RectF(0, 0, iconSize, iconSize), null);
			icon = IconCompat.createWithBitmap(scaled);
		}
		String title = appItem.getTitle();
		Intent launchIntent = new Intent(Intent.ACTION_DEFAULT, Uri.parse(appItem.getPathExt()),
				activity, ConfigActivity.class);
		launchIntent.putExtra(KEY_MIDLET_NAME, title);
		ShortcutInfoCompat shortcut = new ShortcutInfoCompat.Builder(activity, title)
				.setIntent(launchIntent)
				.setShortLabel(title)
				.setIcon(icon)
				.build();
		ShortcutManagerCompat.requestPinShortcut(activity, shortcut, null);
	}

	@Override
	public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
		inflater.inflate(R.menu.main, menu);
		final MenuItem searchItem = menu.findItem(R.id.action_search);
		SearchView searchView = (SearchView) searchItem.getActionView();
		searchViewDisposable = Observable.create((ObservableOnSubscribe<String>) emitter ->
				searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
					@Override
					public boolean onQueryTextSubmit(String query) {
						emitter.onNext(query);
						return true;
					}

					@Override
					public boolean onQueryTextChange(String newText) {
						emitter.onNext(newText);
						return true;
					}
				})).debounce(300, TimeUnit.MILLISECONDS)
				.map(String::toLowerCase)
				.distinctUntilChanged()
				.observeOn(AndroidSchedulers.mainThread())
				.subscribe(charSequence -> adapter.getFilter().filter(charSequence));
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		FragmentActivity activity = requireActivity();
		int itemId = item.getItemId();
		if (itemId == R.id.action_about) {
			AboutDialogFragment aboutDialogFragment = new AboutDialogFragment();
			aboutDialogFragment.show(getChildFragmentManager(), "about");
		} else if (itemId == R.id.action_profiles) {
			Intent intentProfiles = new Intent(activity, ProfilesActivity.class);
			startActivity(intentProfiles);
		} else if (item.getItemId() == R.id.action_settings) {
			startActivity(new Intent(activity, SettingsActivity.class));
			return true;
		} else if (itemId == R.id.action_help) {
			HelpDialogFragment helpDialogFragment = new HelpDialogFragment();
			helpDialogFragment.show(getChildFragmentManager(), "help");
		} else if (itemId == R.id.action_save_log) {
			try {
				LogUtils.writeLog();
				Toast.makeText(activity, R.string.log_saved, Toast.LENGTH_SHORT).show();
			} catch (IOException e) {
				e.printStackTrace();
				Toast.makeText(activity, R.string.error, Toast.LENGTH_SHORT).show();
			}
		} else if (itemId == R.id.action_exit_app) {
			activity.finish();
		} else if (itemId == R.id.action_sort) {
			showSortDialog();
		}
		return false;
	}

	private void showSortDialog() {
		int variant = appRepository.getSort();
		SortAdapter adapter = new SortAdapter(requireActivity(), variant);
		AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity())
				.setTitle(R.string.pref_app_sort_title)
				.setAdapter(adapter, (d, v) -> {
					adapter.setVariant(v);
					setSort(v);
					d.dismiss();
				});
		builder.show();
	}

	private void setSort(int sortVariant) {
		if (appRepository.getSort() == sortVariant) {
			sortVariant |= 0x80000000;
		}
		preferences.edit().putInt(PREF_APP_SORT, sortVariant).apply();
	}

	private void onDbUpdated(List<AppItem> items) {
		adapter.setItems(items);
		if (appUri != null) {
			InstallerDialog.newInstance(appUri, true).show(getParentFragmentManager(), "installer");
			appUri = null;
		}
		updateDetail(adapter.getFirstItem());
		updateEmptyState();
	}

	private void updateEmptyState() {
		if (binding == null) {
			return;
		}
		boolean empty = adapter.getItemCount() == 0;
		binding.empty.setVisibility(empty ? View.VISIBLE : View.GONE);
		binding.appsRecycler.setVisibility(empty ? View.GONE : View.VISIBLE);
		binding.libraryCount.setText(getString(R.string.library_game_count, adapter.getItemCount()));
	}

	private void updateDetail(AppItem item) {
		if (binding == null) {
			return;
		}
		selectedItem = item;
		binding.detailPanel.setVisibility(item != null && !isCompactPhoneLayout()
				? View.VISIBLE : View.GONE);
		if (item == null) {
			return;
		}
		Drawable cover = Drawable.createFromPath(item.getCoverPathExt());
		if (cover != null) {
			cover.setFilterBitmap(false);
			binding.detailCover.setScaleType(ImageView.ScaleType.CENTER_CROP);
			binding.detailCover.setImageDrawable(cover);
		} else {
			Bitmap icon = IconArtUtils.loadLargeIcon(item.getImagePathExt(), dp(210));
			binding.detailCover.setScaleType(ImageView.ScaleType.CENTER_CROP);
			binding.detailCover.setImageBitmap(IconArtUtils.createFallback(item.getTitle(),
					dp(420), dp(260), icon));
		}
		binding.detailTitle.setText(item.getTitle());
		binding.detailFavorite.setText(item.isFavorite()
				? R.string.remove_favorite : R.string.add_favorite);
		String author = item.getAuthor() == null ? "" : item.getAuthor();
		String version = item.getVersion() == null ? "" : item.getVersion();
		binding.detailMeta.setText(author + "  " + version);
	}

	private int dp(int value) {
		return Math.round(value * getResources().getDisplayMetrics().density);
	}

	private boolean isCompactPhoneLayout() {
		return !BuildConfig.HANDHELD_MODE
				&& getResources().getConfiguration().orientation
				== Configuration.ORIENTATION_PORTRAIT;
	}

	private void updateClock() {
		binding.launcherClock.setText(new SimpleDateFormat("HH:mm", Locale.US).format(new Date()));
	}

	private static class SortAdapter extends ArrayAdapter<String> {
		private int variant;
		private final Drawable drawableArrowDown;
		private final Drawable drawableArrowUp;

		public SortAdapter(FragmentActivity activity, int variant) {
			super(activity,
					android.R.layout.simple_list_item_1,
					activity.getResources().getStringArray(R.array.pref_app_sort_entries));
			this.variant = variant;
			drawableArrowDown = AppCompatResources.getDrawable(activity, R.drawable.ic_arrow_down);
			drawableArrowUp = AppCompatResources.getDrawable(activity, R.drawable.ic_arrow_up);
		}

		@NonNull
		@Override
		public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
			TextView tv = (TextView) super.getView(position, convertView, parent);
			if ((variant & 0x7FFFFFFF) == position) {
				TextViewCompat.setCompoundDrawablesRelativeWithIntrinsicBounds(tv, null, null,
						variant >= 0 ? drawableArrowDown : drawableArrowUp, null);
			} else {
				tv.setCompoundDrawables(null, null, null, null);
			}
			return tv;
		}

		public void setVariant(int variant) {
			if (variant == this.variant) {
				variant |= 0x80000000;
			}
			this.variant = variant;
			notifyDataSetChanged();
		}
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		binding = null;
	}

	@Override
	public void onDestroy() {
		folderExecutor.shutdownNow();
		if (searchViewDisposable != null) {
			searchViewDisposable.dispose();
		}
		super.onDestroy();
	}
}
