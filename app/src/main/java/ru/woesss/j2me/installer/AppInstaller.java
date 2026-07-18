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
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;

import com.android.dx.command.dexer.Main;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.io.inputstream.ZipInputStream;
import net.lingala.zip4j.model.FileHeader;

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;

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
import ru.playsoftware.j2meloader.appsdb.AppRepository;
import ru.playsoftware.j2meloader.catalog.SourceIdentity;
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

	AppInstaller(String path, Uri uri, Application context, AppRepository appRepository) {
		id = -1;
		this.appRepository = appRepository;
		if (path != null) srcFile = new File(path);
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
			srcJar = new File(currentApp.getPathExt(), Config.MIDLET_RES_FILE);
			newDesc = new Descriptor(new File(currentApp.getPathExt(), Config.MIDLET_MANIFEST_FILE), false);
			appDirName = currentApp.getPath();
			targetDir = new File(Config.getAppDir(), appDirName);
			emitter.onSuccess(STATUS_EQUAL);
			return;
		}
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
			manifest = loadManifest(srcJar);
			if (!manifest.equals(newDesc)) {
				emitter.onSuccess(STATUS_UNMATCHED);
				return;
			}
			int result = checkDescriptor();
			emitter.onSuccess(result);
		});
	}

	List<String> getArchiveEntries() {
		return archiveEntries == null ? java.util.Collections.emptyList() : archiveEntries;
	}

	Single<Integer> selectArchiveEntry(String entryName) {
		return Single.create(emitter -> {
			resolveArchive(archiveSource, entryName);
			newDesc = loadManifest(srcJar);
			emitter.onSuccess(checkDescriptor());
		});
	}

	private List<String> listArchiveEntries(File archive) throws IOException, ConverterException {
		ArrayList<String> entries = new ArrayList<>();
		if (archive.getName().toLowerCase(Locale.US).endsWith(".zip")) {
			try (ZipFile zip = new ZipFile(archive)) {
				for (FileHeader header : zip.getFileHeaders()) {
					if (!header.isDirectory() && header.getFileName().toLowerCase(Locale.US).endsWith(".jar")) {
						entries.add(header.getFileName());
					}
				}
			}
		} else {
			try (SevenZFile sevenZ = new SevenZFile(archive)) {
				SevenZArchiveEntry entry;
				while ((entry = sevenZ.getNextEntry()) != null) {
					if (!entry.isDirectory() && entry.getName().toLowerCase(Locale.US).endsWith(".jar")) {
						entries.add(entry.getName());
					}
				}
			}
		}
		if (entries.isEmpty()) throw new ConverterException("Archive does not contain a JAR");
		return entries;
	}

	private void resolveArchive(File archive, String entryName) throws IOException, ConverterException {
		selectedArchiveEntry = entryName;
		if (!cacheDir.exists() && !cacheDir.mkdirs()) {
			throw new ConverterException("Can't create installer cache");
		}
		File output = new File(cacheDir, "archive-game.jar");
		String lower = archive.getName().toLowerCase(Locale.US);
		if (lower.endsWith(".zip")) {
			FileHeader selected = null;
			try (ZipFile zip = new ZipFile(archive)) {
				for (FileHeader header : zip.getFileHeaders()) {
					if (!header.isDirectory() && header.getFileName().equals(entryName)) {
						selected = header;
						break;
					}
				}
				if (selected == null) throw new ConverterException("Archive does not contain a JAR");
				if (selected.getUncompressedSize() > 64L * 1024 * 1024) {
					throw new ConverterException("JAR in archive is too large");
				}
				try (InputStream input = zip.getInputStream(selected);
					 OutputStream target = new FileOutputStream(output)) {
					copyLimited(input, target);
				}
			}
		} else {
			try (SevenZFile sevenZ = new SevenZFile(archive)) {
				SevenZArchiveEntry selected = null;
				SevenZArchiveEntry entry;
				while ((entry = sevenZ.getNextEntry()) != null) {
					if (!entry.isDirectory() && entry.getName().equals(entryName)) {
						selected = entry;
						break;
					}
				}
				if (selected == null) throw new ConverterException("Archive does not contain a JAR");
				if (selected.getSize() > 64L * 1024 * 1024) {
					throw new ConverterException("JAR in archive is too large");
				}
				try (OutputStream target = new FileOutputStream(output)) {
					byte[] buffer = new byte[32 * 1024];
					long total = 0;
					int read;
					while ((read = sevenZ.read(buffer)) > 0) {
						total += read;
						if (total > 64L * 1024 * 1024) throw new ConverterException("JAR is too large");
						target.write(buffer, 0, read);
					}
				}
			}
		}
		srcFile = output;
		srcJar = output;
	}

	private void copyLimited(InputStream input, OutputStream output)
			throws IOException, ConverterException {
		byte[] buffer = new byte[32 * 1024];
		long total = 0;
		int read;
		while ((read = input.read(buffer)) != -1) {
			total += read;
			if (total > 64L * 1024 * 1024) throw new ConverterException("JAR is too large");
			output.write(buffer, 0, read);
		}
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
		if (!cacheDir.exists() && !cacheDir.mkdirs()) {
			throw new ConverterException("Can't create cache dir");
		}
		tmpDir = new File(targetDir.getParent(), ".tmp");
		if (!tmpDir.isDirectory() && !tmpDir.mkdirs())
			throw new ConverterException("Can't create directory: '" + targetDir + "'");
		if (srcJar == null) {
			srcJar = new File(cacheDir, "tmp.jar");
			downloadJar();
			manifest = loadManifest(srcJar);
			if (!manifest.equals(newDesc)) {
				emitter.onSuccess(STATUS_UNMATCHED);
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
		String icon = findIconEntry(resJar, newDesc.getIcon());
		File iconFile = new File(tmpDir, Config.MIDLET_ICON_FILE);
		if (icon != null) {
			try {
				ZipUtils.unzipEntry(resJar, icon, iconFile);
			} catch (IOException e) {
				Log.w(TAG, "Can't unzip icon: " + icon, e);
				icon = null;
				//noinspection ResultOfMethodCallIgnored
				iconFile.delete();
			}
		}
		String cover = findCoverEntry(resJar, icon);
		File coverFile = new File(tmpDir, Config.MIDLET_COVER_FILE);
		if (cover != null) {
			try {
				ZipUtils.unzipEntry(resJar, cover, coverFile);
			} catch (IOException e) {
				Log.w(TAG, "Can't unzip cover: " + cover, e);
				cover = null;
				//noinspection ResultOfMethodCallIgnored
				coverFile.delete();
			}
		}
		newDesc.writeTo(new File(tmpDir, Config.MIDLET_MANIFEST_FILE));
		FileUtils.deleteDirectory(targetDir);
		if (!tmpDir.renameTo(targetDir)) {
			throw new ConverterException("Can't move '" + tmpDir + "' to '" + targetDir + "'");
		}
		String name = newDesc.getName();
		String vendor = newDesc.getVendor();
		AppItem app = new AppItem(appDirName, name, vendor, newDesc.getVersion());
		if (uri != null) {
			app.setSourceUri(uri.toString());
			app.setSourceKey(sourceKey());
		}
		app.setSourceHash(SourceIdentity.sha256(srcJar));
		if (icon != null) {
			app.setImagePathExt(Config.MIDLET_ICON_FILE);
		}
		if (cover != null) {
			app.setCoverPathExt(Config.MIDLET_COVER_FILE);
		}
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
		appRepository.insert(app);
		clearCache();
		deleteTemp();
		emitter.onSuccess(STATUS_SUCCESS);
	}

	private String findCoverEntry(File jar, String iconPath) {
		String best = null;
		int bestScore = 0;
		try (ZipFile zip = new ZipFile(jar)) {
			List<FileHeader> headers = zip.getFileHeaders();
			for (FileHeader header : headers) {
				if (header.isDirectory()) {
					continue;
				}
				String name = header.getFileName();
				String lower = name.toLowerCase(Locale.US);
				if (!isImageFile(lower)) {
					continue;
				}
				if (iconPath != null && lower.equals(iconPath.toLowerCase(Locale.US))) {
					continue;
				}
				BitmapFactory.Options options = new BitmapFactory.Options();
				options.inJustDecodeBounds = true;
				try (InputStream stream = zip.getInputStream(header)) {
					BitmapFactory.decodeStream(stream, null, options);
				} catch (Exception ignored) {
					continue;
				}
				if (options.outWidth <= 0 || options.outHeight <= 0) {
					continue;
				}
				int score = scoreCoverCandidate(lower, options.outWidth, options.outHeight);
				if (score > bestScore) {
					bestScore = score;
					best = name;
				}
			}
		} catch (Exception e) {
			Log.w(TAG, "Can't scan cover art", e);
		}
		return bestScore > 0 ? best : null;
	}

	private String findIconEntry(File jar, String preferredIconPath) {
		String best = null;
		int bestScore = Integer.MIN_VALUE;
		String preferred = preferredIconPath == null ? null : preferredIconPath.toLowerCase(Locale.US);
		try (ZipFile zip = new ZipFile(jar)) {
			List<FileHeader> headers = zip.getFileHeaders();
			for (FileHeader header : headers) {
				if (header.isDirectory()) {
					continue;
				}
				String name = header.getFileName();
				String lower = name.toLowerCase(Locale.US);
				if (!isImageFile(lower)) {
					continue;
				}
				BitmapFactory.Options options = new BitmapFactory.Options();
				options.inJustDecodeBounds = true;
				try (InputStream stream = zip.getInputStream(header)) {
					BitmapFactory.decodeStream(stream, null, options);
				} catch (Exception ignored) {
					continue;
				}
				if (options.outWidth <= 0 || options.outHeight <= 0) {
					continue;
				}
				boolean isPreferred = preferred != null && lower.equals(preferred);
				int score = scoreIconCandidate(lower, options.outWidth, options.outHeight, isPreferred);
				if (score > bestScore) {
					bestScore = score;
					best = name;
				}
			}
		} catch (Exception e) {
			Log.w(TAG, "Can't scan icon art", e);
		}
		return bestScore > 0 ? best : null;
	}

	private boolean isImageFile(String lowerName) {
		return lowerName.endsWith(".png")
				|| lowerName.endsWith(".jpg")
				|| lowerName.endsWith(".jpeg");
	}

	private int scoreCoverCandidate(String name, int width, int height) {
		int area = width * height;
		if (area < 12000 || (width <= 72 && height <= 72)) {
			return -1000;
		}
		int score = area / 1024;
		float ratio = width / (float) height;
		if (ratio >= 1.45f && ratio <= 2.6f) {
			score += 220;
		} else if (ratio >= 0.55f && ratio <= 0.85f && height >= 160) {
			score += 70;
		} else if (ratio >= 0.9f && ratio <= 1.1f && area < 60000) {
			score -= 80;
		}
		if (name.contains("title") || name.contains("splash")
				|| name.contains("cover") || name.contains("loading")) {
			score += 180;
		}
		if (name.contains("menu/background") || name.contains("background")) {
			score += 160;
		}
		if (name.contains("logo")) {
			score += 20;
		}
		if (name.contains("font") || name.contains("sprite") || name.contains("button")
				|| name.contains("tile") || name.contains("level") || name.contains("digit")
				|| name.contains("icon") || name.contains("arrow") || name.contains("cursor")
				|| name.contains("hud") || name.contains("gui") || name.contains("soft")) {
			score -= 260;
		}
		return score;
	}

	private int scoreIconCandidate(String name, int width, int height, boolean preferred) {
		int area = width * height;
		if (area < 576) {
			return preferred ? -50 : -400;
		}
		if (width > 192 || height > 192) {
			return -200;
		}
		float ratio = width / (float) height;
		int score = Math.min(120, area / 32);
		if (ratio >= 0.75f && ratio <= 1.33f) {
			score += 140;
		} else {
			score -= 120;
		}
		if (preferred) {
			score += width >= 32 && height >= 32 ? 180 : 20;
		}
		if (name.contains("icon")) {
			score += 110;
		}
		if (name.contains("logo") || name.contains("midlet")) {
			score += 70;
		}
		if (name.contains("splash") || name.contains("title") || name.contains("background")
				|| name.contains("cover")) {
			score -= 80;
		}
		if (name.contains("font") || name.contains("sprite") || name.contains("button")
				|| name.contains("tile") || name.contains("digit") || name.contains("arrow")
				|| name.contains("hud") || name.contains("gui") || name.contains("cursor")) {
			score -= 220;
		}
		return score;
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
		manifest = loadManifest(jar);
		return manifest.equals(newDesc);
	}

	private int checkDescriptor() {
		// Remove invalid characters from app path
		String name = newDesc.getName();
		String vendor = newDesc.getVendor();
		String sourceKey = sourceKey();
		currentApp = sourceKey == null ? null : appRepository.getBySourceKey(sourceKey);
		if (currentApp == null) {
			generatePathName(name.replaceAll(FileUtils.ILLEGAL_FILENAME_CHARS, "").trim());
			return STATUS_NEW;
		}
		appDirName = currentApp.getPath();
		targetDir = new File(Config.getAppDir(), appDirName);
		int versionStatus = newDesc.compareVersion(currentApp.getVersion());
		if (versionStatus == STATUS_EQUAL && srcJar != null && currentApp.getSourceHash() != null) {
			try {
				if (!currentApp.getSourceHash().equals(SourceIdentity.sha256(srcJar))) {
					return STATUS_NEWEST;
				}
			} catch (IOException e) {
				Log.w(TAG, "Unable to compare source hash", e);
			}
		}
		return versionStatus;
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
