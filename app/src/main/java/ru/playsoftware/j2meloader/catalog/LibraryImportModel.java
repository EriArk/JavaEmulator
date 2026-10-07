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

	public LibraryImportModel(@NonNull Application app) {
		super(app);
		repository = new AppListModel(app).getAppRepository();
		importer = new LibraryImporter(app, new File(Config.getEmulatorDir()), LibraryImporter.catalog(repository), cancelled);
	}

	public void scan(Uri uri) {
		if (busy) return;
		busy = true; cancelled.set(false); preview = null;
		state.setValue(new State(true, "Reading library...", 0, 0, null, null));
		executor.execute(() -> {
			try {
				LibraryImporter.Preview found = importer.scan(DocumentFile.fromTreeUri(getApplication(), uri));
				preview = found;
				state.postValue(new State(false, found.entries.size() + (found.entries.size() == 1 ? " game found" : " games found"), 0, 0, found, null));
			} catch (Exception e) {
				state.postValue(new State(false, e instanceof CancellationException ? "Cancelled" : e.getMessage(), 0, 0, null, null));
			} finally { busy = false; }
		});
	}

	public void start() {
		if (busy || preview == null) return;
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
			state.postValue(new State(false, (cancelled.get() ? "Stopped. " : "Finished. ") + imported + " imported, "
					+ (results.size() - imported) + " failed, " + (selected.size() - results.size()) + " not processed",
					results.size(), selected.size(), null, results));
			preview = null; busy = false;
		});
	}

	public void cancel() { cancelled.set(true); }

	@Override protected void onCleared() {
		cancelled.set(true);
		executor.execute(repository::close);
		executor.shutdown();
	}
}
