package ru.playsoftware.j2meloader.catalog;

import android.app.Application;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import ru.playsoftware.j2meloader.applist.AppListModel;
import ru.playsoftware.j2meloader.appsdb.AppRepository;
import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.R;

public class LibraryImportModel extends AndroidViewModel {
	public static final class State {
		public final boolean busy;
		public final String status;
		public final int completed, total;
		public final LibraryImporter.Preview preview;
		public final List<String> results;
		public final LibraryImporter.FolderAccessRequired access;
		public final boolean idle;
		State(boolean busy, String status, int completed, int total, LibraryImporter.Preview preview, List<String> results) {
			this(busy, status, completed, total, preview, results, null);
		}
		State(boolean busy, String status, int completed, int total, LibraryImporter.Preview preview, List<String> results,
				LibraryImporter.FolderAccessRequired access) {
			this(busy, status, completed, total, preview, results, access, false);
		}
		private State(boolean busy, String status, int completed, int total, LibraryImporter.Preview preview, List<String> results,
				LibraryImporter.FolderAccessRequired access, boolean idle) {
			this.busy = busy; this.status = status; this.completed = completed; this.total = total;
			this.preview = preview; this.results = results;
			this.access = access;
			this.idle = idle;
		}
		static State idle(String label) { return new State(false, label, 0, 0, null, null, null, true); }
	}

	public final MutableLiveData<State> state = new MutableLiveData<>();
	private final ExecutorService executor = Executors.newSingleThreadExecutor();
	private final AtomicBoolean cancelled = new AtomicBoolean();
	private final AppRepository repository;
	private final LibraryImporter importer;
	private volatile LibraryImporter.Preview preview;
	private volatile boolean busy;
	private File backupStage;
	private Uri singleGame, singleData, singleConfig;
	private Uri libraryRoot;
	private Uri backupSource;
	private boolean awaitingRestoredPicker;
	public boolean isSingleGame() { return singleGame != null; }

	android.os.Bundle saveState() {
		android.os.Bundle saved = new android.os.Bundle();
		saved.putParcelable("root", libraryRoot);
		saved.putParcelable("game", singleGame);
		saved.putParcelable("data", singleData);
		saved.putParcelable("config", singleConfig);
		saved.putParcelable("backup", backupSource);
		saved.putBundle("grants", importer.saveFolderAccess());
		State current = state.getValue();
		if (current != null && current.access != null) {
			saved.putParcelable("access", current.access.folder);
			saved.putString("accessName", current.access.name);
		}
		return saved;
	}

	void restoreState(android.os.Bundle saved) {
		restoreState(saved, false);
	}

	void restoreState(android.os.Bundle saved, boolean awaitingPicker) {
		// A retained ViewModel owns its worker; only a new process needs restoration.
		if (saved == null || busy || !state.getValue().idle) return;
		libraryRoot = saved.getParcelable("root"); singleGame = saved.getParcelable("game");
		singleData = saved.getParcelable("data"); singleConfig = saved.getParcelable("config");
		backupSource = saved.getParcelable("backup");
		importer.restoreFolderAccess(saved.getBundle("grants"));
		awaitingRestoredPicker = awaitingPicker;
		Uri access = saved.getParcelable("access");
		if (access != null) {
			LibraryImporter.FolderAccessRequired request = new LibraryImporter.FolderAccessRequired(access,
					saved.getString("accessName", "the source folder"));
			state.setValue(new State(false, request.getMessage(), 0, 0, null, null, request));
		} else if (!awaitingPicker) rescanRestoredSource();
		// Only rescan for review. Never replay an import or overwrite a backup automatically.
	}

	void pickerReturned(boolean cancelled) {
		boolean restore = awaitingRestoredPicker;
		awaitingRestoredPicker = false;
		if (restore && cancelled && state.getValue().access == null) rescanRestoredSource();
	}

