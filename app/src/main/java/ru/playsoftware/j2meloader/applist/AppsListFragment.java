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
import ru.playsoftware.j2meloader.catalog.ExternalLibrary;
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
import ru.playsoftware.j2meloader.ui.ConsoleSurface;

public class AppsListFragment extends Fragment implements AppsListAdapter.Listener {
	private static final String TAG = AppsListFragment.class.getSimpleName();

	private Uri appUri;
	private SharedPreferences preferences;
	private AppRepository appRepository;
	private AppsListAdapter adapter;
	private Disposable searchViewDisposable;
	private AppItem selectedItem;
	private int artworkTargetId = -1;
	private boolean pickingIcon;
	private int category = AppsListAdapter.CATEGORY_LIBRARY;
	private int platform;
	private int generation;
	private LibraryNavigation navigation;
	private boolean changingPlatform;
	private boolean bothNgageGenerations;
	private boolean databaseLoaded;
	private boolean externalLoaded = !BuildConfig.SYMBIAN_EXPERIMENT;
	private boolean restoreLocation = true;
	private String restoredSelection;
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
    private String pendingLauncherLink;
    private final ActivityResultLauncher<String> launcherLinkExporter = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/octet-stream"), this::onLauncherLinkExported);
	private final ExecutorService folderExecutor = Executors.newSingleThreadExecutor();
    private List<AppItem> databaseItems = java.util.Collections.emptyList();
    private List<AppItem> externalItems = java.util.Collections.emptyList();
    private int externalGeneration;

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
		navigation = new LibraryNavigation(preferences);
		platform = navigation.active();
		category = navigation.category(platform);
		generation = navigation.generation(platform);
		restoredSearch = navigation.query(platform);
		searchExpanded = !restoredSearch.isEmpty();
		restoredSelection = navigation.selection(platform);
		if (savedInstanceState != null) {
            pendingLauncherLink = savedInstanceState.getString("launcherLink");
			artworkTargetId = savedInstanceState.getInt("artworkTargetId", -1);
			pickingIcon = savedInstanceState.getBoolean("pickingIcon");
			searchExpanded = savedInstanceState.getBoolean("searchExpanded");
			restoredSearch = savedInstanceState.getString("searchQuery", "");
			category = savedInstanceState.getInt("libraryCategory", AppsListAdapter.CATEGORY_LIBRARY);
		}
		adapter = new AppsListAdapter(this);
		adapter.restoreSelection(restoredSelection);
		adapter.setQuery(category, platform, generation, restoredSearch);
		adapter.setDisplayMode(navigation.view(platform));
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
		binding.appsRecycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
			@Override public void onScrollStateChanged(@NonNull RecyclerView view, int state) {
				if (state == RecyclerView.SCROLL_STATE_IDLE) saveLibraryLocation();
			}
		});
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
		libraryDevices = new ru.playsoftware.j2meloader.input.DeviceControls(requireContext(), () -> {},
				this::updateLibraryControls, () -> {});
		updateLibraryControls();
		binding.getRoot().addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> {
			if (r-l != or-ol || b-t != ob-ot) v.post(() -> {
				if (binding != null) { updateResponsiveToolbar(); updateDetail(selectedItem); updateLibraryLayout(); }
			});
		});
		binding.appsRecycler.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
			// notifyDataSetChanged during layout can strand RecyclerView's pending updates and focus.
			v.post(() -> {
				if (binding != null) updateLibraryLayout();
			});
		});
		initialGameFocus = true;
		adapter.registerAdapterDataObserver(libraryObserver);
		binding.viewModeGallery.setOnClickListener(v -> setDisplayMode(AppsListAdapter.MODE_GALLERY));
		binding.viewModeList.setOnClickListener(v -> setDisplayMode(AppsListAdapter.MODE_LIST));
		binding.viewModeGrid.setOnClickListener(v -> setDisplayMode(AppsListAdapter.MODE_GRID));
		for (int i = 0; i < binding.libraryPlatformTabs.getChildCount(); i++) {
			final int target = i + 1;
			binding.libraryPlatformTabs.getChildAt(i).setOnClickListener(v -> setPlatform(target));
		}
		binding.librarySocial.setOnClickListener(v -> showSocial());
		applyConsoleSurfaces();
		binding.libraryGeneration.setOnClickListener(v -> showGenerationMenu());
		installPlatformSwipe();
		binding.librarySearch.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				if (changingPlatform || s.toString().equals(restoredSearch)) return;
				saveLibraryLocation();
				restoredSearch = s.toString();
				restoreLocation = true;
				saveNavigation();
				adapter.setSearchQuery(s);
			}

			@Override
			public void afterTextChanged(Editable s) {
			}
		});
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
		binding.emptyAddGame.setOnClickListener(v -> openLastDirectory());
		setCategory(category);
		binding.librarySearch.setText(restoredSearch);
		applyDisplayMode();
		updateDetail(adapter.getFirstItem());
		libraryObserver.onChanged();
	}

	private final RecyclerView.AdapterDataObserver libraryObserver = new RecyclerView.AdapterDataObserver() {
		@Override public void onChanged() {
			updateEmptyState();
		}
	};
	@Override public void onItemsFiltered() {
			if (binding == null || !databaseLoaded || !externalLoaded) return;
			if (restoreLocation) {
				restoreLocation = false;
				restoreLibraryLocation();
			}
			if (binding != null && ru.playsoftware.j2meloader.input.DeviceControls.connected(requireContext()) && initialGameFocus && adapter.getItemCount() > 0) {
				initialGameFocus = false;
				if (!searchExpanded) focusGame(restoredSelection);
			}
	}

	private void closeSearch() {
		searchExpanded = false;
		binding.librarySearch.setText("");
		binding.librarySearch.clearFocus();
		android.view.inputmethod.InputMethodManager ime = (android.view.inputmethod.InputMethodManager)
				 requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
		ime.hideSoftInputFromWindow(binding.librarySearch.getWindowToken(), 0);
		updateResponsiveToolbar();
		if (ru.playsoftware.j2meloader.input.DeviceControls.connected(requireContext())) focusGame(selectedItem == null ? null : selectedItem.getPath());
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
		if (adapter.getDisplayMode() == mode) return;
		saveLibraryLocation();
		String anchor = preferences.getString(locationKey(), null);
		boolean restoreFocus = binding.appsRecycler.hasFocus();
		String selectedPath = selectedItem == null ? null : selectedItem.getPath();
		adapter.setDisplayMode(mode);
		if (anchor != null) preferences.edit().putString(locationKey(), anchor)
				.putInt(locationKey() + ".offset", 0).apply();
		saveNavigation();
		applyDisplayMode();
		restoreLibraryLocation();
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
		dialog.useEdgePanel();
		dialog.show();
		dialog.page(title);
		return dialog;
	}

	private void showGenerationMenu() {
		GameMenuDialog dialog = libraryDialog("N-Gage generation");
		String[] labels = {"All", "Classic", "2.0"};
		for (int i = 0; i < labels.length; i++) {
			int target = i;
			dialog.action(labels[i], i == generation ? android.R.drawable.checkbox_on_background : 0, () -> {
				saveLibraryLocation(); generation = target; restoreLocation = true;
				adapter.setQuery(category, platform, generation, restoredSearch);
				saveNavigation(); updateCollectionLabel(); dialog.dismiss();
			});
		}
	}

	private void showSocial() {
		GameMenuDialog dialog = libraryDialog("Social");
		ru.playsoftware.j2meloader.input.SocialMenuPages.show(dialog, requireContext(), null, this::startActivity);
	}

	private void installPlatformSwipe() {
		binding.appsRecycler.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
			float x, y;
			long down;
			boolean rejected, swiping;
			int direction;
			@Override public boolean onInterceptTouchEvent(@NonNull RecyclerView view, @NonNull android.view.MotionEvent e) {
				if (e.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
					x = e.getX(); y = e.getY(); down = e.getEventTime(); rejected = false; swiping = false;
				} else if (e.getActionMasked() == android.view.MotionEvent.ACTION_POINTER_DOWN) rejected = true;
				else if (e.getActionMasked() == android.view.MotionEvent.ACTION_MOVE && !rejected) {
					float dx = e.getX() - x, dy = e.getY() - y;
					if (e.getEventTime() - down >= android.view.ViewConfiguration.getLongPressTimeout()
							|| Math.abs(dy) > android.view.ViewConfiguration.get(requireContext()).getScaledTouchSlop()) {
						rejected = true; return false;
					}
					if (Math.abs(dx) > dp(64) && Math.abs(dx) > 3 * Math.abs(dy)) {
						direction = dx < 0 ? 1 : -1; swiping = true; return true;
					}
				}
				return false;
			}
			@Override public void onTouchEvent(@NonNull RecyclerView view, @NonNull android.view.MotionEvent e) {
				if (e.getActionMasked() == android.view.MotionEvent.ACTION_POINTER_DOWN) rejected = true;
				if (e.getActionMasked() == android.view.MotionEvent.ACTION_UP) {
					if (swiping && !rejected) stepPlatform(direction);
					swiping = false;
				} else if (e.getActionMasked() == android.view.MotionEvent.ACTION_CANCEL) swiping = false;
			}
		});
	}

	private void showCategoryMenu() {
		GameMenuDialog dialog = libraryDialog("Collection");
		populateCollectionMenu(dialog);
	}

	private void populateCollectionMenu(GameMenuDialog dialog) {
		dialog.page("Collection");
		String[] names = {"All games", "Recent", "Favorites"};
		for (int i = 0; i < names.length; i++) {
			int target = i;
			dialog.action(names[i], i == category ? android.R.drawable.checkbox_on_background : 0,
					() -> { setCategory(target); dialog.dismiss(); });
		}
	}

	private void showLibraryMenu() {
		GameMenuDialog dialog = libraryDialog("Library menu");
		populateLibraryMenu(dialog);
	}

	private void populateLibraryMenu(GameMenuDialog dialog) {
		dialog.page("Library menu");
		List<AppItem> all = new java.util.ArrayList<>(databaseItems);
		all.addAll(externalItems);
		AppItem last = LibraryQuery.lastPlayed(all);
		if (last != null) dialog.action("Play last: " + last.getTitle(), android.R.drawable.ic_media_play,
				() -> { dialog.dismiss(); startApp(last, false); });
		dialog.action("Collection", R.drawable.ic_list, () -> {
			dialog.dismiss(); showCategoryMenu();
		});
		if (BuildConfig.SYMBIAN_EXPERIMENT) {
			dialog.action("System files", R.drawable.ic_baseline_tune_24, () -> {
				dialog.dismiss();
				startActivity(new Intent().setClassName(requireContext(),
						"io.github.eriark.abyssme.symbian.SymbianActivity"));
			});
		}
		dialog.action("View: " + new String[]{"Gallery", "List", "Grid"}[adapter.getDisplayMode()],
				android.R.drawable.ic_menu_view, () -> {
			dialog.page("View");
			dialog.setBackAction(() -> populateLibraryMenu(dialog));
			String[] modes = {"Gallery", "List", "Grid"};
			for (int i = 0; i < modes.length; i++) {
				int target = i;
				dialog.action(modes[i], 0, () -> {
					setDisplayMode(target); dialog.dismiss();
					if (ru.playsoftware.j2meloader.input.DeviceControls.connected(requireContext())) focusGame(selectedItem == null ? null : selectedItem.getPath());
				});
			}
		});
		dialog.action("Cover format: " + LibraryLayout.COVER_LABELS[coverFormat()], android.R.drawable.ic_menu_gallery, () -> {
			dialog.page("Gallery cover format");
			dialog.setBackAction(() -> populateLibraryMenu(dialog));
			for (int i = 0; i < LibraryLayout.COVER_LABELS.length; i++) {
				final int value = i;
				dialog.action(LibraryLayout.COVER_LABELS[i], 0, () -> {
					preferences.edit().putInt(LibraryLayout.COVER_PREFERENCE, value).apply();
					updateLibraryLayout(); dialog.dismiss();
					if (ru.playsoftware.j2meloader.input.DeviceControls.connected(requireContext())) focusGame(selectedItem == null ? null : selectedItem.getPath());
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
		dialog.action("Fluxer account", android.R.drawable.ic_menu_myplaces, () -> {
			dialog.dismiss(); startActivity(new Intent(requireActivity(),
					ru.playsoftware.j2meloader.multiplayer.FluxerAccountActivity.class));
		});
		dialog.action("Chats", android.R.drawable.ic_dialog_email, () -> {
			dialog.dismiss(); startActivity(new Intent(requireActivity(),
					ru.playsoftware.j2meloader.multiplayer.FluxerChatsActivity.class));
		});
		dialog.action("Friends", android.R.drawable.ic_menu_myplaces, () -> {
			dialog.dismiss(); startActivity(new Intent(requireContext(),
					ru.playsoftware.j2meloader.multiplayer.FluxerChatsActivity.class).putExtra("friends", true));
		});
		dialog.action("Lobbies", android.R.drawable.ic_menu_share, () -> {
			dialog.dismiss(); startActivity(new Intent(requireContext(),
					ru.playsoftware.j2meloader.multiplayer.LobbyDirectoryActivity.class));
		});
		dialog.action("Settings", R.drawable.ic_baseline_tune_24, () -> {
			dialog.dismiss(); startActivity(new Intent(requireActivity(), SettingsActivity.class));
		});
	}

	private void setCategory(int category) {
		if (this.category != category) { saveLibraryLocation(); restoreLocation = true; }
		this.category = category;
		saveNavigation();
		adapter.setCategory(category);
		updateCollectionLabel();
	}

	private void setPlatform(int platform) {
		if (this.platform == platform || binding == null) return;
		saveLibraryLocation();
		if (!restoreLocation) rememberVisibleSelection();
		saveNavigation();
		this.platform = platform;
		restoreLocation = true;
		binding.appsRecycler.clearFocus();
		pendingGameFocus = -1;
		initialGameFocus = true;
		selectedItem = null;
		category = navigation.category(platform);
		generation = bothNgageGenerations ? navigation.generation(platform) : 0;
		restoredSearch = navigation.query(platform);
		restoredSelection = navigation.selection(platform);
		searchExpanded = !restoredSearch.isEmpty();
		changingPlatform = true;
		binding.librarySearch.setText(restoredSearch);
		changingPlatform = false;
		adapter.restoreSelection(restoredSelection);
		adapter.setDisplayMode(navigation.view(platform));
		adapter.setQuery(category, platform, generation, restoredSearch);
		saveNavigation();
		applyDisplayMode();
		updateCollectionLabel();
	}
	public void stepPlatform(int direction) { setPlatform(LibraryNavigation.next(platform, direction)); }
	private void saveNavigation() {
		if (navigation != null && adapter != null)
			navigation.save(platform, adapter.getDisplayMode(), category, generation, restoredSearch, restoredSelection);
	}

	private void updateCollectionLabel() {
		String label = category == 0 ? "All games" : category == 1 ? "Recent" : "Favorites";
		binding.libraryCategory.setText(label);
		binding.libraryCategory.setContentDescription("Collection: " + label);
		binding.libraryCategory.setTooltipText(label);
		for (int i = 0; i < binding.libraryPlatformTabs.getChildCount(); i++) {
			View tab = binding.libraryPlatformTabs.getChildAt(i);
			tab.setSelected(i + 1 == platform);
			if (i + 1 == platform) tab.post(() -> {
				if (binding != null) tab.requestRectangleOnScreen(new Rect(0, 0, tab.getWidth(), tab.getHeight()), true);
			});
		}
		binding.libraryGeneration.setVisibility(platform == 4 && bothNgageGenerations ? View.VISIBLE : View.GONE);
		binding.libraryGeneration.setText(new String[]{"All", "Classic", "2.0"}[generation]);
		binding.libraryGeneration.setContentDescription("N-Gage generation: " + binding.libraryGeneration.getText());
	}

	private String locationKey() { return "libraryLocation." + category + "." + platform
			+ "." + generation + "." + adapter.getDisplayMode() + "." + restoredSearch; }
	private void saveLibraryLocation() {
		if (binding == null || restoreLocation
				|| binding.appsRecycler.hasPendingAdapterUpdates()) return;
		RecyclerView.LayoutManager manager = binding.appsRecycler.getLayoutManager();
		if (!(manager instanceof LinearLayoutManager)) return;
		int first = ((LinearLayoutManager) manager).findFirstVisibleItemPosition();
		if (first < 0 || first >= adapter.getItemCount()) return;
		View child = manager.findViewByPosition(first);
		preferences.edit().putString(locationKey(), adapter.getItem(first).getPath())
				.putInt(locationKey() + ".offset", child == null ? 0 : manager.getDecoratedTop(child)
						- ((ViewGroup.MarginLayoutParams) child.getLayoutParams()).topMargin
						- binding.appsRecycler.getPaddingTop()).apply();
	}
	private void restoreLibraryLocation() {
		if (binding == null) return;
		String key = locationKey();
		if (!preferences.contains(key) && restoredSearch.isEmpty() && generation == 0)
			key = "libraryLocation." + category + "." + platform;
		String path = preferences.getString(key, null);
		int position = 0;
		for (int i = 0; i < adapter.getItemCount(); i++)
			if (adapter.getItem(i).getPath().equals(path)) { position = i; break; }
		RecyclerView.LayoutManager manager = binding.appsRecycler.getLayoutManager();
		if (manager instanceof LinearLayoutManager)
			((LinearLayoutManager) manager).scrollToPositionWithOffset(position,
					position == 0 && !java.util.Objects.equals(path, adapter.getFirstItem() == null
							? null : adapter.getFirstItem().getPath()) ? 0 : preferences.getInt(key + ".offset", 0));
	}

	private void applyDisplayMode() {
		boolean restoreFocus = !restoreLocation && binding.appsRecycler.hasFocus();
		String focusedPath = selectedItem == null ? null : selectedItem.getPath();
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
		if (restoreFocus) binding.appsRecycler.post(() -> focusGame(focusedPath));
	}

	private int calculateSpanCount(int mode) {
		return libraryLayout(mode).columns;
	}

	private int coverFormat() { return LibraryLayout.format(preferences.getInt(LibraryLayout.COVER_PREFERENCE, LibraryLayout.AUTO)); }
	private LibraryLayout libraryLayout(int mode) {
		float density = getResources().getDisplayMetrics().density;
		int width = binding.appsRecycler.getWidth() - binding.appsRecycler.getPaddingLeft() - binding.appsRecycler.getPaddingRight();
		int height = binding.appsRecycler.getHeight() - binding.appsRecycler.getPaddingTop() - binding.appsRecycler.getPaddingBottom();
		return LibraryLayout.calculate(mode, coverFormat(), width > 0 ? Math.round(width / density)
				: getResources().getConfiguration().screenWidthDp - 24, height > 0 ? Math.round(height / density)
				: getResources().getConfiguration().screenHeightDp - 80, getResources().getConfiguration().fontScale);
	}
	private void updateLibraryLayout() {
		boolean restore = !restoreLocation && binding.appsRecycler.hasFocus();
		String path = selectedItem == null ? null : selectedItem.getPath();
		LibraryLayout layout = libraryLayout(adapter.getDisplayMode());
		if (binding.appsRecycler.getLayoutManager() instanceof GridLayoutManager)
			((GridLayoutManager) binding.appsRecycler.getLayoutManager()).setSpanCount(layout.columns);
		adapter.setLibraryLayout(layout);
		if (restore && !binding.appsRecycler.hasFocus()) focusGame(path);
	}


	private void applyConsoleSurfaces() {
		binding.libraryRoot.setBackground(new ConsoleSurface(requireContext(), ConsoleSurface.Kind.CHASSIS));
		binding.libraryScreen.setBackground(new ConsoleSurface(requireContext(), ConsoleSurface.Kind.SCREEN));
		for (View control : new View[]{binding.librarySearchToggle, binding.railAddGame, binding.librarySocial,
				binding.libraryMenu, binding.librarySearchClose, binding.viewModeGallery, binding.viewModeList,
				binding.viewModeGrid, binding.emptyAddGame}) {
			control.setBackgroundTintList(null);
			control.setDefaultFocusHighlightEnabled(false);
			control.setBackground(new ConsoleSurface(requireContext(), ConsoleSurface.Kind.KEY));
		}
		for (View control : new View[]{binding.libraryCategory, binding.libraryGeneration}) {
			control.setDefaultFocusHighlightEnabled(false);
			control.setBackground(new ConsoleSurface(requireContext(), ConsoleSurface.Kind.ROW));
		}
		for (int i = 0; i < binding.libraryPlatformTabs.getChildCount(); i++) {
			TextView tab = (TextView) binding.libraryPlatformTabs.getChildAt(i);
			tab.setDefaultFocusHighlightEnabled(false);
			tab.setBackground(new ConsoleSurface(requireContext(), ConsoleSurface.Kind.TAB, i));
			tab.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
		}
	}

	private void updateResponsiveToolbar() {
		float density = getResources().getDisplayMetrics().density;
		float width = binding.getRoot().getWidth() > 0 ? binding.getRoot().getWidth() / density : getResources().getConfiguration().screenWidthDp;
		float height = binding.getRoot().getHeight() > 0 ? binding.getRoot().getHeight() / density : getResources().getConfiguration().screenHeightDp;
		float fontScale = getResources().getConfiguration().fontScale;
		boolean shortScreen = width >= 440 && height < 400 && fontScale <= 1.2f;
		ViewGroup toolsParent = (ViewGroup) binding.libraryTools.getParent();
		ViewGroup wantedParent = shortScreen ? binding.libraryHeader : binding.libraryScreen;
		if (toolsParent != wantedParent) {
			toolsParent.removeView(binding.libraryTools);
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(shortScreen ? 0 : -1, dp(48), shortScreen ? 1 : 0);
			if (!shortScreen) lp.setMargins(dp(4), 0, dp(4), 0);
			wantedParent.addView(binding.libraryTools, shortScreen ? 0 : 1, lp);
		}
		binding.launcherTitle.setVisibility(shortScreen ? View.GONE : View.VISIBLE);
		compactLibrary = LibraryLayout.compact(width, height, fontScale);
		adapter.setCompact(compactLibrary && height < 600);
		binding.libraryCount.setVisibility(!shortScreen && width >= 560 && fontScale <= 1.3f ? View.VISIBLE : View.GONE);
		// Secondary commands remain in the menu when a narrow viewport cannot fit them.
		binding.railAddGame.setVisibility(!shortScreen && width >= 340 && fontScale <= 1.5f ? View.VISIBLE : View.GONE);
		binding.librarySearchRow.setVisibility(searchExpanded || !restoredSearch.isEmpty() ? View.VISIBLE : View.GONE);
		if (searchBack != null) searchBack.setEnabled(searchExpanded || !restoredSearch.isEmpty());
		binding.librarySearchToggle.setSelected(searchExpanded || !restoredSearch.isEmpty());
		int tabWidth = dp(Math.max(80, Math.round(80 * fontScale)));
		for (int i = 0; i < binding.libraryPlatformTabs.getChildCount(); i++) {
			TextView tab = (TextView) binding.libraryPlatformTabs.getChildAt(i);
			LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) tab.getLayoutParams();
			int desired = Math.max(tabWidth, dp(Math.round((width - 24) / 4)));
			if (lp.width != desired) { lp.width = desired; lp.weight = 0; tab.setLayoutParams(lp); }
		}
		binding.launcherTitle.setTypeface("retro".equals(ru.playsoftware.j2meloader.ui.AppPalette.selected(requireContext()))
				? android.graphics.Typeface.MONOSPACE : android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
		binding.launcherTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
	}

	@Override
	public void onConfigurationChanged(@NonNull Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		saveLibraryLocation();
		applyDisplayMode();
		restoreLibraryLocation();
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
		Context context = requireContext().getApplicationContext();
		ru.playsoftware.j2meloader.appsdb.AppRepository repository = appRepository;
		Toast.makeText(context, R.string.folders_scanning, Toast.LENGTH_SHORT).show();
		folderExecutor.execute(() -> {
			String message;
			try {
				GameFolderIndexer.Result result = GameFolderIndexer.scan(context, uri);
				int added = repository.indexGames(result.games);
				GameFolderIndexer.rememberFolder(context, uri);
				message = result.summary(context, added);
			} catch (Exception error) {
				if (Thread.currentThread().isInterrupted()) return;
				Log.e(TAG, "Folder scan failed", error);
				message = context.getString(R.string.folders_add_failed, error.getMessage());
			}
			String status = message;
			new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
					Toast.makeText(context, status, Toast.LENGTH_LONG).show());
		});
	}

	private void onArtworkPicked(Uri uri) {
		int targetId = artworkTargetId;
		boolean icon = pickingIcon;
		artworkTargetId = -1;
		if (uri == null || targetId == -1) return;
		Context context = requireContext().getApplicationContext();
		folderExecutor.execute(() -> {
			try (InputStream input = context.getContentResolver().openInputStream(uri)) {
				AppItem target = findArtworkTarget(context, targetId);
				File directory = artworkDirectory(target);
				if (input == null) throw new IOException("Unable to open image");
				GameArtwork.saveUserImage(directory, icon, input);
				artworkChanged(target, directory);
			} catch (IOException | SecurityException e) {
				showArtworkError(context, e);
			}
		});
	}

	private AppItem findArtworkTarget(Context context, int id) throws IOException {
		if (id < -1) {
			for (AppItem item : ExternalLibrary.read(context)) if (item.getId() == id) return item;
		} else {
			AppItem item = appRepository.get(id);
			if (item != null && "ready".equals(item.getPreparationState())) return item;
		}
		throw new IOException("Game is no longer available");
	}

	private File artworkDirectory(AppItem item) throws IOException {
		File directory = item instanceof ExternalLibrary.Item
				? ((ExternalLibrary.Item) item).getArtworkDirectory() : new File(item.getPathExt());
		if (directory == null) throw new IOException("This library does not support custom artwork");
		return directory;
	}

	private void artworkChanged(AppItem target, File directory) {
		if (target instanceof ExternalLibrary.Item) {
			new android.os.Handler(android.os.Looper.getMainLooper()).post(this::reloadExternalLibrary);
		} else {
			GameArtwork.applyPaths(target, directory);
			appRepository.update(target);
		}
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
		if (!(item instanceof ExternalLibrary.Item) && new File(item.getPathExt(), Config.MIDLET_RES_FILE).isFile()) {
			dialog.action("Refresh automatic art", android.R.drawable.ic_popup_sync, () -> {
				dialog.confirmationPage("Refresh automatic art?");
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
				AppItem current = findArtworkTarget(context, target.getId());
				File directory = artworkDirectory(current);
				if (current instanceof ExternalLibrary.Item) {
					if (reset != null) java.nio.file.Files.deleteIfExists(new File(directory, reset).toPath());
					artworkChanged(current, directory);
					return;
				}
				if (GameArtwork.USER_ICON.equals(reset) && !new File(directory, GameArtwork.AUTO_MARKER).exists()
						&& !new File(directory, GameArtwork.USER_COVER).exists()) {
					File cover = new File(directory, "cover.png");
					if (cover.isFile()) java.nio.file.Files.copy(cover.toPath(),
							new File(directory, GameArtwork.USER_COVER).toPath());
				}
				if (new File(directory, Config.MIDLET_RES_FILE).isFile()) GameArtwork.refresh(directory);
				if (reset != null) java.nio.file.Files.deleteIfExists(new File(directory, reset).toPath());
				artworkChanged(current, directory);
			} catch (IOException e) { showArtworkError(context, e); }
		});
	}

	private void showArtworkError(Context context, Exception e) {
		Log.w(TAG, "Artwork update failed", e);
		new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
				Toast.makeText(context, "Could not update artwork", Toast.LENGTH_SHORT).show());
	}

	@Override public void onSaveInstanceState(@NonNull Bundle state) {
		saveLibraryLocation();
		if (!restoreLocation) rememberVisibleSelection();
		saveNavigation();
		super.onSaveInstanceState(state);
        state.putString("launcherLink", pendingLauncherLink);
		state.putInt("artworkTargetId", artworkTargetId);
		state.putBoolean("pickingIcon", pickingIcon);
		state.putBoolean("searchExpanded", searchExpanded);
		state.putString("searchQuery", binding == null ? restoredSearch : binding.librarySearch.getText().toString());
		state.putInt("libraryCategory", category);
	}

	private void alertRename(AppItem item) {
		GameMenuDialog dialog = new GameMenuDialog(requireContext(), item.getTitle());
		dialog.show(); dialog.page("Title");
		EditText editText = dialog.textInput("Game title", item.getTitle());
		dialog.action("Save", android.R.drawable.ic_menu_save, () -> {
					String title = editText.getText().toString().trim();
					if (title.equals("")) {
						editText.setError("Enter a title");
						editText.requestFocus();
					} else {
						if (item instanceof ExternalLibrary.Item) {
							try {
								((ExternalLibrary.Item) item).rename(title);
							} catch (java.io.IOException e) {
								editText.setError("Could not save title. " + e.getMessage());
								return;
							}
							publishLibrary();
						} else {
							item.setTitle(title);
							appRepository.update(item);
						}
						dialog.dismiss();
					}
		});
		dialog.action("Cancel", android.R.drawable.ic_menu_close_clear_cancel, dialog::dismiss);
	}

	private void alertDelete(AppItem item) {
		ru.playsoftware.j2meloader.input.GameMenuDialog dialog =
				new ru.playsoftware.j2meloader.input.GameMenuDialog(requireActivity(), item.getTitle());
		dialog.show(); dialog.confirmationPage("Delete game?");
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
		updateDetail(item);
		rememberSelection(item);
		saveLibraryLocation();
        if (item instanceof ExternalLibrary.Item) {
            ((ExternalLibrary.Item) item).launch(requireActivity(), showSettings);
            return;
        }
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
		toggleFavorite(selectedItem);
	}
	private void toggleFavorite(AppItem item) {
		if (item instanceof ExternalLibrary.Item) {
			((ExternalLibrary.Item) item).favorite();
			publishLibrary();
		} else appRepository.toggleFavorite(item);
	}
	private void rememberSelection(AppItem item) {
		if (item == null) return;
		restoredSelection = item.getPath();
		adapter.restoreSelection(restoredSelection);
		saveNavigation();
	}
	private void rememberVisibleSelection() {
		AppItem item = selectedItem;
		if (binding != null && binding.appsRecycler.getLayoutManager() instanceof LinearLayoutManager) {
			LinearLayoutManager manager = (LinearLayoutManager) binding.appsRecycler.getLayoutManager();
			int first = manager.findFirstVisibleItemPosition(), last = manager.findLastVisibleItemPosition();
			boolean visible = false;
			for (int i = Math.max(0, first); i <= last && i < adapter.getItemCount(); i++)
				visible |= item != null && item.getPath().equals(adapter.getItem(i).getPath());
			if (!visible && first >= 0 && first < adapter.getItemCount()) item = adapter.getItem(first);
		}
		rememberSelection(item);
	}

	public boolean handleControllerKey(int keyCode) {
		if (binding == null) return false;
		if (restoreLocation && keyCode == KeyEvent.KEYCODE_BUTTON_A) return true;
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
		if (keyCode == KeyEvent.KEYCODE_BUTTON_B && binding.librarySearchRow.getVisibility() == View.VISIBLE) {
			closeSearch();
			return true;
		}
		if (keyCode == KeyEvent.KEYCODE_BUTTON_X && selectedItem != null && !restoreLocation) {
			View focus = binding.appsRecycler.findFocus();
			showActions(focus != null ? focus : binding.appsRecycler, selectedItem);
			return true;
		}
		if (keyCode == KeyEvent.KEYCODE_BUTTON_Y) {
			if (!restoreLocation) toggleSelectedFavorite();
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
        updateDetail(appItem);
        rememberSelection(appItem);
        saveLibraryLocation();
        if (contextMenu != null) contextMenu.dismiss();
        GameMenuDialog dialog = new GameMenuDialog(requireContext(), appItem.getTitle());
        contextMenu = dialog;
        dialog.useEdgePanel(); dialog.show();
        dialog.artwork(appItem.getImagePathExt(), appItem.getCoverPathExt());
        dialog.setOnDismissListener(d -> {
            if (contextMenu == dialog) contextMenu = null;
            if (binding != null && isAdded() && ru.playsoftware.j2meloader.input.DeviceControls.connected(requireContext()))
                focusGame(appItem.getPath());
        });
        populateGameActions(dialog, appItem);
    }

    private GameMenuDialog contextMenu;
    private ru.playsoftware.j2meloader.input.MapperDialog contextMapper;

    private void populateGameActions(GameMenuDialog dialog, AppItem item) {
        dialog.page("Game actions");
        dialog.action("Play", android.R.drawable.ic_media_play, () -> { dialog.dismiss(); startApp(item, false); });
        if (!(item instanceof ExternalLibrary.Item))
            ru.playsoftware.j2meloader.multiplayer.PlayTogether.action(dialog, item.getPathExt(), this::startActivity);
        if (!(item instanceof ExternalLibrary.Item) || ((ExternalLibrary.Item) item).supportsGameSettings()) {
            dialog.action("Display", R.drawable.ic_quick_size, () -> showCardSettings(dialog, item, false));
            dialog.action("Controls", R.drawable.ic_action_keyboard, () -> showCardSettings(dialog, item, true));
        }
        dialog.action("Game information", android.R.drawable.ic_menu_info_details, () -> {
            dialog.page("Game information");
            dialog.message(ru.playsoftware.j2meloader.catalog.GameInformation.describe(item));
            dialog.setBackAction(() -> populateGameActions(dialog, item));
        });
        dialog.action(item.isFavorite() ? "Remove favorite" : "Add favorite", android.R.drawable.btn_star, () -> {
            toggleFavorite(item); dialog.dismiss();
        });
        dialog.action("More", R.drawable.ic_library_ellipsis_vertical, () -> populateGameMore(dialog, item));
    }

    private void populateGameMore(GameMenuDialog dialog, AppItem appItem) {
        dialog.page("More");
        dialog.setBackAction(() -> populateGameActions(dialog, appItem));
        dialog.action("Edit card", android.R.drawable.ic_menu_edit, () -> populateEditCard(dialog, appItem,
                () -> populateGameMore(dialog, appItem)));
        if (appItem instanceof ExternalLibrary.Item) {
            ExternalLibrary.Item item = (ExternalLibrary.Item) appItem;
            dialog.action("Export launch file", android.R.drawable.ic_menu_save, () -> {
                try {
                    pendingLauncherLink = ru.playsoftware.j2meloader.catalog.LauncherLink.encode(
                            requireContext().getPackageName(), item.getPath());
                    dialog.dismiss();
                    launcherLinkExporter.launch(item.getTitle().replaceAll("[/\\\\:*?\"<>|]", "_") + ".abyssme");
                } catch (IOException error) { dialog.message(error.getMessage()); }
            });
            dialog.action("System files", R.drawable.ic_baseline_tune_24, () -> { dialog.dismiss(); startApp(item, true); });
            if(item.supportsUninstallReview()) dialog.action("Uninstall from phone", android.R.drawable.ic_menu_delete,
                    () -> { dialog.dismiss(); item.uninstall(requireActivity()); });
            return;
        }
        PopupMenu popup = new PopupMenu(requireContext(), binding.libraryMenu);
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
			if (itemId == R.id.action_context_information) {
                showGameInformation(appItem);
			} else if (itemId == R.id.action_context_shortcut) {
				requestAddShortcut(appItem);
			} else if (itemId == R.id.action_context_rename) {
				showEditCard(appItem);
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
		{
			dialog.action("Settings", R.drawable.ic_baseline_tune_24, () -> {
				dialog.dismiss(); startApp(appItem, true);
			});
			for (int i = 0; i < menu.size(); i++) {
				MenuItem item = menu.getItem(i);
				if (item.isVisible() && item.getItemId() != R.id.action_context_settings
						&& item.getItemId() != R.id.action_context_information
						&& item.getItemId() != R.id.action_context_rename
						&& item.getItemId() != R.id.action_context_artwork) dialog.action(item.getTitle().toString(), 0, () -> {
					dialog.dismiss(); action.onMenuItemClick(item);
				});
			}
		}
	}

	private void showEditCard(AppItem item) {
		GameMenuDialog dialog = new GameMenuDialog(requireContext(), item.getTitle());
		dialog.useEdgePanel(); dialog.show();
		populateEditCard(dialog, item, null);
	}

	private void populateEditCard(GameMenuDialog dialog, AppItem item, Runnable back) {
		dialog.page("Edit card");
		dialog.setBackAction(back);
		dialog.action("Title", android.R.drawable.ic_menu_edit, () -> { dialog.dismiss(); alertRename(item); });
		boolean artworkAvailable = item instanceof ExternalLibrary.Item
				? ((ExternalLibrary.Item) item).getArtworkDirectory() != null
				: new File(item.getPathExt(), Config.MIDLET_RES_FILE).isFile()
						|| ru.playsoftware.j2meloader.catalog.AdditionalGames.isManaged(new File(item.getPathExt()));
		if (artworkAvailable)
			dialog.action("Artwork", android.R.drawable.ic_menu_gallery, () -> { dialog.dismiss(); showArtworkMenu(item); });
	}

    private void showCardSettings(GameMenuDialog dialog, AppItem item, boolean controls) {
        File app = new File(item.getPathExt());
        ExternalLibrary.Item external = item instanceof ExternalLibrary.Item ? (ExternalLibrary.Item) item : null;
        ru.playsoftware.j2meloader.config.ProfileModel profile;
        try {
            if (external != null) profile = external.loadGameSettings();
            else {
                File dir = new File(app.getParentFile().getParentFile(), "configs/" + app.getName());
                profile = ru.playsoftware.j2meloader.config.ProfilesManager.loadConfig(dir);
            }
        } catch (IOException error) { dialog.message("Could not load game settings."); return; }
        if (profile == null) { dialog.dismiss(); startApp(item, true); return; }
        ru.playsoftware.j2meloader.config.GameSettingsPages pages = new ru.playsoftware.j2meloader.config.GameSettingsPages(
                dialog, profile, false, external == null && !ru.playsoftware.j2meloader.catalog.AdditionalGames.isManaged(app),
                new ru.playsoftware.j2meloader.config.GameSettingsPages.Host() {
                    public void preview(ru.playsoftware.j2meloader.config.ProfileModel draft) {
                        try {
                            ru.playsoftware.j2meloader.config.SettingsTrial.stage(draft);
                            dialog.dismiss(); startApp(item, false);
                        } catch (Exception error) { dialog.message("Could not prepare settings."); }
                    }
                    public void mapping(ru.playsoftware.j2meloader.config.ProfileModel draft, Runnable back) {
                        dialog.hide();
                        contextMapper = new ru.playsoftware.j2meloader.input.MapperDialog(requireContext(), item.getTitle(), draft, () -> {
                            contextMapper = null;
                            if (binding != null && contextMenu == dialog) { dialog.show(); back.run(); }
                        });
                        contextMapper.show();
                    }
                    public void advanced() {
                        dialog.dismiss();
                        startActivity(new Intent(Intent.ACTION_EDIT, Uri.parse(item.getPathExt()), requireContext(), ConfigActivity.class)
                                .putExtra(KEY_MIDLET_NAME, item.getTitle()));
                    }
                    public void back() { populateGameActions(dialog, item); }
                });
        pages.setHomeAction(() -> populateGameActions(dialog, item));
        if (controls) pages.controls(); else if (external != null) external.displaySettings(pages); else pages.display();
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

	@Override public void onResume() {
		super.onResume();
		updateLibraryControls();
		reloadExternalLibrary();
	}
	@Override public void onPause() {
		saveLibraryLocation();
		if (databaseLoaded && externalLoaded && !restoreLocation) rememberVisibleSelection();
		saveNavigation();
		super.onPause();
	}

	private void reloadExternalLibrary() {
		if (!isAdded() || binding == null) return;
		if (!BuildConfig.SYMBIAN_EXPERIMENT) return;
		Context context = requireContext().getApplicationContext();
		android.app.Activity activity = requireActivity();
		int generation = ++externalGeneration;
		folderExecutor.execute(() -> {
			try {
				List<AppItem> items = ExternalLibrary.read(context);
				activity.runOnUiThread(() -> {
					if (binding == null || generation != externalGeneration) return;
					externalItems = items; externalLoaded = true; publishLibrary();
					if (selectedItem instanceof ExternalLibrary.Item) {
						for (AppItem item : items) if (item.getPath().equals(selectedItem.getPath())) {
							selectedItem = item; updateDetail(item); break;
						}
					}
				});
			} catch (IOException | RuntimeException error) {
				activity.runOnUiThread(() -> { if (binding != null && generation == externalGeneration) {
					externalLoaded = true; publishLibrary(); alertDbError(error);
				} });
			}
		});
	}

	private void publishLibrary() {
		saveLibraryLocation();
		restoreLocation = true;
		List<AppItem> items = new java.util.ArrayList<>(databaseItems);
		items.addAll(externalItems);
		bothNgageGenerations = LibraryQuery.hasBothNgageGenerations(externalItems);
		if (externalLoaded && !bothNgageGenerations && generation != 0) {
			generation = 0;
			adapter.setQuery(category, platform, generation, restoredSearch);
		}
		if (binding != null) updateCollectionLabel();
		if (!externalItems.isEmpty()) {
			java.text.Collator collator = java.text.Collator.getInstance();
			int sort = appRepository.getSort();
			java.util.Comparator<AppItem> byTitle = (a, b) -> collator.compare(a.getTitle(), b.getTitle());
			java.util.Comparator<AppItem> byAuthor = (a, b) -> collator.compare(
					a.getAuthor() == null ? "" : a.getAuthor(), b.getAuthor() == null ? "" : b.getAuthor());
			java.util.Comparator<AppItem> order = (sort & 0x7fffffff) == 1
					? java.util.Comparator.comparingLong(a -> Math.abs((long) a.getId()))
					: (sort & 0x7fffffff) == 2 ? byAuthor : byTitle;
			if (sort < 0) order = order.reversed();
			items.sort(order.thenComparing(byTitle).thenComparing(AppItem::getPath));
		}
		adapter.setItems(items);
	}

	private void onDbUpdated(List<AppItem> items) {
		databaseLoaded = true;
		databaseItems = items;
		publishLibrary();
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
		binding.empty.setText(!restoredSearch.isEmpty() ? "No matching games"
				: category == 2 ? "No favorites in this collection"
				: category == 1 ? "No recently played games in this collection"
				: platform != 0 ? "No " + LibraryQuery.PLATFORMS[platform] + " games"
				: "No games yet");
		binding.libraryEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
		binding.emptyAddGame.setVisibility(category == 0 && restoredSearch.isEmpty() ? View.VISIBLE : View.GONE);
		binding.appsRecycler.setVisibility(empty ? View.GONE : View.VISIBLE);
		binding.libraryCount.setText(getResources().getQuantityString(R.plurals.library_games,
				adapter.getItemCount(), adapter.getItemCount()));
	}

    private void onLauncherLinkExported(Uri uri) {
        String payload = pendingLauncherLink; pendingLauncherLink = null;
        if (uri == null || payload == null) return;
        try (java.io.OutputStream output = requireContext().getContentResolver().openOutputStream(uri, "wt")) {
            if (output == null) throw new IOException("Cannot write launch file");
            output.write(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Toast.makeText(requireContext(), "Launch file exported", Toast.LENGTH_SHORT).show();
        } catch (IOException | RuntimeException error) {
            GameMenuDialog dialog = new GameMenuDialog(requireContext(), "AbyssME");
            dialog.show(); dialog.page("Export failed"); dialog.message(error.getMessage());
        }
    }

    private void showGameInformation(AppItem item) {
        GameMenuDialog dialog = new GameMenuDialog(requireContext(), item.getTitle());
        dialog.show(); dialog.page("Game information");
        dialog.message(ru.playsoftware.j2meloader.catalog.GameInformation.describe(item));
    }

	private void updateDetail(AppItem item) {
		selectedItem = item;
	}

	private int dp(int value) {
		return Math.round(value * getResources().getDisplayMetrics().density);
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
		if (contextMapper != null) { contextMapper.setOnDismissListener(null); contextMapper.dismiss(); contextMapper = null; }
		if (contextMenu != null) { contextMenu.setOnDismissListener(null); contextMenu.dismiss(); contextMenu = null; }
		if (libraryDevices != null) { libraryDevices.close(); libraryDevices = null; }
		externalGeneration++;
		adapter.unregisterAdapterDataObserver(libraryObserver);
		pendingGameFocus = -1;
		super.onDestroyView();
		binding = null;
	}

	private ru.playsoftware.j2meloader.input.DeviceControls libraryDevices;

	private void updateLibraryControls() {
		if (binding == null) return;
		updateResponsiveToolbar();
		boolean connected = ru.playsoftware.j2meloader.input.DeviceControls.connected(requireContext());
		if (connected && !searchExpanded && !initialGameFocus && !binding.getRoot().hasFocus())
			focusGame(selectedItem == null ? restoredSelection : selectedItem.getPath());
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
