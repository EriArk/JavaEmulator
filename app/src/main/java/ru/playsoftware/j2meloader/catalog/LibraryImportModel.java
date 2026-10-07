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

public class LibraryImportModel extends AndroidViewModel {
	public static final class State {
		public final boolean busy;
		public final String status;
		public final int completed, total;
		public final LibraryImporter.Preview preview;
		public final List<String> results;
		State(boolean busy, String status, int completed, int total, LibraryImporter.Preview preview, List<String> results) {
			this.busy = busy; this.status = status; this.completed = completed; this.total = total;
			this.preview = preview; this.results = results;
		}
	}

	public final MutableLiveData<State> state = new MutableLiveData<>(new State(false, "No folder selected", 0, 0, null, null));
	private final ExecutorService executor = Executors.newSingleThreadExecutor();
	private final AtomicBoolean cancelled = new AtomicBoolean();
	private final AppRepository repository;
	private final LibraryImporter importer;
	private volatile LibraryImporter.Preview preview;
	private volatile boolean busy;
	private File backupStage;
	private Uri singleGame, singleData, singleConfig;
	public boolean isSingleGame() { return singleGame != null; }

	public void scanSingle(int part, Uri uri) {
		if (busy) return;
		if (part == 0) { singleGame = uri; singleData = null; singleConfig = null; }
		else if (part == 1) singleData = uri;
		else singleConfig = uri;
		busy = true; cancelled.set(false); preview = null;
		state.setValue(new State(true, "Reading game and saves...", 0, 0, null, null));
		executor.execute(() -> {
			try {
				cleanBackup();
				preview = importer.scanSingle(DocumentFile.fromTreeUri(getApplication(), singleGame),
						singleData == null ? null : DocumentFile.fromTreeUri(getApplication(), singleData),
						singleConfig == null ? null : DocumentFile.fromTreeUri(getApplication(), singleConfig));
				state.postValue(new State(false, "1 game found", 0, 0, preview, null));
			} catch (Exception e) { state.postValue(new State(false, e.getMessage(), 0, 0, null, null)); }
			finally { busy = false; }
		});
	}

	public LibraryImportModel(@NonNull Application app) {
		super(app);
		repository = new AppListModel(app).getAppRepository();
		importer = new LibraryImporter(app, new File(Config.getEmulatorDir()), LibraryImporter.catalog(repository), cancelled);
	}

	public void scan(Uri uri) {
		scan(uri, false);
	}

	public void scanBackup(Uri uri) { scan(uri, true); }

	private void scan(Uri uri, boolean backup) {
		if (busy) return;
		singleGame = singleData = singleConfig = null;
		busy = true; cancelled.set(false); preview = null;
		state.setValue(new State(true, "Reading library...", 0, 0, null, null));
		executor.execute(() -> {
			try {
				cleanBackup();
				LibraryImporter.Preview found;
				if (backup) {
					try (java.io.InputStream in = getApplication().getContentResolver().openInputStream(uri)) {
						if (in == null) throw new java.io.IOException("Cannot open backup");
						backupStage = new LibraryArchive(cancelled).unpack(in, getApplication().getCacheDir());
					}
					found = importer.scanBackup(backupStage);
				} else found = importer.scan(DocumentFile.fromTreeUri(getApplication(), uri));
				preview = found;
				state.postValue(new State(false, found.entries.size() + (found.entries.size() == 1 ? " game found" : " games found"), 0, 0, found, null));
			} catch (Exception e) {
				state.postValue(new State(false, e instanceof CancellationException ? "Cancelled" : e.getMessage(), 0, 0, null, null));
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
		state.setValue(new State(true, "Preparing import...", 0, selected.size(), null, null));
		executor.execute(() -> {
			List<String> results = new ArrayList<>();
			int imported = 0;
			for (LibraryImporter.Entry entry : selected) {
				if (cancelled.get()) break;
				state.postValue(new State(true, "Importing " + entry.title, results.size(), selected.size(), null, null));
				try {
					results.add(entry.title + "\n" + importer.importEntry(entry)); imported++;
				} catch (CancellationException e) { break; }
				catch (Exception e) { results.add(entry.title + "\nNot imported: " + e.getMessage()); }
			}
			int processed = results.size(), failed = processed - imported;
			if (!cancelled.get() && imported > 0 && includeSharedFiles && source.sharedFiles) {
				try { results.add(importer.importSharedFiles(source)); }
				catch (Exception e) { results.add(e.getMessage()); }
			}
			state.postValue(new State(false, (cancelled.get() ? "Stopped. " : "Finished. ") + imported + " imported, "
					+ failed + " failed, " + (selected.size() - processed) + " not processed",
					results.size(), selected.size(), null, results));
			preview = null; busy = false;
			try { cleanBackup(); } catch (java.io.IOException ignored) { }
		});
	}

	public void exportBackup(Uri uri) {
		if (busy) return;
		singleGame = singleData = singleConfig = null;
		busy = true; cancelled.set(false); preview = null;
		state.setValue(new State(true, "Saving games, saves and settings...", 0, 0, null, null));
		executor.execute(() -> {
			boolean success = false;
			try {
				cleanBackup();
				android.app.ActivityManager manager = getApplication().getSystemService(android.app.ActivityManager.class);
				for (android.app.ActivityManager.RunningAppProcessInfo process : manager.getRunningAppProcesses())
					if (process.processName.equals(getApplication().getPackageName() + ":midlet"))
						throw new java.io.IOException("Exit the running game before backing up");
				int count;
				try (java.io.OutputStream out = getApplication().getContentResolver().openOutputStream(uri, "wt")) {
					if (out == null) throw new java.io.IOException("Cannot write backup");
					count = new LibraryArchive(cancelled).write(new File(Config.getEmulatorDir()),
							repository.getAll().firstOrError().blockingGet(), out);
				}
				success = true;
				state.postValue(new State(false, "Backup saved: " + count + " games with saves and settings", 0, 0, null, null));
			} catch (Exception e) {
				state.postValue(new State(false, "Backup not saved: " + e.getMessage(), 0, 0, null, null));
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