	private void rescanRestoredSource() {
		if (singleGame != null) scanSingle(-1, null);
		else if (libraryRoot != null) scan(libraryRoot, false);
		else if (backupSource != null) scan(backupSource, true);
	}

	public void scanSingle(int part, Uri uri) {
		if (busy) return;
		if (part == 0) { importer.resetFolderAccess(); libraryRoot = null; backupSource = null; singleGame = uri; singleData = null; singleConfig = null; }
		else if (part == 1) singleData = uri;
		else if (part == 2) singleConfig = uri;
		busy = true; cancelled.set(false); preview = null;
		state.setValue(new State(true, getApplication().getString(R.string.transfer_reading_game), 0, 0, null, null));
		executor.execute(() -> {
			try {
				cleanBackup();
				preview = importer.scanSingle(DocumentFile.fromTreeUri(getApplication(), singleGame),
						singleData == null ? null : DocumentFile.fromTreeUri(getApplication(), singleData),
						singleConfig == null ? null : DocumentFile.fromTreeUri(getApplication(), singleConfig));
				state.postValue(new State(false, getApplication().getResources().getQuantityString(R.plurals.transfer_games_found, 1, 1), 0, 0, preview, null));
			} catch (Exception e) { scanFailed(e); }
			finally { busy = false; }
		});
	}

	public LibraryImportModel(@NonNull Application app) {
		super(app);
		state.setValue(State.idle(app.getString(R.string.transfer_idle)));
		repository = new AppListModel(app).getAppRepository();
		importer = new LibraryImporter(app, new File(Config.getEmulatorDir()), LibraryImporter.catalog(repository), cancelled);
	}

	public void scan(Uri uri) {
		if (busy) return;
		importer.resetFolderAccess(); libraryRoot = uri; backupSource = null;
		scan(uri, false);
	}

	public void scanBackup(Uri uri) {
		if (busy) return;
		importer.resetFolderAccess(); libraryRoot = null; backupSource = uri;
		scan(uri, true);
	}

	public void allowFolder(Uri uri) {
		State current = state.getValue();
		if (busy || current == null || current.access == null) return;
		try { importer.allowFolder(current.access, uri); }
		catch (java.io.IOException e) {
			state.setValue(new State(false, e.getMessage() + "\n\n" + current.access.getMessage(), 0, 0, null, null, current.access));
			return;
		}
		if (singleGame != null) scanSingle(-1, null);
		else if (libraryRoot != null) scan(libraryRoot, false);
	}

	private void scanFailed(Exception error) {
		LibraryImporter.FolderAccessRequired access = error instanceof LibraryImporter.FolderAccessRequired
				? (LibraryImporter.FolderAccessRequired) error : null;
		state.postValue(new State(false, error instanceof CancellationException ? getApplication().getString(R.string.transfer_cancelled)
				: error.getMessage() == null ? getApplication().getString(R.string.transfer_read_failed) : error.getMessage(),
				0, 0, null, null, access));
	}

	private void scan(Uri uri, boolean backup) {
		if (busy) return;
		singleGame = singleData = singleConfig = null;
		busy = true; cancelled.set(false); preview = null;
		state.setValue(new State(true, getApplication().getString(R.string.transfer_reading_library), 0, 0, null, null));
		executor.execute(() -> {
			try {
				cleanBackup();
				LibraryImporter.Preview found;
				if (backup) {
					try (java.io.InputStream in = getApplication().getContentResolver().openInputStream(uri)) {
						if (in == null) throw new java.io.IOException(getApplication().getString(R.string.transfer_cannot_open_backup));
						backupStage = new LibraryArchive(cancelled).unpack(in, getApplication().getCacheDir());
					}
					found = importer.scanBackup(backupStage);
				} else found = importer.scan(DocumentFile.fromTreeUri(getApplication(), uri));
				preview = found;
				state.postValue(new State(false, getApplication().getResources().getQuantityString(
						R.plurals.transfer_games_found, found.entries.size(), found.entries.size()), 0, 0, found, null));
			} catch (Exception e) {
				scanFailed(e);
			} finally { busy = false; }
		});
	}

