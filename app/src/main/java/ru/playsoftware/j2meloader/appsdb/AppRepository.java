/*
 * Copyright 2018 Nikita Shakarun
 * Copyright 2019-2022 Yury Kharchenko
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

package ru.playsoftware.j2meloader.appsdb;

import static ru.playsoftware.j2meloader.util.Constants.PREF_APP_SORT;
import static ru.playsoftware.j2meloader.util.Constants.PREF_EMULATOR_DIR;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.preference.PreferenceManager;
import androidx.sqlite.db.SupportSQLiteProgram;
import androidx.sqlite.db.SupportSQLiteQuery;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import io.reactivex.Completable;
import io.reactivex.CompletableObserver;
import io.reactivex.Flowable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.flowables.ConnectableFlowable;
import io.reactivex.schedulers.Schedulers;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.applist.AppItem;
import ru.playsoftware.j2meloader.applist.AppListModel;
import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.catalog.LibraryImporter;
import ru.playsoftware.j2meloader.util.AppUtils;

public class AppRepository implements SharedPreferences.OnSharedPreferenceChangeListener {

	private final String[] orderTerms;
	private final Context context;
	private final MutableLiveData<List<AppItem>> listLiveData = new MutableLiveData<>();
	private final MutableLiveData<Throwable> errorsLiveData = new MutableLiveData<>();
	private final CompositeDisposable compositeDisposable = new CompositeDisposable();
	private final ErrorObserver errorObserver = new ErrorObserver(errorsLiveData);
	private final java.util.concurrent.ExecutorService databaseExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
	private final io.reactivex.Scheduler databaseScheduler = Schedulers.from(databaseExecutor);
	private volatile boolean closed;

	private AppDatabase db;
	private AppItemDao appItemDao;
	private int sortVariant;

	public AppRepository(AppListModel model) {
		if (model.getAppRepository() != null) {
			throw new IllegalStateException("You must get instance from 'AppListModel'");
		}
		this.context = model.getApplication();
		orderTerms = context.getResources().getStringArray(R.array.pref_app_sort_values);
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
		try {
			sortVariant = preferences.getInt(PREF_APP_SORT, 0);
		} catch (Exception e) {
			sortVariant = preferences.getString(PREF_APP_SORT, "name").equals("name") ? 0 : 1;
			preferences.edit().putInt(PREF_APP_SORT, sortVariant).apply();
		}
		preferences.registerOnSharedPreferenceChangeListener(this);
		String emulatorDir = Config.getEmulatorDir();
		File dir = new File(emulatorDir);
		if (dir.isDirectory() && dir.canWrite()) {
			initDb(emulatorDir);
		}
	}

	public synchronized void initDb(String path) {
		if (closed) return;
		db = AppDatabase.open(context, path, databaseExecutor);
		appItemDao = db.appItemDao();
		ConnectableFlowable<List<AppItem>> listConnectableFlowable = getAll()
				.subscribeOn(databaseScheduler)
				.publish();
		compositeDisposable.add(listConnectableFlowable
				.firstElement()
				.subscribe(list -> {
					if (closed) return;
					synchronized (LibraryImporter.class) {
						LibraryImporter.recover(new File(path), LibraryImporter.catalog(this));
						AppUtils.updateDb(this, new ArrayList<>(list));
					}
				}, errorsLiveData::postValue));
		compositeDisposable.add(listConnectableFlowable.subscribe(listLiveData::postValue, errorsLiveData::postValue));
		compositeDisposable.add(listConnectableFlowable.connect());
	}

	public void observeApps(LifecycleOwner owner, Observer<List<AppItem>> observer) {
		listLiveData.observe(owner, observer);
	}

	public Flowable<List<AppItem>> getAll() {
		return appItemDao.getAll(new MutableSortSQLiteQuery(this, orderTerms));
	}

	public synchronized void insert(AppItem item) {
		if (closed) return;
		AppItemDao dao = appItemDao;
		Completable.fromAction(() -> dao.insert(item))
				.subscribeOn(databaseScheduler)
				.subscribe(errorObserver);
	}

	public synchronized void insert(List<AppItem> items) {
		if (closed) return;
		AppItemDao dao = appItemDao;
		Completable.fromAction(() -> dao.insert(items))
				.subscribeOn(databaseScheduler)
				.subscribe(errorObserver);
	}

	/** Worker-thread only. A rescan discovers new entries; it never replaces installed games. */
	public int indexGames(List<AppItem> items) {
		return indexGames(db, items);
	}

	public static int indexGames(AppDatabase database, List<AppItem> items) {
		synchronized (LibraryImporter.class) {
			return database.runInTransaction(() -> {
				int added = 0;
				AppItemDao dao = database.appItemDao();
				for (AppItem item : items) {
					if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
					if (!"indexed".equals(item.getPreparationState()) || item.getSourceKey() == null)
						throw new IllegalArgumentException("Only discovered games may be indexed");
					if (!dao.containsIndexedSource(item.getSourceKey(), item.getSourceUri())
							&& dao.insertIndexed(item) != -1) added++;
				}
				return added;
			});
		}
	}

	public synchronized void update(AppItem item) {
		if (closed) return;
		AppItemDao dao = appItemDao;
		Completable.fromAction(() -> dao.update(item))
				.subscribeOn(databaseScheduler)
				.subscribe(errorObserver);
	}

	public synchronized void delete(AppItem item) {
		if (closed) return;
		AppItemDao dao = appItemDao;
		Completable.fromAction(() -> dao.delete(item))
				.subscribeOn(databaseScheduler)
				.subscribe(errorObserver);
	}

	public synchronized void delete(List<AppItem> items) {
		if (closed) return;
		AppItemDao dao = appItemDao;
		Completable.fromAction(() -> dao.delete(items))
				.subscribeOn(databaseScheduler)
				.subscribe(errorObserver);
	}

	public synchronized void deleteAll() {
		if (closed) return;
		Completable.fromAction(appItemDao::deleteAll)
				.subscribeOn(databaseScheduler)
				.subscribe(errorObserver);
	}

	public AppItem get(String name, String vendor) {
		return appItemDao.get(name, vendor);
	}

	public AppItem get(int id) {
		return appItemDao.get(id);
	}

	public AppItem getBySourceKey(String sourceKey) {
		return appItemDao.getBySourceKey(sourceKey);
	}

	public AppItem getByPath(String path) {
		return appItemDao.getByPath(path);
	}

	public List<AppItem> getBySourceHash(String hash) { return appItemDao.getBySourceHash(hash); }

	public AppItem getSourceAlias(String key, String fingerprint) { return appItemDao.getSourceAlias(key, fingerprint); }

	/** Worker-thread only; cancel/preview never writes a remembered choice. */
	public void rememberSourceAlias(String key, String fingerprint, AppItem chosen) {
		db.runInTransaction(() -> {
			AppItem fresh = appItemDao.get(chosen.getId());
			if (fresh == null || !fresh.getPath().equals(chosen.getPath())
					|| !java.util.Objects.equals(fresh.getSourceHash(), chosen.getSourceHash())
					|| appItemDao.getBySourceKey(key) != null)
				throw new IllegalStateException("The library changed. Open the game file again.");
			appItemDao.rememberSourceAlias(new SourceAlias(key, fingerprint, chosen.getId()));
		});
	}

	/** Keep row identity and its aliases when reinstalling; report DB failure to the installer. */
	public void saveInstalled(AppItem item) {
		db.runInTransaction(() -> {
			if (item.getId() == 0) item.setId((int) appItemDao.insertImported(item));
			else {
				if (appItemDao.get(item.getId()) == null) throw new IllegalStateException("The game was removed during installation");
				appItemDao.update(item);
			}
		});
	}

	/** Worker-thread only: the importer must observe insertion failure before publishing success. */
	public void insertImported(AppItem item) {
		appItemDao.insertImported(item);
	}

	public void recordLaunch(AppItem item) {
		item.setLastPlayedAt(System.currentTimeMillis());
		item.setPlayCount(item.getPlayCount() + 1);
		update(item);
	}

	public void toggleFavorite(AppItem item) {
		item.setFavorite(!item.isFavorite());
		update(item);
	}

	public synchronized void close() {
		if (closed) return;
		closed = true;
		PreferenceManager.getDefaultSharedPreferences(context).unregisterOnSharedPreferenceChangeListener(this);
		compositeDisposable.dispose();
		AppDatabase previous = db;
		// Finish accepted writes and in-flight Room reads before closing their connection.
		databaseExecutor.execute(() -> {
			try { if (previous != null) previous.close(); }
			finally { databaseExecutor.shutdown(); }
		});
	}

	public int getSort() {
		return sortVariant;
	}

	private void setSort(int variant) {
		if (closed || appItemDao == null) return;
		if (this.sortVariant == variant) {
			variant |= 0x80000000;
		}
		this.sortVariant = variant;
		Disposable disposable = appItemDao.getAllSingle(new MutableSortSQLiteQuery(this, orderTerms))
				.subscribeOn(databaseScheduler)
				.subscribe(listLiveData::postValue, errorsLiveData::postValue);
		compositeDisposable.add(disposable);
	}

	@Override
	public synchronized void onSharedPreferenceChanged(SharedPreferences sp, String key) {
		if (closed) return;
		if (PREF_APP_SORT.equals(key)) {
			setSort(sp.getInt(PREF_APP_SORT, 0));
		} else if (PREF_EMULATOR_DIR.equals(key)) {
			String newPath = sp.getString(key, null);
			if (db != null) {
				String databaseName = db.getOpenHelper().getDatabaseName();
				if (databaseName != null) {
					String dbDir = new File(databaseName).getParent();
					if (dbDir != null) {
						if (dbDir.equals(newPath)) {
							return;
						}
					}
				}
				compositeDisposable.clear();
				AppDatabase previous = db;
				databaseExecutor.execute(previous::close);
			}
			initDb(newPath);
		}
	}

	public void observeErrors(LifecycleOwner owner, Observer<Throwable> observer) {
		errorsLiveData.observe(owner, observer);
	}

	public void onWorkDirReady() {
		if (db == null) {
			initDb(Config.getEmulatorDir());
		}
	}

	private static class ErrorObserver implements CompletableObserver {
		private final MutableLiveData<Throwable> callback;

		public ErrorObserver(MutableLiveData<Throwable> callback) {
			this.callback = callback;
		}

		@Override
		public void onSubscribe(@NotNull Disposable d) {
		}

		@Override
		public void onComplete() {
		}

		@Override
		public void onError(@NotNull Throwable e) {
			callback.postValue(e);
		}
	}

	private static class MutableSortSQLiteQuery implements SupportSQLiteQuery {
		private static final String SELECT = "SELECT * FROM apps ORDER BY ";
		private final AppRepository repository;
		private final String[] orderTerms;

		private MutableSortSQLiteQuery(AppRepository repository, String[] orderTerms) {
			this.repository = repository;
			this.orderTerms = orderTerms;
		}

		@Override
		public String getSql() {
			int sortVariant = repository.getSort();
			String order = sortVariant >= 0 ? " ASC" : " DESC";
			return SELECT + String.format(orderTerms[sortVariant & 0x7FFFFFFF], order);
		}

		@Override
		public void bindTo(SupportSQLiteProgram statement) {
		}

		@Override
		public int getArgCount() {
			return 0;
		}
	}
}
