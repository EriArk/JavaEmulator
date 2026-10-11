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
import androidx.activity.OnBackPressedCallback;
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
import ru.playsoftware.j2meloader.input.GameMenuDialog;
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
	private int artworkTargetId = -1;
	private boolean pickingIcon;
	private int category = AppsListAdapter.CATEGORY_LIBRARY;
	private boolean searchExpanded;
	private String restoredSearch = "";
	private OnBackPressedCallback searchBack;
	private boolean compactLibrary;
	private boolean initialGameFocus = true;
	private int pendingGameFocus = -1;

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
		if (savedInstanceState != null) {
			artworkTargetId = savedInstanceState.getInt("artworkTargetId", -1);
			pickingIcon = savedInstanceState.getBoolean("pickingIcon");
			searchExpanded = savedInstanceState.getBoolean("searchExpanded");
			restoredSearch = savedInstanceState.getString("searchQuery", "");
			category = savedInstanceState.getInt("libraryCategory", AppsListAdapter.CATEGORY_LIBRARY);
		}
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
		binding.appsRecycler.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
			if (binding == null || pendingGameFocus < 0) return;
			RecyclerView.ViewHolder holder = binding.appsRecycler.findViewHolderForAdapterPosition(pendingGameFocus);
			if (holder != null && binding.getRoot().hasWindowFocus() && holder.itemView.requestFocus()) {
				pendingGameFocus = -1;
			}
		});
		binding.appsRecycler.getViewTreeObserver().addOnWindowFocusChangeListener(hasFocus -> {
			if (hasFocus && binding != null && pendingGameFocus >= 0) binding.appsRecycler.requestLayout();
		});
		binding.launcherSoftbar.setVisibility(BuildConfig.HANDHELD_MODE ? View.VISIBLE : View.GONE);
		binding.appsRecycler.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
			// notifyDataSetChanged during layout can strand RecyclerView's pending updates and focus.
			v.post(() -> {
				if (binding != null) adapter.setAvailableHeight(Math.round(binding.appsRecycler.getHeight()
						/ getResources().getDisplayMetrics().density));
			});
			if (r - l != or - ol && binding.appsRecycler.getLayoutManager() instanceof GridLayoutManager) {
				((GridLayoutManager) binding.appsRecycler.getLayoutManager())
						.setSpanCount(calculateSpanCount(adapter.getDisplayMode()));
			}
		});
		initialGameFocus = true;
		adapter.registerAdapterDataObserver(libraryObserver);
		binding.viewModeGallery.setOnClickListener(v -> setDisplayMode(AppsListAdapter.MODE_GALLERY));
		binding.viewModeList.setOnClickListener(v -> setDisplayMode(AppsListAdapter.MODE_LIST));
		binding.viewModeGrid.setOnClickListener(v -> setDisplayMode(AppsListAdapter.MODE_GRID));
		for (View tab : new View[]{binding.railLibrary, binding.railRecent, binding.railFavorites,
				binding.viewModeGallery, binding.viewModeList, binding.viewModeGrid}) {
			tab.setBackgroundResource(R.drawable.bg_library_tab);
		}
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
				restoredSearch = s.toString();
				adapter.setSearchQuery(s);
			}

			@Override
			public void afterTextChanged(Editable s) {
			}
		});
		binding.librarySort.setOnClickListener(v -> showSortDialog());
		binding.libraryCategory.setOnClickListener(v -> showCategoryMenu());
		binding.libraryMenu.setOnClickListener(v -> showLibraryMenu());
		binding.librarySearchToggle.setOnClickListener(v -> {
			if (searchExpanded) { closeSearch(); return; }
			searchExpanded = true;
			pendingGameFocus = -1;
			updateResponsiveToolbar();
			binding.librarySearch.requestFocus();
			binding.librarySearch.post(() -> {
				if (binding != null && searchExpanded) {
					android.view.inputmethod.InputMethodManager ime = (android.view.inputmethod.InputMethodManager)
							requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
					ime.showSoftInput(binding.librarySearch, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
				}
			});
		});
		binding.librarySearchClose.setOnClickListener(v -> closeSearch());
		searchBack = new OnBackPressedCallback(false) {
			@Override public void handleOnBackPressed() { closeSearch(); }
		};
		requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), searchBack);
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
		setCategory(category);
		binding.librarySearch.setText(restoredSearch);
		applyDisplayMode();
		updateDetail(adapter.getFirstItem());
		libraryObserver.onChanged();
	}

	private final RecyclerView.AdapterDataObserver libraryObserver = new RecyclerView.AdapterDataObserver() {
		@Override public void onChanged() {
			updateEmptyState();
			if (binding != null && BuildConfig.HANDHELD_MODE && initialGameFocus && adapter.getItemCount() > 0) {
				initialGameFocus = false;
				if (!searchExpanded) focusGame(null);
			}
		}
	};

	private void closeSearch() {
		searchExpanded = false;
		binding.librarySearch.setText("");
		binding.librarySearch.clearFocus();
		android.view.inputmethod.InputMethodManager ime = (android.view.inputmethod.InputMethodManager)
				 requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
		ime.hideSoftInputFromWindow(binding.librarySearch.getWindowToken(), 0);
		updateResponsiveToolbar();
		if (BuildConfig.HANDHELD_MODE) focusGame(selectedItem == null ? null : selectedItem.getPath());
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
		boolean restoreFocus = binding.appsRecycler.hasFocus();
		String selectedPath = selectedItem == null ? null : selectedItem.getPath();
		adapter.setDisplayMode(mode);
		preferences.edit().putInt(PREF_LIBRARY_VIEW_MODE, mode).apply();
		applyDisplayMode();
		if (restoreFocus) focusGame(selectedPath);
	}

	private void focusGame(String path) {
		int position = 0;
		for (int i = 0; i < adapter.getItemCount(); i++) {
			if (adapter.getItem(i).getPath().equals(path)) { position = i; break; }
		}
		pendingGameFocus = position;
		binding.appsRecycler.scrollToPosition(position);
		binding.appsRecycler.requestLayout();
	}

	private GameMenuDialog libraryDialog(String title) {
		GameMenuDialog dialog = new GameMenuDialog(requireContext(),
				getResources().getQuantityString(R.plurals.library_games, adapter.getItemCount(), adapter.getItemCount()));
		dialog.show();
		dialog.page(title);
		return dialog;
	}

	private void showCategoryMenu() {
		GameMenuDialog dialog = libraryDialog("Library");
		String[] names = {"All games", "Recent", "Favorites"};
		for (int i = 0; i < names.length; i++) {
			int target = i;
			dialog.action(names[i], 0, () -> { setCategory(target); dialog.dismiss(); });
		}
	}

	private void showLibraryMenu() {
		GameMenuDialog dialog = libraryDialog("Library menu");
		populateLibraryMenu(dialog);
	}

	private void populateLibraryMenu(GameMenuDialog dialog) {
		dialog.page("Library menu");
		dialog.action("View: " + new String[]{"Gallery", "List", "Grid"}[adapter.getDisplayMode()],
				android.R.drawable.ic_menu_view, () -> {
			dialog.page("View");
			dialog.setBackAction(() -> populateLibraryMenu(dialog));
			String[] modes = {"Gallery", "List", "Grid"};
			for (int i = 0; i < modes.length; i++) {
				int target = i;
				dialog.action(modes[i], 0, () -> {
					setDisplayMode(target); dialog.dismiss();
					if (BuildConfig.HANDHELD_MODE) focusGame(selectedItem == null ? null : selectedItem.getPath());
				});
			}
		});
		dialog.action("Sort", R.drawable.ic_setting_sort, () -> {
			dialog.page("Sort");
			dialog.setBackAction(() -> populateLibraryMenu(dialog));
			dialog.choice("Sort by", getResources().getStringArray(R.array.pref_app_sort_entries),
					appRepository.getSort() & Integer.MAX_VALUE, index -> preferences.edit()
							.putInt(PREF_APP_SORT, index | (appRepository.getSort() & Integer.MIN_VALUE)).apply());
			dialog.toggle("Reverse order", appRepository.getSort() < 0, checked -> {
				int sort = appRepository.getSort() & Integer.MAX_VALUE;
				preferences.edit().putInt(PREF_APP_SORT, checked ? sort | Integer.MIN_VALUE : sort).apply();
			});
		});
		dialog.action("Add game", R.drawable.ic_add_white, () -> { dialog.dismiss(); openLastDirectory(); });
		dialog.action("Add folder", R.drawable.ic_setting_folder, () -> { dialog.dismiss(); openFolderLauncher.launch(null); });
		dialog.action("Settings", R.drawable.ic_baseline_tune_24, () -> {
			dialog.dismiss(); startActivity(new Intent(requireActivity(), SettingsActivity.class));
		});
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
		binding.libraryCategory.setText(category == AppsListAdapter.CATEGORY_LIBRARY
				? "All games" : category == AppsListAdapter.CATEGORY_RECENT ? "Recent" : "Favorites");
		updateDetail(adapter.getFirstItem());
	}

	private void applyDisplayMode() {
		updateResponsiveToolbar();
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
		int width = binding.appsRecycler.getWidth();
		int available = width > 0 ? Math.round(width / getResources().getDisplayMetrics().density)
				: getResources().getConfiguration().screenWidthDp - 24;
		return Math.max(1, available / (mode == AppsListAdapter.MODE_GALLERY ? 220 : 144));
	}

	private void updateResponsiveToolbar() {
		boolean wide = getResources().getConfiguration().screenWidthDp >= 560;
		boolean shortLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE
				&& getResources().getConfiguration().screenHeightDp < 480;
		boolean compact = shortLandscape || getResources().getConfiguration().screenWidthDp < 600;
		compactLibrary = compact;
		adapter.setCompact(shortLandscape);
		binding.libraryCategoryRow.setVisibility(compact ? View.GONE : View.VISIBLE);
		binding.libraryCount.setVisibility(compact ? View.GONE : View.VISIBLE);
		binding.libraryCategory.setVisibility(compact ? View.VISIBLE : View.GONE);
		binding.librarySearchToggle.setVisibility(compact ? View.VISIBLE : View.GONE);
		binding.libraryMenu.setVisibility(compact ? View.VISIBLE : View.GONE);
		binding.railAddGame.setVisibility(compact ? View.GONE : View.VISIBLE);
		binding.railFolders.setVisibility(compact ? View.GONE : View.VISIBLE);
		binding.railSettings.setVisibility(compact ? View.GONE : View.VISIBLE);
		binding.libraryModeRow.setVisibility(compact ? View.GONE : View.VISIBLE);
		binding.librarySort.setVisibility(compact ? View.GONE : View.VISIBLE);
		binding.librarySearchClose.setVisibility(compact ? View.VISIBLE : View.GONE);
		if (searchBack != null) searchBack.setEnabled(compact && (searchExpanded || binding.librarySearch.length() > 0));
		binding.librarySearchToggle.setSelected(searchExpanded || binding.librarySearch.length() > 0);
		binding.librarySearchRow.setVisibility(!compact || searchExpanded
				|| binding.librarySearch.length() > 0 ? View.VISIBLE : View.GONE);
		ViewGroup parent = (ViewGroup) binding.libraryModeRow.getParent();
		boolean inline = wide && !compact;
		ViewGroup desired = inline ? binding.librarySearchRow : binding.libraryRoot;
		if (parent != desired) {
			parent.removeView(binding.libraryModeRow);
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(inline ? dp(276) : -1, dp(48));
			lp.setMargins(dp(12),0,dp(inline ? 0 : 12),0);
			if (inline) desired.addView(binding.libraryModeRow,lp);
			else desired.addView(binding.libraryModeRow,3,lp);
		}
		ViewGroup.LayoutParams bar = binding.launcherSoftbar.getLayoutParams();
		bar.height = dp(compact ? 24 : 32);
		binding.launcherSoftbar.setLayoutParams(bar);
		binding.launcherSoftbar.setText(compact ? "A  Play     X  Options     L/R  View     Start  Menu"
				: "A  Play     X  Options     Y  Favorite     L/R  View");
		binding.launcherTitle.setGravity(android.view.Gravity.CENTER_VERTICAL);
		binding.launcherTitle.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
		binding.launcherTitle.setTextSize(18);
		TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(binding.launcherTitle, 14, 18, 1,
				android.util.TypedValue.COMPLEX_UNIT_SP);
		binding.launcherTitle.setText("AbyssME");
		binding.launcherTitle.setSingleLine(true);
		binding.launcherTitle.setHorizontallyScrolling(false);
		binding.launcherTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
		binding.railRecent.setText("Recent");
		binding.railFavorites.setText("Favorites");
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
		ru.playsoftware.j2meloader.catalog.AdditionalGames.install(requireContext(), getParentFragmentManager(), uri, false, false);
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
		int targetId = artworkTargetId;
		boolean icon = pickingIcon;
		artworkTargetId = -1;
		if (uri == null || targetId < 0) return;
		Context context = requireContext().getApplicationContext();
		folderExecutor.execute(() -> {
			AppItem target = appRepository.get(targetId);
			if (target == null || !"ready".equals(target.getPreparationState())) return;
			File directory = new File(target.getPathExt());
			try (InputStream input = context.getContentResolver().openInputStream(uri)) {
				if (input == null) throw new IOException("Unable to open image");
				GameArtwork.saveUserImage(directory, icon, input);
				GameArtwork.applyPaths(target, directory);
				appRepository.update(target);
			} catch (IOException e) {
				showArtworkError(context, e);
			}
		});
	}

	private void showArtworkMenu(AppItem item) {
		GameMenuDialog dialog = new GameMenuDialog(requireContext(), item.getTitle());
		dialog.show();
		populateArtworkMenu(dialog, item);
	}

	private void populateArtworkMenu(GameMenuDialog dialog, AppItem item) {
		dialog.page("Artwork");
		for (boolean icon : new boolean[]{true, false}) {
			dialog.action(icon ? "Icon" : "Cover", android.R.drawable.ic_menu_gallery, () -> {
				dialog.page(icon ? "Icon" : "Cover");
				dialog.setBackAction(() -> populateArtworkMenu(dialog, item));
				dialog.action("Choose image", android.R.drawable.ic_menu_gallery, () -> {
					dialog.dismiss(); artworkTargetId = item.getId(); pickingIcon = icon;
					artworkLauncher.launch("image/*");
				});
				dialog.action("Use automatic", android.R.drawable.ic_menu_revert, () -> {
					dialog.dismiss(); refreshArtwork(item, icon ? GameArtwork.USER_ICON : GameArtwork.USER_COVER);
				});
			});
		}
		if (new File(item.getPathExt(), Config.MIDLET_RES_FILE).isFile()) {
			dialog.action("Refresh automatic art", android.R.drawable.ic_popup_sync, () -> {
				dialog.page("Refresh automatic art?");
				dialog.setBackAction(() -> populateArtworkMenu(dialog, item));
				dialog.action("Replace legacy art, keep custom images", 0, () -> {
					dialog.dismiss(); refreshArtwork(item, null);
				});
				dialog.action("Cancel", 0, dialog::dismiss);
			});
		}
	}

	private void refreshArtwork(AppItem target, String reset) {
		Context context = requireContext().getApplicationContext();
		folderExecutor.execute(() -> {
			try {
				File directory = new File(target.getPathExt());
				if (GameArtwork.USER_ICON.equals(reset) && !new File(directory, GameArtwork.AUTO_MARKER).exists()
						&& !new File(directory, GameArtwork.USER_COVER).exists()) {
					File cover = new File(directory, "cover.png");
					if (cover.isFile()) java.nio.file.Files.copy(cover.toPath(),
							new File(directory, GameArtwork.USER_COVER).toPath());
				}
				if (new File(directory, Config.MIDLET_RES_FILE).isFile()) GameArtwork.refresh(directory);
				if (reset != null) java.nio.file.Files.deleteIfExists(new File(directory, reset).toPath());
				GameArtwork.applyPaths(target, directory);
				appRepository.update(target);
			} catch (IOException e) { showArtworkError(context, e); }
		});
	}

	private void showArtworkError(Context context, Exception e) {
		Log.w(TAG, "Artwork update failed", e);
		new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
				Toast.makeText(context, "Could not update artwork", Toast.LENGTH_SHORT).show());
	}

	@Override public void onSaveInstanceState(@NonNull Bundle state) {
		super.onSaveInstanceState(state);
		state.putInt("artworkTargetId", artworkTargetId);
		state.putBoolean("pickingIcon", pickingIcon);
		state.putBoolean("searchExpanded", searchExpanded);
		state.putString("searchQuery", binding == null ? restoredSearch : binding.librarySearch.getText().toString());
		state.putInt("libraryCategory", category);
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
		ru.playsoftware.j2meloader.input.GameMenuDialog dialog =
				new ru.playsoftware.j2meloader.input.GameMenuDialog(requireActivity(), item.getTitle());
		dialog.show(); dialog.page("Delete game?");
		dialog.message("This removes the installed game, its settings and saves. Original source files are kept.");
		dialog.action("Cancel", android.R.drawable.ic_menu_close_clear_cancel, dialog::dismiss);
		dialog.action("Delete game", android.R.drawable.ic_menu_delete, () -> {
			dialog.dismiss();
			AppUtils.deleteApp(item);
			appRepository.delete(item);
		});
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
			ru.playsoftware.j2meloader.catalog.AdditionalGames.install(requireContext(), getParentFragmentManager(),
					Uri.parse(item.getSourceUri()), true, false);
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
		if (binding.getRoot().findFocus() == null) {
			if (keyCode == KeyEvent.KEYCODE_BUTTON_A && selectedItem != null) {
				startApp(selectedItem, false);
				return true;
			}
			if (ru.playsoftware.j2meloader.input.ControllerInput.direction(keyCode) != 0) {
				focusGame(selectedItem == null ? null : selectedItem.getPath());
				return true;
			}
		}
		if (keyCode == KeyEvent.KEYCODE_BUTTON_START || keyCode == KeyEvent.KEYCODE_MENU) {
			showLibraryMenu();
			return true;
		}
		if (keyCode == KeyEvent.KEYCODE_BUTTON_B && compactLibrary && binding.librarySearchRow.getVisibility() == View.VISIBLE) {
			closeSearch();
			return true;
		}
		if (keyCode == KeyEvent.KEYCODE_BUTTON_X && selectedItem != null) {
			View focus = binding.appsRecycler.findFocus();
			showActions(focus != null ? focus : binding.appsRecycler, selectedItem);
			return true;
		}
		if (keyCode == KeyEvent.KEYCODE_BUTTON_Y) {
			toggleSelectedFavorite();
			return true;
		}
		if (keyCode == KeyEvent.KEYCODE_BUTTON_L1 || keyCode == KeyEvent.KEYCODE_BUTTON_R1) {
			int direction = keyCode == KeyEvent.KEYCODE_BUTTON_R1 ? 1 : -1;
			int next = (adapter.getDisplayMode() + direction + 3) % 3;
			String path = selectedItem == null ? null : selectedItem.getPath();
			setDisplayMode(next);
			focusGame(path);
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
		if (ru.playsoftware.j2meloader.catalog.AdditionalGames.isManaged(new File(appItem.getPathExt()))) {
			menu.findItem(R.id.action_context_shortcut).setVisible(false);
			menu.findItem(R.id.action_context_artwork).setVisible(true);
		}
		PopupMenu.OnMenuItemClickListener action = item -> {
			int itemId = item.getItemId();
			if (itemId == R.id.action_context_shortcut) {
				requestAddShortcut(appItem);
			} else if (itemId == R.id.action_context_rename) {
				alertRename(appItem);
			} else if (itemId == R.id.action_context_settings) {
				startApp(appItem, true);
			} else if (itemId == R.id.action_context_artwork) {
				showArtworkMenu(appItem);
			} else if (itemId == R.id.action_context_reinstall) {
				InstallerDialog.newInstance(appItem.getId()).show(getParentFragmentManager(), "installer");
			} else if (itemId == R.id.action_context_delete) {
				alertDelete(appItem);
			} else {
				return false;
			}
			return true;
		};
		if (compactLibrary) {
			GameMenuDialog dialog = new GameMenuDialog(requireContext(), "Game options");
			dialog.show();
			dialog.page(appItem.getTitle());
			dialog.action("Settings", R.drawable.ic_baseline_tune_24, () -> {
				dialog.dismiss(); startApp(appItem, true);
			});
			for (int i = 0; i < menu.size(); i++) {
				MenuItem item = menu.getItem(i);
				if (item.isVisible() && item.getItemId() != R.id.action_context_settings) dialog.action(item.getTitle().toString(), 0, () -> {
					dialog.dismiss(); action.onMenuItemClick(item);
				});
			}
		} else {
			popup.setOnMenuItemClickListener(action);
			popup.show();
		}
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
				.subscribe(adapter::setSearchQuery);
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
			ru.playsoftware.j2meloader.catalog.AdditionalGames.install(requireContext(), getParentFragmentManager(), appUri, true, true);
			appUri = null;
		}
		updateEmptyState();
	}

	private void updateEmptyState() {
		if (binding == null) {
			return;
		}
		boolean empty = adapter.getItemCount() == 0;
		binding.empty.setVisibility(empty ? View.VISIBLE : View.GONE);
		binding.appsRecycler.setVisibility(empty ? View.GONE : View.VISIBLE);
		binding.libraryCount.setText(getResources().getQuantityString(R.plurals.library_games,
				adapter.getItemCount(), adapter.getItemCount()));
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
		return getResources().getConfiguration().screenWidthDp < 900
				|| getResources().getConfiguration().screenHeightDp < 480;
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
		adapter.unregisterAdapterDataObserver(libraryObserver);
		pendingGameFocus = -1;
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