	public void start(boolean includeSharedFiles) {
		if (busy || preview == null) return;
		LibraryImporter.Preview source = preview;
		List<LibraryImporter.Entry> selected = new ArrayList<>();
		for (LibraryImporter.Entry entry : preview.entries) if (entry.selected && entry.problem == null) selected.add(entry);
		if (selected.isEmpty()) return;
		busy = true; cancelled.set(false);
		state.setValue(new State(true, getApplication().getString(R.string.transfer_prepare), 0, selected.size(), null, null));
		executor.execute(() -> {
			List<String> results = new ArrayList<>();
			int imported = 0;
			for (LibraryImporter.Entry entry : selected) {
				if (cancelled.get()) break;
				state.postValue(new State(true, getApplication().getString(R.string.transfer_importing, entry.title), results.size(), selected.size(), null, null));
				try {
					results.add(entry.title + "\n" + importer.importEntry(entry)); imported++;
				} catch (CancellationException e) { break; }
				catch (Exception e) { results.add(getApplication().getString(R.string.transfer_not_imported, entry.title, e.getMessage())); }
			}
			int processed = results.size(), failed = processed - imported;
			if (!cancelled.get() && imported > 0 && includeSharedFiles && source.sharedFiles) {
				try { results.add(importer.importSharedFiles(source)); }
				catch (Exception e) { results.add(e.getMessage()); }
			}
			state.postValue(new State(false, getApplication().getString(cancelled.get() ? R.string.transfer_stopped : R.string.transfer_finished,
					imported, failed, selected.size() - processed),
					results.size(), selected.size(), null, results));
			preview = null; busy = false;
			try { cleanBackup(); } catch (java.io.IOException ignored) { }
		});
	}

	public void exportBackup(Uri uri) {
		if (busy) return;
		libraryRoot = backupSource = null;
		singleGame = singleData = singleConfig = null;
		busy = true; cancelled.set(false); preview = null;
		state.setValue(new State(true, getApplication().getString(R.string.transfer_saving), 0, 0, null, null));
		executor.execute(() -> {
			boolean success = false;
			try {
				cleanBackup();
				android.app.ActivityManager manager = getApplication().getSystemService(android.app.ActivityManager.class);
				for (android.app.ActivityManager.RunningAppProcessInfo process : manager.getRunningAppProcesses())
					if (process.processName.equals(getApplication().getPackageName() + ":midlet"))
						throw new java.io.IOException(getApplication().getString(R.string.transfer_exit_before_backup));
				int count;
				try (java.io.OutputStream out = getApplication().getContentResolver().openOutputStream(uri, "wt")) {
					if (out == null) throw new java.io.IOException(getApplication().getString(R.string.transfer_cannot_write_backup));
					count = new LibraryArchive(cancelled).write(new File(Config.getEmulatorDir()),
							repository.getAll().firstOrError().blockingGet(), out);
				}
				success = true;
				state.postValue(new State(false, getApplication().getResources().getQuantityString(R.plurals.transfer_backup_saved, count, count), 0, 0, null, null));
			} catch (Exception e) {
				state.postValue(new State(false, getApplication().getString(R.string.transfer_backup_failed, e.getMessage()), 0, 0, null, null));
			} finally {
				if (!success) try { android.provider.DocumentsContract.deleteDocument(getApplication().getContentResolver(), uri); } catch (Exception ignored) { }
				busy = false;
			}
		});
	}

	private void cleanBackup() throws java.io.IOException {
		if (backupStage != null) { LibraryArchive.remove(backupStage); backupStage = null; }
	}

	public void cancel() { cancelled.set(true); }

	@Override protected void onCleared() {
		cancelled.set(true);
		executor.execute(() -> { try { cleanBackup(); } catch (java.io.IOException ignored) { } finally { repository.close(); } });
		executor.shutdown();
	}
}
