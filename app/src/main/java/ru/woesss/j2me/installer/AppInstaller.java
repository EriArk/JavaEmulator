/*
 *  Copyright 2020-2022 Yury Kharchenko
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package ru.woesss.j2me.installer;

import android.app.Application;
import android.net.Uri;
import android.util.Log;

import com.android.dx.command.dexer.Main;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.io.inputstream.ZipInputStream;
import net.lingala.zip4j.model.FileHeader;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.jar.JarFile;

import io.reactivex.Single;
import io.reactivex.SingleEmitter;
import ru.playsoftware.j2meloader.applist.AppItem;
import ru.playsoftware.j2meloader.applist.GameArtwork;
import ru.playsoftware.j2meloader.appsdb.AppRepository;
import ru.playsoftware.j2meloader.catalog.SourceIdentity;
import ru.playsoftware.j2meloader.catalog.SourceHints;
import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.util.ConverterException;
import ru.playsoftware.j2meloader.util.FileUtils;
import ru.playsoftware.j2meloader.util.ZipUtils;
import ru.woesss.j2me.jar.Descriptor;

public class AppInstaller {
	private static final String TAG = AppInstaller.class.getSimpleName();
	static final int STATUS_OLDEST = -1;
	static final int STATUS_EQUAL = 0;
	static final int STATUS_NEWEST = 1;
	static final int STATUS_NEW = 2;
	static final int STATUS_UNMATCHED = 3;
	static final int STATUS_NEED_JAD = 4;
	static final int STATUS_SUCCESS = 5;
	static final int STATUS_ARCHIVE_CHOICE = 6;
	static final int STATUS_EXISTING_CHOICES = 7;
	static final int STATUS_REUSED = 8;
	static final int STATUS_ADDITIONAL_GAME = 9;

	private final int id;
	private final Application context;
	private final AppRepository appRepository;
	private final File cacheDir;

	private Uri uri;
	private Descriptor manifest;
	private Descriptor newDesc;
	private String appDirName;
	private File targetDir;
	private File srcJar;
	private File tmpDir;
	private AppItem currentApp;
	private File srcFile;
	private File archiveSource;
	private List<String> archiveEntries;
	private String selectedArchiveEntry;
	private String sourceName;
	private List<AppItem> existingChoices = java.util.Collections.emptyList();
	private String incomingHash, incomingFingerprint;
	private boolean separateCopy;

	AppInstaller(Uri uri, Application context, AppRepository appRepository) {
		id = -1;
		this.appRepository = appRepository;
		this.uri = uri;
		this.context = context;
		this.cacheDir = new File(context.getCacheDir(), "installer");
	}

	public AppInstaller(int id, Application context, AppRepository appRepository) {
		this.id = id;
		this.context = context;
		this.appRepository = appRepository;
		this.cacheDir = new File(context.getCacheDir(), "installer");
	}

	Descriptor getNewDescriptor() {
		return newDesc;
	}

	String getCurrentVersion() {
		return currentApp.getVersion();
	}

	Descriptor getManifest() {
		return manifest;
	}

	/** Load and check app info from source */
	void loadInfo(SingleEmitter<Integer> emitter) throws IOException, ConverterException {
		if (id != -1) {
			currentApp = appRepository.get(id);
			sourceName = SourceHints.read(new File(currentApp.getPathExt()));
			srcJar = new File(currentApp.getPathExt(), Config.MIDLET_RES_FILE);
			newDesc = new Descriptor(new File(currentApp.getPathExt(), Config.MIDLET_MANIFEST_FILE), false);
			appDirName = currentApp.getPath();
			targetDir = new File(Config.getAppDir(), appDirName);
			emitter.onSuccess(STATUS_EQUAL);
			return;
		}
		sourceName = SourceHints.fromUri(context, uri);
		boolean isLocal;
		boolean isContentUri = uri.getScheme().equals("content");
		if ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) {
			downloadJad();
			isLocal = false;
		} else {
			srcFile = FileUtils.getFileForUri(context, uri);
			isLocal = true;
		}

		String name = srcFile.getName();
		String lowerName = name.toLowerCase(Locale.US);
		if (lowerName.endsWith(".zip") || lowerName.endsWith(".7z")) {
			archiveSource = srcFile;
			archiveEntries = listArchiveEntries(srcFile);
			if (archiveEntries.size() > 1) {
				emitter.onSuccess(STATUS_ARCHIVE_CHOICE);
				return;
			}
			resolveArchive(srcFile, archiveEntries.get(0));
			name = srcFile.getName();
			if (GameArchive.mophun(name)) {
				emitter.onSuccess(STATUS_ADDITIONAL_GAME);
				return;
			}
		}

		if (name.toLowerCase().endsWith(".jad")) {
			newDesc = new Descriptor(srcFile, true);
			String url = newDesc.getJarUrl();
			if (url == null) {
				throw new ConverterException("Jad not have " + Descriptor.MIDLET_JAR_URL);
			}
			Uri uri = Uri.parse(url);
			String scheme = uri.getScheme();
			String host = uri.getHost();
			if (isLocal && scheme == null && host == null) {
				if (isContentUri && !FileUtils.isExternalStorageLegacy()) {
					emitter.onSuccess(STATUS_NEED_JAD);
					return;
				} else if (!checkJarFile(srcFile)) {
					emitter.onSuccess(STATUS_UNMATCHED);
					return;
				}
			}
		} else if (name.toLowerCase().endsWith(".kjx")) {
			// Load kjx file
			parseKjx();
			newDesc = new Descriptor(srcFile, true);
		} else {
			srcJar = srcFile;
			newDesc = loadManifest(srcFile);
		}
		int result = checkDescriptor();
		emitter.onSuccess(result);
	}

	Single<Integer> updateInfo(Uri jarUri) {
		return Single.create(emitter -> {
			srcJar = FileUtils.getFileForUri(context, jarUri);
			sourceName = SourceHints.fromUri(context, jarUri);
			manifest = loadManifest(srcJar);
			if (!manifest.equals(newDesc)) {
				emitter.onSuccess(STATUS_UNMATCHED);
				return;
			}
			int result = checkDescriptor();
			emitter.onSuccess(result);
		});
	}

	Single<Integer> useJarManifest() {
		return Single.create(emitter -> {
			if (srcJar == null || manifest == null) {
				throw new IOException("No JAR selected");
			}
			// Keep the original source identity and staged JAR; only discard the mismatched JAD.
			newDesc = manifest;
			emitter.onSuccess(checkDescriptor());
		});
	}

	List<String> getArchiveEntries() {
		return archiveEntries == null ? java.util.Collections.emptyList() : archiveEntries;
	}

	Single<Integer> selectArchiveEntry(String entryName) {
		return Single.create(emitter -> {
			resolveArchive(archiveSource, entryName);
			if (GameArchive.mophun(entryName)) {
				emitter.onSuccess(STATUS_ADDITIONAL_GAME);
				return;
			}
			newDesc = loadManifest(srcJar);
			emitter.onSuccess(checkDescriptor());
		});
	}

	private List<String> listArchiveEntries(File archive) throws IOException {
		return GameArchive.list(archive);
	}

	private void resolveArchive(File archive, String entryName) throws IOException {
		if (!cacheDir.exists() && !cacheDir.mkdirs()) throw new IOException("Can't create installer cache");
		boolean mophun = GameArchive.mophun(entryName);
		File output = new File(cacheDir, mophun ? "archive-game.mpn" : "archive-game.jar");
		GameArchive.extract(archive, entryName, output);
		selectedArchiveEntry = entryName;
		srcFile = output;
		srcJar = mophun ? null : output;
		sourceName = mophun ? GameArchive.leaf(entryName) : SourceHints.fileName(entryName);
	}

	String getSourceName() { return sourceName; }

	Single<Integer> installAdditional() {
		return Single.fromCallable(() -> {
			try {
				ru.playsoftware.j2meloader.catalog.AdditionalGames.Engine engine =
						ru.playsoftware.j2meloader.catalog.AdditionalGames.get(context);
				if (engine == null) throw new IOException("Mophun is not available in this build");
				currentApp = engine.installArchiveGame(context, new File(Config.getEmulatorDir()),
						srcFile, sourceName, uri, selectedArchiveEntry, archiveEntries.size() == 1);
				return STATUS_SUCCESS;
			} finally {
				clearCache();
				deleteTemp();
			}
		});
	}

	private void parseKjx() throws ConverterException {
		if (!cacheDir.exists() && !cacheDir.mkdirs()) {
			throw new ConverterException("Can't create cache dir");
		}
		try (DataInputStream dis = new DataInputStream(new FileInputStream(srcFile))) {
			byte[] magic = new byte[3];
			dis.readFully(magic, 0, 3);
			if (!Arrays.equals(magic, "KJX".getBytes())) {
				throw new ConverterException("Magic KJX does not match: " + new String(magic));
			}

			/*byte startJadPos = */dis.readByte();
			byte lenKjxFileName = dis.readByte();
			dis.skipBytes(lenKjxFileName);
			int lenJadFileContent = dis.readUnsignedShort();
			byte lenJadFileName = dis.readByte();
			byte[] jadFileName = new byte[lenJadFileName];
			dis.readFully(jadFileName, 0, lenJadFileName);
			String strJadFileName = new String(jadFileName);

			int bufSize = 2048;
			byte[] buf = new byte[bufSize];

			File jadFile = new File(cacheDir, strJadFileName);
			try (FileOutputStream fos = new FileOutputStream(jadFile)) {
				int restSize = lenJadFileContent;
				while (restSize > 0) {
					int readSize = dis.read(buf, 0, Math.min(restSize, bufSize));
					fos.write(buf, 0, readSize);
					restSize -= readSize;
				}
			}

			File jarFile = new File(cacheDir, strJadFileName.substring(0, strJadFileName.length() - 4) + ".jar");
			try (FileOutputStream fos = new FileOutputStream(jarFile)) {
				int length;
				while ((length = dis.read(buf)) > 0) {
					fos.write(buf, 0, length);
				}
			}

			srcFile = jadFile;
			srcJar = jarFile;
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void downloadJad() throws ConverterException {
		if (!cacheDir.exists() && !cacheDir.mkdirs()) {
			throw new ConverterException("Can't create cache dir");
		}
		srcFile = new File(cacheDir, "tmp.jad");
		String url = uri.toString();
		Log.d(TAG, "Downloading " + url);
		Exception exception;
		HttpURLConnection connection = null;
		try {
			connection = (HttpURLConnection) new URL(url).openConnection();
			connection.setInstanceFollowRedirects(true);
			connection.setReadTimeout(3 * 60 * 1000);
			connection.setConnectTimeout(15000);
			int code = connection.getResponseCode();
			if (code == HttpURLConnection.HTTP_MOVED_PERM
					|| code == HttpURLConnection.HTTP_MOVED_TEMP) {
				String urlStr = connection.getHeaderField("Location");
				connection.disconnect();
				connection = (HttpURLConnection) new URL(urlStr).openConnection();
				connection.setInstanceFollowRedirects(true);
				connection.setReadTimeout(3 * 60 * 1000);
				connection.setConnectTimeout(15000);
			}
			try (InputStream inputStream = connection.getInputStream();
				 OutputStream outputStream = new FileOutputStream(srcFile)) {
				byte[] buffer = new byte[2048];
				int length;
				while ((length = inputStream.read(buffer)) > 0) {
					outputStream.write(buffer, 0, length);
				}
			}
			connection.disconnect();
			Log.d(TAG, "Download complete");
			return;
		} catch (MalformedURLException e) {
			exception = e;
		} catch (FileNotFoundException e) {
			exception = e;
		} catch (IOException e) {
			exception = e;
		} finally {
			if (connection != null) {
				connection.disconnect();
			}
		}
		deleteTemp();
		throw new ConverterException("Can't download jad", exception);
	}

	/** Install app */
	void install(SingleEmitter<Integer> emitter) throws ConverterException, IOException {
		if(!existingChoices.isEmpty() && !separateCopy)
			throw new IOException("Choose an existing game or install separately first.");
		if (!cacheDir.exists() && !cacheDir.mkdirs()) {
			throw new ConverterException("Can't create cache dir");
		}
		tmpDir = new File(targetDir.getParent(), ".tmp");
		if (!tmpDir.isDirectory() && !tmpDir.mkdirs())
			throw new ConverterException("Can't create directory: '" + targetDir + "'");
		if (srcJar == null) {
			srcJar = new File(cacheDir, "tmp.jar");
			sourceName = SourceHints.fromUri(context, Uri.parse(newDesc.getJarUrl()));
			downloadJar();
			manifest = loadManifest(srcJar);
			if (!manifest.equals(newDesc)) {
				emitter.onSuccess(STATUS_UNMATCHED);
				return;
			}
			int resolved=checkDescriptor();
			if(resolved==STATUS_EXISTING_CHOICES || resolved==STATUS_REUSED) {
				emitter.onSuccess(resolved);
				return;
			}
		}
		try {
			Main.main(new String[]{"--no-optimize", "--core-library",
					"--output=" + tmpDir + Config.MIDLET_DEX_FILE,
					srcJar.getAbsolutePath()});
		} catch (Throwable e) {
			throw new ConverterException("Dexing error", e);
		}
		if (manifest != null) {
			manifest.merge(newDesc);
			newDesc = manifest;
		}
		File resJar = new File(tmpDir, Config.MIDLET_RES_FILE);
		FileUtils.copyFileUsingChannel(srcJar, resJar);
		try {
			GameArtwork.refresh(tmpDir, newDesc.getIcon());
		} catch (IOException e) {
			Log.w(TAG, "Can't extract artwork", e);
		}
		if (currentApp != null) GameArtwork.preserveUserArt(new File(currentApp.getPathExt()), tmpDir);
		newDesc.writeTo(new File(tmpDir, Config.MIDLET_MANIFEST_FILE));
		SourceHints.write(tmpDir, sourceName);
		FileUtils.deleteDirectory(targetDir);
		if (!tmpDir.renameTo(targetDir)) {
			throw new ConverterException("Can't move '" + tmpDir + "' to '" + targetDir + "'");
		}
		String name = newDesc.getName();
		AppItem app = new AppItem(appDirName, name, newDesc.getVendor(), newDesc.getVersion());
		if (uri != null) {
			app.setSourceUri(uri.toString());
			app.setSourceKey(sourceKey());
		}
		app.setSourceHash(SourceIdentity.sha256(srcJar));
		GameArtwork.applyPaths(app, targetDir);
		if (currentApp != null) {
			app.setId(currentApp.getId());
			if (!"indexed".equals(currentApp.getPreparationState())) {
				app.setTitle(currentApp.getTitle());
			}
			app.setFavorite(currentApp.isFavorite());
			app.setLastPlayedAt(currentApp.getLastPlayedAt());
			app.setPlayCount(currentApp.getPlayCount());
			if (app.getSourceUri() == null) {
				app.setSourceUri(currentApp.getSourceUri());
				app.setSourceKey(currentApp.getSourceKey());
			}
			String path = currentApp.getPath();
			if (!path.equals(appDirName)) {
				File rms = new File(Config.getDataDir(), path);
				if (rms.exists()) {
					File newRms = new File(Config.getDataDir(), appDirName);
					FileUtils.deleteDirectory(newRms);
					rms.renameTo(newRms);
				}
				File config = new File(Config.getConfigsDir(), path);
				if (config.exists()) {
					File newConfig = new File(Config.getConfigsDir(), appDirName);
					FileUtils.deleteDirectory(newConfig);
					config.renameTo(newConfig);
				}
				File appDir = new File(Config.getAppDir(), path);
				FileUtils.deleteDirectory(appDir);
			}
		}
		currentApp = app;
		appRepository.saveInstalled(app);
		clearCache();
		deleteTemp();
		emitter.onSuccess(STATUS_SUCCESS);
	}


	private Descriptor loadManifest(File jar) throws IOException {
		ZipFile zip = new ZipFile(jar);
		FileHeader manifest = zip.getFileHeader(JarFile.MANIFEST_NAME);
		if (manifest == null) throw new IOException("JAR not have " + JarFile.MANIFEST_NAME);
		try (ZipInputStream is = zip.getInputStream(manifest)) {
			ByteArrayOutputStream baos = new ByteArrayOutputStream(20480);
			byte[] buf = new byte[4096];
			int read;
			while ((read = is.read(buf)) != -1) {
				baos.write(buf, 0, read);
			}
			return new Descriptor(baos.toString(), false);
		}
	}

	/** return true if JAR exists and matches JAD **/
	private boolean checkJarFile(File jad) throws IOException, ConverterException {
		File dir = jad.getParentFile();
		String jarUrl = newDesc.getJarUrl();
		File jar = new File(dir, jarUrl);
		if (!jar.exists()) {
			String name = jad.getName();
			jar = new File(dir, name.substring(0, name.length() - 4) + ".jar");
			if (!jar.exists()) {
				throw new ConverterException("Jar-file not found for url: " + jarUrl);
			}
		}
		srcJar = jar;
		sourceName = SourceHints.fileName(jar.getName());
		manifest = loadManifest(jar);
		return manifest.equals(newDesc);
	}

	private int checkDescriptor() throws IOException {
		// Remove invalid characters from app path
		String name = newDesc.getName();
		String sourceKey = sourceKey();
		currentApp = sourceKey == null ? null : appRepository.getBySourceKey(sourceKey);
		existingChoices = java.util.Collections.emptyList();
		separateCopy = false;
		if (currentApp == null && srcJar != null && sourceKey != null) {
			incomingHash = SourceIdentity.sha256(srcJar);
			incomingFingerprint = ImportFingerprint.of(incomingHash, manifest, newDesc);
			AppItem remembered = appRepository.getSourceAlias(sourceKey, incomingFingerprint);
			if (matchesInstalled(remembered)) {
				currentApp = remembered;
				clearCache();
				deleteTemp();
				return STATUS_REUSED;
			}
			existingChoices = new ArrayList<>();
			for (AppItem item : appRepository.getBySourceHash(incomingHash)) {
				if (matchesInstalled(item)) existingChoices.add(item);
			}
			if (!existingChoices.isEmpty()) return STATUS_EXISTING_CHOICES;
		}
		if (currentApp == null) {
			generatePathName(name.replaceAll(FileUtils.ILLEGAL_FILENAME_CHARS, "").trim());
			return STATUS_NEW;
		}
		appDirName = currentApp.getPath();
		targetDir = new File(Config.getAppDir(), appDirName);
		int versionStatus = newDesc.compareVersion(currentApp.getVersion());
		// A completed catalog row can survive an interrupted write of its prepared files.
		// Reimport the supplied source in place; conversion preserves RMS and settings.
		if (srcJar != null && !hasPreparedFiles(currentApp)) {
			return STATUS_NEW;
		}
		if (versionStatus == STATUS_EQUAL && srcJar != null && currentApp.getSourceHash() != null) {
			try {
				String sourceHash = SourceIdentity.sha256(srcJar);
				if (!currentApp.getSourceHash().equals(sourceHash)) {
					return STATUS_NEWEST;
				}
				if (!sourceHash.equals(SourceIdentity.sha256(
						new File(targetDir, Config.MIDLET_RES_FILE)))) {
					return STATUS_NEW;
				}
			} catch (IOException e) {
				Log.w(TAG, "Unable to compare source hash", e);
			}
		}
		return versionStatus;
	}

	List<AppItem> getExistingChoices() { return existingChoices; }

	private boolean hasPreparedFiles(AppItem item) {
		File directory = new File(item.getPathExt());
		File dex = new File(directory, Config.MIDLET_DEX_FILE);
		File jar = new File(directory, Config.MIDLET_RES_FILE);
		File descriptor = new File(directory, Config.MIDLET_MANIFEST_FILE);
		if (!dex.isFile() || dex.length() == 0 || !jar.isFile() || jar.length() == 0
				|| !descriptor.isFile() || descriptor.length() == 0) return false;
		try {
			Descriptor stored = new Descriptor(descriptor, false);
			return stored.getName() != null && stored.getAttrs().containsKey("MIDlet-1");
		} catch (IOException e) {
			Log.w(TAG, "Prepared descriptor needs reimport", e);
			return false;
		}
	}

	private boolean matchesInstalled(AppItem item) throws IOException {
		if (item == null || !incomingHash.equals(item.getSourceHash())
				|| "indexed".equals(item.getPreparationState()) || !hasPreparedFiles(item)) return false;
		File jar = new File(item.getPathExt(), Config.MIDLET_RES_FILE);
		return jar.isFile() && incomingHash.equals(SourceIdentity.sha256(jar));
	}

	Single<Integer> useExisting(int appId) {
		return Single.create(emitter -> {
			boolean offered = false;
			for (AppItem item : existingChoices) if (item.getId() == appId) offered = true;
			AppItem chosen = appRepository.get(appId);
			if (!offered || !matchesInstalled(chosen) || !incomingHash.equals(SourceIdentity.sha256(srcJar)))
				throw new IOException("The game file or library changed. Open the file again.");
			appRepository.rememberSourceAlias(sourceKey(), incomingFingerprint, chosen);
			currentApp = chosen;
			clearCache();
			deleteTemp();
			emitter.onSuccess(STATUS_REUSED);
		});
	}

	Single<Integer> installSeparately() {
		return Single.create(emitter -> {
			if (existingChoices.isEmpty() || appRepository.getBySourceKey(sourceKey()) != null)
				throw new IOException("The library changed. Open the file again.");
			currentApp = null;
			separateCopy = true;
			generatePathName(newDesc.getName().replaceAll(FileUtils.ILLEGAL_FILENAME_CHARS, "").trim());
			emitter.onSuccess(STATUS_NEW);
		});
	}

	private String sourceKey() {
		String key = SourceIdentity.key(uri);
		if (key != null && selectedArchiveEntry != null) {
			key += "#" + selectedArchiveEntry;
		}
		return key;
	}

	private void generatePathName(String name) {
		String appsDir = Config.getAppDir();
		File dir = new File(appsDir, name);
		for (int i = 1; dir.exists(); i++) {
			dir = new File(appsDir, name + "_" + i);
		}
		appDirName = dir.getName();
		targetDir = dir;
	}

	private void downloadJar() throws ConverterException {
		Uri jarUri = Uri.parse(newDesc.getJarUrl());
		if (jarUri.getScheme() == null) {
			String schemeOfJadSource = this.uri.getScheme();
			if ("http".equals(schemeOfJadSource) || "https".equals(schemeOfJadSource)) {
				List<String> pathSegments = uri.getPathSegments();
				StringBuilder path = new StringBuilder(pathSegments.get(0));
				for (int i = 1; i < pathSegments.size() - 1; i++) {
					path.append('/').append(pathSegments.get(i));
				}
				path.append('/').append(jarUri.getPath());
				jarUri = uri.buildUpon().path(path.toString()).build();
			} else {
				jarUri = jarUri.buildUpon().scheme("http").build();
			}
		}
		String url = jarUri.toString();
		Log.d(TAG, "Downloading " + url);
		Exception exception;
		HttpURLConnection connection = null;
		try {
			connection = (HttpURLConnection) new URL(url).openConnection();
			connection.setInstanceFollowRedirects(true);
			connection.setReadTimeout(3 * 60 * 1000);
			connection.setConnectTimeout(15000);
			int code = connection.getResponseCode();
			if (code == HttpURLConnection.HTTP_MOVED_PERM
					|| code == HttpURLConnection.HTTP_MOVED_TEMP) {
				String urlStr = connection.getHeaderField("Location");
				connection.disconnect();
				connection = (HttpURLConnection) new URL(urlStr).openConnection();
				connection.setInstanceFollowRedirects(true);
				connection.setReadTimeout(3 * 60 * 1000);
				connection.setConnectTimeout(15000);
			}
			try (InputStream inputStream = connection.getInputStream();
				 OutputStream outputStream = new FileOutputStream(srcJar)) {
				byte[] buffer = new byte[2048];
				int length;
				while ((length = inputStream.read(buffer)) > 0) {
					outputStream.write(buffer, 0, length);
				}
			}
			connection.disconnect();
			Log.d(TAG, "Download complete");
			return;
		} catch (MalformedURLException e) {
			exception = e;
		} catch (FileNotFoundException e) {
			exception = e;
		} catch (IOException e) {
			exception = e;
		} finally {
			if (connection != null) {
				connection.disconnect();
			}
		}
		deleteTemp();
		throw new ConverterException("Can't download jar", exception);
	}

	void deleteTemp() {
		if (tmpDir != null) {
			FileUtils.deleteDirectory(tmpDir);
		}
	}

	public String getJar() {
		return srcJar == null ? null : srcJar.getAbsolutePath();
	}

	void clearCache() {
		FileUtils.deleteDirectory(cacheDir);
	}

	String getIconPath() {
		return targetDir.getAbsolutePath() + Config.MIDLET_ICON_FILE;
	}

	public AppItem getExistsApp() {
		return currentApp;
	}
}
